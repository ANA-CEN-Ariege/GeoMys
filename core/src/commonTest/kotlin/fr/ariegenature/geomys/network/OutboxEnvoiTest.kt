/*
 * GeoMys-Common — application de saisie naturaliste pour GeoNature, Android et iOS.
 * Copyright (C) 2026 ANA - CEN Ariège
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/OutboxEnvoiTest.kt) :
// Robolectric remplacé par ServicesDeTest, MockWebServer par ServeurSimule, Gson par kotlinx
// (JSON_STOCKAGE), JUnit par kotlin.test. Le multipart est celui de Ktor : ses champs se lisent par
// ServeurSimule.champMultipart.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.model.SaisieEnAttente
import fr.ariegenature.geomys.network.ServeurSimule.Companion.champMultipart
import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.network.ServeurSimule.Companion.statut
import fr.ariegenature.geomys.store.NomValeur
import fr.ariegenature.geomys.store.io.JSON_STOCKAGE
import io.ktor.http.HttpMethod
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.SYSTEM

/** Orchestration de l'envoi des Suivis ([OutboxEnvoi]) contre le serveur simulé : idempotence
 *  (envois concurrents, saisie SENDING orpheline d'un plantage), dépendances parent → enfant
 *  (résolution d'id serveur, blocage sur parent en échec) et nouvel essai ciblé. Complète
 *  OutboxMonitoringTest (état pur) en couvrant la couche réseau. */
class OutboxEnvoiTest {

    private val t = ServicesDeTest()
    private val serveur get() = t.serveur
    private val config get() = t.services.config
    private val outbox get() = t.services.outbox
    private val verrou = SynchronizedObject()

    /** Corps des POST de saisie reçus par le serveur, dans l'ordre (hors login). */
    private val postsVisite = mutableListOf<String>()
    private val prochainIdServeur = atomic(41)

    /** Codes HTTP à servir aux POST de saisie successifs (vide = toujours 200). */
    private val codesVisite = mutableListOf<Int>()

    /** Codes HTTP à servir aux POST de média successifs (vide = toujours 200). */
    private val codesMedia = mutableListOf<Int>()
    private val postsMedia = atomic(0)

    /** Réponse de la recherche par uuid `GET /monitorings/list/…` (vérification anti-doublon). Null =
     *  liste vide : l'objet n'existe pas. */
    private var reponseRechercheUuid: ServeurSimule.Reponse? = null

    /** Adresses des recherches par uuid reçues. */
    private val recherchesUuid = mutableListOf<String>()

    /** Les PATCH reçus : adresse et corps. */
    private val patchs = mutableListOf<Pair<String, String>>()

    /** Corps multipart du dernier POST média reçu. */
    private var dernierCorpsMedia: String = ""

    @BeforeTest
    fun setup() {
        // TYPE_MEDIA en cache : l'envoi des médias doit résoudre id_nomenclature_media_type (NOT
        // NULL côté gn_commons — son absence = 500 serveur, bug terrain).
        t.services.nomenclatures.setAll(mapOf("TYPE_MEDIA" to listOf(NomValeur(7, "Photo"))))
        config.apply {
            urlServeur = serveur.base
            login = "alice"; motDePasse = "pwd"; idDataset = "12"
        }
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> json("""{"access_token":"t","user":{"id_role":1}}""")
                requete.method == HttpMethod.Get && chemin.startsWith("/api/monitorings/list/") -> {
                    synchronized(verrou) { recherchesUuid.add(chemin) }
                    reponseRechercheUuid ?: json("[]")
                }
                requete.method == HttpMethod.Patch && chemin.startsWith("/api/monitorings/object/") -> {
                    synchronized(verrou) { patchs.add(chemin to requete.texte) }
                    json("""{"id":41}""")
                }
                requete.method == HttpMethod.Post && chemin.startsWith("/api/monitorings/object/") -> {
                    val code = synchronized(verrou) {
                        postsVisite.add(requete.texte)
                        if (codesVisite.isEmpty()) 200 else codesVisite.removeAt(0)
                    }
                    if (code in 200..299) {
                        json("""{"id":${prochainIdServeur.getAndIncrement()}}""", code)
                    } else {
                        json("""{"description":"boom"}""", code)
                    }
                }
                chemin.startsWith("/api/gn_commons/get_id_table_location/") -> json("1")
                requete.method == HttpMethod.Post && chemin.startsWith("/api/gn_commons/media") -> {
                    postsMedia.incrementAndGet()
                    dernierCorpsMedia = serveur.corps(requete)
                    val code = synchronized(verrou) { if (codesMedia.isEmpty()) 200 else codesMedia.removeAt(0) }
                    json("""{"id_media":7}""", code)
                }
                // Résolution du jeu de données du module, etc. — sans objet pour ces tests.
                else -> json("""{"data":[]}""")
            }
        }
    }

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private fun saisie(
        uuid: String,
        parentUuidLocal: String? = null,
        etat: SaisieEnAttente.Etat = SaisieEnAttente.Etat.PENDING,
        avecMedia: Boolean = false,
        objetCree: Boolean = false,
        idServeur: Int? = null,
        dejaTentee: Boolean = false,
        parentObjectType: String? = null,
        parentIdServeur: Int? = null,
        uuidPayload: String? = if (avecMedia) "uuid-$uuid" else null,
        uuidFieldName: String? = if (avecMedia) "uuid_base_visit" else null,
        champsManquants: List<String>? = null,
    ) = SaisieEnAttente(
        uuid = uuid, moduleCode = "STOC", objectType = "visite",
        parentObjectType = parentObjectType, parentIdServeur = parentIdServeur,
        parentUuidLocal = parentUuidLocal, parentIdField = "id_base_site",
        valeursJson = """{"comments":"test $uuid"}""", etat = etat,
        objetCree = objetCree, idServeur = idServeur, dejaTentee = dejaTentee,
        uuidPayload = uuidPayload, uuidFieldName = uuidFieldName,
        mediaPathsLocal = if (avecMedia) listOf(fichierMedia()) else emptyList(),
        mediaSchemaDotTable = if (avecMedia) "gn_monitoring.t_base_visits" else null,
        champsManquants = champsManquants,
    )

    /** Crée un vrai fichier local (l'envoi du média le lit) et renvoie son URI. */
    private fun fichierMedia(): String {
        val dossier = t.dossier / "fichiers"
        FileSystem.SYSTEM.createDirectories(dossier)
        val f = dossier / "photo_test.jpg"
        FileSystem.SYSTEM.write(f) { writeUtf8("fake-jpeg") }
        return "file://$f"
    }

    private suspend fun envoyerTout(): OutboxEnvoi.Resultat = t.services.outboxEnvoi.envoyerTout(config) { _, _, _ -> }

    // ── Report dans le cache ───────────────────────────────────────────────────────

    /** Propre au portage (décision de l'utilisateur du 20 octobre) : les écrans ne lisent que le cache,
     *  une saisie envoyée y est donc reportée sous son parent, sans attendre la synchro suivante. Le
     *  type « visite » de [saisie] sert ici aux deux niveaux. */
    @Test
    fun une_saisie_envoyee_est_reportee_dans_la_fiche_de_son_parent() = runTest {
        val cache = t.services.monitoringCache
        cache.setJson(
            cache.keyObjet("STOC", "site", 5),
            """{"id":5,"properties":{"base_site_name":"Col"},"children":{"visite":[{"id":3,"properties":{"comments":"ancienne"}}]}}""",
        )
        outbox.ajouter(saisie("visite", parentObjectType = "site", parentIdServeur = 5))
        outbox.ajouter(saisie("obs", parentUuidLocal = "visite", parentObjectType = "visite"))
        assertEquals(2, envoyerTout().succes)
        val visites = assertNotNull(t.services.monitoring.lireObjet("STOC", "site", 5)).enfants.getValue("visite")
        assertEquals(listOf(3, 41), visites.map { it.id })
        assertEquals("test visite", visites.last().proprietes["comments"])
        assertEquals("5", visites.last().proprietes["id_base_site"], "la clé du parent est reportée")
        // La visite a sa fiche, où l'observation envoyée après elle est reportée à son tour.
        val visite = assertNotNull(t.services.monitoring.lireObjet("STOC", "visite", 41))
        assertEquals(listOf(42), visite.enfants.getValue("visite").map { it.id })
    }

    // ── Idempotence ────────────────────────────────────────────────────────────────

    @Test
    fun deux_envois_concurrents_ne_postent_qu_une_fois() = runTest {
        outbox.ajouter(saisie("u1"))
        val a = async { t.services.outboxEnvoi.envoyerTout(config) { _, _, _ -> } }
        val b = async { t.services.outboxEnvoi.envoyerTout(config) { _, _, _ -> } }
        a.await(); b.await()
        assertEquals(1, postsVisite.size, "la saisie ne doit partir qu'une seule fois")
        assertTrue(outbox.tout().isEmpty(), "file vide après envoi (SENT purgé)")
    }

    @Test
    fun saisie_interrompue_avec_objet_deja_cree_repart_sans_re_poster() = runTest {
        // Plantage APRÈS la confirmation du POST (objetCree persisté) : le nouvel essai par la
        // flèche ne doit PAS recréer l'objet côté serveur (doublon sinon).
        outbox.ajouter(saisie("u1", etat = SaisieEnAttente.Etat.SENDING, objetCree = true, idServeur = 99))
        val res = envoyerTout()
        assertEquals(1, res.succes)
        assertEquals(0, postsVisite.size, "aucun re-POST : l'objet existe déjà côté serveur")
        assertTrue(outbox.tout().isEmpty(), "envoyée et purgée")
    }

    @Test
    fun saisie_interrompue_avant_confirmation_serveur_est_re_postee_au_clic() = runTest {
        // Plantage AVANT la confirmation du POST (objetCree absent) : le clic « envoyer » vaut
        // nouvel essai assumé — la saisie repart entière, en un seul clic.
        outbox.ajouter(saisie("u1", etat = SaisieEnAttente.Etat.SENDING))
        val res = envoyerTout()
        assertEquals(1, res.succes)
        assertEquals(1, postsVisite.size)
        assertTrue(outbox.tout().isEmpty())
    }

    @Test
    fun parent_interrompu_repart_avec_ses_enfants_en_un_clic() = runTest {
        // Plantage pendant l'envoi du parent : parent SENDING, enfant PENDING. Un seul envoi doit
        // faire repartir le parent PUIS l'enfant (clé résolue) — avant, l'enfant restait bloqué à vie
        // sur un parent jamais SENT.
        outbox.ajouter(saisie("parent", etat = SaisieEnAttente.Etat.SENDING))
        outbox.ajouter(saisie("enfant", parentUuidLocal = "parent"))
        val res = envoyerTout()
        assertEquals(2, res.succes)
        assertEquals(2, postsVisite.size)
        assertTrue(postsVisite[1].contains("\"id_base_site\":41"), "le POST enfant porte la clé du parent : ${postsVisite[1]}")
        assertTrue(outbox.tout().isEmpty())
    }

    // ── Dépendances parent → enfant ────────────────────────────────────────────────

    @Test
    fun enfant_envoye_apres_son_parent_avec_id_serveur_resolu() = runTest {
        outbox.ajouter(saisie("parent"))
        outbox.ajouter(saisie("enfant", parentUuidLocal = "parent"))
        val res = envoyerTout()
        assertEquals(2, res.succes)
        assertEquals(2, postsVisite.size)
        // Le parent reçoit l'id serveur 41 ; le POST de l'enfant doit le porter en clé étrangère.
        assertTrue(postsVisite[1].contains("\"id_base_site\":41"),
            "le POST enfant doit référencer l'id serveur du parent : ${postsVisite[1]}")
    }

    @Test
    fun parent_en_echec_bascule_l_enfant_en_erreur_sans_le_poster() = runTest {
        codesVisite.add(500) // le POST du parent échoue
        outbox.ajouter(saisie("parent"))
        outbox.ajouter(saisie("enfant", parentUuidLocal = "parent"))
        val res = envoyerTout()
        assertEquals(0, res.succes)
        assertEquals(2, res.echecs)
        assertEquals(1, postsVisite.size, "seul le parent a été posté")
        val enfant = outbox.tout().single { it.uuid == "enfant" }
        assertEquals(SaisieEnAttente.Etat.ERROR, enfant.etat)
        assertTrue(enfant.messageErreur!!.contains("parent en échec"))
    }

    // ── Flèche d'envoi sur un groupe en erreur (retour terrain) ────────────────────

    @Test
    fun fleche_sur_un_groupe_en_erreur_reessaye_la_visite_ET_ses_obs() = runTest {
        // État après un envoi raté (ex. coupure réseau) : visite ERROR, obs ERROR « parent en
        // échec ». Avant le correctif, la flèche ne faisait RIEN (seuls les PENDING étaient
        // traités) et « Réessayer » sur la seule visite l'envoyait sans ses obs (puis les rendait
        // orphelines à la purge : « parent introuvable »).
        outbox.ajouter(saisie("visite", etat = SaisieEnAttente.Etat.ERROR))
        outbox.ajouter(saisie("obs", parentUuidLocal = "visite", etat = SaisieEnAttente.Etat.ERROR))
        val res = t.services.outboxEnvoi.envoyerGroupe(config, "visite") { _, _, _ -> }
        assertEquals(2, res.succes, "visite ET obs envoyées")
        assertEquals(0, res.echecs)
        assertEquals(2, postsVisite.size)
        assertTrue(postsVisite[1].contains("\"id_base_site\":41"), "le POST de l'obs porte la clé de la visite : ${postsVisite[1]}")
        assertTrue(outbox.tout().isEmpty(), "file vide après envoi complet")
    }

    @Test
    fun envoyer_un_groupe_ne_requalifie_pas_les_erreurs_des_autres_groupes() = runTest {
        outbox.ajouter(saisie("g1", etat = SaisieEnAttente.Etat.ERROR))
        outbox.ajouter(saisie("g2", etat = SaisieEnAttente.Etat.ERROR))
        t.services.outboxEnvoi.envoyerGroupe(config, "g1") { _, _, _ -> }
        assertEquals(1, postsVisite.size, "seul g1 est parti")
        val g2 = outbox.tout().single { it.uuid == "g2" }
        assertEquals(SaisieEnAttente.Etat.ERROR, g2.etat, "g2 reste en erreur, non touché")
    }

    @Test
    fun envoi_partiel_garde_la_visite_en_groupe_avec_les_obs_restantes() = runTest {
        // Visite + 2 obs ; la visite et la 1re obs partent, la 2e échoue. La visite SENT doit RESTER
        // dans la file comme groupe de l'obs restante (lien local conservé) — avant, elle était
        // purgée et l'obs devenait une racine détachée.
        codesVisite.add(200); codesVisite.add(200); codesVisite.add(500)
        outbox.ajouter(saisie("visite"))
        outbox.ajouter(saisie("obs1", parentUuidLocal = "visite"))
        outbox.ajouter(saisie("obs2", parentUuidLocal = "visite"))
        envoyerTout()
        val restants = outbox.tout()
        assertEquals(setOf("visite", "obs2"), restants.map { it.uuid }.toSet(),
            "la visite (groupe) et l'obs en échec restent, obs1 purgée")
        val visite = restants.single { it.uuid == "visite" }
        assertEquals(SaisieEnAttente.Etat.SENT, visite.etat, "la visite reste à l'état envoyé (pas de re-POST possible)")
        val obs2 = restants.single { it.uuid == "obs2" }
        assertEquals("visite", obs2.parentUuidLocal, "l'obs restante reste rattachée au groupe")
        assertEquals(41, obs2.parentIdServeur, "sa clé serveur est déjà résolue")

        // La flèche du groupe n'envoie QUE le reste : pas de re-POST de la visite ni d'obs1.
        val postsAvant = postsVisite.size
        val res = t.services.outboxEnvoi.envoyerGroupe(config, "visite") { _, _, _ -> }
        assertEquals(1, res.succes)
        assertEquals(postsAvant + 1, postsVisite.size, "un seul POST de plus (obs2)")
        assertTrue(postsVisite.last().contains("\"id_base_site\":41"), "le re-POST d'obs2 porte la clé : ${postsVisite.last()}")
        assertTrue(outbox.tout().isEmpty(), "groupe complet → tout est purgé ensemble")
    }

    @Test
    fun saisie_legacy_sans_uuid_en_recoit_un_a_la_premiere_tentative() = runTest {
        // Saisie créée AVANT la génération systématique d'uuid (réservé aux médias à l'époque) :
        // uuidFieldName connu par le schéma, mais uuidPayload absent. L'envoi doit lui en attribuer
        // un et l'injecter au corps — sans identité client, l'anti-doublon est impossible si la
        // réponse de ce POST se perd.
        outbox.ajouter(saisie("u1", uuidPayload = null, uuidFieldName = "uuid_base_visit"))
        val res = envoyerTout()
        assertEquals(1, res.succes)
        assertTrue(Regex("\"uuid_base_visit\":\"[0-9a-f]{8}-[0-9a-f-]{27}\"").containsMatchIn(postsVisite[0]),
            "le POST doit porter un uuid généré : ${postsVisite[0]}")
    }

    // ── Anti-doublon : re-POST après réponse perdue ────────────────────────────────

    /** Saisie déjà tentée (réponse perdue en pleine coupure) : porte un uuid client → la vérification
     *  anti-doublon est possible. */
    private fun saisieAmbigue() = saisie(
        "obs", etat = SaisieEnAttente.Etat.ERROR, dejaTentee = true,
        parentObjectType = "site", parentIdServeur = 12,
        uuidPayload = "uuid-obs", uuidFieldName = "uuid_observation",
    )

    @Test
    fun re_envoi_ne_duplique_pas_une_saisie_deja_creee_dont_la_reponse_s_est_perdue() = runTest {
        // Le serveur a déjà l'objet : le nouvel envoi doit le DÉTECTER au lieu de re-POSTer — c'est
        // le doublon observé sur le terrain. La recherche passe par la liste filtrée sur l'uuid : la
        // fiche du parent ne donne pas l'uuid de ses enfants (gn_module_monitoring 1.3.2, C4).
        reponseRechercheUuid = json("""[{"id_observation":77,"uuid_observation":"UUID-OBS"}]""")
        outbox.ajouter(saisieAmbigue())
        val res = envoyerTout()
        assertEquals(1, res.succes)
        assertEquals(0, postsVisite.size, "AUCUN re-POST : l'objet existe déjà")
        assertTrue(outbox.tout().isEmpty(), "réconciliée et purgée")
        assertEquals(
            "/api/monitorings/list/STOC/visite?uuid_observation=uuid-obs&fields=id_observation&fields=uuid_observation",
            recherchesUuid.single(),
        )
    }

    @Test
    fun re_envoi_poste_normalement_si_l_objet_est_absent_du_serveur() = runTest {
        // Liste vide (par défaut) → l'objet n'a jamais été créé.
        outbox.ajouter(saisieAmbigue())
        val res = envoyerTout()
        assertEquals(1, res.succes)
        assertEquals(1, postsVisite.size, "le POST doit partir cette fois")
    }

    @Test
    fun re_envoi_sans_verification_possible_signale_le_doublon_potentiel() = runTest {
        // Saisie déjà tentée mais SANS uuid (vieux protocole sans uuid_field_name) : on re-POSTe —
        // pas le choix — mais le message doit avertir du doublon possible.
        outbox.ajouter(saisie("u1", etat = SaisieEnAttente.Etat.ERROR, dejaTentee = true, uuidPayload = null, uuidFieldName = null))
        val res = envoyerTout()
        assertEquals(1, res.succes)
        assertEquals(1, postsVisite.size)
        assertTrue(res.messages.any { it.contains("doublon") }, "le message doit avertir : ${res.messages}")
    }

    @Test
    fun premiere_tentative_ne_declenche_aucun_avertissement_doublon() = runTest {
        outbox.ajouter(saisie("u1")) // jamais tentée
        val res = envoyerTout()
        assertEquals(1, res.succes)
        assertTrue(res.messages.none { it.contains("doublon") }, "pas d'avertissement sur un premier envoi : ${res.messages}")
    }

    @Test
    fun une_reponse_illisible_de_la_verification_bloque_aussi_le_re_envoi() = runTest {
        // Portail captif : du HTML au lieu de la liste. Pas de re-POST à l'aveugle.
        reponseRechercheUuid = statut(200, "<html>portail</html>", "Content-Type" to "text/html")
        outbox.ajouter(saisieAmbigue())
        val res = envoyerTout()
        assertEquals(1, res.echecs)
        assertEquals(0, postsVisite.size)
    }

    @Test
    fun un_objet_cree_dont_l_id_s_est_perdu_le_retrouve_par_son_uuid() = runTest {
        // Avant le 21 octobre, cette saisie restait bloquée : « id inconnu — réessayez » à chaque
        // essai, et ses enfants ne partaient jamais.
        reponseRechercheUuid = json("""[{"id_observation":88,"uuid_observation":"uuid-obs"}]""")
        outbox.ajouter(saisieAmbigue().copy(objetCree = true, idServeur = null))
        val res = envoyerTout()
        assertEquals(1, res.succes)
        assertEquals(0, postsVisite.size, "pas de nouveau POST")
    }

    @Test
    fun verification_anti_doublon_inaccessible_bloque_le_re_envoi() = runTest {
        // Vérification impossible (réseau ou serveur en panne) : ne PAS re-POSTer à l'aveugle.
        reponseRechercheUuid = statut(500)
        outbox.ajouter(saisieAmbigue())
        val res = envoyerTout()
        assertEquals(1, res.echecs)
        assertEquals(0, postsVisite.size, "pas de re-POST aveugle")
        assertEquals(SaisieEnAttente.Etat.ERROR, outbox.tout().single().etat)
    }

    // ── Mise à jour d'un objet déjà créé (audit du 21 octobre, C3) ──

    @Test
    fun une_saisie_corrigee_apres_un_envoi_partiel_part_en_mise_a_jour() = runTest {
        // La visite est créée (#41) mais sa photo a échoué ; l'utilisateur corrige un champ par
        // « Éditer ». Avant le 21 octobre, seule la photo repartait : la correction se perdait.
        outbox.ajouter(
            saisie("u1", etat = SaisieEnAttente.Etat.ERROR, objetCree = true, idServeur = 41, uuidPayload = "uuid-u1", uuidFieldName = "uuid_base_visit")
                .copy(valeursJson = """{"comments":"corrigé","date_ponte":""}""", modifieeApresCreation = true, champsDate = listOf("date_ponte")),
        )
        val res = envoyerTout()
        assertEquals(1, res.succes)
        assertEquals(0, postsVisite.size, "pas de nouvelle création")
        val (chemin, corps) = patchs.single()
        assertEquals("/api/monitorings/object/STOC/visite/41", chemin)
        val props = JSONObject(corps).getJSONObject("properties")
        assertEquals("corrigé", props.getString("comments"))
        assertTrue(props.isNull("date_ponte"), "une date vidée part à null : absente, le serveur garderait l'ancienne")
        assertFalse(props.has("medias"), "medias: [] détacherait les photos")
        assertFalse(props.has("id_digitiser"), "le numérisateur reste celui de la création")
        assertEquals("uuid-u1", props.getString("uuid_base_visit"))
    }

    @Test
    fun une_saisie_creee_et_non_modifiee_ne_part_pas_en_mise_a_jour() = runTest {
        outbox.ajouter(saisie("u1", etat = SaisieEnAttente.Etat.ERROR, objetCree = true, idServeur = 41))
        assertEquals(1, envoyerTout().succes)
        assertTrue(patchs.isEmpty())
    }

    @Test
    fun une_mise_a_jour_refusee_laisse_la_saisie_en_erreur() = runTest {
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> json("""{"access_token":"t","user":{"id_role":1}}""")
                requete.method == HttpMethod.Patch -> json("""{"description":"boom"}""", 500)
                else -> json("""{"data":[]}""")
            }
        }
        outbox.ajouter(saisie("u1", etat = SaisieEnAttente.Etat.ERROR, objetCree = true, idServeur = 41).copy(modifieeApresCreation = true))
        val res = envoyerTout()
        assertEquals(1, res.echecs)
        val s = outbox.tout().single()
        assertEquals(SaisieEnAttente.Etat.ERROR, s.etat)
        assertTrue(s.modifieeApresCreation, "la correction repartira")
    }

    // ── Médias (objet créé, photo en échec — ex. mode avion pendant le transfert) ──

    @Test
    fun echec_media_marque_la_saisie_erreur_reessayable_au_lieu_de_sent_silencieux() = runTest {
        codesMedia.add(500) // la visite passe, la photo échoue (coupure en plein transfert)
        outbox.ajouter(saisie("u1", avecMedia = true))
        val res = envoyerTout()
        assertEquals(0, res.succes)
        assertEquals(1, res.echecs)
        // La saisie ne doit PAS avoir été purgée comme envoyée : elle reste visible en erreur, avec
        // l'explication et l'id serveur (l'objet, lui, est créé).
        val s = outbox.tout().single { it.uuid == "u1" }
        assertEquals(SaisieEnAttente.Etat.ERROR, s.etat)
        assertTrue(s.objetCree, "objet bien marqué créé côté serveur")
        assertEquals(41, s.idServeur)
        assertTrue(s.messageErreur!!.contains("média"), "le message doit expliquer l'échec média : ${s.messageErreur}")
        assertEquals(1, postsVisite.size)
    }

    @Test
    fun upload_media_monitoring_porte_le_type_de_media() = runTest {
        // id_nomenclature_media_type est NOT NULL côté gn_commons.t_medias : il doit être résolu
        // depuis le cache TYPE_MEDIA et présent dans le multipart — son absence faisait répondre
        // 500 au serveur (« photo en échec », bug terrain).
        outbox.ajouter(saisie("u1", avecMedia = true))
        val res = envoyerTout()
        assertEquals(1, res.succes)
        assertEquals(1, postsMedia.value)
        assertEquals("7", champMultipart(dernierCorpsMedia, "id_nomenclature_media_type"),
            "le multipart doit porter id_nomenclature_media_type, celui du TYPE_MEDIA « Photo » (7) : $dernierCorpsMedia")
        assertNotNull(champMultipart(dernierCorpsMedia, "uuid_attached_row"), "et le rattachement uuid_attached_row")
    }

    @Test
    fun reessayer_apres_echec_media_ne_renvoie_que_les_medias() = runTest {
        codesMedia.add(500)
        outbox.ajouter(saisie("u1", avecMedia = true))
        envoyerTout()
        assertEquals(1, postsVisite.size)
        val avantNouvelEssai = postsMedia.value
        // « Réessayer » (l'écran remet PENDING sans toucher au reste).
        outbox.mettreAJour("u1") { it.copy(etat = SaisieEnAttente.Etat.PENDING, messageErreur = null) }
        val res = envoyerTout() // le média passe cette fois (codesMedia vide → 200)
        assertEquals(1, res.succes)
        assertEquals(1, postsVisite.size, "PAS de second POST de la visite (doublon sinon)")
        assertTrue(postsMedia.value > avantNouvelEssai, "le média doit avoir été retenté")
        assertTrue(outbox.tout().isEmpty(), "saisie envoyée et purgée")
    }

    @Test
    fun enfant_envoye_meme_si_seuls_les_medias_du_parent_ont_echoue() = runTest {
        codesMedia.add(500) // photo du parent en échec ; la visite parent est créée (id 41)
        outbox.ajouter(saisie("parent", avecMedia = true))
        outbox.ajouter(saisie("enfant", parentUuidLocal = "parent"))
        val res = envoyerTout()
        assertEquals(1, res.succes, "l'enfant doit partir (la clé du parent est connue)")
        assertEquals(1, res.echecs, "seul le parent (médias) est en échec")
        assertEquals(2, postsVisite.size)
        assertTrue(postsVisite[1].contains("\"id_base_site\":41"), "le POST enfant doit porter la clé du parent créé : ${postsVisite[1]}")
        // Le parent reste en erreur ré-essayable (médias seulement) ; l'enfant est purgé.
        val restants = outbox.tout()
        assertEquals(listOf("parent"), restants.map { it.uuid })
    }

    @Test
    fun retry_apres_echec_de_l_enfant_ne_reposte_que_l_enfant() = runTest {
        // 1er envoi : parent OK (id 41), enfant 500 → ERROR avec parentIdServeur résolu.
        codesVisite.add(200); codesVisite.add(500)
        outbox.ajouter(saisie("parent"))
        outbox.ajouter(saisie("enfant", parentUuidLocal = "parent"))
        envoyerTout()
        assertEquals(2, postsVisite.size)
        val enfant = outbox.tout().single { it.uuid == "enfant" }
        assertEquals(SaisieEnAttente.Etat.ERROR, enfant.etat)
        assertEquals(41, enfant.parentIdServeur, "l'id serveur du parent doit être conservé pour le nouvel essai")
        // « Réessayer » : seul l'enfant repart — le parent (purgé après SENT) n'est pas re-posté.
        outbox.mettreAJour("enfant") { it.copy(etat = SaisieEnAttente.Etat.PENDING, messageErreur = null) }
        val res = envoyerTout()
        assertEquals(1, res.succes)
        assertEquals(3, postsVisite.size, "un seul POST de plus (l'enfant)")
        assertTrue(postsVisite[2].contains("\"id_base_site\":41"), "le re-POST de l'enfant garde la clé parent : ${postsVisite[2]}")
        assertTrue(outbox.tout().isEmpty(), "file vide après succès")
    }

    // ── Saisies « à compléter » (champs obligatoires de FIN de visite) ─────────────────
    // Décision terrain 2026-09-03 : certains protocoles imposent des infos qu'on ne connaît qu'à
    // la fin (heure de fin, température de fin). La visite est donc ENREGISTRABLE incomplète, mais
    // elle ne doit JAMAIS partir — le serveur la rejetterait, et une visite à moitié saisie côté
    // GeoNature est pire qu'une visite en attente sur le téléphone.

    @Test
    fun saisie_a_completer_n_est_jamais_envoyee() = runTest {
        outbox.ajouter(saisie("v1", champsManquants = listOf("Heure de fin")))
        val res = envoyerTout()
        assertEquals(0, serveur.requetes.size, "aucun POST")
        assertEquals(0, res.succes)
        assertEquals(1, res.echecs)
        assertTrue(res.messages.any { it.contains("Heure de fin") }, "le message nomme le champ manquant")
        // Elle reste en file, intacte, prête à être complétée.
        val restante = outbox.tout().single { it.uuid == "v1" }
        assertEquals(SaisieEnAttente.Etat.PENDING, restante.etat)
        assertTrue(restante.aCompleter)
    }

    @Test
    fun les_observations_d_une_visite_a_completer_restent_bloquees() = runTest {
        outbox.ajouter(saisie("v1", champsManquants = listOf("Température de fin")))
        outbox.ajouter(saisie("o1", parentUuidLocal = "v1"))
        val res = envoyerTout()
        assertEquals(0, serveur.requetes.size, "ni la visite ni son observation ne partent")
        assertEquals(0, res.succes)
        assertEquals(SaisieEnAttente.Etat.PENDING, outbox.tout().single { it.uuid == "o1" }.etat)
    }

    @Test
    fun une_saisie_a_completer_ne_bloque_pas_les_autres() = runTest {
        // Envoi partiel assumé (décision 2026-09-03) : bloquer tout le groupe à cause d'une visite
        // oubliée serait pénalisant sur le terrain.
        outbox.ajouter(saisie("v1", champsManquants = listOf("Heure de fin")))
        outbox.ajouter(saisie("v2"))
        val res = envoyerTout()
        assertEquals(1, res.succes, "la visite complète part")
        assertEquals(1, res.echecs)
        assertTrue(outbox.tout().none { it.uuid == "v2" }) // envoyée puis purgée
        assertTrue(outbox.tout().any { it.uuid == "v1" })
    }

    @Test
    fun saisie_completee_part_normalement() = runTest {
        outbox.ajouter(saisie("v1", champsManquants = listOf("Heure de fin")))
        assertEquals(0, envoyerTout().succes)
        // L'utilisateur complète le formulaire : plus de champ manquant.
        outbox.mettreAJour("v1") { it.copy(champsManquants = null) }
        assertEquals(1, envoyerTout().succes)
    }

    @Test
    fun saisie_ecrite_avant_le_champ_est_relue_comme_complete() {
        // Brouillon d'une version antérieure, sans le champ champsManquants : la saisie ne doit pas
        // être considérée « à compléter ».
        val ancienne = JSON_STOCKAGE.decodeFromString(
            SaisieEnAttente.serializer(),
            """{"uuid":"vx","moduleCode":"STOC","objectType":"visite","valeursJson":"{}","etat":"PENDING"}""",
        )
        assertFalse(ancienne.aCompleter)
        assertTrue(ancienne.manquants().isEmpty())
    }
}

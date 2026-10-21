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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/MediasMonitoringReconciliationTest.kt) :
// Robolectric remplacé par ServicesDeTest (fichiers dans son dossier temporaire, par okio),
// MockWebServer par ServeurSimule, JUnit par kotlin.test. Le multipart est celui de Ktor : le titre
// se lit par ServeurSimule.champMultipart.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.MediaSuivi
import fr.ariegenature.geomys.network.ServeurSimule.Companion.champMultipart
import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.store.NomValeur
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.SYSTEM

/**
 * MÉDIAS DES SUIVIS : un « Réessayer » ne doit renvoyer aucune photo déjà détenue par le serveur.
 *
 * DÉFAUT CONSTATÉ SUR LE TERRAIN (2026-09-16) : trois photos, réseau coupé pendant l'envoi ; la
 * visite part, les photos non ; réseau rétabli, ré-envoi — et UNE photo se retrouve en double sur le
 * serveur. Exactement celle qui était EN VOL au moment de la coupure.
 *
 * Mémoriser les fichiers dont la réponse est revenue ne suffit donc pas : un POST parti, reçu et
 * traité par le serveur, mais dont la RÉPONSE se perd, ne laisse aucune trace locale — et repart.
 * C'est le même défaut que R1-C1 pour les occurrences, transposé aux fichiers. La seule source de
 * vérité est le serveur lui-même : on lui demande ce qu'il détient déjà pour cet objet
 * (`GET /api/gn_commons/medias/<uuid_attached_row>`), et l'appariement se fait sur `title_fr`, que
 * l'application contrôle et rend DÉTERMINISTE par fichier.
 */
class MediasMonitoringReconciliationTest {

    private val t = ServicesDeTest()
    private val serveur get() = t.serveur
    private val config get() = t.services.config
    private val titresUploades = mutableListOf<String>()
    private var listagesDemandes = 0

    private val uuidObjet = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    private val titreBase = "visit aaaaaaaa"

    @BeforeTest
    fun setup() {
        t.services.nomenclatures.setAll(mapOf("TYPE_MEDIA" to listOf(NomValeur(7, "Photo"))))
        config.apply {
            urlServeur = serveur.base
            login = "alice"; motDePasse = "pwd"
        }
        titresUploades.clear()
        listagesDemandes = 0
    }

    @AfterTest
    fun nettoyer() = t.nettoyer()

    /** Fichiers réels : l'envoi lit le contenu sur le disque. */
    private fun fichier(nom: String): String {
        val dossier = t.dossier / "fichiers" / "medias"
        FileSystem.SYSTEM.createDirectories(dossier)
        val f = dossier / nom
        FileSystem.SYSTEM.write(f) { writeUtf8("contenu de $nom") }
        return "file://$f"
    }

    /** [dejaLa] = titres que le serveur déclare déjà détenir pour l'objet ; [fichiersLa] : les noms
     *  de fichier de ses médias (`media_path`). */
    private fun router(dejaLa: List<String>, listageEnPanne: Boolean = false, fichiersLa: List<String> = emptyList()) {
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> json("""{"access_token":"t","user":{"id_role":1}}""")
                chemin.contains("get_id_table_location") -> json("55")
                chemin.startsWith("/api/gn_commons/medias/") -> {
                    listagesDemandes++
                    if (listageEnPanne) {
                        json("{}", 500)
                    } else {
                        json(
                            (dejaLa.map { """{"id_media":1,"title_fr":"$it"}""" } +
                                fichiersLa.mapIndexed { i, f -> """{"id_media":${i + 2},"title_fr":"Mare","media_path":"55/${i + 2}_$f"}""" })
                                .joinToString(",", "[", "]"),
                        )
                    }
                }
                chemin.startsWith("/api/gn_commons/media") -> {
                    val titre = champMultipart(serveur.corps(requete), "title_fr")?.trim()
                    titresUploades.add(titre ?: "?")
                    json("""{"id_media":9}""")
                }
                else -> json("{}")
            }
        }
    }

    /** Des médias d'une saisie d'avant le 20 octobre 2026 : sans titre, titrés d'office. */
    private suspend fun envoyer(chemins: List<String>, reconcilier: Boolean) =
        envoyerMedias(chemins.map { MediaSuivi(it, schemaDotTable = "gn_monitoring.t_base_visits") }, reconcilier)

    private suspend fun envoyerMedias(medias: List<MediaSuivi>, reconcilier: Boolean) =
        t.services.upload.uploaderMediaMonitoring(
            config = config, medias = medias,
            uuidAttachedRow = uuidObjet, titreParDefaut = titreBase, auteurParDefaut = "alice",
            reconcilier = reconcilier,
        )

    @Test
    fun un_media_titre_part_avec_son_titre_et_se_reconnait_a_son_fichier() = runTest {
        val a = fichier("a.jpg"); val b = fichier("b.jpg")
        // Le serveur détient déjà b (même titre « Mare » que a) : seul a repart, avec SON titre.
        router(dejaLa = emptyList(), fichiersLa = listOf("b.jpg"))
        val medias = listOf(
            MediaSuivi(a, "Mare", "DUPONT Alice", "Vue nord", "photos", "gn_monitoring.t_base_visits"),
            MediaSuivi(b, "Mare", schemaDotTable = "gn_monitoring.t_base_visits"),
        )

        val res = envoyerMedias(medias, reconcilier = true)

        assertEquals(listOf("Mare"), titresUploades.toList(), "le titre saisi, pas un titre d'office")
        assertTrue(res.ok)
        assertEquals(2, res.transmis.size)
    }

    @Test
    fun le_reessai_ne_renvoie_pas_la_photo_dont_la_reponse_s_etait_perdue() = runTest {
        val a = fichier("a.jpg"); val b = fichier("b.jpg"); val c = fichier("c.jpg")
        // Le serveur détient déjà a (réponse reçue) ET b (POST arrivé, réponse perdue : l'appareil
        // l'ignore). Seule c doit repartir.
        router(dejaLa = listOf(
            GeoNatureUpload.titreMedia(titreBase, a),
            GeoNatureUpload.titreMedia(titreBase, b),
        ))

        val res = envoyer(listOf(a, b, c), reconcilier = true)

        assertEquals(
            listOf(GeoNatureUpload.titreMedia(titreBase, c)), titresUploades.toList(),
            "seule la photo réellement absente du serveur doit être envoyée",
        )
        assertTrue(res.ok)
        assertEquals(3, res.transmis.size, "les trois photos sont désormais acquises, y compris celles déjà détenues")
    }

    @Test
    fun un_premier_envoi_n_interroge_pas_le_serveur() = runTest {
        val a = fichier("a.jpg")
        router(dejaLa = emptyList())

        envoyer(listOf(a), reconcilier = false)

        assertEquals(0, listagesDemandes, "pas d'aller-retour inutile sur un premier envoi")
        assertEquals(1, titresUploades.size)
    }

    @Test
    fun listage_en_panne_on_envoie_quand_meme() = runTest {
        // Si l'on ne peut pas savoir ce que le serveur détient, on envoie : un doublon de photo est
        // ennuyeux, des photos de terrain qui n'arrivent jamais le sont davantage. C'est l'arbitrage
        // inverse de celui retenu pour les relevés, où un doublon est une donnée fausse en base.
        val a = fichier("a.jpg")
        router(dejaLa = emptyList(), listageEnPanne = true)

        val res = envoyer(listOf(a), reconcilier = true)

        assertEquals(1, titresUploades.size)
        assertTrue(res.ok)
    }

    @Test
    fun le_titre_ne_depend_pas_de_la_position_dans_la_liste() {
        // Un titre indexé « (1) », « (2) » se décalait dès qu'un fichier était sauté, rendant toute
        // réconciliation impossible : le même fichier changeait de nom d'un essai à l'autre.
        val b = fichier("b.jpg")
        assertEquals(
            GeoNatureUpload.titreMedia(titreBase, b),
            GeoNatureUpload.titreMedia(titreBase, b),
        )
        assertTrue(GeoNatureUpload.titreMedia(titreBase, b).endsWith("b.jpg"))
    }
}

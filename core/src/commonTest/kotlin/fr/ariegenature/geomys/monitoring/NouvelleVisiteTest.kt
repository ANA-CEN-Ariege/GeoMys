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

// Gardes du lot 7 sur ce que NouvelleVisiteFragment de GeoMys-Android @ cdd5196 faisait sans test : les
// phrases accordées au type créé, la préparation du formulaire (options, défauts, liste taxonomique, champ
// du parent) et ce qui suit un enregistrement.

package fr.ariegenature.geomys.monitoring

import fr.ariegenature.geomys.model.SaisieEnAttente
import fr.ariegenature.geomys.monitoring.form.EditableField
import fr.ariegenature.geomys.monitoring.form.ValeursFormulaire
import fr.ariegenature.geomys.monitoring.form.ViewType
import fr.ariegenature.geomys.monitoring.form.construireFormulaire
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringPropertySchema
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringSchemaObjet
import fr.ariegenature.geomys.network.MonitoringApi.OptionDatalist
import fr.ariegenature.geomys.util.DateHeureDefaut
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NouvelleVisiteTest {

    private fun prop(nom: String, widget: String, label: String = nom, obligatoire: Boolean = false, apiUrl: String? = null, multiple: Boolean = false) =
        MonitoringPropertySchema(nom, widget, label, obligatoire, apiUrl = apiUrl, multiple = multiple)

    private val visite = MonitoringSchemaObjet(
        type = "visit", label = "Passage", labelList = "Passages", nameField = null, parentType = "dalle",
        childrenTypes = listOf("observation"),
        properties = linkedMapOf(
            "id_dalle" to prop("id_dalle", "datalist", apiUrl = "monitorings/list/x/dalle"),
            "visit_date_min" to prop("visit_date_min", "date", "Date", obligatoire = true),
            "heure" to prop("heure", "time"),
            "id_dataset" to prop("id_dataset", "dataset", "Jeu de données", obligatoire = true, apiUrl = "meta/datasets?module_code=x"),
            "observers" to prop("observers", "observers", "Observateurs", apiUrl = "users/menu/1", multiple = true),
            "stade" to prop("stade", "nomenclature", "Stade", apiUrl = "nomenclatures/nomenclature/STADE")
                .copy(defaultObjet = mapOf("cd_nomenclature" to "2")),
            "cd_nom" to prop("cd_nom", "taxonomy", "Espèce").copy(defaultValue = "4117"),
            "comments" to prop("comments", "textarea", "Commentaire"),
        ),
        uuidFieldName = "uuid_base_visit",
        genre = "M",
    )
    private val dalle = MonitoringSchemaObjet(
        type = "dalle", label = "Dalle", labelList = "Dalles", nameField = null, parentType = "module",
        childrenTypes = listOf("visit"), idFieldName = "id_dalle",
    )
    private val module = MonitoringSchemaObjet(
        type = "module", label = "Protocole", labelList = null, nameField = null, parentType = null,
        childrenTypes = listOf("dalle"), idListTaxonomy = 100,
    )

    private val options = mapOf(
        "meta/datasets?module_code=x" to listOf(OptionDatalist("12", "Jeu unique")),
        "users/menu/1" to listOf(OptionDatalist("3", "DUPONT Alice"), OptionDatalist("7", "MARTIN Paul")),
        "nomenclatures/nomenclature/STADE" to listOf(
            OptionDatalist("801", "Adulte", cdNomenclature = "1"),
            OptionDatalist("802", "Juvénile", cdNomenclature = "2"),
        ),
    )

    private class Sources(
        val schemaRendu: Map<String, MonitoringSchemaObjet>?,
        val optionsRendues: (MonitoringPropertySchema) -> List<OptionDatalist>?,
        override val idRoleUtilisateur: Int = 7,
    ) : SourcesFormulaire {
        override val urlServeur = "https://geonature.exemple.org/"
        override suspend fun schema(moduleCode: String) = schemaRendu
        override suspend fun options(prop: MonitoringPropertySchema) = optionsRendues(prop)
    }

    @Test
    fun titres_accordes_au_genre_et_a_la_voyelle() {
        assertEquals("Nouvelle visite", NouvelleVisite.titreNouvelle("Visite", "F"))
        assertEquals("Nouvelle visite", NouvelleVisite.titreNouvelle("Visite", null))
        assertEquals("Nouveau passage", NouvelleVisite.titreNouvelle("Passage", "M"))
        assertEquals("Nouvel inventaire", NouvelleVisite.titreNouvelle("Inventaire", "m"))
        assertEquals("Édition de la visite", NouvelleVisite.titreEdition("Visite", null))
        assertEquals("Édition du passage", NouvelleVisite.titreEdition("Passage", "M"))
        assertEquals("Édition de l'observation", NouvelleVisite.titreEdition("Observation", "F"))
        assertEquals("Modifier…", NouvelleVisite.titreProvisoire(edition = true, libelleNouveau = "Nouvelle visite"))
        assertEquals("Nouvelle entrée", NouvelleVisite.titreProvisoire(edition = false, libelleNouveau = null))
        assertEquals("Sur : Col de Port — Protocole : stom", NouvelleVisite.contexte("Col de Port", "stom"))
        assertEquals("Protocole : stom", NouvelleVisite.contexte("", "stom"))
    }

    @Test
    fun phrases_accordees_au_type_cree() {
        val passage = NouvelleVisite.Accords("Passage", "M")
        assertEquals("Passage non enregistré", passage.titreNonEnregistre)
        assertEquals("Continuer le passage", passage.boutonContinuer)
        val observation = NouvelleVisite.Accords("Observation", "F")
        assertEquals("Continuer l'observation", observation.boutonContinuer)
        assertEquals("Observation enregistrée localement — envoi à la demande depuis « Mes visites »", observation.messageEnregistre(emptyList()))
        // Avant le chargement du schéma : « entrée », féminin.
        assertEquals("Entrée non enregistrée", NouvelleVisite.Accords(null, null).titreNonEnregistre)
        assertEquals(
            "Enregistrer la visite en cours avant de quitter ?\n\n1 champ à compléter avant l'envoi : Heure de fin",
            NouvelleVisite.Accords("Visite", "F").questionEnregistrer(listOf("Heure de fin")),
        )
    }

    @Test
    fun les_manquants_cites_sont_trois_au_plus() {
        assertEquals("1 champ à compléter avant l'envoi : A", NouvelleVisite.messageManquants(listOf("A")))
        assertEquals("4 champs à compléter avant l'envoi : A, B, C… (+1)", NouvelleVisite.messageManquants(listOf("A", "B", "C", "D")))
        assertEquals("Modifications enregistrées", NouvelleVisite.messageModifie(emptyList()))
        assertEquals(
            "La visite a été enregistrée sans 2 champs obligatoires : Heure de fin, Vent.\n\nCes informations sont " +
                "maintenant connues — les saisir permet d'envoyer la visite et ses observations.",
            NouvelleVisite.messageCompleter(listOf("Heure de fin", "Vent")),
        )
        val champs = construireFormulaire(visite).fields
        assertEquals(listOf("Date", "heure"), NouvelleVisite.libellesManquants(listOf("visit_date_min", "heure", "visit_date_min"), champs))
    }

    @Test
    fun l_utilisateur_connecte_est_pose_meme_hors_de_la_liste_comme_le_web() = runTest {
        val champs = construireFormulaire(visite).fields
        val (enrichis, _) = NouvelleVisite.enrichirAvecOptions(champs, visite, null, 42, "MARTIN Alice") { p -> options[p.apiUrl] }
        val observateurs = enrichis.single { it.code == "observers" }
        assertEquals(listOf("42"), observateurs.value)
        assertEquals("MARTIN Alice", observateurs.values.single { it.value == "42" }.label, "le menu le montre")
    }

    @Test
    fun une_propriete_masquee_requise_sans_valeur_bloque_comme_sur_le_web() {
        val schema = visite.copy(
            properties = visite.properties +
                ("id_jdd" to prop("id_jdd", "datalist", "JDD associé", apiUrl = "meta/datasets?module_code=x").copy(hiddenBool = true, obligatoire = true)) +
                ("id_digitiser" to prop("id_digitiser", "text", "Numérisateur").copy(hiddenBool = true, obligatoire = true)),
        )
        assertEquals(listOf("JDD associé"), NouvelleVisite.proprietesMasqueesManquantes(schema, "id_dalle", emptyMap()))
        assertEquals(emptyList(), NouvelleVisite.proprietesMasqueesManquantes(schema, "id_dalle", mapOf("id_jdd" to 12)))
    }

    @Test
    fun options_chargees_puis_valeurs_initiales_du_web() = runTest {
        val champs = construireFormulaire(visite).fields
        val (enrichis, echecs) = NouvelleVisite.enrichirAvecOptions(champs, visite, null, 7) { p -> options[p.apiUrl] }
        val parCode = enrichis.associateBy { it.code }
        // Le champ du parent n'a pas d'options : échec signalé.
        assertEquals(listOf("id_dalle (fetch options échoué)"), echecs)
        // Jeu de données unique et requis : choisi d'office, et le champ reste affiché.
        assertEquals("12", parCode.getValue("id_dataset").value)
        // L'utilisateur connecté parmi les observateurs.
        assertEquals(listOf("7"), parCode.getValue("observers").value)
        // `value` objet résolue par son cd_nomenclature.
        assertEquals("802", parCode.getValue("stade").value)
        // `value` d'un champ espèce : son cd_nom, comme le web.
        assertEquals(4117, parCode.getValue("cd_nom").value)
        // La date du jour dans la date de visite ; aucune heure par défaut.
        assertNotNull(parCode.getValue("visit_date_min").value)
        assertNull(parCode.getValue("heure").value)
        assertNull(parCode.getValue("comments").value)
    }

    @Test
    fun valeurs_initiales_que_le_web_ne_pose_pas() = runTest {
        val objet = MonitoringSchemaObjet(
            type = "observation", label = "Observation", labelList = null, nameField = null, parentType = "visit", childrenTypes = emptyList(),
            properties = linkedMapOf(
                // Option unique d'une liste NON requise : rien n'est choisi.
                "methode" to prop("methode", "datalist", apiUrl = "nomenclatures/nomenclature/METH").copy(keyValue = "id_nomenclature"),
                // `default` sans la clé de valeur de la liste : ignoré, comme le composant datalist du web.
                "stade" to prop("stade", "datalist", apiUrl = "nomenclatures/nomenclature/STADE")
                    .copy(keyValue = "id_nomenclature", defautDatalist = mapOf("label_default" to "Juvénile")),
                // `default` qui porte la clé de valeur : appliqué.
                "sexe" to prop("sexe", "datalist", apiUrl = "nomenclatures/nomenclature/SEXE")
                    .copy(keyValue = "id_nomenclature", defautDatalist = mapOf("id_nomenclature" to "902")),
                // Une date autre que celles de la visite : pas de date du jour.
                "date_ponte" to prop("date_ponte", "date"),
                // Un champ d'observateurs autre que `observers` : pas d'utilisateur connecté.
                "id_operateur" to prop("id_operateur", "observers", apiUrl = "users/menu/1"),
                "nb" to prop("nb", "number").copy(defaultValue = "0"),
            ),
        )
        val opts = mapOf(
            "nomenclatures/nomenclature/METH" to listOf(OptionDatalist("701", "À vue", cdNomenclature = "1")),
            "nomenclatures/nomenclature/STADE" to options.getValue("nomenclatures/nomenclature/STADE"),
            "nomenclatures/nomenclature/SEXE" to listOf(OptionDatalist("901", "Femelle"), OptionDatalist("902", "Mâle")),
            "users/menu/1" to options.getValue("users/menu/1"),
        )
        val (enrichis, _) = NouvelleVisite.enrichirAvecOptions(construireFormulaire(objet).fields, objet, null, 7) { p -> opts[p.apiUrl] }
        val parCode = enrichis.associateBy { it.code }
        assertNull(parCode.getValue("methode").value)
        assertNull(parCode.getValue("stade").value)
        assertEquals("902", parCode.getValue("sexe").value)
        assertNull(parCode.getValue("date_ponte").value)
        assertNull(parCode.getValue("id_operateur").value)
        assertEquals("0", parCode.getValue("nb").value, "la `value` du schéma")
    }

    @Test
    fun observateur_masque_sans_utilisateur_connecte() = runTest {
        val avecObservateursMasques = visite.copy(
            properties = visite.properties + ("observers" to visite.properties.getValue("observers").copy(hiddenBool = true)),
        )
        val (enrichis, _) = NouvelleVisite.enrichirAvecOptions(
            construireFormulaire(visite).fields, avecObservateursMasques, null, 7,
        ) { p -> options[p.apiUrl] }
        assertNull(enrichis.single { it.code == "observers" }.value)
    }

    @Test
    fun valeurs_fixes_des_proprietes_masquees() = runTest {
        val objet = MonitoringSchemaObjet(
            type = "observation", label = "Observation", labelList = null, nameField = null, parentType = "visit", childrenTypes = emptyList(),
            properties = linkedMapOf(
                "id_base_visit" to prop("id_base_visit", "number").copy(hiddenBool = true),
                // Protocole mono-espèce : le cd_nom fixe (nidif_gypa).
                "cd_nom" to prop("cd_nom", "text").copy(hiddenBool = true, defaultValue = "2852", valeurTypee = 2852),
                "code" to prop("code", "text").copy(hiddenBool = true, defaultValue = "007", valeurTypee = "007"),
                // Objet nomenclature d'un champ texte masqué (suivi_nardaie).
                "technique" to prop("technique", "text", apiUrl = "nomenclatures/nomenclature/TECHNIQUE_OBS")
                    .copy(hiddenBool = true, defaultObjet = mapOf("code_nomenclature_type" to "TECHNIQUE_OBS", "cd_nomenclature" to "133")),
                // Jeu de données masqué et requis : son option unique.
                "id_dataset" to prop("id_dataset", "dataset", obligatoire = true, apiUrl = "meta/datasets?module_code=x").copy(hiddenBool = true),
                // Masqué sans valeur : rien (l'envoi le complète à null).
                "id_digitiser" to prop("id_digitiser", "text", obligatoire = true).copy(hiddenBool = true),
                "comments" to prop("comments", "text"),
            ),
        )
        val opts = mapOf(
            "nomenclatures/nomenclature/TECHNIQUE_OBS" to listOf(OptionDatalist("1330", "Vu", cdNomenclature = "133")),
            "meta/datasets?module_code=x" to options.getValue("meta/datasets?module_code=x"),
        )
        val fixes = NouvelleVisite.valeursFixes(objet, "id_base_visit") { p -> opts[p.apiUrl] }
        assertEquals(mapOf<String, Any>("cd_nom" to 2852, "code" to "007", "technique" to 1330, "id_dataset" to 12), fixes)

        // À l'enregistrement : elles partent, le texte reste un texte, le formulaire l'emporte.
        val enregistrement = ValeursFormulaire.preparerEnregistrement(
            construireFormulaire(objet).fields, mapOf("comments" to "RAS", "cd_nom" to 3511), fixes,
        )
        val valeurs = ValeursFormulaire.parseValeursJson(enregistrement.valeursJson)
        assertEquals(3511, valeurs["cd_nom"])
        assertEquals(1330, valeurs["technique"])
        assertTrue("code" in enregistrement.champsTexteLibre)
        assertFalse("cd_nom" in enregistrement.champsTexteLibre)
    }

    @Test
    fun valeurs_fixes_dates_du_jour_types_du_web_et_saisie_rouverte() = runTest {
        val objet = MonitoringSchemaObjet(
            type = "visit", label = "Visite", labelList = null, nameField = null, parentType = "site", childrenTypes = emptyList(),
            properties = linkedMapOf(
                // Masquée dans le schéma générique : le web la met au jour (setDefaultFormValue) ; ici,
                // l'envoi lui donne la date de début (décision du 21 octobre).
                "visit_date_max" to prop("visit_date_max", "date", obligatoire = true).copy(hiddenBool = true),
                "visit_date_min" to prop("visit_date_min", "date").copy(hiddenBool = true),
                "id_dataset" to prop("id_dataset", "dataset", obligatoire = true, apiUrl = "meta/datasets?module_code=x").copy(hiddenBool = true),
                "code" to prop("code", "text").copy(hiddenBool = true, defaultValue = "007", valeurTypee = "007"),
                "nb_observations" to prop("nb_observations", "number").copy(hiddenBool = true),
            ),
        )
        val opts = listOf(OptionDatalist("12", "Jeu 12", brut = 12))
        val neuve = NouvelleVisite.valeursFixes(objet, null) { opts }
        assertEquals(mapOf<String, Any>("id_dataset" to 12, "code" to "007", "visit_date_min" to DateHeureDefaut.dateDuJour()), neuve)
        // Requise et masquée, la date de fin ne bloque pas l'enregistrement : l'envoi la pose.
        assertEquals(emptyList(), NouvelleVisite.proprietesMasqueesManquantes(objet, null, neuve))

        // Rouverte : chaque propriété masquée garde la valeur enregistrée, comme le web garde celle
        // de l'objet ; sauf la date de fin, que l'envoi reprend de la date de début (une saisie
        // enregistrée entre le 20 et le 21 octobre portait la date de saisie).
        val rouverte = NouvelleVisite.valeursFixes(objet, null, mapOf("visit_date_max" to "2026-10-01", "code" to "008")) { opts }
        assertNull(rouverte["visit_date_max"])
        assertEquals("008", rouverte["code"])
    }

    @Test
    fun hors_ligne_une_saisie_rouverte_garde_ses_valeurs() = runTest {
        val champs = construireFormulaire(visite).fields
        val sauvees = mapOf<String, Any?>("observers" to listOf("3", "7"), "stade" to "801")
        val (enrichis, echecs) = NouvelleVisite.enrichirAvecOptions(champs, visite, sauvees, 0) { null }
        val parCode = enrichis.associateBy { it.code }
        assertEquals(4, echecs.size)
        assertEquals(listOf("3", "7"), parCode.getValue("observers").values.map { it.value })
        assertEquals("801 (option non rechargée — hors-ligne ?)", parCode.getValue("stade").values.single().label)
        // Sans options, rien n'est choisi d'office ; la valeur viendra du pré-remplissage.
        assertNull(parCode.getValue("stade").value)
    }

    @Test
    fun preparer_retire_le_champ_du_parent_et_ne_restreint_pas_un_champ_espece_sans_liste() = runTest {
        val sources = Sources(mapOf("module" to module, "dalle" to dalle, "visit" to visite), { p -> options[p.apiUrl] })
        val prete = assertIs<PreparationFormulaire.Prete>(NouvelleVisite.preparer(sources, "x", "visit", "dalle", null, emptyList()))
        val f = prete.formulaire
        assertFalse(f.champs.any { it.code == "id_dalle" })
        assertEquals("id_dalle", f.parentIdField)
        // Sans `id_list` propre, tout TaxRef, comme le web, même si le module déclare une liste
        // (décision du 21 octobre ; la 0.2.0 lui donnait celle du module).
        assertNull(f.champs.single { it.viewType == ViewType.TAXON }.idListeTaxonomieRestreinte)
        assertEquals("observation", f.typeSaisieEnfant)
        assertEquals("Passage", f.labelType)
        assertEquals(visite.properties.keys.toList(), f.nomsChamps)
        assertTrue(f.avertissements.isEmpty())
    }

    @Test
    fun sans_liste_du_protocole_le_champ_espece_n_est_pas_restreint() = runTest {
        // Le web ne restreint pas un champ espèce à la liste d'un jeu de données.
        val sources = Sources(mapOf("dalle" to dalle, "visit" to visite), { p -> options[p.apiUrl] })
        val f = assertIs<PreparationFormulaire.Prete>(NouvelleVisite.preparer(sources, "x", "visit", "dalle", null, emptyList())).formulaire
        assertNull(f.champs.single { it.viewType == ViewType.TAXON }.idListeTaxonomieRestreinte)
    }

    @Test
    fun preparation_impossible_dit_pourquoi() = runTest {
        val sansSchema = NouvelleVisite.preparer(Sources(null, { null }), "stom", "visit", "site", null, emptyList())
        assertEquals(
            "⚠ /api/monitorings/config/stom a renvoyé null.\nTeste dans ton navigateur :\nhttps://geonature.exemple.org/api/monitorings/config/stom",
            assertIs<PreparationFormulaire.Impossible>(sansSchema).messages.single(),
        )
        val sansType = NouvelleVisite.preparer(Sources(mapOf("dalle" to dalle), { null }), "x", "", "dalle", null, emptyList())
        assertEquals("⚠ Type 'visit/visite/passage/…' absent du schéma.", assertIs<PreparationFormulaire.Impossible>(sansType).messages.single())
    }

    @Test
    fun seules_les_informations_de_fin_d_une_visite_peuvent_attendre() {
        // 22 octobre : « seule l'heure de fin, si elle est demandée, n'est pas obligatoire au début »,
        // puis les autres informations de fin, « seulement si elles sont obligatoires ». Celles des
        // protocoles de l'ANA, puis ce qui n'en est pas une.
        fun champ(code: String, type: ViewType, libelle: String) = EditableField(code, type, libelle, obligatoire = true)
        val infosDeFin = listOf(
            champ("heure_fin_prospection", ViewType.TIME, "Heure de fin de prospection"),
            champ("Heure_fin", ViewType.TIME, "Heure de fin"),
            champ("time_end", ViewType.TIME, "Heure de fin du relevé"),
            champ("end_hour", ViewType.TIME, "Heure de fin"),
            champ("heureFin", ViewType.TIME, "Fin"),
            champ("h2", ViewType.TIME, "Heure de fin"),
            champ("date_heure_fin", ViewType.DATETIME, "Fin de l'écoute"),
            champ("end_temperature", ViewType.NUMBER, "Température à la fin du relevé"),
            champ("visit_time", ViewType.NUMBER, "Durée de la visite (min)"),
            champ("duration", ViewType.NUMBER, "Durée de parcours du transect (en mn) *"),
            champ("visit_date_max", ViewType.DATE, "Date de relevé du piège*"),
        )
        infosDeFin.forEach { assertTrue(NouvelleVisite.estInfoDeFin(it), it.code) }
        val autres = listOf(
            champ("heure_debut", ViewType.TIME, "Heure de début"),
            champ("start_temperature", ViewType.NUMBER, "Température au début du relevé"),
            champ("temperature", ViewType.SELECT, "Température (°C)"),
            champ("visit_date_min", ViewType.DATE, "Date"),
            champ("temps_identification", ViewType.NUMBER, "Temps passé à identifier (en minutes, entre 30 et 180)"),
            champ("weekend", ViewType.TIME, "Heure du week-end"),
            champ("remarque", ViewType.TEXT, "Remarque afin de préciser"),
        )
        autres.forEach { assertFalse(NouvelleVisite.estInfoDeFin(it), it.code) }
        val champs = infosDeFin.take(1) + infosDeFin.takeLast(1) + autres.take(2)
        val manquants = listOf("heure_fin_prospection", "heure_debut", "visit_date_max", "start_temperature")
        assertEquals(listOf("heure_debut", "start_temperature"), NouvelleVisite.manquantsBloquants(manquants, champs, incompletAutorise = true))
        assertEquals(manquants, NouvelleVisite.manquantsBloquants(manquants, champs, incompletAutorise = false), "une saisie d'espèce doit être complète")
        assertEquals(listOf("heure_fin_prospection", "visit_date_max"), NouvelleVisite.manquantsACompleter(manquants, champs))
    }

    @Test
    fun chaine_de_saisies_de_geomys_android() = runTest {
        // 22 octobre : sous une visite, « Enregistrer + suivante » enchaîne les observations et
        // « Terminer » sort de la chaîne, comme dans GeoMys-Android ; le web n'enchaînait que sur un
        // type `chained`, son interrupteur mis.
        assertTrue(NouvelleVisite.estModeChaine("visit", edition = false))
        assertFalse(NouvelleVisite.estModeChaine("visit", edition = true), "jamais en édition")
        assertFalse(NouvelleVisite.estModeChaine("site", edition = false), "une visite sous un site")
        assertFalse(NouvelleVisite.estModeChaine(null, edition = false))
        assertEquals("Enregistrer + suivante", NouvelleVisite.libelleBouton(edition = false, enchainer = false, chaine = true))
        assertEquals("Valider", NouvelleVisite.libelleBouton(edition = true, enchainer = false, chaine = true))
        assertEquals("Valider et enchaîner les saisies", NouvelleVisite.libelleBouton(edition = false, enchainer = true))
        assertEquals("Valider", NouvelleVisite.libelleBouton(edition = false, enchainer = false))
        val sources = Sources(mapOf("module" to module, "dalle" to dalle, "visit" to visite), { p -> options[p.apiUrl] })
        val f = assertIs<PreparationFormulaire.Prete>(NouvelleVisite.preparer(sources, "x", "visit", "dalle", null, emptyList())).formulaire
        val enregistrement = ValeursFormulaire.Enregistrement("{}", emptyList(), null, emptyList())
        // Une observation : pas de saisie enfant.
        val observation = f.copy(typeSaisieEnfant = null)
        val sousVisite = NouvelleVisite.nouvelleSaisieEnAttente("x", observation, enregistrement, "visit", null, "u-visite", emptyList())
        // « Enregistrer + suivante » : le formulaire suivant, les champs `keep` repris.
        val avecGardes = observation.copy(schemaObjet = observation.schemaObjet.copy(gardes = listOf("heure")))
        assertEquals(
            SuiteEnregistrement.Reinitialiser(mapOf("heure" to "06:30")),
            NouvelleVisite.suiteApresCreation(sousVisite, avecGardes, enchainer = false, puisTerminer = false, mapOf("heure" to "06:30"), chaine = true),
        )
        // « Terminer » : la fin des saisies, la visite parente « à compléter » proposée.
        assertEquals(
            SuiteEnregistrement.TerminerOuCompleter,
            NouvelleVisite.suiteApresCreation(sousVisite, observation, enchainer = false, puisTerminer = true, chaine = true),
        )
        assertEquals(
            "L'observation en cours est incomplète (champ obligatoire manquant ou valeur hors limites) et ne peut pas être enregistrée. Quitter et la perdre ?",
            NouvelleVisite.Accords("observation", "F").messageQuitterIncomplet,
        )
    }

    @Test
    fun suites_d_un_enregistrement() = runTest {
        val sources = Sources(mapOf("module" to module, "dalle" to dalle, "visit" to visite), { p -> options[p.apiUrl] })
        val f = assertIs<PreparationFormulaire.Prete>(NouvelleVisite.preparer(sources, "x", "visit", "dalle", null, emptyList())).formulaire
        val enregistrement = ValeursFormulaire.Enregistrement("{}", emptyList(), null, emptyList())
        val saisie = NouvelleVisite.nouvelleSaisieEnAttente("x", f, enregistrement, "dalle", 31, null, listOf("Heure"))
        assertNotNull(saisie.uuidPayload)
        assertEquals("id_dalle", saisie.parentIdField)
        assertEquals(listOf("Heure"), saisie.champsManquants)
        // « Valider » d'une visite : la saisie de ses observations, sous elle, comme GeoMys-Android
        // (22 octobre : il remplace « Valider et saisir des Observations »).
        assertEquals(
            SuiteEnregistrement.Enchainer("visit", saisie.uuid, "observation", "Passage (local)"),
            NouvelleVisite.suiteApresCreation(saisie, f, enchainer = false, puisTerminer = false),
        )
        // Sans le droit de créer des observations, ou sans saisie enfant : la page du parent serveur
        // (la saisie locale n'a pas de page).
        assertEquals(
            SuiteEnregistrement.Fiche("dalle", 31),
            NouvelleVisite.suiteApresCreation(saisie, f, enchainer = false, puisTerminer = false, enfantsPermis = false),
        )
        val sansEnfant = f.copy(typeSaisieEnfant = null)
        assertEquals(SuiteEnregistrement.Fiche("dalle", 31), NouvelleVisite.suiteApresCreation(saisie, sansEnfant, enchainer = false, puisTerminer = false))
        // « Valider et enchaîner les saisies » d'un type `chained` : formulaire suivant, les champs
        // `keep` repris.
        val avecGardes = sansEnfant.copy(schemaObjet = sansEnfant.schemaObjet.copy(gardes = listOf("heure", "absent")))
        assertEquals(
            SuiteEnregistrement.Reinitialiser(mapOf("heure" to "06:30")),
            NouvelleVisite.suiteApresCreation(saisie, avecGardes, enchainer = true, puisTerminer = false, mapOf("heure" to "06:30", "meteo" to "Beau")),
        )
        // Sous une saisie locale : fin de ses saisies (visite parente à compléter) ; sortie par la garde,
        // sans enchaîner.
        val sousVisite = saisie.copy(parentObjectType = "visit", parentIdServeur = null, parentUuidLocal = "u-visite")
        assertEquals(SuiteEnregistrement.TerminerOuCompleter, NouvelleVisite.suiteApresCreation(sousVisite, sansEnfant, false, false))
        assertEquals(SuiteEnregistrement.TerminerOuCompleter, NouvelleVisite.suiteApresCreation(saisie, f, false, puisTerminer = true))
        assertEquals(
            SuiteEnregistrement.Retour,
            NouvelleVisite.suiteApresCreation(saisie.copy(parentIdServeur = null), sansEnfant, false, false),
        )
        // Modification : complétion → retour ; sinon la fiche du parent serveur.
        val ancienne = saisie.copy(etat = SaisieEnAttente.Etat.ERROR, messageErreur = "400")
        val modifiee = NouvelleVisite.saisieModifiee(ancienne, f, enregistrement, emptyList())
        assertEquals(SaisieEnAttente.Etat.PENDING, modifiee.etat)
        assertNull(modifiee.messageErreur)
        assertNull(modifiee.champsManquants)
        assertFalse(modifiee.modifieeApresCreation, "pas encore créée : la création portera tout")
        // Déjà créée sur le serveur (envoi partiel) : la modification partira en mise à jour, si
        // les valeurs ont changé (audit du 21 octobre, C3).
        val creee = ancienne.copy(objetCree = true, idServeur = 31, valeursJson = """{"comments":"avant correction"}""")
        assertTrue(NouvelleVisite.saisieModifiee(creee, f, enregistrement, emptyList()).modifieeApresCreation)
        val inchangee = creee.copy(valeursJson = enregistrement.valeursJson)
        assertFalse(NouvelleVisite.saisieModifiee(inchangee, f, enregistrement, emptyList()).modifieeApresCreation)
        // Modifiée, une visite enchaîne sur d'éventuelles nouvelles observations (22 octobre) ; pas en
        // complétion, ni à la sortie par la garde, ni sans le droit d'en créer.
        assertEquals(
            SuiteEnregistrement.Enchainer("visit", saisie.uuid, "observation", "Passage (local)"),
            NouvelleVisite.suiteApresEdition(saisie.uuid, f, modeCompletion = false, puisTerminer = false, "dalle", 31),
        )
        assertEquals(SuiteEnregistrement.Retour, NouvelleVisite.suiteApresEdition(saisie.uuid, f, modeCompletion = true, puisTerminer = false, "dalle", 31))
        assertEquals(SuiteEnregistrement.Fiche("dalle", 31), NouvelleVisite.suiteApresEdition(saisie.uuid, f, modeCompletion = false, puisTerminer = true, "dalle", 31))
        assertEquals(
            SuiteEnregistrement.Fiche("dalle", 31),
            NouvelleVisite.suiteApresEdition(saisie.uuid, f, modeCompletion = false, puisTerminer = false, "dalle", 31, enfantsPermis = false),
        )
        assertEquals(SuiteEnregistrement.Fiche("dalle", 31), NouvelleVisite.suiteApresEdition(saisie.uuid, sansEnfant, false, false, "dalle", 31))
        assertEquals(SuiteEnregistrement.Retour, NouvelleVisite.suiteApresEdition(saisie.uuid, sansEnfant, false, false, null, null))
    }
}

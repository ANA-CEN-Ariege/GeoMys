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
import fr.ariegenature.geomys.monitoring.form.ValeursFormulaire
import fr.ariegenature.geomys.monitoring.form.ViewType
import fr.ariegenature.geomys.monitoring.form.construireFormulaire
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringPropertySchema
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringSchemaObjet
import fr.ariegenature.geomys.network.MonitoringApi.OptionDatalist
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
            "id_dataset" to prop("id_dataset", "dataset", "Jeu de données", apiUrl = "meta/datasets?module_code=x"),
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
        override val datasetsCacheJson: String = "",
        override val idRoleUtilisateur: Int = 7,
        val viaLive: Int? = null,
    ) : SourcesFormulaire {
        var appelsLive = 0
        override val urlServeur = "https://geonature.exemple.org/"
        override suspend fun schema(moduleCode: String) = schemaRendu
        override suspend fun options(prop: MonitoringPropertySchema) = optionsRendues(prop)
        override suspend fun idListeViaDatasetLive(moduleCode: String): Int? {
            appelsLive++
            return viaLive
        }
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
        assertEquals(
            "Le passage en cours est incomplet (champ obligatoire manquant ou valeur hors limites) et ne peut pas " +
                "être enregistré. Quitter et le perdre ?",
            passage.messageQuitterIncomplet,
        )
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
    fun options_chargees_puis_defauts_poses() = runTest {
        val champs = construireFormulaire(visite).fields
        val (enrichis, echecs) = NouvelleVisite.enrichirAvecOptions(champs, visite, null, 7) { p -> options[p.apiUrl] }
        val parCode = enrichis.associateBy { it.code }
        // Le champ du parent n'a pas d'options : échec signalé.
        assertEquals(listOf("id_dalle (fetch options échoué)"), echecs)
        // Jeu de données unique : choisi d'office et masqué.
        assertEquals("12", parCode.getValue("id_dataset").value)
        assertTrue(parCode.getValue("id_dataset").masque)
        // L'utilisateur connecté parmi les observateurs.
        assertEquals(listOf("7"), parCode.getValue("observers").value)
        assertFalse(parCode.getValue("observers").masque)
        // Défaut objet résolu par son cd_nomenclature.
        assertEquals("802", parCode.getValue("stade").value)
        // Jamais de défaut sur un champ espèce.
        assertNull(parCode.getValue("cd_nom").value)
        // Date du jour, heure actuelle.
        assertNotNull(parCode.getValue("visit_date_min").value)
        assertNotNull(parCode.getValue("heure").value)
        assertNull(parCode.getValue("comments").value)
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
    fun preparer_retire_le_champ_du_parent_et_cascade_la_liste_taxonomique() = runTest {
        val sources = Sources(mapOf("module" to module, "dalle" to dalle, "visit" to visite), { p -> options[p.apiUrl] })
        val prete = assertIs<PreparationFormulaire.Prete>(NouvelleVisite.preparer(sources, "x", "visit", "dalle", null, emptyList()))
        val f = prete.formulaire
        assertFalse(f.champs.any { it.code == "id_dalle" })
        assertEquals("id_dalle", f.parentIdField)
        // La liste du module, sans aller la demander au serveur.
        assertEquals(100, f.champs.single { it.viewType == ViewType.TAXON }.idListeTaxonomieRestreinte)
        assertEquals(0, sources.appelsLive)
        assertEquals("observation", f.typeSaisieEnfant)
        assertEquals("Passage", f.labelType)
        assertEquals(visite.properties.keys.toList(), f.nomsChamps)
        assertTrue(f.avertissements.isEmpty())
    }

    @Test
    fun liste_taxonomique_du_jeu_de_donnees_puis_du_serveur() = runTest {
        val schema = mapOf("dalle" to dalle, "visit" to visite)
        val cache = """[{"id":1,"nom":"Autre","idTaxaList":5,"moduleCodes":["y"]},{"id":2,"nom":"STOM","idTaxaList":42,"moduleCodes":["x"]}]"""
        val viaCache = Sources(schema, { p -> options[p.apiUrl] }, datasetsCacheJson = cache, viaLive = 9)
        val f1 = assertIs<PreparationFormulaire.Prete>(NouvelleVisite.preparer(viaCache, "x", "visit", "dalle", null, emptyList())).formulaire
        assertEquals(42, f1.champs.single { it.viewType == ViewType.TAXON }.idListeTaxonomieRestreinte)
        assertEquals(0, viaCache.appelsLive)
        val viaServeur = Sources(schema, { p -> options[p.apiUrl] }, viaLive = 9)
        val f2 = assertIs<PreparationFormulaire.Prete>(NouvelleVisite.preparer(viaServeur, "x", "visit", "dalle", null, emptyList())).formulaire
        assertEquals(9, f2.champs.single { it.viewType == ViewType.TAXON }.idListeTaxonomieRestreinte)
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
    fun suites_d_un_enregistrement() = runTest {
        val sources = Sources(mapOf("module" to module, "dalle" to dalle, "visit" to visite), { p -> options[p.apiUrl] })
        val f = assertIs<PreparationFormulaire.Prete>(NouvelleVisite.preparer(sources, "x", "visit", "dalle", null, emptyList())).formulaire
        val enregistrement = ValeursFormulaire.Enregistrement("{}", emptyList(), null, emptyList())
        val saisie = NouvelleVisite.nouvelleSaisieEnAttente("x", f, enregistrement, "dalle", 31, null, listOf("Heure"))
        assertNotNull(saisie.uuidPayload)
        assertEquals("id_dalle", saisie.parentIdField)
        assertEquals(listOf("Heure"), saisie.champsManquants)
        assertEquals(
            SuiteEnregistrement.Enchainer("visit", saisie.uuid, "observation", "Passage (local)"),
            NouvelleVisite.suiteApresCreation(saisie, f, puisTerminer = false, chaine = false),
        )
        assertEquals(SuiteEnregistrement.TerminerOuCompleter, NouvelleVisite.suiteApresCreation(saisie, f, puisTerminer = true, chaine = false))
        val sansEnfant = f.copy(typeSaisieEnfant = null)
        assertEquals(SuiteEnregistrement.Reinitialiser, NouvelleVisite.suiteApresCreation(saisie, sansEnfant, puisTerminer = false, chaine = true))
        assertEquals(SuiteEnregistrement.Retour, NouvelleVisite.suiteApresCreation(saisie, sansEnfant, puisTerminer = false, chaine = false))
        // Modification : complétion → retour ; sortie explicite → la fiche du parent serveur.
        val ancienne = saisie.copy(etat = SaisieEnAttente.Etat.ERROR, messageErreur = "400")
        val modifiee = NouvelleVisite.saisieModifiee(ancienne, f, enregistrement, emptyList())
        assertEquals(SaisieEnAttente.Etat.PENDING, modifiee.etat)
        assertNull(modifiee.messageErreur)
        assertNull(modifiee.champsManquants)
        assertEquals(SuiteEnregistrement.Retour, NouvelleVisite.suiteApresEdition(modifiee.uuid, f, true, false, "dalle", 31))
        assertEquals(SuiteEnregistrement.Fiche("dalle", 31), NouvelleVisite.suiteApresEdition(modifiee.uuid, f, false, true, "dalle", 31))
        assertIs<SuiteEnregistrement.Enchainer>(NouvelleVisite.suiteApresEdition(modifiee.uuid, f, false, false, "dalle", 31))
        assertEquals(SuiteEnregistrement.Retour, NouvelleVisite.suiteApresEdition(modifiee.uuid, sansEnfant, false, false, null, null))
    }
}

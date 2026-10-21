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

// Propre au portage : ces règles vivaient dans le dialogue de GeoMys-Android @ cdd5196
// (ui/DetailsReleveDialog.kt), sans test. Les attendus sont ceux de son code.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.AdditionalFieldDef
import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.model.GeoNatureObservateur
import fr.ariegenature.geomys.model.WidgetType
import fr.ariegenature.geomys.store.io.listeVersJson
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** [DetailsReleveFormulaire] : listes proposées, valeurs de départ, dates, résultat. */
class DetailsReleveFormulaireTest {

    private val t = ServicesDeTest()
    private val config get() = t.services.config
    private val paris = TimeZone.of("Europe/Paris")

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private fun millis(annee: Int, mois: Int, jour: Int, heure: Int, minute: Int, seconde: Int = 0) =
        LocalDateTime(annee, mois, jour, heure, minute, seconde).toInstant(paris).toEpochMilliseconds()

    private fun local(millis: Long) = Instant.fromEpochMilliseconds(millis).toLocalDateTime(paris)

    private fun donnees(
        champsExtra: Map<String, String> = emptyMap(),
        additionnels: Map<String, String> = emptyMap(),
        typGrp: String = "",
        avecFin: Boolean = true,
        dateDebut: Long? = null,
        dateFin: Long? = null,
    ) = DonneesDetailsReleve(
        titre = "Détails du relevé",
        datasets = emptyList(),
        idDataset = null,
        nomDataset = null,
        observateurs = emptyList(),
        idsObservateurs = emptyList(),
        nomsObservateurs = emptyList(),
        defsReleve = emptyList(),
        additionnels = additionnels,
        formFieldsJson = "",
        typGrp = typGrp,
        commentaire = "",
        dateDebut = dateDebut,
        dateFin = dateFin,
        heures = true,
        avecFin = avecFin,
        cdHab = null,
        habitatLabel = null,
        champsExtra = champsExtra,
    )

    @Test
    fun seuls_les_jeux_actifs_occtax_et_creables_sont_proposes_sous_leur_nom() {
        config.datasetsCacheJson = listeVersJson(
            GeoNatureDataset.serializer(),
            listOf(
                GeoNatureDataset(1, "Opportuniste", moduleCodes = listOf("OCCTAX")),
                GeoNatureDataset(2, "Fermé", actif = false),
                GeoNatureDataset(3, "Suivi", moduleCodes = listOf("MONITORINGS")),
                GeoNatureDataset(4, "Sans module"),
                GeoNatureDataset(5, "Non créable", moduleCodes = listOf("OCCTAX")),
            ),
        )
        config.datasetsCreablesOcctax = setOf(1, 4, 5)
        // Comme le pnx-datasets du web (active, module_code et create OCCTAX) : un jeu sans module
        // OCCTAX n'est pas proposé, même créable.
        assertEquals(listOf(1 to "Opportuniste", 5 to "Non créable"), DetailsReleveFormulaire.datasets(config))
        config.datasetsCreablesOcctax = setOf(1, 4)
        assertEquals(listOf(1 to "Opportuniste"), DetailsReleveFormulaire.datasets(config))
        config.datasetsCacheJson = "pas du json"
        assertEquals(emptyList(), DetailsReleveFormulaire.datasets(config), "cache illisible : aucune proposition")
    }

    @Test
    fun sans_observateur_porte_l_utilisateur_connecte_comme_le_web() {
        config.observateursCacheJson = listeVersJson(GeoNatureObservateur.serializer(), listOf(GeoNatureObservateur(7, "MARTIN Alice")))
        assertEquals(listOf(7 to "MARTIN Alice"), DetailsReleveFormulaire.observateurs(config))
        config.idRoleUtilisateur = 12
        config.login = "amartin"
        assertEquals(listOf(12), DetailsReleveFormulaire.idsObservateursDepart(emptyList(), config))
        assertEquals(listOf("amartin"), DetailsReleveFormulaire.nomsObservateursDepart(emptyList(), config), "sans nom complet : l'identifiant")
        config.nomUtilisateur = "MARTIN Bob"
        assertEquals(listOf("MARTIN Bob"), DetailsReleveFormulaire.nomsObservateursDepart(emptyList(), config))
        assertEquals("MARTIN Bob", DetailsReleveFormulaire.observateursTexteDepart(config), "observers_txt : le nom complet")
        assertEquals(listOf(3, 4), DetailsReleveFormulaire.idsObservateursDepart(listOf(3, 4), config), "ceux du relevé priment")
    }

    @Test
    fun les_controles_du_releve_sont_ceux_du_web() {
        val jour = millis(2026, 10, 19, 8, 30)
        val veille = millis(2026, 10, 18, 8, 30)
        fun refus(
            debut: Long? = jour,
            fin: Long? = jour,
            hd: String = "",
            hf: String = "",
            obs: Boolean = true,
            texte: Boolean = false,
            simples: Map<String, String> = emptyMap(),
            jeu: Boolean = true,
        ) = DetailsReleveFormulaire.refus(debut, fin, hd, hf, obs, texte, simples, datasetChoisi = jeu)
        assertNull(refus())
        assertEquals("Choisissez un jeu de données", refus(jeu = false))
        assertEquals("Choisissez un observateur", refus(obs = false))
        assertEquals("Saisissez les observateurs", refus(obs = false, texte = true))
        assertEquals("Choisissez une date de début", refus(debut = null))
        // Le motif du web : « h:mm » ou « hh:mm », secondes permises.
        assertNull(refus(hd = "8:30", hf = "09:15:30"))
        assertEquals("Heure attendue au format hh:mm", refus(hd = "8h30", hf = "9:00"))
        assertEquals("Heure attendue au format hh:mm", refus(hd = "24:00", hf = "24:00"))
        assertEquals("La date de début ne peut pas être postérieure à la date de fin", refus(debut = jour, fin = veille))
        // Une heure de début sans heure de fin, ou après elle le même jour (invalidHour).
        assertEquals("L'heure de début ne peut pas être supérieure à l'heure de fin", refus(hd = "10:00"))
        assertEquals("L'heure de début ne peut pas être supérieure à l'heure de fin", refus(hd = "10:00", hf = "9:59"))
        assertNull(refus(debut = veille, fin = jour, hd = "10:00", hf = "09:00"), "sur deux jours, l'heure de fin peut précéder")
        assertNull(refus(hf = "09:00"), "une heure de fin seule passe, comme sur le web")
        // minMaxValidator : 0 et vide ne comptent pas ; les négatifs, si.
        assertEquals("L'altitude max ne peut être inférieure à l'altitude min", refus(simples = mapOf("altitude_min" to "1200", "altitude_max" to "900")))
        assertNull(refus(simples = mapOf("altitude_min" to "1200", "altitude_max" to "0")))
        assertNull(refus(simples = mapOf("altitude_min" to "-20", "altitude_max" to "-5")))
        assertEquals("La profondeur max ne peut être inférieure à la profondeur min", refus(simples = mapOf("depth_min" to "5", "depth_max" to "2")))
    }

    @Test
    fun le_filtre_ignore_accents_et_casse() {
        val libelles = listOf("Émile Durand", "emilie Roux", "Jean Dupont")
        assertEquals(listOf(0, 1), DetailsReleveFormulaire.filtrer(libelles, "EMIL"))
        assertEquals(listOf(2), DetailsReleveFormulaire.filtrer(libelles, "dup"))
        assertEquals(listOf(0, 1, 2), DetailsReleveFormulaire.filtrer(libelles, ""), "saisie vide : toute la liste")
    }

    @Test
    fun choisir_le_jour_garde_l_heure_et_choisir_l_heure_garde_le_jour() {
        val depart = millis(2026, 10, 19, 8, 30, 15)
        val autreJour = DetailsReleveFormulaire.avecJour(depart, LocalDate(2026, 3, 29), paris)
        // Le jour du passage à l'heure d'été : 8 h 30 locales restent 8 h 30.
        assertEquals(LocalDateTime(2026, 3, 29, 8, 30, 15), local(autreJour))
        val autreHeure = DetailsReleveFormulaire.avecHeure(depart, 17, 5, paris)
        assertEquals(LocalDateTime(2026, 10, 19, 17, 5, 15), local(autreHeure))
    }

    @Test
    fun le_resultat_garde_les_champs_masques_et_retire_les_champs_vides() {
        val d = donnees(
            champsExtra = mapOf("altitude_min" to "800", "place_name" to "Col", "geo_object_nature" to "NSP", "depth_min" to "2"),
            additionnels = mapOf("meteo" to "beau"),
            typGrp = "OBS",
        )
        val res = DetailsReleveFormulaire.resultat(
            d,
            dataset = 4 to "Opportuniste (4)",
            observateurs = listOf(7 to "Alice Martin"),
            additionnels = mapOf("ignore" to "x"),
            commentaire = "  Brouillard  ",
            cdHab = null,
            habitatTexte = "Pelouses",
            simples = mapOf("altitude_min" to " 950 ", "place_name" to ""),
            nomenclatures = mapOf("NAT_OBJ_GEO" to ""),
            dates = 1L to 2L,
            dateModifiee = true,
        )
        assertEquals(4, res.idDataset)
        assertEquals(listOf(7), res.idsObservateurs)
        assertEquals(listOf("Alice Martin"), res.nomsObservateurs)
        assertEquals(mapOf("altitude_min" to "950", "depth_min" to "2"), res.champsExtra, "champ vidé retiré, champ masqué gardé")
        assertEquals("OBS", res.typGrp, "TYP_GRP masqué : valeur gardée")
        assertEquals(mapOf("meteo" to "beau"), res.additionnels, "sans champ additionnel affiché : valeurs de départ")
        assertEquals("Brouillard", res.comment)
        assertNull(res.habitatLabel, "pas d'habitat choisi : pas de libellé")
        assertEquals(1L, res.dateDebut)
        assertEquals(2L, res.dateFin)
        assertTrue(res.dateModifiee)
        // TYP_GRP affiché et laissé vide : plus de type de regroupement.
        val vide = DetailsReleveFormulaire.resultat(d, null, emptyList(), null, "", 9, " Pelouses ", emptyMap(), mapOf("TYP_GRP" to ""), 1L to 1L, false)
        assertEquals("", vide.typGrp)
        assertEquals("Pelouses", vide.habitatLabel)
        assertNull(vide.idDataset)
    }

    @Test
    fun un_jeu_sans_champ_de_releve_n_envoie_pas_ceux_de_l_ancien_jeu() {
        // Comme le web, qui reconstruit le formulaire au changement de jeu (21 octobre).
        val strate = AdditionalFieldDef(1, "strate", "Strate", WidgetType.TEXT, objectsCode = listOf("OCCTAX_RELEVE"), datasetsIds = listOf(4))
        val d = donnees(additionnels = mapOf("strate" to "herbacée", "ancien" to "x")).copy(idDataset = 4, defsReleve = listOf(strate))
        val res = DetailsReleveFormulaire.resultat(d, 5 to "Atlas (5)", emptyList(), emptyMap(), "", null, "", emptyMap(), emptyMap(), 1L to 1L, false)
        assertEquals(mapOf("ancien" to "x"), res.additionnels, "le champ du jeu 4 ne part pas ; une clé qu'aucun jeu ne définit reste")
        val memeJeu = DetailsReleveFormulaire.resultat(d, 4 to "Opportuniste (4)", emptyList(), mapOf("strate" to "arborée"), "", null, "", emptyMap(), emptyMap(), 1L to 1L, false)
        assertEquals(mapOf("strate" to "arborée"), memeJeu.additionnels, "le jeu qui le définit : la valeur saisie")
    }
}

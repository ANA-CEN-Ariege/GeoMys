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
import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.model.GeoNatureObservateur
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
        infos = emptyList(),
        datasets = emptyList(),
        idDataset = null,
        nomDataset = null,
        observateurs = emptyList(),
        idsObservateurs = emptyList(),
        nomsObservateurs = emptyList(),
        defs = emptyList(),
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
    fun seuls_les_jeux_actifs_occtax_et_creables_sont_proposes() {
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
        config.datasetsCreablesOcctax = setOf(1, 4)
        assertEquals(listOf(1 to "Opportuniste (1)", 4 to "Sans module (4)"), DetailsReleveFormulaire.datasets(config))
        config.datasetsCacheJson = "pas du json"
        assertEquals(emptyList(), DetailsReleveFormulaire.datasets(config), "cache illisible : aucune proposition")
    }

    @Test
    fun sans_observateur_porte_l_observateur_par_defaut_puis_l_utilisateur() {
        config.observateursCacheJson = listeVersJson(GeoNatureObservateur.serializer(), listOf(GeoNatureObservateur(7, "Alice Martin")))
        assertEquals(listOf(7 to "Alice Martin"), DetailsReleveFormulaire.observateurs(config))
        config.idRoleUtilisateur = 12
        config.login = "amartin"
        assertEquals(listOf(12), DetailsReleveFormulaire.idsObservateursDepart(emptyList(), config))
        assertEquals(listOf("amartin"), DetailsReleveFormulaire.nomsObservateursDepart(emptyList(), config))
        config.observateurDefautId = "7"
        config.observateurDefautNom = "Alice Martin"
        assertEquals(listOf(7), DetailsReleveFormulaire.idsObservateursDepart(emptyList(), config))
        assertEquals(listOf("Alice Martin"), DetailsReleveFormulaire.nomsObservateursDepart(emptyList(), config))
        assertEquals(listOf(3, 4), DetailsReleveFormulaire.idsObservateursDepart(listOf(3, 4), config), "ceux du relevé priment")
    }

    @Test
    fun le_filtre_ignore_accents_et_casse() {
        val libelles = listOf("Émile Durand", "emilie Roux", "Jean Dupont")
        assertEquals(listOf(0, 1), DetailsReleveFormulaire.filtrer(libelles, "EMIL"))
        assertEquals(listOf(2), DetailsReleveFormulaire.filtrer(libelles, "dup"))
        assertEquals(listOf(0, 1, 2), DetailsReleveFormulaire.filtrer(libelles, ""), "saisie vide : toute la liste")
    }

    @Test
    fun la_date_de_fin_n_est_depliee_que_si_elle_differe_du_debut() {
        val debut = millis(2026, 10, 19, 8, 30)
        assertFalse(DetailsReleveFormulaire.finActiveAuDepart(donnees(dateDebut = debut, dateFin = debut)))
        assertFalse(DetailsReleveFormulaire.finActiveAuDepart(donnees(dateDebut = debut, dateFin = null)))
        assertTrue(DetailsReleveFormulaire.finActiveAuDepart(donnees(dateDebut = debut, dateFin = debut + 3_600_000)))
        assertFalse(DetailsReleveFormulaire.finActiveAuDepart(donnees(dateDebut = debut, dateFin = debut + 3_600_000, avecFin = false)))
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
    fun sans_heures_le_releve_couvre_la_journee_et_la_fin_suit_le_debut() {
        val debut = millis(2026, 10, 19, 8, 30, 15)
        val fin = millis(2026, 10, 21, 10, 0)
        val (d1, f1) = DetailsReleveFormulaire.dates(debut, fin, finActive = true, heures = false, avecFin = true, fuseau = paris)
        assertEquals(LocalDateTime(2026, 10, 19, 0, 0), local(d1))
        assertEquals(LocalDateTime(2026, 10, 21, 23, 59), local(f1))
        // Fin repliée : elle vaut le début.
        val (d2, f2) = DetailsReleveFormulaire.dates(debut, fin, finActive = false, heures = true, avecFin = true, fuseau = paris)
        assertEquals(debut, d2)
        assertEquals(debut, f2)
        // Fin avant le début : ramenée au début.
        val (d3, f3) = DetailsReleveFormulaire.dates(fin, debut, finActive = true, heures = true, avecFin = true, fuseau = paris)
        assertEquals(fin, d3)
        assertEquals(fin, f3)
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
}

/*
 * GeoMys — application de saisie naturaliste pour GeoNature, Android et iOS.
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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/ResumeOutboxTest.kt) : JUnit
// remplacé par kotlin.test ; resumeOutbox est dans monitoring/MesVisites.

package fr.ariegenature.geomys.monitoring

import fr.ariegenature.geomys.model.SaisieEnAttente
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Résumé de l'écran « Mes visites » (audit 2026-09-14, R4-C1).
 *
 * L'écran ne comptait que les objets de niveau VISITE — un choix voulu, les observations gonflaient
 * un total qui ne correspondait pas à ce que l'utilisateur appelle une visite. Mais la phrase de
 * synthèse et les compteurs d'onglet reposaient sur ce même filtre : une observation PENDING ou
 * ERROR sous une visite déjà SENT n'était comptée NULLE PART. L'écran affirmait alors « Toutes les
 * données ont été envoyées » alors qu'une observation naturaliste n'était jamais partie, et
 * l'onglet annonçait « À envoyer (0) » au-dessus d'une liste non vide.
 *
 * Cet état n'a rien d'hypothétique : une visite envoyée garde dans la file ses observations restées
 * en échec, et l'écran sait d'ailleurs l'afficher sous
 * « Visite déjà envoyée ». Croyant tout transmis, l'utilisateur pouvait vider le cache, changer de
 * compte ou réinstaller : donnée perdue.
 */
class ResumeOutboxTest {

    /** Une observation porte parentObjectType = "visite" ; une visite, le type du protocole. */
    private val estNiveauVisite: (String?) -> Boolean = { it == null || it == "module" }

    private fun saisie(
        uuid: String,
        etat: SaisieEnAttente.Etat,
        parentType: String? = "module",
    ) = SaisieEnAttente(
        uuid = uuid, moduleCode = "stom", objectType = "visit",
        parentObjectType = parentType, valeursJson = "{}", etat = etat,
    )

    @Test
    fun une_observation_en_attente_sous_une_visite_envoyee_est_comptee() {
        val toutes = listOf(
            saisie("v1", SaisieEnAttente.Etat.SENT),
            saisie("o1", SaisieEnAttente.Etat.PENDING, parentType = "visite"),
        )

        val r = resumeOutbox(toutes, estNiveauVisite)

        assertEquals(1, r.nbAEnvoyer, "l'onglet doit s'accorder avec la liste affichée")
        assertTrue(
            !r.texte.contains("Toutes les données ont été envoyées"),
            "l'écran ne doit JAMAIS affirmer que tout est parti s'il reste quoi que ce soit — " +
                "texte obtenu : ${r.texte}",
        )
        assertTrue(
            r.texte.contains("1 observation en attente"),
            "l'observation restante doit être nommée : ${r.texte}",
        )
    }

    @Test
    fun tout_envoye_reste_tout_envoye() {
        val toutes = listOf(
            saisie("v1", SaisieEnAttente.Etat.SENT),
            saisie("o1", SaisieEnAttente.Etat.SENT, parentType = "visite"),
        )

        val r = resumeOutbox(toutes, estNiveauVisite)

        assertEquals(0, r.nbAEnvoyer)
        assertTrue(r.texte.startsWith("Toutes les données ont été envoyées"))
    }

    @Test
    fun le_vocabulaire_visites_est_conserve_pour_le_decompte_principal() {
        // La demande terrain du 2026-09-03 tient : on ne se remet pas à compter les observations
        // dans le total des visites.
        val toutes = listOf(
            saisie("v1", SaisieEnAttente.Etat.PENDING),
            saisie("v2", SaisieEnAttente.Etat.PENDING),
            saisie("o1", SaisieEnAttente.Etat.SENT, parentType = "visite"),
            saisie("v3", SaisieEnAttente.Etat.SENT),
        )

        val r = resumeOutbox(toutes, estNiveauVisite)

        assertTrue(r.texte.contains("2 visites en attente"), "texte obtenu : ${r.texte}")
        assertTrue(r.texte.contains("1 envoyée"), "texte obtenu : ${r.texte}")
        assertEquals(2, r.nbAEnvoyer)
    }

    @Test
    fun visites_et_observations_restantes_sont_toutes_deux_nommees() {
        val toutes = listOf(
            saisie("v1", SaisieEnAttente.Etat.PENDING),
            saisie("o1", SaisieEnAttente.Etat.ERROR, parentType = "visite"),
            saisie("o2", SaisieEnAttente.Etat.PENDING, parentType = "visite"),
        )

        val r = resumeOutbox(toutes, estNiveauVisite)

        assertTrue(r.texte.contains("1 visite et 2 observations en attente"), "texte obtenu : ${r.texte}")
        assertEquals(3, r.nbAEnvoyer)
    }

    @Test
    fun outbox_vide() {
        val r = resumeOutbox(emptyList(), estNiveauVisite)
        assertEquals("Aucune donnée locale.", r.texte)
        assertEquals(0, r.nbAEnvoyer)
        assertEquals(0, r.nbEnvoyees)
    }
}

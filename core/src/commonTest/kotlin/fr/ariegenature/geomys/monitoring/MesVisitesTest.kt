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

// Gardes du lot 7 sur ce que SaisiesEnAttenteFragment de GeoMys-Android @ cdd5196 faisait sans test : l'arbre
// protocole → groupe → saisies et ses onglets, les actions d'une ligne, les phrases accordées et le fil
// d'Ariane d'une saisie rouverte.

package fr.ariegenature.geomys.monitoring

import fr.ariegenature.geomys.model.SaisieEnAttente
import fr.ariegenature.geomys.model.SaisieEnAttente.Etat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MesVisitesTest {

    private val cache = object : CacheSuivis {
        override fun labelModule(moduleCode: String) = if (moduleCode == "stom") "STOM Ariège" else null
        override fun labelType(moduleCode: String, type: String) = mapOf(
            "visit" to "Visite", "passage" to "Passage", "site" to "Point d'écoute", "observation" to "Observation", "sites_group" to "Groupe de sites",
        )[type]
        override fun genreType(moduleCode: String, type: String) = if (type == "passage" || type == "site") "M" else "F"
        override fun labelObjet(moduleCode: String, type: String, id: Int) = if (type == "site" && id == 501) "Col de Port" else null
        override fun chaineParents(moduleCode: String, type: String, id: Int) =
            if (type == "site") listOf(Triple("sites_group", 7, "Massif d'Arize")) else emptyList()
        override fun nomTaxon(cdNom: Int) = if (cdNom == 4117) "Merle noir" else null
    }

    private fun saisie(
        uuid: String,
        etat: Etat = Etat.PENDING,
        objectType: String = "visit",
        parentType: String? = "site",
        parentId: Int? = 501,
        parentLocal: String? = null,
        valeurs: String = "{}",
        date: Long = 0L,
        module: String = "stom",
    ) = SaisieEnAttente(
        uuid = uuid, moduleCode = module, objectType = objectType, parentObjectType = parentType,
        parentIdServeur = parentId, parentUuidLocal = parentLocal, valeursJson = valeurs, etat = etat, dateLocale = date,
    )

    @Test
    fun un_groupe_reste_a_envoyer_tant_qu_une_observation_n_est_pas_partie() {
        val toutes = listOf(
            saisie("v1", Etat.SENT),
            saisie("o1", Etat.PENDING, "observation", "visit", null, parentLocal = "v1", valeurs = """{"cd_nom":4117}"""),
            saisie("v2", Etat.SENT, date = 5),
        )
        val aEnvoyer = MesVisites.items(toutes, OngletMesVisites.A_ENVOYER, cache)
        assertEquals(
            listOf("🔬 STOM ARIÈGE", "📍 Groupe de sites : Massif d'Arize › Point d'écoute : Col de Port"),
            aEnvoyer.take(2).map { (it as? ItemMesVisites.Protocole)?.texte ?: (it as ItemMesVisites.Groupe).texte },
        )
        val lignes = aEnvoyer.filterIsInstance<ItemMesVisites.Ligne>()
        assertEquals(listOf("✅ Visite", "↳ ⏳ Merle noir"), lignes.map { it.titre })
        assertEquals(listOf(0, 1), lignes.map { it.profondeur })
        // Le groupe entièrement envoyé, seul, dans l'autre onglet.
        val envoyees = MesVisites.items(toutes, OngletMesVisites.ENVOYEES, cache).filterIsInstance<ItemMesVisites.Ligne>()
        assertEquals(listOf("v2"), envoyees.map { it.saisie.uuid })
    }

    @Test
    fun une_observation_dont_la_visite_n_est_plus_dans_la_file_est_une_racine() {
        val toutes = listOf(saisie("o1", Etat.ERROR, "observation", "visit", 9001, parentLocal = "envoyee-et-purgee"))
        val lignes = MesVisites.items(toutes, OngletMesVisites.A_ENVOYER, cache).filterIsInstance<ItemMesVisites.Ligne>()
        assertEquals(listOf("⚠ Observation"), lignes.map { it.titre })
        assertEquals(0, lignes.single().profondeur)
    }

    @Test
    fun listes_vides() {
        assertEquals(listOf(ItemMesVisites.Vide(MesVisites.MESSAGE_VIDE)), MesVisites.items(emptyList(), OngletMesVisites.A_ENVOYER, cache))
        assertEquals(
            listOf(ItemMesVisites.Vide("Aucune donnée envoyée.")),
            MesVisites.items(listOf(saisie("v1")), OngletMesVisites.ENVOYEES, cache),
        )
    }

    @Test
    fun options_et_phrases_accordees() {
        val visite = saisie("v1", Etat.ERROR)
        assertEquals(
            listOf("Réessayer", "Envoyer ce groupe (3 données)", "Supprimer cette visite"),
            MesVisites.options(visite, nbEnfants = 2, envoiAutorise = true, cache = cache).map { it.libelle },
        )
        assertEquals(listOf("Retirer de la liste"), MesVisites.options(saisie("v2", Etat.SENT), 0, true, cache).map { it.libelle })
        val passage = saisie("p1", objectType = "passage")
        assertEquals("Supprimer ce passage ?", MesVisites.titreSuppression(passage, cache))
        assertTrue(MesVisites.messageSuppression(passage, 2, cache).startsWith("Ce passage a 2 donnée(s) locale(s)"))
        assertTrue(MesVisites.messageSuppression(passage, 2, cache).endsWith("Supprimer le passage ET ses 2 enfant(s) ?"))
        assertEquals("Supprimer cette observation", MesVisites.options(saisie("o1", objectType = "observation"), 0, true, cache).single().libelle)
    }

    @Test
    fun une_visite_envoyee_garde_sa_fleche_tant_qu_il_reste_des_observations() {
        val toutes = listOf(
            saisie("v1", Etat.SENT),
            saisie("o1", Etat.SENT, "observation", parentLocal = "v1"),
            saisie("o2", Etat.PENDING, "observation", parentLocal = "o1"),
        )
        assertTrue(MesVisites.aDescendantsAEnvoyer(toutes[0], toutes))
        assertEquals(1, MesVisites.nbEnvoyables(toutes) { true })
        assertEquals(0, MesVisites.nbEnvoyables(toutes) { false })
    }

    @Test
    fun fil_d_une_saisie_rouverte() {
        val fil = MesVisites.filPourEdition(saisie("v1"), cache)
        assertEquals(
            filRacineSuivis("STOM Ariège") + FilSegment("sites_group", 7, "Massif d'Arize") + FilSegment("site", 501, "Col de Port"),
            fil,
        )
        // Sans parent serveur : le protocole seul.
        assertEquals(filRacineSuivis("STOM Ariège"), MesVisites.filPourEdition(saisie("o1", parentType = "visit", parentId = null), cache))
    }
}

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

// Essai d'affichage du lot 6, lancé par le paramètre caché `action=taxref-essai` de GeoMysApp, sur
// l'émulateur et le simulateur seulement : un petit référentiel pour voir la palette des groupes, les
// propositions et la ligne de statut des écrans de saisie sans serveur GeoNature.

package fr.ariegenature.geomys.outils

import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.store.TaxRefEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class EspeceEssai(val cdNom: Int, val sciNom: String, val nomsFr: List<String>, val groupe2: String, val taxon: Taxon)

private val ESPECES_ESSAI = listOf(
    EspeceEssai(4117, "Turdus merula", listOf("Merle noir"), "Oiseaux", Taxon.OISEAU),
    EspeceEssai(4001, "Erithacus rubecula", listOf("Rougegorge familier"), "Oiseaux", Taxon.OISEAU),
    EspeceEssai(534742, "Cyanistes caeruleus", listOf("Mésange bleue"), "Oiseaux", Taxon.OISEAU),
    EspeceEssai(3764, "Parus major", listOf("Mésange charbonnière"), "Oiseaux", Taxon.OISEAU),
    EspeceEssai(3603, "Picus viridis", listOf("Pic vert", "Pivert"), "Oiseaux", Taxon.OISEAU),
    EspeceEssai(3611, "Dendrocopos major", listOf("Pic épeiche"), "Oiseaux", Taxon.OISEAU),
    EspeceEssai(2623, "Buteo buteo", listOf("Buse variable"), "Oiseaux", Taxon.OISEAU),
    EspeceEssai(61153, "Sciurus vulgaris", listOf("Écureuil roux"), "Mammifères", Taxon.MAMMIFERE),
    EspeceEssai(60585, "Vulpes vulpes", listOf("Renard roux"), "Mammifères", Taxon.MAMMIFERE),
    EspeceEssai(77756, "Podarcis muralis", listOf("Lézard des murailles"), "Reptiles", Taxon.REPTILE),
    EspeceEssai(444432, "Lissotriton helveticus", listOf("Triton palmé (Le)"), "Amphibiens", Taxon.BATRACIEN),
    EspeceEssai(54303, "Papilio machaon", listOf("Machaon", "Grand porte-queue"), "Insectes", Taxon.INSECTE),
    EspeceEssai(128268, "Urtica dioica", listOf("Ortie dioïque", "Grande ortie"), "Angiospermes", Taxon.PLANTE),
)

/** Remplace le cache TaxRef par une quinzaine d'espèces de six groupes, sans liste de saisie. */
suspend fun ServicesGeoNature.ajouterTaxRefEssai() = withContext(Dispatchers.Default) {
    taxRef.vider()
    val entrees = LinkedHashMap<String, TaxRefEntry>()
    for (e in ESPECES_ESSAI) {
        entrees[TaxRefCache.normaliser(e.sciNom)] = TaxRefEntry(e.cdNom, e.sciNom)
        for (nom in e.nomsFr) entrees.getOrPut(TaxRefCache.normaliser(nom)) { TaxRefEntry(e.cdNom, e.sciNom, nom) }
    }
    taxRef.remplacerTout(entrees)
    taxRef.ajouterVerns(ESPECES_ESSAI.associate { it.cdNom to it.nomsFr })
    taxRef.ajouterSciNoms(ESPECES_ESSAI.associate { it.cdNom to it.sciNom })
    taxRef.ajouterGroupes(ESPECES_ESSAI.associate { it.cdNom to it.groupe2 })
    taxRef.setIndexParTaxon(ESPECES_ESSAI.groupBy({ it.taxon }, { it.cdNom }))
}

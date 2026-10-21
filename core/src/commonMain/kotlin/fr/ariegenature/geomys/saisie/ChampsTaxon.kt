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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/saisie/ChampsTaxon.kt) :
// l'objet NomenclatureCache devient une instance, passée en paramètre.

package fr.ariegenature.geomys.saisie

import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.store.GroupeTaxonomique
import fr.ariegenature.geomys.store.TaxRefCache

/** Contexte taxonomique pour le filtrage des VALEURS de nomenclature, factorisé depuis les écrans de
 *  saisie. Le filtrage se fait ensuite via [fr.ariegenature.geomys.store.NomenclatureCache.filtrerPourTaxon].
 *
 *  NB : la VISIBILITÉ des champs n'est plus décidée ici (elle l'était par taxon, en dur) — elle est
 *  désormais pilotée par la config serveur via [fr.ariegenature.geomys.store.OcctaxFieldsConfig].
 *  Seul le filtrage par taxon des valeurs reste pertinent et vit ici. */
object ChampsTaxon {

    /** Le règne, le group2_inpn et le group3_inpn du taxon [cdNom] dans le cache TaxRef, ceux que le
     *  web transmet au serveur ; à défaut (pas de cd_nom, taxon hors du cache), le règne et le
     *  groupe du [taxon] de la palette. GeoMys-Android filtrait les mollusques et autres invertébrés
     *  sur « Animalia » comme s'il s'agissait d'un groupe, et les plantes sur l'union des groupes
     *  botaniques. */
    fun groupeTaxonomique(taxon: Taxon, cdNom: Int?, taxRef: TaxRefCache): GroupeTaxonomique {
        val cle = cdNom?.toString()
        return GroupeTaxonomique(
            regne = cle?.let { taxRef.tousLesRegnes()[it] }?.takeIf { it.isNotBlank() } ?: regnePalette(taxon),
            group2 = cle?.let { taxRef.tousLesGroupes()[it] }?.takeIf { it.isNotBlank() } ?: group2Palette(taxon),
            group3 = cle?.let { taxRef.tousLesGroupes3()[it] }?.takeIf { it.isNotBlank() },
        )
    }

    private fun regnePalette(taxon: Taxon): String = when (taxon) {
        Taxon.PLANTE -> "Plantae"
        Taxon.FONGE -> "Fungi"
        else -> "Animalia"
    }

    /** Le group2_inpn d'un groupe de la palette, null quand il en couvre plusieurs. */
    private fun group2Palette(taxon: Taxon): String? = when (taxon) {
        Taxon.OISEAU -> "Oiseaux"
        Taxon.MAMMIFERE -> "Mammifères"
        Taxon.REPTILE -> "Reptiles"
        Taxon.BATRACIEN -> "Amphibiens"
        Taxon.POISSON -> "Poissons"
        Taxon.INSECTE -> "Insectes"
        Taxon.PLANTE, Taxon.FONGE, Taxon.MOLLUSQUE, Taxon.INVERTEBRES -> null
    }
}

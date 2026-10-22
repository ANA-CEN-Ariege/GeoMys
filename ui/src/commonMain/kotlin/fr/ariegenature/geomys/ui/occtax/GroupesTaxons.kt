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

// L'icône et la couleur de chaque groupe : taxonIcon et taxonCouleur de GeoMys-Android
// (ui/saisie/TaxonPalette.kt). GeoMys-IOS emploie les mêmes vectoriels, convertis, et les mêmes couleurs.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.ui.graphics.Color
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.amphibiens
import fr.ariegenature.geomys.ui.ressources.araignees
import fr.ariegenature.geomys.ui.ressources.champignons2
import fr.ariegenature.geomys.ui.ressources.fleurs
import fr.ariegenature.geomys.ui.ressources.insectes
import fr.ariegenature.geomys.ui.ressources.mammiferes2
import fr.ariegenature.geomys.ui.ressources.mollusques
import fr.ariegenature.geomys.ui.ressources.oiseaux
import fr.ariegenature.geomys.ui.ressources.poissons
import fr.ariegenature.geomys.ui.ressources.reptiles2
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import org.jetbrains.compose.resources.DrawableResource

/** L'icône du groupe (palette, liste des espèces). */
val Taxon.icone: DrawableResource
    get() = when (this) {
        Taxon.OISEAU -> Res.drawable.oiseaux
        Taxon.MAMMIFERE -> Res.drawable.mammiferes2
        Taxon.REPTILE -> Res.drawable.reptiles2
        Taxon.BATRACIEN -> Res.drawable.amphibiens
        Taxon.POISSON -> Res.drawable.poissons
        Taxon.INSECTE -> Res.drawable.insectes
        Taxon.FONGE -> Res.drawable.champignons2
        Taxon.MOLLUSQUE -> Res.drawable.mollusques
        Taxon.INVERTEBRES -> Res.drawable.araignees
        Taxon.PLANTE -> Res.drawable.fleurs
    }

/** La couleur canonique du groupe : fond de son bouton choisi dans la palette. */
val Taxon.couleur: Color
    get() = when (this) {
        Taxon.OISEAU -> CouleursGeoMys.orange
        Taxon.MAMMIFERE -> Color(0xFF795548)
        Taxon.REPTILE -> CouleursGeoMys.vert
        Taxon.BATRACIEN -> Color(0xFF0288D1)
        Taxon.POISSON -> Color(0xFF0097A7)
        Taxon.INSECTE -> Color(0xFFF9A825)
        Taxon.FONGE -> Color(0xFF6D4C41)
        Taxon.MOLLUSQUE, Taxon.INVERTEBRES -> Color(0xFF7B1FA2)
        Taxon.PLANTE -> Color(0xFF009688)
    }

/** Libellé du groupe pour l'accessibilité (VoiceOver, TalkBack). */
val Taxon.libelle: String
    get() = when (this) {
        Taxon.OISEAU -> "Oiseau"
        Taxon.MAMMIFERE -> "Mammifère"
        Taxon.REPTILE -> "Reptile"
        Taxon.BATRACIEN -> "Batracien"
        Taxon.POISSON -> "Poisson"
        Taxon.INSECTE -> "Insecte"
        Taxon.FONGE -> "Champignon"
        Taxon.MOLLUSQUE -> "Mollusque"
        Taxon.INVERTEBRES -> "Invertébré"
        Taxon.PLANTE -> "Plante"
    }

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

// Boutons ronds posés sur les cartes : btn_map_bg, btn_circle_white et btn_circle_blue de
// GeoMys-Android, BoutonCarteRond de GeoMys-IOS (UI/Map/MapView.swift).

package fr.ariegenature.geomys.ui.carte

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Les sortes de boutons ronds des cartes. */
enum class StyleBoutonCarte {
    /** Contrôle de carte (fond, liste…) : disque blanc, icône gris foncé (#424242). */
    CONTROLE,

    /** Contrôle des cartes de saisie (centrer, zoom, détails communs) : comme [CONTROLE], mais sur
     *  `btn_circle_white` sous Android, gris (#E0E0E0) appuyé. */
    CONTROLE_SAISIE,

    /** Action secondaire (partager) : disque blanc, icône bleu primaire. */
    SECONDAIRE,

    /** Action principale (envoyer) : disque bleu primaire, icône blanche. */
    PRINCIPAL,
}

/** iOS : `Couleurs.iconeCarteNeutre`, le gris des icônes Material. */
private val GRIS_ICONE = Color(0xFF424242)

/** colorPrimaryVariant : disque bleu appuyé. */
private val BLEU_APPUYE = Color(0xFF1565C0)

/** Bouton rond de 44 de côté sur une carte.
 *  - Android : ImageButton à fond ovale ; les contrôles gardent l'icône à sa taille (24 dp, sans
 *    teinte), les actions la ramènent à 24 dp dans 10 dp de marge ; disques blanc (gris E0E0E0
 *    appuyé) ou bleu (colorPrimaryVariant appuyé), sans ombre ;
 *  - iOS : BoutonCarteRond de GeoMys-IOS (disque blanc, icône teintée de 24 pt) ; l'envoi est un
 *    disque bleu ombré, icône blanche de 22 pt.
 *  [teinteIos] remplace la teinte du style sous iOS (goutte de « Centrer », bleue ou grise). */
@Composable
fun BoutonCarte(
    icone: DrawableResource,
    description: String,
    style: StyleBoutonCarte,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    teinteIos: Color? = null,
) {
    val interactions = remember { MutableInteractionSource() }
    val appuye by interactions.collectIsPressedAsState()
    val ios = estIos
    val fond = when {
        style == StyleBoutonCarte.PRINCIPAL -> if (!ios && appuye) BLEU_APPUYE else CouleursGeoMys.primaire
        !ios && appuye && style != StyleBoutonCarte.CONTROLE -> Color(0xFFE0E0E0)
        else -> Color.White
    }
    val teinte = if (ios && teinteIos != null) teinteIos else when (style) {
        StyleBoutonCarte.CONTROLE, StyleBoutonCarte.CONTROLE_SAISIE -> if (ios) GRIS_ICONE else null
        StyleBoutonCarte.SECONDAIRE -> CouleursGeoMys.primaire
        StyleBoutonCarte.PRINCIPAL -> Color.White
    }
    Box(
        modifier
            .size(44.dp)
            .then(if (ios && style == StyleBoutonCarte.PRINCIPAL) Modifier.shadow(3.dp, CircleShape) else Modifier)
            .background(fond, CircleShape)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
            // Sous iOS, `.buttonStyle(.plain)` estompe le bouton appuyé.
            .then(if (ios && appuye) Modifier.alpha(0.6f) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(icone),
            contentDescription = description,
            colorFilter = teinte?.let { ColorFilter.tint(it) },
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(if (ios && style == StyleBoutonCarte.PRINCIPAL) 22.dp else 24.dp),
        )
    }
}

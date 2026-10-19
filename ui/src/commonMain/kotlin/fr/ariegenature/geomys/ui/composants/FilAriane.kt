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

package fr.ariegenature.geomys.ui.composants

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.monitoring.FIL_TYPE_ACCUEIL
import fr.ariegenature.geomys.monitoring.FilSegment
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.ic_home
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import org.jetbrains.compose.resources.painterResource

/** Séparateur des niveaux du fil, identique dans les deux applis (`FIL_SEPARATEUR`). */
const val FIL_SEPARATEUR = " › "

/** La maison : 2,1 fois le texte de 13, sur les deux plateformes. GeoMys-IOS la réduisait d'un
 *  cinquième ; l'appli reprend la taille d'Android (demande du 19 octobre). */
private const val TAILLE_MAISON = 13f * 2.10f

/** Marge verticale de la pastille d'Android (`view_bandeau_saisie`). */
private const val MARGE_VERTICALE = 10f

/** Hauteur du bandeau de navigation sous iOS, la même sur tous les écrans, fil des Suivis compris :
 *  celle de la pastille d'Android, la maison et ses marges. Un fil trop long pour une ligne passe à
 *  la suivante, et le bandeau grandit d'autant. Un bouton posé dessus (la coche de fin) prend cette
 *  hauteur pour s'y centrer. */
val HAUTEUR_BANDEAU_IOS = (TAILLE_MAISON + 2 * MARGE_VERTICALE).dp


/** Fil d'Ariane cliquable des Suivis : « 🏠 › Monitoring › Protocole › Site ». Chaque ancêtre
 *  remonte à son niveau : [onNiveau] reçoit le fil tronqué au niveau touché. [dernierCliquable] :
 *  faux quand le dernier niveau est l'écran courant (une fiche), vrai quand tous sont des
 *  ancêtres (formulaire de nouvelle visite). Rien si [segments] est vide.
 *  - Android : `appliquerFilAriane` (pastille de `view_bandeau_saisie` en pleine largeur, liens
 *    jaune clair sans soulignement, maison de 2,1 fois le texte) ;
 *  - iOS : `FilAriane` de GeoMys-IOS (bande noire à 25 %, niveaux demi-gras, cliquables en jaune,
 *    le dernier en blanc), avec la maison et la hauteur de la pastille d'Android. */
@Composable
fun FilArianeSuivis(
    segments: List<FilSegment>,
    dernierCliquable: Boolean,
    onNiveau: (List<FilSegment>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (segments.isEmpty()) return
    val ios = estIos
    val texte = buildAnnotatedString {
        segments.forEachIndexed { i, seg ->
            if (i > 0) {
                if (ios) withStyle(SpanStyle(color = CouleursGeoMys.blanc70, fontWeight = FontWeight.Normal)) { append(FIL_SEPARATEUR) }
                else append(FIL_SEPARATEUR)
            }
            if (seg.type == FIL_TYPE_ACCUEIL) {
                appendInlineContent("maison", "Accueil")
                return@forEachIndexed
            }
            val cible = segments.subList(0, i + 1).toList()
            if (i < segments.lastIndex || dernierCliquable) {
                withLink(LinkAnnotation.Clickable("fil-$i", TextLinkStyles(SpanStyle(color = CouleursGeoMys.jauneClair))) { onNiveau(cible) }) {
                    append(seg.label)
                }
            } else if (ios) {
                withStyle(SpanStyle(color = Color.White)) { append(seg.label) }
            } else {
                append(seg.label)
            }
        }
    }
    val accueil = segments.firstOrNull { it.type == FIL_TYPE_ACCUEIL }?.let { listOf(it) }
    if (ios) {
        val icone = InlineTextContent(Placeholder(TAILLE_MAISON.sp, TAILLE_MAISON.sp, PlaceholderVerticalAlign.TextCenter)) {
            Box(Modifier.fillMaxSize().clickable(onClickLabel = "Accueil") { accueil?.let(onNiveau) }, contentAlignment = Alignment.Center) {
                SymboleSf("house.fill", TAILLE_MAISON, listOf(CouleursGeoMys.jauneClair))
            }
        }
        BandeIos(modifier) {
            Text(texte, inlineContent = mapOf("maison" to icone), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    } else {
        val couleurs = MaterialTheme.colorScheme
        val icone = InlineTextContent(Placeholder(TAILLE_MAISON.sp, TAILLE_MAISON.sp, PlaceholderVerticalAlign.Bottom)) {
            Image(
                painterResource(Res.drawable.ic_home),
                contentDescription = "Accueil",
                colorFilter = ColorFilter.tint(CouleursGeoMys.jauneClair),
                modifier = Modifier.fillMaxSize().clickable { accueil?.let(onNiveau) },
            )
        }
        val forme = RoundedCornerShape(12.dp)
        Text(
            texte,
            inlineContent = mapOf("maison" to icone),
            color = couleurs.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = modifier
                .fillMaxWidth()
                .shadow(6.dp, forme)
                .background(couleurs.surfaceVariant, forme)
                .padding(horizontal = 14.dp, vertical = MARGE_VERTICALE.dp),
        )
    }
}

/** Bandeau de navigation des écrans ouverts depuis l'accueil : « 🏠 › [libelle] », la maison
 *  ramenant à l'accueil.
 *  - Android : `appliquerBandeauNavigation` sur `view_bandeau_saisie` (pastille aux coins de 12 dp,
 *    colorSurfaceVariant, élévation 6 dp, 13 sp gras, maison `ic_home` jaune de 2,1 fois le texte,
 *    calée sur le bas de la ligne). L'écran le place à 12 dp du coin haut-gauche.
 *  - iOS : `FilAriane` de GeoMys-IOS (bande pleine largeur, noir 25 %, 13 pt, maison « house.fill »,
 *    niveaux demi-gras blancs), avec la maison et la hauteur de la pastille d'Android
 *    ([HAUTEUR_BANDEAU_IOS]). [titreIos] : l'écran courant en dernier niveau,
 *    là où GeoMys-IOS met le titre que l'écran Android affiche sous le bandeau. */
@Composable
fun BandeauNavigation(libelle: String, onAccueil: () -> Unit, modifier: Modifier = Modifier, titreIos: String? = null) {
    if (estIos) {
        val texte = buildAnnotatedString {
            appendInlineContent("maison", "Accueil")
            for (niveau in listOfNotNull(libelle, titreIos)) {
                withStyle(SpanStyle(color = CouleursGeoMys.blanc70, fontSize = 13.sp)) { append(FIL_SEPARATEUR) }
                withStyle(SpanStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)) { append(niveau) }
            }
        }
        val icone = InlineTextContent(Placeholder(TAILLE_MAISON.sp, TAILLE_MAISON.sp, PlaceholderVerticalAlign.TextCenter)) {
            Box(Modifier.fillMaxSize().clickable(onClickLabel = "Accueil", onClick = onAccueil), contentAlignment = Alignment.Center) {
                SymboleSf("house.fill", TAILLE_MAISON, listOf(CouleursGeoMys.jauneClair))
            }
        }
        BandeIos(modifier) { Text(texte, inlineContent = mapOf("maison" to icone)) }
    } else {
        val couleurs = MaterialTheme.colorScheme
        val texte = buildAnnotatedString {
            appendInlineContent("maison", "Accueil")
            append(FIL_SEPARATEUR)
            append(libelle)
        }
        val icone = InlineTextContent(Placeholder(TAILLE_MAISON.sp, TAILLE_MAISON.sp, PlaceholderVerticalAlign.Bottom)) {
            Image(
                painterResource(Res.drawable.ic_home),
                contentDescription = "Accueil",
                colorFilter = ColorFilter.tint(CouleursGeoMys.jauneClair),
                modifier = Modifier.fillMaxSize().clickable(onClick = onAccueil),
            )
        }
        val forme = RoundedCornerShape(12.dp)
        Text(
            texte,
            inlineContent = mapOf("maison" to icone),
            color = couleurs.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = modifier
                .shadow(6.dp, forme)
                .background(couleurs.surfaceVariant, forme)
                .padding(horizontal = 14.dp, vertical = MARGE_VERTICALE.dp),
        )
    }
}

/** La bande des deux bandeaux sous iOS : pleine largeur, noir à 25 %, au moins
 *  [HAUTEUR_BANDEAU_IOS] de haut, le fil centré verticalement. */
@Composable
private fun BandeIos(modifier: Modifier, contenu: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = HAUTEUR_BANDEAU_IOS)
            .background(Color.Black.copy(alpha = 0.25f))
            .padding(horizontal = 12.dp, vertical = MARGE_VERTICALE.dp),
        contentAlignment = Alignment.CenterStart,
    ) { contenu() }
}

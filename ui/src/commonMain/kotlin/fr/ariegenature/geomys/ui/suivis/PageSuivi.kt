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

// La page des écrans de suivi (détail d'un protocole, fiche d'un objet) : fragment_suivi_detail.xml et
// fragment_fiche_objet.xml de GeoMys-Android @ cdd5196 (fil d'Ariane flottant au-dessus du défilement, le
// contenu suivant sa hauteur — garderContenuSousFil), SuiviDetailView et FicheObjetView de GeoMys-IOS (fil en
// bande, puis le contenu).

package fr.ariegenature.geomys.ui.suivis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.monitoring.DroitsSuivis
import fr.ariegenature.geomys.monitoring.FilSegment
import fr.ariegenature.geomys.ui.composants.BoutonIcone
import fr.ariegenature.geomys.ui.composants.BoutonIos
import fr.ariegenature.geomys.ui.composants.FilArianeSuivis
import fr.ariegenature.geomys.ui.composants.GraisseSymbole
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.ic_add
import fr.ariegenature.geomys.ui.ressources.ic_eye
import fr.ariegenature.geomys.ui.ressources.ic_info
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys
import org.jetbrains.compose.resources.DrawableResource

/** Fil d'Ariane, puis le contenu défilant ([contenu]).
 *  - Android : fil flottant (12 dp du haut, 16 dp des bords) au-dessus du défilement, qui
 *    commence 12 dp sous lui en suivant sa hauteur ; contenu à 16 dp des bords, 24 dp en bas ;
 *  - iOS : fil en bande, puis le contenu à 16 pt des bords, 12 pt en haut et en bas, ses
 *    éléments espacés de 10 pt (`VStack(spacing: 10)`). */
@Composable
internal fun PageSuivi(
    fil: List<FilSegment>,
    dernierCliquable: Boolean,
    onNiveau: (List<FilSegment>) -> Unit,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    FondGeoMys {
        if (estIos) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                FilArianeSuivis(fil, dernierCliquable, onNiveau)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = contenu,
                )
            }
        } else {
            val densite = LocalDensity.current
            // 64 dp tant que le fil n'est pas mesuré (paddingTop du ScrollView dans le layout).
            var hautFil by remember { mutableStateOf(64.dp) }
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).union(WindowInsets.ime)),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = hautFil)
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    content = contenu,
                )
                FilArianeSuivis(
                    fil,
                    dernierCliquable,
                    onNiveau,
                    Modifier
                        .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                        .onSizeChanged { taille -> hautFil = 12.dp + with(densite) { taille.height.toDp() } + 12.dp },
                )
            }
        }
    }
}

/** En-tête d'un groupe d'objets (« SITES », « VISITES (3) »), en capitales. [action] : un
 *  bouton à droite (le « + » d'une nouvelle saisie). */
@Composable
internal fun EnteteSuivi(texte: String, action: (@Composable () -> Unit)? = null) {
    if (estIos) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(texte.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CouleursGeoMys.blanc70, modifier = Modifier.weight(1f))
            action?.invoke()
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                texte.uppercase(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            action?.invoke()
        }
    }
}

/** Ligne d'un objet : [nom] (15), [sousTitre] (12, une ligne), puis ses boutons.
 *  - Android : bloc à marges 8 dp (haut, droite, bas), boutons ImageButton de 40 dp ;
 *  - iOS : `ligneEnfant` de GeoMys-IOS (nom sur deux lignes au plus, boutons SF espacés de 4 pt). */
@Composable
internal fun LigneObjet(nom: String, sousTitre: String, boutons: @Composable () -> Unit) {
    if (estIos) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(nom, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (sousTitre.isNotEmpty()) {
                    Text(sousTitre, fontSize = 12.sp, color = CouleursGeoMys.blanc70, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.size(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { boutons() }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(top = 8.dp, end = 8.dp, bottom = 8.dp)) {
                Text(nom, fontSize = 15.sp)
                if (sousTitre.isNotEmpty()) {
                    Text(
                        sousTitre,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            boutons()
        }
    }
}

/** Les actions d'une ligne de suivi. */
internal enum class ActionSuivi(val icone: DrawableResource, val symboleIos: String) {
    FICHE(Res.drawable.ic_info, "info.circle.fill"),
    CARTE(Res.drawable.ic_eye, "eye.fill"),
    NOUVELLE(Res.drawable.ic_add, "plus"),
}

/** Un bouton d'action de ligne : ImageButton de 40 dp teinté jaune clair sous Android, symbole SF
 *  de 20 pt demi-gras sur une cible de 40 pt sous iOS (`boutonIcone` de BoutonsLigneMonitoring) ;
 *  [compactIos] : le « + » des en-têtes et des saisies locales de la fiche, de [tailleIos] points
 *  gras sur une cible de 36 pt. [permis] faux : sans le droit de créer, grisé comme sur le web ; le
 *  toucher dit son infobulle (« Vous n'avez pas les permissions nécessaires »). */
@Composable
internal fun BoutonSuivi(
    action: ActionSuivi,
    description: String,
    compactIos: Boolean = false,
    tailleIos: Float = 16f,
    permis: Boolean = true,
    onClick: () -> Unit,
) {
    val toasts = LocalToasts.current
    val clic = if (permis) onClick else ({ toasts.afficher(DroitsSuivis.SANS_PERMISSION, longue = false) })
    val couleur = if (permis) CouleursGeoMys.jauneClair else CouleursGeoMys.jauneClair.copy(alpha = 0.38f)
    val texte = if (permis) description else "$description — ${DroitsSuivis.SANS_PERMISSION}"
    if (estIos) {
        BoutonIos(clic, Modifier.size(if (compactIos) 36.dp else 40.dp), opaciteAppui = 0.3f) {
            SymboleSf(
                action.symboleIos,
                if (compactIos) tailleIos else 20f,
                listOf(couleur),
                graisse = if (compactIos) GraisseSymbole.GRASSE else GraisseSymbole.DEMI_GRASSE,
                description = texte,
            )
        }
    } else {
        BoutonIcone(action.icone, couleur, texte, clic, cible = DpSize(40.dp, 40.dp))
    }
}

/** Un message d'erreur sous le titre : holo_red_dark de 13 sp sous Android ; sous iOS, le
 *  `Label` de GeoMys-IOS (triangle d'alerte et texte en rouge pastille). */
@Composable
internal fun ErreurSuivi(message: String) {
    if (estIos) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            SymboleSf("exclamationmark.triangle.fill", 15f, listOf(CouleursGeoMys.rougePastille))
            Text(message, color = CouleursGeoMys.rougePastille)
        }
    } else {
        Text(message, color = CouleursGeoMys.holoRougeFonce, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
    }
}

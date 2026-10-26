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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/AccueilFragment.kt et
// res/layout/fragment_accueil.xml) ; allure iOS de GeoMys-IOS (GeoMys-IOS/UI/AccueilView.swift).

package fr.ariegenature.geomys.ui.accueil

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.ui.composants.BoutonAlerte
import fr.ariegenature.geomys.ui.composants.BoutonIos
import fr.ariegenature.geomys.ui.composants.BoutonMaterial
import fr.ariegenature.geomys.ui.composants.DialogueAlerte
import fr.ariegenature.geomys.ui.composants.EtatFlou
import fr.ariegenature.geomys.ui.composants.PILULE
import fr.ariegenature.geomys.ui.composants.Pastille
import fr.ariegenature.geomys.ui.composants.SeparateurIos
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.fondFlou
import fr.ariegenature.geomys.ui.composants.rememberEtatFlou
import fr.ariegenature.geomys.ui.composants.sourceFlou
import fr.ariegenature.geomys.ui.composants.styleLibelleMaterial
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.ic_menu
import fr.ariegenature.geomys.ui.ressources.ic_settings
import fr.ariegenature.geomys.ui.ressources.logo_geomys
import fr.ariegenature.geomys.ui.ressources.logo_geomys_ios
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys
import fr.ariegenature.geomys.ui.theme.fondGeoMys
import fr.ariegenature.geomys.ui.theme.FormeContinue
import org.jetbrains.compose.resources.painterResource

/** Ce que l'accueil affiche, relu à chaque retour sur l'écran (`onResume` sous Android). */
data class EtatAccueil(
    /** Le nom de l'appli, celui de l'icône ([fr.ariegenature.geomys.InfosApp.nom]). */
    val nomAppli: String,
    val version: String,
    /** Saisie OCCTAX réellement réalisable : sinon les boutons de saisie sont grisés. */
    val saisieValide: Boolean,
    /** Au moins un protocole de suivi en cache : bouton « Monitoring » et « Mes visites ». */
    val monitoringDisponible: Boolean,
    /** Module OccHab présent ET droit de créer : bouton « OccHab » et « Mes stations ». */
    val occhabDisponible: Boolean,
    val saisiesEnAttente: Int = 0,
    val visitesEnAttente: Int = 0,
    val stationsEnAttente: Int = 0,
) {
    /** Pastille du menu : il reste quelque chose à envoyer, même dans une entrée masquée
     *  (`updatePastilleMenu` de GeoMys-Android ; GeoMys-IOS n'y comptait que les entrées visibles). */
    val pastilleMenu: Boolean
        get() = saisiesEnAttente > 0 || visitesEnAttente > 0 || stationsEnAttente > 0
}

/** Les entrées du menu de l'accueil. */
enum class EntreeMenuAccueil(val libelle: String) {
    MES_SAISIES("Mes saisies"),
    MES_VISITES("Mes visites"),
    MES_STATIONS("Mes stations"),
    EXPLORER("Explorer"),
    MAPS_MANAGER("Maps Manager"),
}

/** Les actions de l'accueil. */
class ActionsAccueil(
    val saisieMulti: () -> Unit,
    val saisieMono: () -> Unit,
    val monitoring: () -> Unit,
    val occhab: () -> Unit,
    val parametres: () -> Unit,
    val menu: (EntreeMenuAccueil) -> Unit,
)

/** Mention légale de la GPL : la garantie nulle doit être portée à l'utilisateur. */
private fun texteLicence(version: String, nomAppli: String) =
    "$nomAppli v$version\n" +
        "© 2026 ANA - CEN Ariège\n\n" +
        "Ce programme est un logiciel libre : vous pouvez le redistribuer et/ou le " +
        "modifier selon les termes de la GNU General Public License telle que publiée " +
        "par la Free Software Foundation, en version 3 ou (à votre choix) toute version " +
        "ultérieure.\n\n" +
        "Ce programme est distribué dans l'espoir qu'il sera utile, mais SANS AUCUNE " +
        "GARANTIE, sans même la garantie implicite de COMMERCIALISATION ou " +
        "d'ADÉQUATION À UN USAGE PARTICULIER. Voir la GNU General Public License pour " +
        "plus de détails."

/** Écran d'accueil : logo, version et licence, boutons de saisie, interrupteur de la trace GPS,
 *  menu (haut gauche) et accès aux Paramètres (haut droite). */
@Composable
fun AccueilScreen(etat: EtatAccueil, actions: ActionsAccueil, onTexteLicence: () -> Unit) {
    var licence by remember { mutableStateOf(false) }
    var menuOuvert by remember { mutableStateOf(false) }
    val nomAppli = etat.nomAppli
    val ios = estIos
    val flou = rememberEtatFlou()
    FondGeoMys {
        if (ios) {
            // Ce que le menu déroulant floute : le contenu, fond compris, et le voile.
            Box(Modifier.fillMaxSize().sourceFlou(flou).fondGeoMys()) {
                ContenuIos(etat, actions, nomAppli, onLicence = { licence = true })
                // Voile de fermeture du menu déroulant (sous la barre haute, au-dessus du contenu).
                AnimatedVisibility(menuOuvert, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.15f))
                            .clickable(remember { MutableInteractionSource() }, indication = null) { menuOuvert = false },
                    )
                }
            }
            BarreHautIos(etat, actions, menuOuvert, flou, onMenu = { menuOuvert = it })
        } else {
            ContenuAndroid(etat, actions, nomAppli, onLicence = { licence = true })
            BarreHautAndroid(etat, actions, menuOuvert, onMenu = { menuOuvert = it })
        }
        DialogueAlerte(
            visible = licence,
            titre = "Licence",
            message = texteLicence(etat.version, nomAppli),
            boutons = listOf(BoutonAlerte("Texte complet", action = onTexteLicence), BoutonAlerte("Fermer", annulation = true)),
            onFermer = { licence = false },
        )
    }
}

/** Entrées du menu, dans l'ordre et selon les conditions de GeoMys-Android, avec leur nombre
 *  d'éléments en attente d'envoi. */
private fun entreesMenu(etat: EtatAccueil): List<Pair<EntreeMenuAccueil, Int>> = buildList {
    add(EntreeMenuAccueil.MES_SAISIES to etat.saisiesEnAttente)
    if (etat.monitoringDisponible) add(EntreeMenuAccueil.MES_VISITES to etat.visitesEnAttente)
    if (etat.occhabDisponible) add(EntreeMenuAccueil.MES_STATIONS to etat.stationsEnAttente)
    add(EntreeMenuAccueil.EXPLORER to 0)
    add(EntreeMenuAccueil.MAPS_MANAGER to 0)
}

// ─── Allure Android (fragment_accueil.xml) ───

private val insetsSysteme @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)

@Composable
private fun ContenuAndroid(etat: EtatAccueil, actions: ActionsAccueil, nomAppli: String, onLicence: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(insetsSysteme.only(WindowInsetsSides.Vertical))
            .padding(top = 60.dp, bottom = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(Res.drawable.logo_geomys), contentDescription = "Logo GeoMys", modifier = Modifier.size(120.dp))
        Text(nomAppli, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 16.dp))
        Text("v${etat.version}", color = CouleursGeoMys.blanc70, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Text("Observations naturalistes", color = CouleursGeoMys.blanc70, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
        Text(
            "Logiciel libre — GNU GPL v3",
            color = CouleursGeoMys.jauneClair,
            fontSize = 12.sp,
            modifier = Modifier
                .padding(top = 6.dp)
                .clickable(remember { MutableInteractionSource() }, ripple(bounded = false), onClick = onLicence)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            RangeeSaisiesAndroid(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                BoutonAccueilAndroid("Saisie multi-taxons", etat.saisieValide, actions.saisieMulti, Modifier, 18.dp)
                BoutonAccueilAndroid("Saisie mono-taxon", etat.saisieValide, actions.saisieMono, Modifier, 6.dp)
            }
            if (etat.monitoringDisponible) {
                BoutonAccueilAndroid("Monitoring", etat.saisieValide, actions.monitoring, Modifier.fillMaxWidth().padding(bottom = 12.dp), 16.dp)
            }
            if (etat.occhabDisponible) {
                BoutonAccueilAndroid("OccHab", etat.saisieValide, actions.occhab, Modifier.fillMaxWidth().padding(bottom = 12.dp), 16.dp)
            }
            // Pas d'interrupteur « Enregistrer la trace GPS » : la trace se lance du bonhomme de la
            // carte multi-taxons (demande du 24 octobre 2026).
        }
    }
}

/** La rangée des deux boutons de saisie, comme son LinearLayout : deux colonnes de même largeur
 *  (les 8 dp de `layout_marginStart` retirés avant le partage des poids) ; la rangée prend la
 *  hauteur du bouton multi-taxons (`wrap_content`) et le bouton mono-taxon s'y étire
 *  (`match_parent`). */
@Composable
private fun RangeeSaisiesAndroid(modifier: Modifier, boutons: @Composable () -> Unit) {
    Layout(boutons, modifier) { mesurables, contraintes ->
        val ecart = 8.dp.roundToPx()
        val largeur = (contraintes.maxWidth - ecart) / 2
        val multi = mesurables[0].measure(Constraints(minWidth = largeur, maxWidth = largeur))
        val mono = mesurables[1].measure(Constraints.fixed(largeur, multi.height))
        layout(contraintes.maxWidth, multi.height) {
            multi.place(0, 0)
            mono.place(largeur + ecart, 0)
        }
    }
}

/** Bouton de l'accueil : MaterialButton teinté green_85, texte blanc 15 sp sans majuscules,
 *  estompé à 40 % et inactif tant que la configuration n'est pas valide (`setBoutonActif`). */
@Composable
private fun BoutonAccueilAndroid(texte: String, actif: Boolean, onClick: () -> Unit, modifier: Modifier, margeVerticale: Dp) {
    BoutonMaterial(
        onClick = onClick,
        modifier = modifier.alpha(if (actif) 1f else 0.4f),
        actif = actif,
        fond = CouleursGeoMys.vert85,
        marges = PaddingValues(horizontal = 24.dp, vertical = margeVerticale),
        couleurContenu = Color.White,
    ) { Text(texte, style = styleLibelleMaterial(Color.White, taille = 15f)) }
}

/** Boutons menu (haut gauche) et Paramètres (haut droite), dans des conteneurs de 8 dp de marge
 *  décalés de la barre d'état, avec leurs pastilles ; menu déroulant PopupMenu Material 3. */
@Composable
private fun BarreHautAndroid(etat: EtatAccueil, actions: ActionsAccueil, menuOuvert: Boolean, onMenu: (Boolean) -> Unit) {
    Box(Modifier.fillMaxWidth().windowInsetsPadding(insetsSysteme.only(WindowInsetsSides.Top))) {
        Box(Modifier.align(Alignment.TopStart).padding(8.dp)) {
            BoutonIconeAndroid(onClick = { onMenu(true) }) {
                Icon(painterResource(Res.drawable.ic_menu), contentDescription = "Menu", tint = Color.White)
            }
            if (etat.pastilleMenu) Pastille(CouleursGeoMys.rougePastille, Modifier.align(Alignment.TopEnd).padding(6.dp))
            DropdownMenu(
                expanded = menuOuvert,
                onDismissRequest = { onMenu(false) },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(4.dp),
            ) {
                for ((entree, enAttente) in entreesMenu(etat)) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                buildAnnotatedString {
                                    append(entree.libelle)
                                    // Pastille « ● » rouge accolée au titre (`titreAvecPastille`).
                                    if (enAttente > 0) withStyle(SpanStyle(color = CouleursGeoMys.rougePastille)) { append("   ●") }
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        onClick = {
                            onMenu(false)
                            actions.menu(entree)
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    )
                }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            BoutonIconeAndroid(onClick = actions.parametres) {
                Icon(painterResource(Res.drawable.ic_settings), contentDescription = "Configuration", tint = CouleursGeoMys.blanc70)
            }
            Pastille(
                if (etat.saisieValide) CouleursGeoMys.vertIndicateur else CouleursGeoMys.rougePastille,
                Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
        }
    }
}

/** ImageButton de 48 dp à fond `selectableItemBackgroundBorderless` : icône de 24 dp centrée,
 *  ondulation circulaire non bornée. */
@Composable
private fun BoutonIconeAndroid(onClick: () -> Unit, icone: @Composable () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clickable(remember { MutableInteractionSource() }, ripple(bounded = false), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icone() }
}

// ─── Allure iOS (AccueilView.swift) ───

@Composable
private fun ContenuIos(etat: EtatAccueil, actions: ActionsAccueil, nomAppli: String, onLicence: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(60.dp))
        // Coins arrondis à la façon d'une icône iOS (environ 22 %, courbure continue).
        Image(
            painterResource(Res.drawable.logo_geomys_ios),
            contentDescription = "Logo GeoMys",
            modifier = Modifier.size(120.dp).clip(FormeContinue(26.dp)),
        )
        Text(nomAppli, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 16.dp))
        Text("v${etat.version}", color = CouleursGeoMys.blanc70, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Text("Observations naturalistes", color = CouleursGeoMys.blanc70, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
        BoutonIos(onClick = onLicence, opaciteAppui = 0.3f) {
            Text("Logiciel libre — GNU GPL v3", color = CouleursGeoMys.jauneClair, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoutonAccueilIos("Saisie\nmulti-taxons", etat.saisieValide, actions.saisieMulti, Modifier.weight(1f), 18.dp)
                BoutonAccueilIos("Saisie\nmono-taxon", etat.saisieValide, actions.saisieMono, Modifier.weight(1f), 18.dp)
            }
            if (etat.monitoringDisponible) {
                BoutonAccueilIos("Monitoring", etat.saisieValide, actions.monitoring, Modifier.fillMaxWidth(), 16.dp)
            }
            if (etat.occhabDisponible) {
                BoutonAccueilIos("OccHab", etat.saisieValide, actions.occhab, Modifier.fillMaxWidth(), 16.dp)
            }
            // Pas d'interrupteur « Enregistrer la trace GPS » : la trace se lance du bonhomme de la
            // carte multi-taxons (demande du 24 octobre 2026).
        }
    }
}

/** `GeoMysBoutonVertStyle` : pilule green_85, texte blanc 15 pt medium centré, marges de 24 pt sur
 *  les côtés. Désactivé, le style l'estompe à 40 % et l'écran encore à 40 % : 16 % en tout, comme
 *  dans GeoMys-IOS. */
@Composable
private fun BoutonAccueilIos(texte: String, actif: Boolean, onClick: () -> Unit, modifier: Modifier, margeVerticale: Dp) {
    BoutonIos(onClick, modifier.alpha(if (actif) 1f else 0.4f), actif, opaciteInactif = 0.4f) {
        Text(
            texte,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(CouleursGeoMys.vert85, PILULE)
                .padding(horizontal = 24.dp, vertical = margeVerticale),
        )
    }
}

/** Barre haute : menu (« line.3.horizontal » blanc) et Paramètres (« gearshape.fill » blanc 70 %)
 *  dans des cadres de 48 pt, pastilles de 10 pt, menu déroulant maison ancré sous le menu. */
@Composable
private fun BarreHautIos(etat: EtatAccueil, actions: ActionsAccueil, menuOuvert: Boolean, flou: EtatFlou, onMenu: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Box {
                BoutonIos(onClick = { onMenu(!menuOuvert) }, modifier = Modifier.size(48.dp), opaciteAppui = 0.3f) {
                    SymboleSf("line.3.horizontal", 22f, listOf(Color.White), description = "Menu")
                }
                if (etat.pastilleMenu) {
                    Pastille(CouleursGeoMys.rougePastille, Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            Box {
                BoutonIos(onClick = actions.parametres, modifier = Modifier.size(48.dp), opaciteAppui = 0.3f) {
                    SymboleSf("gearshape.fill", 22f, listOf(CouleursGeoMys.blanc70), description = "Configuration")
                }
                Pastille(
                    if (etat.saisieValide) CouleursGeoMys.vert else CouleursGeoMys.rougePastille,
                    Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp),
                )
            }
        }
        AnimatedVisibility(
            menuOuvert,
            enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { -it / 4 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 4 },
        ) {
            MenuDeroulantIos(etat, flou) { entree ->
                onMenu(false)
                actions.menu(entree)
            }
        }
    }
}

/** La teinte sombre de `.regularMaterial` posée sur le flou : assez claire pour laisser deviner ce qui
 *  est dessous, assez dense pour le texte du menu. */
private val TEINTE_MATERIAU_REGULIER = Color(0xB3252525)

/** Menu déroulant de GeoMys-IOS : 240 pt de large, matériau translucide aux coins continus de
 *  14 pt, filet blanc 12 %, ombre ; lignes de 16 pt séparées, pastille rouge après le titre. Le
 *  matériau `.regularMaterial` floute ce qui est dessous ([flou], depuis le 24 octobre), sous sa
 *  teinte sombre. */
@Composable
private fun MenuDeroulantIos(etat: EtatAccueil, flou: EtatFlou, onChoix: (EntreeMenuAccueil) -> Unit) {
    val forme = FormeContinue(14.dp)
    Column(
        Modifier
            .padding(start = 8.dp)
            .width(240.dp)
            .shadow(10.dp, forme, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
            .fondFlou(flou, forme, TEINTE_MATERIAU_REGULIER, rayon = 30.dp)
            .border(1.dp, Color.White.copy(alpha = 0.12f), forme),
    ) {
        val entrees = entreesMenu(etat)
        entrees.forEachIndexed { i, (entree, enAttente) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onChoix(entree) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(entree.libelle, fontSize = 16.sp, color = Color.White)
                if (enAttente > 0) Pastille(CouleursGeoMys.rougePastille)
            }
            if (i < entrees.lastIndex) SeparateurIos()
        }
    }
}

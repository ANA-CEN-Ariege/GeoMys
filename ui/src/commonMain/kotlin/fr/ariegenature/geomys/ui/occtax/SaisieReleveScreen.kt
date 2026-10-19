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

// L'écran d'un relevé multi-taxons : fragment_saisie_observation.xml et item_pending_obs.xml de
// GeoMys-Android @ cdd5196 sous Android, SaisieObservationView et LigneEspeceView de GeoMys-IOS sous iOS.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.occtax.EspeceReleve
import fr.ariegenature.geomys.ui.composants.BandeauNavigation
import fr.ariegenature.geomys.ui.composants.BoutonContour
import fr.ariegenature.geomys.ui.composants.BoutonIos
import fr.ariegenature.geomys.ui.composants.HAUTEUR_BANDEAU_IOS
import fr.ariegenature.geomys.ui.composants.Interrupteur
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.afficher
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.rememberDictee
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.coche
import fr.ariegenature.geomys.ui.ressources.ic_delete
import fr.ariegenature.geomys.ui.ressources.ic_info
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Titre de l'écran (fragment_saisie_observation.xml, y compris quand on reprend un relevé). */
private const val TITRE = "Nouveau relevé"

/** android.R.color.darker_gray : icônes des groupes non choisis. */
private val GRIS_GROUPE = Color(0xFFAAAAAA)

/** Le relevé en cours : groupe, espèces ajoutées une à une, ✓ pour le terminer. [onCaracterisation]
 *  et [onDenombrement] ouvrent les sous-écrans d'une espèce, « Détails » le dialogue des détails du
 *  relevé, [onTermine] revient à la carte. */
@Composable
fun SaisieReleveScreen(
    vm: SaisieReleveViewModel,
    onAccueil: () -> Unit,
    onCaracterisation: (index: Int, comportementSeul: Boolean) -> Unit,
    onDenombrement: (index: Int) -> Unit,
    onTermine: () -> Unit,
) {
    val ios = estIos
    val toasts = LocalToasts.current
    LaunchedEffect(vm) {
        vm.evenements.collect { evenement ->
            when (evenement) {
                is EvenementReleve.Message -> toasts.afficher(evenement.texte)
                is EvenementReleve.Caracterisation -> onCaracterisation(evenement.index, evenement.comportementSeul)
                is EvenementReleve.Denombrement -> onDenombrement(evenement.index)
                EvenementReleve.Termine -> onTermine()
            }
        }
    }
    // À chaque affichage (onViewCreated) : le relevé est enregistré.
    LaunchedEffect(Unit) { vm.afficher() }
    val dictee = rememberDictee(
        onPartiel = vm::saisir,
        onFinal = vm::dicter,
        onErreur = { message, longue -> toasts.afficher(message, longue) },
    )
    val especes by vm.releve.especes.collectAsState()
    val ligne by vm.recherche.ligne.collectAsState()

    FondGeoMys {
        if (ios) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                BandeauNavigation(vm.typeSaisieLabel, onAccueil, titreIos = TITRE)
                // 20 pt entre le bandeau, les groupes, « Nom scientifique » et le champ espèce, comme en
                // saisie mono (demande du 19 octobre).
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    PaletteGroupes(vm.groupes, vm.taxon, vm::choisirTaxon, Modifier.padding(start = 12.dp, end = 12.dp, top = 20.dp))
                    // Le Toggle SwiftUI garde sa taille (libellé, puis l'interrupteur) ; l'espace restant
                    // le sépare de « Détails ».
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Nom scientifique", fontSize = 13.sp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Interrupteur(vm.nomScientifique, vm::basculerNomScientifique, teinteIos = CouleursGeoMys.vert)
                        Spacer(Modifier.weight(1f))
                        BoutonDetailsIos(vm::ouvrirDetails)
                    }
                    ChampEspece(
                        texte = vm.texte,
                        onTexte = vm::saisir,
                        libelle = vm.libelleChamp,
                        suggestions = vm.suggestions,
                        onChoisir = { suggestion ->
                            dictee.arreter()
                            vm.choisirSuggestion(suggestion)
                        },
                        ligne = ligne,
                        synchroEnCours = vm.synchroEnCours,
                        dictee = dictee,
                        onMicro = { dictee.basculer(vm.vocabulaire()) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // La DERNIÈRE espèce saisie en tête de liste.
                        for (index in especes.indices.reversed()) {
                            LigneEspece(especes[index], vm.nomScientifiqueSous(especes[index]), index, vm)
                        }
                    }
                }
            }
            BoutonIos(vm::terminer, Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(end = 12.dp).height(HAUTEUR_BANDEAU_IOS), opaciteAppui = 0.6f) {
                SymboleSf("checkmark.circle.fill", 30f, listOf(Color.White, CouleursGeoMys.vert), description = "Terminer le relevé")
            }
        } else {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    BandeauNavigation(vm.typeSaisieLabel, onAccueil, Modifier.padding(bottom = 12.dp))
                    Text(TITRE, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                    PaletteGroupes(vm.groupes, vm.taxon, vm::choisirTaxon, Modifier.padding(bottom = 12.dp))
                    // L'interrupteur à gauche, « Détails » à droite.
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.defaultMinSize(minHeight = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Nom scientifique", fontSize = 13.sp)
                            Spacer(Modifier.width(16.dp))
                            Interrupteur(vm.nomScientifique, vm::basculerNomScientifique)
                        }
                        Spacer(Modifier.weight(1f))
                        BoutonContour(
                            "Détails",
                            vm::ouvrirDetails,
                            margesAndroid = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            hauteurMinAndroid = 36.dp,
                        )
                    }
                    ChampEspece(
                        texte = vm.texte,
                        onTexte = vm::saisir,
                        libelle = vm.libelleChamp,
                        suggestions = vm.suggestions,
                        onChoisir = vm::choisirSuggestion,
                        ligne = ligne,
                        synchroEnCours = vm.synchroEnCours,
                        dictee = dictee,
                        onMicro = { dictee.basculer(emptyList()) },
                    )
                    Column(Modifier.padding(top = 16.dp)) {
                        // La DERNIÈRE espèce saisie en tête de liste.
                        for (index in especes.indices.reversed()) {
                            LigneEspece(especes[index], vm.nomScientifiqueSous(especes[index]), index, vm)
                        }
                    }
                }
                // ✓ « Terminer le relevé » : zone de 48 dp, coche de 32 dp.
                Image(
                    painterResource(Res.drawable.coche),
                    contentDescription = "Terminer le relevé",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(48.dp)
                        .clickable(onClick = vm::terminer)
                        .padding(8.dp),
                )
            }
        }
        DialogueDetailsReleve(vm.details, vm::fermerDetails, vm::validerDetails)
    }
    // Un échec de l'enregistrement au fil de l'eau est signalé par la carte, au retour (comme sous
    // Android, où seuls TraceFragment et SaisieRapideFragment observent echecEcritureBrouillon).
}

/** La palette des [groupes] (null pendant leur calcul), défilant à l'horizontale ; seuls les groupes
 *  chargés y figurent. Mêmes boutons dans les saisies mono et multi-taxons.
 *  - Android : MaterialButton texte de 88 × 48 dp, coins de 8 dp, icône de 24 dp, grise, blanche
 *    sur la couleur du groupe choisi ;
 *  - iOS : boutons de 44 pt espacés de 8, icône blanc 70 %, blanche sur la couleur du groupe choisi. */
@Composable
internal fun PaletteGroupes(groupes: List<Taxon>?, choisi: Taxon, onChoix: (Taxon) -> Unit, modifier: Modifier) {
    val ios = estIos
    if (groupes == null) return
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(if (ios) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (groupe in groupes) {
            val actif = groupe == choisi
            if (ios) {
                BoutonIos({ onChoix(groupe) }, opaciteAppui = 0.6f) {
                    Box(
                        Modifier.size(44.dp).background(if (actif) groupe.couleur else Color.Transparent, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) { IconeGroupe(groupe, 24, if (actif) Color.White else CouleursGeoMys.blanc70) }
                }
            } else {
                val interactions = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .defaultMinSize(minWidth = 88.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (actif) groupe.couleur else Color.Transparent)
                        .clickable(interactions, ripple(color = if (actif) Color.White else GRIS_GROUPE), role = Role.Button) { onChoix(groupe) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { IconeGroupe(groupe, 24, if (actif) Color.White else GRIS_GROUPE) }
            }
        }
    }
}

@Composable
private fun IconeGroupe(groupe: Taxon, taille: Int, teinte: Color) {
    Image(painterResource(groupe.icone), contentDescription = groupe.libelle, colorFilter = ColorFilter.tint(teinte), modifier = Modifier.size(taille.dp))
}

/** « Détails » de GeoMys-IOS : pilule au contour jaune clair de 120 pt, 13 pt medium. */
@Composable
internal fun BoutonDetailsIos(onClick: () -> Unit) {
    BoutonIos(onClick, Modifier.width(120.dp), opaciteAppui = 0.7f) {
        Box(
            Modifier
                .fillMaxWidth()
                .border(1.dp, CouleursGeoMys.jauneClair, RoundedCornerShape(percent = 50))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Détails",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = CouleursGeoMys.jauneClair,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

/** Une espèce du relevé : icône du groupe, nom (et nom scientifique dessous), « × N » qui ouvre le
 *  dénombrement, « i » la caractérisation, et la corbeille.
 *  - Android : item_pending_obs (icône teintée du texte, compteur dans un disque blanc de 24 dp,
 *    « i » blanc de 28 dp, corbeille rouge de 24 dp, zones de 36 dp) ;
 *  - iOS : LigneEspeceView (icône teintée du groupe, pastilles blanches de 28 pt, corbeille SF). */
@Composable
private fun LigneEspece(espece: EspeceReleve, sci: String?, index: Int, vm: SaisieReleveViewModel) {
    if (estIos) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { IconeGroupe(espece.taxon, 18, espece.taxon.couleur) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(espece.espece, fontSize = 15.sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (sci != null) Text(sci, fontSize = 13.sp, fontStyle = FontStyle.Italic, color = CouleursGeoMys.blanc70, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PastilleIos("× ${espece.total}", "Dénombrement") { vm.ouvrirDenombrement(index) }
            PastilleIos("i", "Caractérisation") { vm.ouvrirCaracterisation(index) }
            BoutonIos({ vm.supprimer(index) }, Modifier.size(36.dp), opaciteAppui = 0.6f) {
                SymboleSf("trash", 18f, listOf(CouleursGeoMys.rougePastille), description = "Supprimer")
            }
        }
    } else {
        val texte = MaterialTheme.colorScheme.onSurface
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(espece.taxon.icone),
                contentDescription = null,
                colorFilter = ColorFilter.tint(texte),
                modifier = Modifier.padding(end = 10.dp).size(24.dp),
            )
            Column(Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 6.dp)) {
                Text(espece.espece, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (sci != null) {
                    Text(sci, fontSize = 13.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            CompteurAndroid("× ${espece.total}") { vm.ouvrirDenombrement(index) }
            BoutonLigneAndroid(Res.drawable.ic_info, "Détails", Color.White, marge = 4) { vm.ouvrirCaracterisation(index) }
            BoutonLigneAndroid(Res.drawable.ic_delete, "Supprimer", CouleursGeoMys.holoRougeFonce, marge = 6) { vm.supprimer(index) }
        }
    }
}

/** Compteur « × N » d'Android : texte gras de 11 sp dans un disque blanc de 24 dp (gris appuyé). */
@Composable
private fun CompteurAndroid(texte: String, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val appuye by interactions.collectIsPressedAsState()
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(36.dp)
            .clickable(interactions, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(24.dp).background(if (appuye) Color(0xFFE0E0E0) else Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Text(texte, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212121), maxLines = 1)
        }
    }
}

/** ImageButton de 36 dp sans fond (ondulation sans bord), icône teintée dans [marge] dp. */
@Composable
private fun BoutonLigneAndroid(icone: DrawableResource, description: String, teinte: Color, marge: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(36.dp)
            .clickable(remember { MutableInteractionSource() }, ripple(bounded = false, radius = 18.dp), role = Role.Button, onClick = onClick)
            .padding(marge.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(icone), contentDescription = description, colorFilter = ColorFilter.tint(teinte), modifier = Modifier.fillMaxSize())
    }
}

/** BoutonRondInline de GeoMys-IOS : disque blanc de 28 pt, texte gras de 12 pt gris très foncé. */
@Composable
private fun PastilleIos(texte: String, description: String, onClick: () -> Unit) {
    BoutonIos(onClick, Modifier.semantics { contentDescription = description }, opaciteAppui = 0.6f) {
        Box(Modifier.size(28.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Text(texte, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212121), maxLines = 1)
        }
    }
}

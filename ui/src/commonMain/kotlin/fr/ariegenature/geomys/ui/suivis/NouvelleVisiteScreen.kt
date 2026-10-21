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

// La nouvelle saisie des Suivis : fragment_nouvelle_visite.xml et NouvelleVisiteFragment de GeoMys-Android
// @ cdd5196 sous Android (fil d'Ariane flottant, titre et contexte dans le défilement, barre d'enregistrement
// fixe), NouvelleVisiteView et BarreBoutonsVisiteView de GeoMys-IOS (UI/Monitoring/NouvelleVisiteView.swift)
// sous iOS (fil en bande, en-tête fixe, `Form`, barre à fond noir 25 %).

package fr.ariegenature.geomys.ui.suivis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.monitoring.ActionValider
import fr.ariegenature.geomys.monitoring.FilSegment
import fr.ariegenature.geomys.monitoring.NouvelleVisite
import fr.ariegenature.geomys.ui.composants.BalayageRetourIosDesactive
import fr.ariegenature.geomys.ui.composants.BoutonAlerte
import fr.ariegenature.geomys.ui.composants.BoutonIos
import fr.ariegenature.geomys.ui.composants.BoutonPlein
import fr.ariegenature.geomys.ui.composants.BoutonTexte
import fr.ariegenature.geomys.ui.composants.DialogueActions
import fr.ariegenature.geomys.ui.composants.DialogueAlerte
import fr.ariegenature.geomys.ui.composants.FilArianeSuivis
import fr.ariegenature.geomys.ui.composants.Interrupteur
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.PILULE
import fr.ariegenature.geomys.ui.composants.RetourSysteme
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.rememberAjoutMedias
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys

/** `GeoMysBoutonVertStyle` désactivé sous `.saturation(0)` : le vert 85 % ramené à sa luminance. */
private val VERT85_DESATURE = Color(0xD9868686)

@Composable
fun NouvelleVisiteScreen(
    vm: NouvelleVisiteViewModel,
    onNiveau: (List<FilSegment>) -> Unit,
    onRetour: () -> Unit,
    onRemplacer: (OuvertureNouvelleVisite) -> Unit,
    onFiche: (EvenementNouvelleVisite.Fiche) -> Unit,
    choixMultipleInitial: String? = null,
) {
    val ios = estIos
    val toasts = LocalToasts.current
    LaunchedEffect(vm) {
        vm.evenements.collect { evenement ->
            when (evenement) {
                is EvenementNouvelleVisite.Toast -> toasts.afficher(evenement.texte, evenement.longue)
                EvenementNouvelleVisite.Retour -> onRetour()
                is EvenementNouvelleVisite.Niveau -> onNiveau(evenement.cible)
                is EvenementNouvelleVisite.Remplacer -> onRemplacer(evenement.ouverture)
                is EvenementNouvelleVisite.Fiche -> onFiche(evenement)
            }
        }
    }
    // Retour d'Android : la garde de sortie. GeoMys-IOS masque le bouton retour, et avec lui le
    // balayage : on y quitte le formulaire par le fil d'Ariane ou « Terminer ».
    if (ios) BalayageRetourIosDesactive() else RetourSysteme(onRetour = vm::retour)

    // [choixMultipleInitial] : essai d'affichage, le choix multiple de ce champ ouvert d'emblée.
    var choixMultiple by remember { mutableStateOf(choixMultipleInitial) }
    val ouvrirChoixMultiple: (String) -> Unit = { choixMultiple = it }
    var cibleMedia by rememberSaveable { mutableStateOf("") }
    var choixPhoto by remember { mutableStateOf(false) }
    val medias = rememberAjoutMedias(
        vm.dossierMedias,
        onImportes = { uris -> vm.ajouterMedias(cibleMedia, uris) },
        onErreur = { toasts.afficher(it, true) },
    )
    val ajouterPhotos: (String) -> Unit = { code ->
        cibleMedia = code
        // Sans appareil photo (simulateur), la galerie directement.
        if (medias.appareilPhoto) choixPhoto = true else medias.choisirPhotos()
    }

    FondGeoMys {
        // Le fil d'Ariane et la maison passent par la garde de sortie.
        if (ios) PageIos(vm, vm::allerVers, ajouterPhotos, ouvrirChoixMultiple) else PageAndroid(vm, vm::allerVers, ajouterPhotos, ouvrirChoixMultiple)
    }
    vm.etat?.let { DialogueChoixMultipleFormulaire(it, choixMultiple) { choixMultiple = null } }
    DialogueActions(
        visible = choixPhoto,
        titre = "Ajouter une photo",
        actions = listOfNotNull(
            BoutonAlerte("Prendre une photo") { medias.prendrePhoto() },
            BoutonAlerte("Choisir dans la galerie") { medias.choisirPhotos() },
            if (ios) BoutonAlerte("Annuler", annulation = true) else null,
        ),
        onFermer = { choixPhoto = false },
    )
    Dialogues(vm)
}

/** fragment_nouvelle_visite.xml : le défilement (titre 22 sp gras, contexte 13 sp darker_gray,
 *  formulaire) commence sous le fil flottant, en suivant sa hauteur ; la barre d'enregistrement
 *  (colorSurface, élévation 8 dp, marges 16 × 8 dp) reste en bas, hors défilement. */
@Composable
private fun PageAndroid(
    vm: NouvelleVisiteViewModel,
    onNiveau: (List<FilSegment>) -> Unit,
    ajouterPhotos: (String) -> Unit,
    ouvrirChoixMultiple: (String) -> Unit,
) {
    val densite = LocalDensity.current
    // 64 dp tant que le fil n'est pas mesuré (paddingTop du ScrollView dans le layout).
    var hautFil by remember { mutableStateOf(64.dp) }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).union(WindowInsets.ime))) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = hautFil)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                Text(vm.titre, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(vm.contexte, fontSize = 13.sp, color = CouleursGeoMys.grisFonce, modifier = Modifier.padding(top = 4.dp))
                if (vm.enchainementPropose) InterrupteurEnchainer(vm, Modifier.padding(top = 8.dp))
                // Un nouveau formulaire (saisie suivante d'une chaîne) remplace l'ancien, comme `rendre` sous
                // Android : le focus et l'état propre à chaque champ ne passent pas de l'un à l'autre.
                vm.etat?.let { e -> key(e) { FormulaireMonitoring(e, vm.barres, ajouterPhotos, ouvrirChoixMultiple) } }
            }
            if (!vm.impossible) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .shadow(8.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    // Les boutons du web : « Valider et saisir des … », puis « Annuler » et « Valider ».
                    vm.libelleSaisirEnfants?.let { libelle ->
                        BoutonPlein(
                            libelle,
                            { vm.enregistrer(action = ActionValider.SAISIR_ENFANTS) },
                            Modifier.fillMaxWidth().alpha(if (vm.saisirEnfantsPermis) 1f else 0.5f),
                            actif = vm.boutonActif,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BoutonTexte(NouvelleVisite.ANNULER, CouleursGeoMys.jauneClair, vm::retour, Modifier.padding(end = 8.dp))
                        BoutonPlein(vm.libelleBouton, { vm.enregistrer() }, Modifier.weight(1f), actif = vm.boutonActif)
                    }
                }
            }
        }
        FilArianeSuivis(
            vm.fil,
            dernierCliquable = true,
            onNiveau,
            Modifier
                .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                .onSizeChanged { taille -> hautFil = 12.dp + with(densite) { taille.height.toDp() } + 12.dp },
        )
    }
}

/** NouvelleVisiteView : le fil en bande, l'en-tête (titre 18 pt gras, contexte 13 pt blanc 70 %),
 *  le `Form` défilant, puis la barre de boutons. */
@Composable
private fun PageIos(
    vm: NouvelleVisiteViewModel,
    onNiveau: (List<FilSegment>) -> Unit,
    ajouterPhotos: (String) -> Unit,
    ouvrirChoixMultiple: (String) -> Unit,
) {
    // Le fond de la barre descend sous l'indicateur d'accueil (et jusqu'au clavier), comme un fond SwiftUI.
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        FilArianeSuivis(vm.fil, dernierCliquable = true, onNiveau)
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(vm.titre, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (vm.contexte.isNotEmpty()) Text(vm.contexte, fontSize = 13.sp, color = CouleursGeoMys.blanc70)
            if (vm.enchainementPropose) InterrupteurEnchainer(vm, Modifier.padding(top = 6.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            vm.etat?.let { e -> key(e) { FormulaireMonitoring(e, vm.barres, ajouterPhotos, ouvrirChoixMultiple) } }
        }
        if (vm.impossible) Spacer(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) else BarreBoutonsIos(vm)
    }
}

/** BarreBoutonsVisiteView, aux boutons du web : « Valider et saisir des … » vert sur toute la
 *  largeur s'il y a lieu, puis « Annuler » (style `.bordered` teinté blanc 70 %) et le bouton vert
 *  (`GeoMysBoutonVertStyle`, demi-gras), côte à côte ; désactivé, le vert est estompé et grisé. */
@Composable
private fun BarreBoutonsIos(vm: NouvelleVisiteViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.25f))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val actif = vm.boutonActif
        vm.libelleSaisirEnfants?.let { libelle ->
            BoutonVertIos(libelle, actif, Modifier.fillMaxWidth().alpha(if (vm.saisirEnfantsPermis) 1f else 0.5f)) {
                vm.enregistrer(action = ActionValider.SAISIR_ENFANTS)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BoutonIos(vm::retour, Modifier.weight(1f), opaciteAppui = 0.6f) {
                Text(
                    NouvelleVisite.ANNULER,
                    fontSize = 17.sp,
                    color = CouleursGeoMys.blanc70,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.12f), PILULE)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
            BoutonVertIos(vm.libelleBouton, actif, Modifier.weight(1f)) { vm.enregistrer() }
        }
    }
}

/** Le bouton vert de la barre (`GeoMysBoutonVertStyle`, demi-gras) ; désactivé, estompé et grisé. */
@Composable
private fun BoutonVertIos(libelle: String, actif: Boolean, modifier: Modifier, onClick: () -> Unit) {
    BoutonIos(onClick, modifier, actif = actif) {
        Text(
            libelle,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (actif) 1f else 0.16f)
                .background(if (actif) CouleursGeoMys.vert85 else VERT85_DESATURE, PILULE)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

/** L'interrupteur « Enchaîner les saisies » du web (`mat-slide-toggle`, à droite du formulaire) :
 *  activé, « Valider » enchaîne un nouveau formulaire du même type. */
@Composable
private fun InterrupteurEnchainer(vm: NouvelleVisiteViewModel, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Text(
            NouvelleVisite.ENCHAINER_LES_SAISIES,
            fontSize = if (estIos) 15.sp else 14.sp,
            color = if (estIos) CouleursGeoMys.blanc70 else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        Interrupteur(vm.enchainer, { vm.enchainer = it }, description = NouvelleVisite.ENCHAINER_LES_SAISIES, teinteIos = CouleursGeoMys.vert)
    }
}

/** Les dialogues : garde de sortie et « Terminer » (titres et boutons accordés au type), complétion
 *  de la visite parente (sans fermeture hors de ses boutons), échec d'écriture. Le dernier reste
 *  composé le temps de sa disparition. */
@Composable
private fun Dialogues(vm: NouvelleVisiteViewModel) {
    var dernier by remember { mutableStateOf<DialogueNouvelleVisite?>(null) }
    vm.dialogue?.let { dernier = it }
    val visible = vm.dialogue != null
    when (val d = dernier) {
        null -> Unit
        is DialogueNouvelleVisite.GardeSortie -> DialogueAlerte(
            visible,
            d.titre,
            d.message,
            listOfNotNull(
                BoutonAlerte(d.continuer, annulation = true),
                BoutonAlerte("Quitter sans enregistrer", destructif = true, action = vm::quitter),
                if (d.enregistrable) BoutonAlerte("Enregistrer et quitter") { vm.enregistrer(puisTerminer = true) } else null,
            ),
            vm::fermerDialogue,
        )
        is DialogueNouvelleVisite.Completer -> DialogueAlerte(
            visible,
            NouvelleVisite.TITRE_COMPLETER,
            NouvelleVisite.messageCompleter(d.parent.manquants()),
            listOf(BoutonAlerte("Plus tard", annulation = true, action = vm::quitter), BoutonAlerte("Compléter") { vm.completer(d.parent) }),
            vm::fermerDialogue,
            annulable = false,
        )
        is DialogueNouvelleVisite.EcritureImpossible -> DialogueAlerte(
            visible,
            NouvelleVisite.TITRE_ENREGISTREMENT_IMPOSSIBLE,
            d.message,
            listOf(BoutonAlerte("OK")),
            vm::fermerDialogue,
        )
    }
}

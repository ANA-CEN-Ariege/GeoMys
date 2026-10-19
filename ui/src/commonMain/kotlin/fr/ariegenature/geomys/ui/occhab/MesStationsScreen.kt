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

// Écran « Mes stations » : fragment_occhab_stations.xml et item_occhab_station.xml de GeoMys-Android @ cdd5196
// sous Android, OccHabStationsView de GeoMys-IOS sous iOS.

package fr.ariegenature.geomys.ui.occhab

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.ariegenature.geomys.envoi.EnvoiGroupe
import fr.ariegenature.geomys.model.OccHabSaisie
import fr.ariegenature.geomys.occhab.MesStations
import fr.ariegenature.geomys.occhab.OngletMesStations
import fr.ariegenature.geomys.ui.composants.BandeauNavigation
import fr.ariegenature.geomys.ui.composants.BoutonAlerte
import fr.ariegenature.geomys.ui.composants.BoutonIcone
import fr.ariegenature.geomys.ui.composants.BoutonIos
import fr.ariegenature.geomys.ui.composants.BoutonVert
import fr.ariegenature.geomys.ui.composants.DialogueAlerte
import fr.ariegenature.geomys.ui.composants.GraisseSymbole
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.OngletsListe
import fr.ariegenature.geomys.ui.composants.ProgressionEnvoi
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.afficher
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.ic_delete
import fr.ariegenature.geomys.ui.ressources.ic_edit
import fr.ariegenature.geomys.ui.ressources.ic_map
import fr.ariegenature.geomys.ui.ressources.ic_send
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys
import fr.ariegenature.geomys.ui.theme.FormeContinue
import org.jetbrains.compose.resources.painterResource

/** android.R.color.holo_red_light : icône « Supprimer » des lignes. */
private val HOLO_ROUGE_CLAIR = Color(0xFFFF4444)

/** ?attr/colorControlHighlight du thème sombre : filet sous le bandeau. */
private val SURBRILLANCE = Color(0x33FFFFFF)

@Composable
fun MesStationsScreen(vm: MesStationsViewModel, onAccueil: () -> Unit, onCarte: () -> Unit) {
    val ios = estIos
    val toasts = LocalToasts.current
    LaunchedEffect(vm) { vm.messages.collect { toasts.afficher(it.texte, it.longue) } }
    LaunchedEffect(vm) { vm.reprises.collect { onCarte() } }
    LifecycleResumeEffect(vm) {
        vm.rafraichir()
        onPauseOrDispose {}
    }
    val toutes = vm.toutes
    val saisies = vm.saisies

    FondGeoMys {
        Column(
            Modifier
                .fillMaxSize()
                .then(
                    if (ios) Modifier.safeDrawingPadding()
                    else Modifier.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                ),
        ) {
            if (ios) {
                BandeauNavigation(MesStations.TITRE, onAccueil)
            } else {
                BandeauNavigation(MesStations.TITRE, onAccueil, Modifier.padding(start = 12.dp, top = 12.dp))
                Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(1.dp).background(SURBRILLANCE))
            }
            OngletsListe(
                titres = OngletMesStations.entries.map { MesStations.titreOnglet(toutes, it) },
                selection = vm.onglet.ordinal,
                onChoisir = { vm.choisirOnglet(OngletMesStations.entries[it]) },
            )
            vm.messageEnvoi?.let { ProgressionEnvoi(it) }
            if (vm.boutonToutEnvoyerVisible) {
                BoutonVert(
                    EnvoiGroupe.libelleBouton(vm.nbEnvoyables),
                    vm::demanderToutEnvoyer,
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = if (ios) 10.dp else 8.dp),
                )
            }
            if (saisies.isEmpty()) {
                ListeVide(vm.onglet.messageVide, Modifier.weight(1f))
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    contentPadding = if (ios) PaddingValues(horizontal = 12.dp, vertical = 8.dp) else PaddingValues(),
                    verticalArrangement = if (ios) Arrangement.spacedBy(6.dp) else Arrangement.Top,
                ) {
                    items(saisies, key = { it.id }) { saisie ->
                        if (ios) LigneIos(saisie, vm) else LigneAndroid(saisie, vm)
                    }
                }
            }
        }

        val aSupprimer = vm.suppressionDemandee
        DialogueAlerte(
            visible = aSupprimer != null,
            titre = "Supprimer la saisie ?",
            message = aSupprimer?.let(MesStations::messageSuppression).orEmpty(),
            boutons = listOf(
                BoutonAlerte("Supprimer", action = vm::supprimer),
                BoutonAlerte("Annuler", annulation = true, action = vm::annulerSuppression),
            ),
            onFermer = vm::annulerSuppression,
        )
        val aEnvoyer = vm.confirmationToutEnvoyer
        DialogueAlerte(
            visible = aEnvoyer != null,
            titre = EnvoiGroupe.TITRE_CONFIRMATION,
            message = aEnvoyer?.let { EnvoiGroupe.messageConfirmation(it.size) }.orEmpty(),
            boutons = listOf(
                BoutonAlerte(EnvoiGroupe.BOUTON_CONFIRMATION, action = vm::toutEnvoyer),
                BoutonAlerte("Annuler", annulation = true, action = vm::annulerToutEnvoyer),
            ),
            onFermer = vm::annulerToutEnvoyer,
        )
        val info = vm.information
        DialogueAlerte(
            visible = info != null,
            titre = info?.titre.orEmpty(),
            message = info?.message.orEmpty(),
            boutons = listOf(BoutonAlerte("OK")),
            onFermer = vm::fermerInformation,
        )
    }
}

/** Liste vide : l'icône de carte estompée et le message de l'onglet. */
@Composable
private fun ListeVide(message: String, modifier: Modifier = Modifier) {
    val ios = estIos
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (ios) {
            SymboleSf("map", 48f, listOf(CouleursGeoMys.blanc70.copy(alpha = 0.5f)))
            Text(
                message,
                fontSize = 14.sp,
                color = CouleursGeoMys.blanc70,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp).padding(top = 12.dp),
            )
        } else {
            // L'icône garde son gris (#424242) à 30 %.
            Image(painterResource(Res.drawable.ic_map), contentDescription = null, modifier = Modifier.size(64.dp).alpha(0.3f))
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** item_occhab_station : date, nombre de stations, état d'envoi (« ✅ Envoyée », ou le dernier
 *  échec dans un cadre rouge) ; puis Envoyer · Modifier · Supprimer. La ligne n'est pas cliquable. */
@Composable
private fun LigneAndroid(saisie: OccHabSaisie, vm: MesStationsViewModel) {
    val couleurErreur = MaterialTheme.colorScheme.error
    val erreur = MesStations.ligneErreur(saisie)
    val enErreur = !saisie.envoyeGeoNature && erreur != null
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (enErreur) Modifier.border(2.dp, couleurErreur, RoundedCornerShape(8.dp)) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(MesStations.dateLigne(saisie), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(MesStations.nbStations(saisie), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            when {
                saisie.envoyeGeoNature -> Text("✅ Envoyée", fontSize = 12.sp, color = CouleursGeoMys.succes, modifier = Modifier.padding(top = 4.dp))
                erreur != null -> Text("⚠ $erreur", fontSize = 12.sp, color = couleurErreur, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (MesStations.peutEnvoyer(saisie, vm.envoiAutorise)) {
            // `@color/colorSecondary` : la ressource reste verte en mode nuit.
            BoutonIcone(Res.drawable.ic_send, CouleursGeoMys.vert, "Envoyer au serveur", { vm.envoyer(saisie) })
        }
        if (MesStations.peutEditer(saisie)) {
            BoutonIcone(Res.drawable.ic_edit, CouleursGeoMys.primaire, "Modifier la saisie", { vm.editer(saisie) })
        }
        BoutonIcone(Res.drawable.ic_delete, HOLO_ROUGE_CLAIR, "Supprimer", { vm.demanderSuppression(saisie) })
    }
}

/** La ligne d'OccHabStationsView : mêmes éléments, symboles SF de 16 pt demi-gras sur des cibles de
 *  44 pt, cadre rouge aux coins continus de 8 pt sur un échec. */
@Composable
private fun LigneIos(saisie: OccHabSaisie, vm: MesStationsViewModel) {
    val erreur = MesStations.ligneErreur(saisie)
    val enErreur = !saisie.envoyeGeoNature && erreur != null
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (enErreur) Modifier.border(2.dp, CouleursGeoMys.rougePastille, FormeContinue(8.dp)) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(MesStations.dateLigne(saisie), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(MesStations.nbStations(saisie), fontSize = 12.sp, color = CouleursGeoMys.blanc70)
            when {
                saisie.envoyeGeoNature -> Text("✅ Envoyée", fontSize = 12.sp, color = CouleursGeoMys.vert)
                erreur != null -> Text("⚠ $erreur", fontSize = 12.sp, color = CouleursGeoMys.rougePastille, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (MesStations.peutEnvoyer(saisie, vm.envoiAutorise)) {
            BoutonSymbole("paperplane.fill", CouleursGeoMys.vert, "Envoyer la saisie") { vm.envoyer(saisie) }
        }
        if (MesStations.peutEditer(saisie)) {
            BoutonSymbole("pencil", CouleursGeoMys.jauneClair, "Modifier la saisie") { vm.editer(saisie) }
        }
        BoutonSymbole("trash", CouleursGeoMys.rougePastille, "Supprimer la saisie") { vm.demanderSuppression(saisie) }
    }
}

@Composable
private fun BoutonSymbole(symbole: String, couleur: Color, description: String, onClick: () -> Unit) {
    BoutonIos(onClick, Modifier.size(44.dp), opaciteAppui = 0.6f) {
        SymboleSf(symbole, 16f, listOf(couleur), graisse = GraisseSymbole.DEMI_GRASSE, description = description)
    }
}

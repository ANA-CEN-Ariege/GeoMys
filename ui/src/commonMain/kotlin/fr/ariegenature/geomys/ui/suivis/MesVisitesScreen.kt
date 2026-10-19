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

// « Mes visites » : fragment_saisies_en_attente.xml et les lignes construites par SaisiesEnAttenteFragment de
// GeoMys-Android @ cdd5196 sous Android, SaisiesEnAttenteView de GeoMys-IOS (UI/Monitoring/SaisiesEnAttenteView.swift)
// sous iOS.

package fr.ariegenature.geomys.ui.suivis

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.ariegenature.geomys.model.SaisieEnAttente
import fr.ariegenature.geomys.monitoring.ItemMesVisites
import fr.ariegenature.geomys.monitoring.MesVisites
import fr.ariegenature.geomys.monitoring.OngletMesVisites
import fr.ariegenature.geomys.monitoring.OptionSaisie
import fr.ariegenature.geomys.ui.composants.BandeauNavigation
import fr.ariegenature.geomys.ui.composants.BoutonAlerte
import fr.ariegenature.geomys.ui.composants.BoutonIcone
import fr.ariegenature.geomys.ui.composants.BoutonIos
import fr.ariegenature.geomys.ui.composants.BoutonVert
import fr.ariegenature.geomys.ui.composants.DialogueActions
import fr.ariegenature.geomys.ui.composants.DialogueAlerte
import fr.ariegenature.geomys.ui.composants.DialogueChoixLignesAndroid
import fr.ariegenature.geomys.ui.composants.GraisseSymbole
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.OngletsListe
import fr.ariegenature.geomys.ui.composants.ProgressionEnvoi
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.ic_delete
import fr.ariegenature.geomys.ui.ressources.ic_edit
import fr.ariegenature.geomys.ui.ressources.ic_send
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys
import fr.ariegenature.geomys.ui.theme.FormeContinue
import org.jetbrains.compose.resources.DrawableResource

/** android.R.color.holo_red_light : la corbeille des lignes, celle de « Mes saisies ». */
private val HOLO_ROUGE_CLAIR = Color(0xFFFF4444)

/** La flèche d'envoi d'une saisie « à compléter » : le rouge franc des barres du formulaire. */
private val ROUGE_A_COMPLETER = Color(0xFFD32F2F)

@Composable
fun MesVisitesScreen(
    vm: MesVisitesViewModel,
    onAccueil: () -> Unit,
    onEditer: (OuvertureNouvelleVisite) -> Unit,
) {
    val ios = estIos
    val toasts = LocalToasts.current
    LaunchedEffect(vm) {
        vm.evenements.collect { evenement ->
            when (evenement) {
                is EvenementMesVisites.Toast -> toasts.afficher(evenement.texte, evenement.longue)
                is EvenementMesVisites.Edition -> onEditer(evenement.ouverture)
            }
        }
    }
    LifecycleResumeEffect(vm) {
        vm.rafraichir()
        onPauseOrDispose {}
    }
    val couleurSecondaire = if (ios) CouleursGeoMys.blanc70 else MaterialTheme.colorScheme.onSurfaceVariant

    FondGeoMys {
        // Android : la colonne de fragment_saisies_en_attente.xml, à 16 dp des bords.
        Column(
            Modifier
                .fillMaxSize()
                .then(
                    if (ios) Modifier.safeDrawingPadding()
                    else Modifier.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).padding(16.dp)
                ),
        ) {
            BandeauNavigation(MesVisites.TITRE, onAccueil)
            Text(
                vm.resume.texte,
                fontSize = if (ios) 13.sp else 14.sp,
                fontWeight = if (ios) FontWeight.SemiBold else FontWeight.Normal,
                color = if (ios) Color.White else couleurSecondaire,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (ios) PaddingValues(horizontal = 14.dp, vertical = 8.dp) else PaddingValues(top = 20.dp)),
            )
            vm.messageEnvoi?.let { ProgressionEnvoi(it, margeAndroid = 0.dp) }
            OngletsListe(
                titres = OngletMesVisites.entries.map { MesVisites.titreOnglet(it, vm.resume) },
                selection = vm.onglet.ordinal,
                onChoisir = { vm.choisirOnglet(OngletMesVisites.entries[it]) },
                modifier = if (ios) Modifier else Modifier.padding(top = 8.dp),
            )
            if (vm.onglet == OngletMesVisites.A_ENVOYER && vm.nbEnvoyables > 0 && !vm.envoiEnCours) {
                BoutonVert(
                    MesVisites.libelleToutEnvoyer(vm.nbEnvoyables),
                    vm::demanderToutEnvoyer,
                    if (ios) Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp)
                    else Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).then(if (ios) Modifier else Modifier.padding(top = 12.dp)),
                contentPadding = if (ios) PaddingValues(horizontal = 12.dp, vertical = 8.dp) else PaddingValues(),
                verticalArrangement = if (ios) Arrangement.spacedBy(6.dp) else Arrangement.Top,
            ) {
                items(vm.items) { item ->
                    when (item) {
                        is ItemMesVisites.Vide -> Text(
                            item.texte,
                            fontSize = if (ios) 14.sp else 13.sp,
                            color = couleurSecondaire,
                            modifier = if (ios) Modifier.padding(top = 8.dp) else Modifier,
                        )
                        is ItemMesVisites.Protocole -> Text(
                            item.texte,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = CouleursGeoMys.jauneClair,
                            modifier = Modifier.padding(if (ios) PaddingValues(top = 6.dp) else PaddingValues(start = 12.dp, top = 18.dp, end = 12.dp, bottom = 2.dp)),
                        )
                        is ItemMesVisites.Groupe -> Text(
                            item.texte,
                            fontSize = 12.sp,
                            color = if (ios) CouleursGeoMys.blanc70 else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(
                                if (ios) PaddingValues(top = 10.dp, bottom = 2.dp) else PaddingValues(start = 12.dp, top = 20.dp, end = 12.dp, bottom = 4.dp),
                            ),
                        )
                        is ItemMesVisites.Ligne -> LigneSaisie(vm, item, couleurSecondaire)
                    }
                }
            }
        }
    }
    Dialogues(vm)
}

/** Une saisie : « ⏳ Visite » (le taxon quand elle en porte un), « stom · 19/10 13:39 », l'erreur
 *  du dernier envoi ; indentée de 20 par niveau ; cadre d'état (erreur, envoyée, en cours).
 *  Actions : envoyer le groupe (racine, flèche rouge si « à compléter »), éditer, supprimer ; une
 *  racine envoyée dont des observations restent à envoyer ne garde que la flèche. */
@Composable
private fun LigneSaisie(vm: MesVisitesViewModel, ligne: ItemMesVisites.Ligne, couleurSecondaire: Color) {
    val ios = estIos
    val s = ligne.saisie
    val couleurErreur = if (ios) CouleursGeoMys.rougePastille else MaterialTheme.colorScheme.error
    val cadre = when (s.etat) {
        SaisieEnAttente.Etat.ERROR -> couleurErreur
        SaisieEnAttente.Etat.SENT -> if (ios) CouleursGeoMys.vert else CouleursGeoMys.succes
        SaisieEnAttente.Etat.SENDING -> if (ios) CouleursGeoMys.orange else CouleursGeoMys.enCours
        SaisieEnAttente.Etat.PENDING -> null
    }
    val modifiable = s.etat == SaisieEnAttente.Etat.PENDING || s.etat == SaisieEnAttente.Etat.ERROR
    val racine = ligne.profondeur == 0
    val actions: @Composable () -> Unit = {
        if (modifiable) {
            if (racine && vm.envoiAutorise(s)) {
                ActionLigne(
                    Res.drawable.ic_send, "paperplane.fill",
                    if (s.aCompleter) ROUGE_A_COMPLETER else CouleursGeoMys.vert,
                    if (s.aCompleter) "Compléter avant d'envoyer" else "Envoyer ce groupe",
                ) { vm.envoyerGroupe(s) }
            }
            ActionLigne(Res.drawable.ic_edit, "pencil", CouleursGeoMys.jauneClair, "Éditer ${vm.ceTypeDe(s)}") { vm.editer(s) }
            ActionLigne(Res.drawable.ic_delete, "trash", if (ios) CouleursGeoMys.rougePastille else HOLO_ROUGE_CLAIR, "Supprimer ${vm.ceTypeDe(s)}") {
                vm.demanderSuppression(s)
            }
        } else if (racine && s.etat == SaisieEnAttente.Etat.SENT && vm.aDescendantsAEnvoyer(s) && vm.envoiAutorise(s)) {
            ActionLigne(Res.drawable.ic_send, "paperplane.fill", CouleursGeoMys.vert, "Envoyer les données restantes") { vm.envoyerGroupe(s) }
        }
    }
    val toucher = Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { vm.toucher(s) }
    if (ios) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(toucher)
                .then(if (cadre != null) Modifier.border(2.dp, cadre, FormeContinue(8.dp)) else Modifier)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ligne.profondeur > 0) Spacer(Modifier.width((20 * ligne.profondeur).dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(ligne.titre, fontSize = 15.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ligne.sousTitre, fontSize = 11.sp, color = couleurSecondaire, maxLines = 1, overflow = TextOverflow.Ellipsis)
                s.messageErreur?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 11.sp, color = CouleursGeoMys.rougePastille, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            actions()
        }
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = if (racine) 4.dp else 1.dp)
                .then(toucher)
                .then(if (cadre != null) Modifier.border(2.dp, cadre, RoundedCornerShape(8.dp)) else Modifier)
                .padding(start = (12 + 20 * ligne.profondeur).dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(ligne.titre, fontSize = 15.sp, modifier = Modifier.weight(1f))
                actions()
            }
            Text(ligne.sousTitre, fontSize = 12.sp, color = couleurSecondaire)
            s.messageErreur?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, color = couleurErreur) }
        }
    }
}

/** Une action de ligne : ImageButton de 48 dp à fond sans bord, icône de 24 dp teintée sous
 *  Android ; symbole SF de 18 pt demi-gras sur une cible de 40 pt sous iOS. */
@Composable
private fun ActionLigne(icone: DrawableResource, symboleIos: String, couleur: Color, description: String, onClick: () -> Unit) {
    if (estIos) {
        BoutonIos(onClick, Modifier.size(40.dp), opaciteAppui = 0.6f) {
            SymboleSf(symboleIos, 18f, listOf(couleur), graisse = GraisseSymbole.DEMI_GRASSE, description = description)
        }
    } else {
        BoutonIcone(icone, couleur, description, onClick, cible = DpSize(48.dp, 48.dp))
    }
}

@Composable
private fun Dialogues(vm: MesVisitesViewModel) {
    val ios = estIos
    val menu = vm.options
    if (ios) {
        DialogueActions(
            visible = menu != null,
            titre = menu?.titre.orEmpty(),
            actions = menu?.let { m ->
                m.options.map { o -> BoutonAlerte(o.libelle, destructif = o is OptionSaisie.Supprimer || o is OptionSaisie.Retirer) { vm.choisir(m.saisie, o) } } +
                    BoutonAlerte("Fermer", annulation = true)
            }.orEmpty(),
            onFermer = vm::fermerOptions,
        )
    } else {
        DialogueChoixLignesAndroid(
            visible = menu != null,
            titre = menu?.titre.orEmpty(),
            lignes = menu?.options?.map { it.libelle }.orEmpty(),
            onChoisir = { i -> menu?.let { m -> vm.choisir(m.saisie, m.options[i]) } },
            onFermer = vm::fermerOptions,
        )
    }
    val suppression = vm.suppression
    DialogueAlerte(
        visible = suppression != null,
        titre = suppression?.titre.orEmpty(),
        message = suppression?.message.orEmpty(),
        boutons = listOf(
            BoutonAlerte("Annuler", annulation = true, action = vm::annulerSuppression),
            BoutonAlerte(MesVisites.BOUTON_TOUT_SUPPRIMER, destructif = true, action = vm::supprimerEnCascade),
        ),
        onFermer = vm::annulerSuppression,
    )
    val nb = vm.confirmationToutEnvoyer
    DialogueAlerte(
        visible = nb != null,
        titre = MesVisites.TITRE_TOUT_ENVOYER,
        message = nb?.let(MesVisites::messageToutEnvoyer).orEmpty(),
        boutons = listOf(
            BoutonAlerte("Annuler", annulation = true, action = vm::annulerToutEnvoyer),
            BoutonAlerte(MesVisites.BOUTON_ENVOYER, action = vm::toutEnvoyer),
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

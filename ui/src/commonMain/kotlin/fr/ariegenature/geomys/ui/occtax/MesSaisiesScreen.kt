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

// Écran « Mes saisies » : fragment_sorties.xml et item_sortie.xml de GeoMys-Android @ cdd5196 sous Android,
// SortiesView de GeoMys-IOS (UI/PlaceholderViews.swift) sous iOS.

package fr.ariegenature.geomys.ui.occtax

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.ariegenature.geomys.envoi.EnvoiGroupe
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.occtax.MesSaisies
import fr.ariegenature.geomys.occtax.OngletMesSaisies
import fr.ariegenature.geomys.occtax.TextesProfils
import fr.ariegenature.geomys.ui.composants.AlerteEchecEcriture
import fr.ariegenature.geomys.ui.composants.BandeauNavigation
import fr.ariegenature.geomys.ui.composants.BoutonAlerte
import fr.ariegenature.geomys.ui.composants.BoutonIcone
import fr.ariegenature.geomys.ui.composants.BoutonTexte
import fr.ariegenature.geomys.ui.composants.BoutonVert
import fr.ariegenature.geomys.ui.composants.DialogueAlerte
import fr.ariegenature.geomys.ui.composants.DialogueElements
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.OngletsListe
import fr.ariegenature.geomys.ui.composants.ProgressionEnvoi
import fr.ariegenature.geomys.ui.composants.Statistique
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.rememberSelecteurGpx
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.ic_delete
import fr.ariegenature.geomys.ui.ressources.ic_edit
import fr.ariegenature.geomys.ui.ressources.ic_list
import fr.ariegenature.geomys.ui.ressources.ic_location_on
import fr.ariegenature.geomys.ui.ressources.ic_map
import fr.ariegenature.geomys.ui.ressources.ic_send
import fr.ariegenature.geomys.ui.ressources.ic_walk
import fr.ariegenature.geomys.ui.ressources.oiseaux
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys
import fr.ariegenature.geomys.util.formaterDecimal
import org.jetbrains.compose.resources.painterResource

/** android.R.color.holo_red_light : icône « Supprimer » des lignes. */
private val HOLO_ROUGE_CLAIR = Color(0xFFFF4444)

/** android.R.color.holo_orange_dark : données exceptionnelles. */
private val ORANGE_ANDROID = Color(0xFFFF8800)

/** ?attr/colorControlHighlight du thème sombre : filet sous la barre d'import. */
private val SURBRILLANCE = Color(0x33FFFFFF)

@Composable
fun MesSaisiesScreen(
    vm: MesSaisiesViewModel,
    onAccueil: () -> Unit,
    onDetail: (String) -> Unit,
    onContinuer: (Sortie) -> Unit,
) {
    val ios = estIos
    val toasts = LocalToasts.current
    LaunchedEffect(vm) { vm.messages.collect { toasts.afficher(it.texte, it.longue) } }
    LifecycleResumeEffect(vm) {
        vm.afficher()
        onPauseOrDispose {}
    }
    val profils by vm.profils.collectAsState()
    val selecteurGpx = rememberSelecteurGpx(TAILLE_MAX_IMPORT_GPX, vm::importer)
    val toutes = vm.toutes
    val sorties = vm.sorties
    val couleurSecondaire = if (ios) CouleursGeoMys.blanc70 else MaterialTheme.colorScheme.onSurfaceVariant

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
                BandeauNavigation("Mes saisies", onAccueil)
            } else {
                BandeauNavigation("Mes saisies", onAccueil, Modifier.padding(start = 12.dp, top = 12.dp))
            }
            // Barre de 56 dp : bouton texte « Importer GPX » aligné à droite.
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = if (ios) 16.dp else 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                BoutonTexte("Importer GPX", CouleursGeoMys.jauneClair, selecteurGpx, tailleIos = 14f)
            }
            // Filet : ?attr/colorControlHighlight sur 1 dp sous Android ; `Divider()` blanc 15 % sous iOS.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (ios) with(LocalDensity.current) { 1f.toDp() } else 1.dp)
                    .background(if (ios) Color.White.copy(alpha = 0.15f) else SURBRILLANCE),
            )
            OngletsListe(
                titres = OngletMesSaisies.entries.map { MesSaisies.titreOnglet(toutes, it) },
                selection = vm.onglet.ordinal,
                onChoisir = { vm.choisirOnglet(OngletMesSaisies.entries[it]) },
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
            if (sorties.isEmpty()) {
                ListeVide(vm.onglet.messageVide, couleurSecondaire, Modifier.weight(1f))
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    contentPadding = if (ios) PaddingValues(top = 4.dp, bottom = 16.dp) else PaddingValues(),
                ) {
                    items(sorties, key = { it.id }) { sortie ->
                        LigneSortie(
                            sortie = sortie,
                            nbExceptionnelles = vm.exceptionnelles(sortie, profils).size,
                            envoiAutorise = vm.envoiAutorise,
                            couleurSecondaire = couleurSecondaire,
                            onDetail = { onDetail(sortie.id) },
                            onListe = { vm.listerEspeces(sortie) },
                            onEnvoyer = { vm.envoyer(sortie) },
                            onContinuer = { if (vm.peutContinuer()) onContinuer(sortie) },
                            onSupprimer = { vm.demanderSuppression(sortie) },
                        )
                    }
                }
            }
        }

        val aSupprimer = vm.suppressionDemandee
        DialogueAlerte(
            visible = aSupprimer != null,
            titre = "Supprimer la saisie ?",
            message = aSupprimer?.let(MesSaisies::messageSuppression).orEmpty(),
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
            message = aEnvoyer?.let { EnvoiGroupe.messageConfirmation(it.size) + vm.complementToutEnvoyer }.orEmpty(),
            boutons = listOf(
                BoutonAlerte(EnvoiGroupe.BOUTON_CONFIRMATION, action = vm::toutEnvoyer),
                BoutonAlerte("Annuler", annulation = true, action = vm::annulerToutEnvoyer),
            ),
            onFermer = vm::annulerToutEnvoyer,
        )
        val exceptionnelles = vm.confirmationExceptionnelles
        DialogueAlerte(
            visible = exceptionnelles != null,
            titre = TextesProfils.TITRE_ENVOI,
            message = exceptionnelles?.let { TextesProfils.messageEnvoi(it.second) }.orEmpty(),
            boutons = listOf(
                BoutonAlerte(TextesProfils.BOUTON_ENVOI, action = vm::envoyerQuandMeme),
                BoutonAlerte("Annuler", annulation = true, action = vm::annulerEnvoiExceptionnelles),
            ),
            onFermer = vm::annulerEnvoiExceptionnelles,
        )
        val especes = vm.especesDe
        DialogueElements(
            visible = especes != null,
            titre = especes?.let { MesSaisies.titreEspeces(MesSaisies.especesAgregees(it).size) }.orEmpty(),
            lignes = especes?.let { MesSaisies.lignesEspeces(it, TextesProfils.especes(vm.exceptionnelles(it, profils))) }.orEmpty(),
            onFermer = vm::fermerEspeces,
        )
        val info = vm.information
        DialogueAlerte(
            visible = info != null,
            titre = info?.titre.orEmpty(),
            message = info?.message.orEmpty(),
            boutons = listOf(BoutonAlerte("OK")),
            onFermer = vm::fermerInformation,
        )
        AlerteEchecEcriture(vm.echecEcritureImport, vm::fermerEchecEcriture, MesSaisiesViewModel.CONSIGNE_IMPORT)
    }
}

/** Liste vide : l'icône de carte estompée et le message de l'onglet. */
@Composable
private fun ListeVide(message: String, couleur: Color, modifier: Modifier = Modifier) {
    val ios = estIos
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(Res.drawable.ic_map),
            contentDescription = null,
            // Sous Android, l'icône garde son gris (#424242) à 30 % ; GeoMys-IOS la teinte en blanc 70 %.
            colorFilter = if (ios) ColorFilter.tint(CouleursGeoMys.blanc70) else null,
            modifier = Modifier.size(64.dp).alpha(0.3f),
        )
        Text(
            message,
            color = couleur,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp).then(if (ios) Modifier.padding(horizontal = 32.dp) else Modifier),
        )
    }
}

/** Une sortie : date, distance, observations et points de trace, dernier échec d'envoi (cadre rouge)
 *  ou état « ✅ Envoyée » ; puis les actions Liste · Envoyer · Continuer · Supprimer. */
@Composable
private fun LigneSortie(
    sortie: Sortie,
    nbExceptionnelles: Int,
    envoiAutorise: Boolean,
    couleurSecondaire: Color,
    onDetail: () -> Unit,
    onListe: () -> Unit,
    onEnvoyer: () -> Unit,
    onContinuer: () -> Unit,
    onSupprimer: () -> Unit,
) {
    val ios = estIos
    val erreur = MesSaisies.ligneErreur(sortie)
    val couleurErreur = if (ios) CouleursGeoMys.rougePastille else MaterialTheme.colorScheme.error
    val cadre = if (erreur != null) {
        val forme = RoundedCornerShape(8.dp)
        if (ios) Modifier.padding(horizontal = 8.dp).border(2.dp, couleurErreur, forme).padding(horizontal = 8.dp)
        else Modifier.border(2.dp, couleurErreur, forme)
    } else {
        if (ios) Modifier.padding(horizontal = 16.dp) else Modifier
    }
    Row(
        Modifier
            .fillMaxWidth()
            // Ni ondulation ni surlignage : la ligne d'item_sortie n'a pas de fond sélectionnable.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDetail)
            .then(cadre)
            .padding(horizontal = if (ios) 0.dp else 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = if (ios) Arrangement.spacedBy(4.dp) else Arrangement.Top) {
            Text(
                MesSaisies.dateLigne(sortie),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (ios) Color.White else Color.Unspecified,
            )
            Row(
                Modifier.then(if (ios) Modifier else Modifier.padding(top = 4.dp)),
                horizontalArrangement = Arrangement.spacedBy(if (ios) 14.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Statistique(Res.drawable.ic_walk, "${formaterDecimal(sortie.distanceTotale, 0)} m", couleurSecondaire)
                Statistique(Res.drawable.oiseaux, "${MesSaisies.nbObservations(sortie)} obs.", couleurSecondaire)
                Statistique(Res.drawable.ic_location_on, "${sortie.pointsParcours.size} pts", couleurSecondaire)
            }
            if (erreur != null) {
                Text(
                    "⚠ $erreur",
                    fontSize = 12.sp,
                    color = couleurErreur,
                    maxLines = if (ios) 1 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (ios) Modifier else Modifier.padding(top = 4.dp),
                )
            }
            if (nbExceptionnelles > 0) {
                // Les profils de taxons du serveur : le détail est dans la liste des espèces.
                Text(
                    TextesProfils.ligneSaisie(nbExceptionnelles),
                    fontSize = 12.sp,
                    color = if (ios) CouleursGeoMys.orange else ORANGE_ANDROID,
                    modifier = if (ios) Modifier else Modifier.padding(top = 4.dp),
                )
            }
            if (sortie.envoyeGeoNature) {
                Text(
                    "✅ Envoyée",
                    fontSize = 12.sp,
                    color = CouleursGeoMys.succes,
                    modifier = if (ios) Modifier else Modifier.padding(top = 4.dp),
                )
            }
        }
        val androidSecondaire = MaterialTheme.colorScheme.onSurfaceVariant
        if (MesSaisies.aDesObservations(sortie)) {
            BoutonIcone(Res.drawable.ic_list, if (ios) CouleursGeoMys.blanc70 else androidSecondaire, "Lister les espèces", onListe)
        }
        if (MesSaisies.peutEnvoyer(sortie, envoiAutorise)) {
            // `@color/colorSecondary` : la ressource reste verte en mode nuit (seul l'attribut du thème passe à l'orange).
            BoutonIcone(Res.drawable.ic_send, CouleursGeoMys.vert, "Envoyer au serveur", onEnvoyer)
        }
        if (MesSaisies.peutEditer(sortie)) {
            BoutonIcone(Res.drawable.ic_edit, CouleursGeoMys.primaire, "Continuer la saisie", onContinuer)
        }
        BoutonIcone(Res.drawable.ic_delete, if (ios) CouleursGeoMys.rougePastille else HOLO_ROUGE_CLAIR, "Supprimer", onSupprimer)
    }
}

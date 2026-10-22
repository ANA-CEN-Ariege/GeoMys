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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/ObservationsFragment.kt,
// res/layout/fragment_observations.xml, res/layout/item_observation.xml) : les relevés de la saisie en cours,
// ouverts par le bouton liste de la carte. Sous iOS, l'allure d'ObservationsView de GeoMys-IOS
// (UI/Saisie/ObservationsView.swift).

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.occtax.ListeObservations
import fr.ariegenature.geomys.occtax.Releve
import fr.ariegenature.geomys.occtax.grouperEnReleves
import fr.ariegenature.geomys.ui.composants.BandeauNavigation
import fr.ariegenature.geomys.ui.composants.BoutonAlerte
import fr.ariegenature.geomys.ui.composants.BoutonIcone
import fr.ariegenature.geomys.ui.composants.BoutonIos
import fr.ariegenature.geomys.ui.composants.BoutonTexte
import fr.ariegenature.geomys.ui.composants.DialogueConfirmation
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.ic_bird_large
import fr.ariegenature.geomys.ui.ressources.ic_delete
import fr.ariegenature.geomys.ui.ressources.ic_edit
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys
import org.jetbrains.compose.resources.painterResource

/** Les relevés de la saisie en cours, leurs suppressions et l'effacement complet. */
class ObservationsViewModel(services: ServicesGeoNature, sessionVm: SessionSaisieViewModel) : ViewModel() {
    private val session = sessionVm.session

    /** Libellé du bandeau : celui du flux de saisie. */
    val typeSaisieLabel: String get() = session.typeSaisieLabel

    val observations = session.observations
    val parcours = services.locationTracker.parcours

    /** Le relevé de plusieurs espèces dont la suppression attend confirmation. */
    var aSupprimer by mutableStateOf<Releve?>(null)
        private set

    /** « Tout effacer » attend confirmation. */
    var confirmerToutEffacer by mutableStateOf(false)
        private set

    /** Supprimer : un relevé solo part aussitôt, un relevé de plusieurs espèces demande
     *  confirmation. */
    fun supprimer(releve: Releve) {
        if (releve.observations.size == 1) effacer(releve) else aSupprimer = releve
    }

    fun effacer(releve: Releve) {
        val rid = releve.releveId
        when {
            !rid.isNullOrEmpty() -> session.supprimerReleve(rid)
            releve.observations.size == 1 -> session.supprimerObservation(releve.premier.id)
            else -> session.supprimerObservations(releve.observations.map { it.id })
        }
    }

    fun fermerSuppression() {
        aSupprimer = null
    }

    fun demanderToutEffacer() {
        confirmerToutEffacer = true
    }

    fun fermerToutEffacer() {
        confirmerToutEffacer = false
    }

    /** Efface les observations et la trace de la saisie en cours. */
    fun toutEffacer() {
        session.reinitialiser()
    }
}

/** `?attr/colorControlHighlight` du thème sombre : le trait sous la barre de titre. */
private val TRAIT_TITRE = Color(0x33FFFFFF)

/** android.R.color.holo_red_light : l'icône « Supprimer ». */
private val ROUGE_SUPPRIMER = Color(0xFFFF4444)

/** La liste des relevés. [onEditer] rouvre un relevé, [onFermer] revient à la carte (après « Tout
 *  effacer » aussi). */
@Composable
fun ObservationsScreen(
    vm: ObservationsViewModel,
    onAccueil: () -> Unit,
    onFermer: () -> Unit,
    onEditer: (Releve) -> Unit,
) {
    val observations by vm.observations.collectAsState()
    val parcours by vm.parcours.collectAsState()
    val releves = observations.grouperEnReleves()
    val titre = ListeObservations.titre(releves.size, observations.size)
    // Il reste quelque chose à effacer : des observations, ou la trace seule.
    val nonVide = observations.isNotEmpty() || parcours.isNotEmpty()
    val ios = estIos
    FondGeoMys {
        Column(Modifier.fillMaxSize().windowInsetsPadding(if (ios) WindowInsets.safeDrawing else WindowInsets.systemBars)) {
            if (ios) {
                BandeauNavigation(vm.typeSaisieLabel, onAccueil, titreIos = "Observations")
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(titre, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                    BoutonTexte("Fermer", CouleursGeoMys.jauneClair, onFermer)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.15f)))
            } else {
                BandeauNavigation(vm.typeSaisieLabel, onAccueil, Modifier.padding(start = 12.dp, top = 12.dp))
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(titre, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    BoutonTexte("Fermer", CouleursGeoMys.jauneClair, onFermer)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(TRAIT_TITRE))
            }
            if (observations.isEmpty()) {
                ListeVide(Modifier.weight(1f).fillMaxWidth())
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(releves, key = { it.releveId ?: it.premier.id }) { releve ->
                        LigneReleve(releve, onEditer = { onEditer(releve) }, onSupprimer = { vm.supprimer(releve) })
                        if (ios) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                    }
                }
            }
            if (ios) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.15f)))
                BoutonIos(vm::demanderToutEffacer, actif = nonVide, opaciteAppui = 0.3f) {
                    Text(
                        "Tout effacer",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (nonVide) CouleursGeoMys.holoRougeFonce else CouleursGeoMys.blanc70.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    )
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    BoutonTexte("Tout effacer", CouleursGeoMys.holoRougeFonce, vm::demanderToutEffacer, Modifier.weight(1f), actif = nonVide)
                }
            }
        }

        val releve = vm.aSupprimer
        DialogueConfirmation(
            visible = releve != null,
            titre = ListeObservations.TITRE_SUPPRESSION,
            message = releve?.let(ListeObservations::messageSuppression).orEmpty(),
            boutons = listOf(
                BoutonAlerte("Annuler", annulation = true),
                BoutonAlerte("Supprimer", destructif = true) { releve?.let(vm::effacer) },
            ),
            onFermer = vm::fermerSuppression,
        )
        DialogueConfirmation(
            visible = vm.confirmerToutEffacer,
            titre = ListeObservations.TITRE_TOUT_EFFACER,
            message = ListeObservations.MESSAGE_TOUT_EFFACER,
            boutons = listOf(
                BoutonAlerte("Annuler", annulation = true),
                BoutonAlerte(ListeObservations.BOUTON_TOUT_EFFACER, destructif = true) {
                    vm.toutEffacer()
                    onFermer()
                },
            ),
            onFermer = vm::fermerToutEffacer,
        )
    }
}

/** Aucune observation : l'icône de 64 dp estompée et « Aucune observation enregistrée ». */
@Composable
private fun ListeVide(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (estIos) {
            Image(
                painterResource(Taxon.OISEAU.icone),
                contentDescription = null,
                colorFilter = ColorFilter.tint(CouleursGeoMys.blanc70.copy(alpha = 0.4f)),
                modifier = Modifier.size(64.dp),
            )
            Text("Aucune observation enregistrée", fontSize = 15.sp, color = CouleursGeoMys.blanc70, modifier = Modifier.padding(top = 12.dp))
        } else {
            val gris = MaterialTheme.colorScheme.onSurfaceVariant
            Image(
                painterResource(Res.drawable.ic_bird_large),
                contentDescription = null,
                colorFilter = ColorFilter.tint(gris),
                modifier = Modifier.size(64.dp).alpha(0.4f),
            )
            Text("Aucune observation enregistrée", color = gris, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/** Une ligne de relevé (item_observation.xml) : icône du groupe, espèce(s) et effectif, notes ou
 *  liste des espèces, date et position ; « Modifier » et « Supprimer ». */
@Composable
private fun LigneReleve(releve: Releve, onEditer: () -> Unit, onSupprimer: () -> Unit) {
    val ios = estIos
    val rep = releve.premier
    val taxon = rep.taxon ?: Taxon.OISEAU
    val secondaire = ListeObservations.secondaire(releve)
    val gris = if (ios) CouleursGeoMys.blanc70 else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(if (ios) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(taxon.icone), contentDescription = null, colorFilter = ColorFilter.tint(taxon.couleur), modifier = Modifier.size(32.dp))
        Column(
            Modifier.weight(1f).padding(start = if (ios) 0.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (ios) 2.dp else 0.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ListeObservations.espece(releve),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (ios) Color.White else Color.Unspecified,
                    modifier = if (ios) Modifier else Modifier.weight(1f),
                )
                ListeObservations.effectif(releve)?.let { effectif ->
                    Text(
                        effectif,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .background(CouleursGeoMys.orange, RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = if (ios) 1.dp else 2.dp),
                    )
                }
            }
            secondaire?.let {
                Text(
                    it,
                    fontSize = 12.sp,
                    color = gris,
                    maxLines = if (ios || releve.observations.size > 1) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (ios) {
                Text(
                    "${ListeObservations.date(releve)} · ${ListeObservations.coordonnees(rep)}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = gris,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ListeObservations.date(releve), fontSize = 11.sp, color = gris)
                    Text("·", fontSize = 11.sp, color = gris, modifier = Modifier.padding(horizontal = 6.dp))
                    Text(ListeObservations.coordonnees(rep), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = gris)
                }
            }
        }
        // `Spacer(minLength: 4)` de GeoMys-IOS, entre les textes et les boutons.
        if (ios) Spacer(Modifier.width(4.dp))
        BoutonIcone(
            Res.drawable.ic_edit,
            CouleursGeoMys.primaire,
            "Modifier",
            onEditer,
            cible = DpSize(36.dp, 36.dp),
            tailleIconeIos = 22.dp,
        )
        BoutonIcone(
            Res.drawable.ic_delete,
            if (ios) CouleursGeoMys.rougePastille else ROUGE_SUPPRIMER,
            "Supprimer",
            onSupprimer,
            cible = DpSize(36.dp, 36.dp),
            tailleIconeIos = 22.dp,
        )
    }
}

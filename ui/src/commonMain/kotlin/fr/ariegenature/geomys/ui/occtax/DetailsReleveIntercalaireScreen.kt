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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/DetailsReleveFragment.kt,
// res/layout/fragment_details_releve.xml) : l'écran intercalé entre le positionnement et la saisie des espèces.
// Sous iOS, l'allure de DetailsReleveIntercalaireView de GeoMys-IOS (UI/Saisie/DetailsReleveIntercalaireView.swift).

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.model.AdditionalFieldsObject
import fr.ariegenature.geomys.occtax.DetailsReleveFormulaire
import fr.ariegenature.geomys.saisie.ChampsAdditionnels
import fr.ariegenature.geomys.saisie.EtatChampsAdditionnels
import fr.ariegenature.geomys.ui.composants.BandeauNavigation
import fr.ariegenature.geomys.ui.composants.BoutonContour
import fr.ariegenature.geomys.ui.composants.BoutonPlein
import fr.ariegenature.geomys.ui.composants.BoutonTexte
import fr.ariegenature.geomys.ui.composants.BoutonVert
import fr.ariegenature.geomys.ui.composants.DialogueGardeSortie
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.rememberGardeSortie
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys

/** Les champs additionnels de relevé à renseigner avant les espèces, au départ les détails communs
 *  de la saisie. */
class DetailsReleveIntercalaireViewModel(services: ServicesGeoNature, sessionVm: SessionSaisieViewModel) : ViewModel() {
    private val session = sessionVm.session

    /** Libellé du bandeau : celui du flux de saisie. */
    val typeSaisieLabel: String get() = session.typeSaisieLabel

    // Les champs du jeu de données du relevé à venir, comme le web.
    val champs = EtatChampsAdditionnels(
        ChampsAdditionnels.defsPour(
            services.config.additionalFieldsOcctaxJson,
            AdditionalFieldsObject.RELEVE,
            session.datasetNouveauReleve(services.config.idDatasetParDefaut),
        ),
        session.detailsCommuns?.additionnels.orEmpty(),
        DetailsReleveFormulaire.observateurs(services.config),
        services.taxRef,
    )

    private val initiales = champs.collecter()

    /** Les champs ont changé depuis l'ouverture : la garde de sortie demande avant de les perdre. */
    fun modifie(): Boolean = champs.collecter() != initiales

    /** Les champs obligatoires laissés vides, sous les champs. */
    var erreur by mutableStateOf<String?>(null)
        private set

    /** « Continuer » : les valeurs saisies, ou null si un contrôle du web les refuse. */
    fun continuer(): Map<String, String>? {
        champs.refus()?.let {
            erreur = it
            return null
        }
        return champs.collecter()
    }
}

/** android.R.color.holo_red_dark : le message des champs manquants. */
private val ROUGE_ERREUR = Color(0xFFCC0000)

/** `.red` de SwiftUI en mode sombre. */
private val ROUGE_IOS = Color(0xFFFF453A)

/** `.ultraThinMaterial` de SwiftUI : rendu par sa teinte, sans le flou de ce qui est dessous. */
private val MATERIAU_ULTRA_FIN = Color(0x33808080)

/** Titre de l'écran (fragment_details_releve.xml). */
private const val TITRE = "Détails du relevé"

/** L'avis de fragment_details_releve.xml, les « * » devenus la barre rouge du web. */
private const val AVIS_OBLIGATOIRES = "Les champs marqués d'une barre rouge sont obligatoires."

/** L'écran « Détails du relevé » intercalé : [onContinuer] reçoit les valeurs une fois les champs
 *  obligatoires remplis ; [onAnnuler] revient à la carte, comme le retour et la maison, qui
 *  demandent avant de perdre des champs remplis. */
@Composable
fun DetailsReleveIntercalaireScreen(
    vm: DetailsReleveIntercalaireViewModel,
    onAccueil: () -> Unit,
    onAnnuler: () -> Unit,
    onContinuer: (Map<String, String>) -> Unit,
) {
    val continuer: () -> Unit = { vm.continuer()?.let(onContinuer) }
    val garde = rememberGardeSortie(vm::modifie, onAnnuler)
    val accueil = { garde.demander(onAccueil) }
    FondGeoMys {
        if (estIos) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                BandeauNavigation(vm.typeSaisieLabel, accueil, titreIos = TITRE)
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(TITRE, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text(AVIS_OBLIGATOIRES, fontSize = 12.sp, color = CouleursGeoMys.blanc70)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(MATERIAU_ULTRA_FIN, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) { ChampsAdditionnelsVue(vm.champs) }
                    vm.erreur?.let { Text(it, fontSize = 12.sp, color = ROUGE_IOS) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BoutonContour("Annuler", onAnnuler, Modifier.weight(1f))
                        BoutonVert("Continuer", continuer, Modifier.weight(1f), symboleIos = "arrow.right")
                    }
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
                    .padding(16.dp),
            ) {
                BandeauNavigation(vm.typeSaisieLabel, accueil, Modifier.padding(bottom = 12.dp))
                Text(TITRE, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    AVIS_OBLIGATOIRES,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    ChampsAdditionnelsVue(vm.champs)
                }
                vm.erreur?.let { Text(it, fontSize = 13.sp, color = ROUGE_ERREUR, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    BoutonTexte("Annuler", CouleursGeoMys.jauneClair, onAnnuler, Modifier.padding(end = 8.dp))
                    BoutonPlein("Continuer", continuer)
                }
            }
        }
        DialogueGardeSortie(garde, "Détails non enregistrés")
    }
}

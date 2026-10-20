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

// La fiche d'un objet de suivi : fragment_fiche_objet.xml et FicheObjetFragment de GeoMys-Android @ cdd5196
// sous Android, FicheObjetView de GeoMys-IOS (UI/MonitoringViews.swift) sous iOS : cartes translucides des
// propriétés et des enfants, cadre rouge des saisies locales.

package fr.ariegenature.geomys.ui.suivis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.ariegenature.geomys.monitoring.ElementFiche
import fr.ariegenature.geomys.monitoring.FilSegment
import fr.ariegenature.geomys.monitoring.NouvelleSaisie
import fr.ariegenature.geomys.ui.composants.IndicateurActivite
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FormeContinue

/** Fond des cartes de la fiche sous iOS (`Color.white.opacity(0.06)`). */
private val FOND_CARTE_IOS = Color.White.copy(alpha = 0.06f)

@Composable
fun FicheObjetScreen(
    vm: FicheObjetViewModel,
    onNiveau: (List<FilSegment>) -> Unit,
    onFiche: (ElementFiche.Serveur) -> Unit,
    onCarte: (ElementFiche.Serveur) -> Unit,
    onNouvelle: (NouvelleSaisie) -> Unit,
) {
    val ios = estIos
    LifecycleResumeEffect(vm) {
        vm.aLaReprise()
        onPauseOrDispose {}
    }
    PageSuivi(vm.fil, dernierCliquable = false, onNiveau = onNiveau) {
        // Type au-dessus du nom (tv_type, tv_titre).
        Text(
            vm.type.uppercase(),
            fontSize = 12.sp,
            color = if (ios) CouleursGeoMys.blanc70 else CouleursGeoMys.grisFonce,
        )
        Text(vm.titre, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = if (ios) Modifier else Modifier.padding(top = 2.dp))
        if (vm.chargement) {
            if (ios) {
                Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                    IndicateurActivite(CouleursGeoMys.jauneClair, grandIos = true)
                }
            } else {
                IndicateurActivite(
                    CouleursGeoMys.orange,
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp),
                    tailleAndroid = 32f,
                )
            }
        }
        vm.erreur?.let { ErreurSuivi(it) }
        if (vm.proprietes.isNotEmpty()) Proprietes(vm.proprietes)
        vm.note?.let {
            Text(
                it,
                fontSize = if (ios) 13.sp else 12.sp,
                color = if (ios) CouleursGeoMys.blanc70 else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (ios) Modifier else Modifier.padding(top = 8.dp),
            )
        }
        if (vm.elements.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = if (ios) 4.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (ios) 6.dp else 0.dp),
            ) {
                vm.elements.forEach { element ->
                    when (element) {
                        is ElementFiche.Entete -> EnteteSuivi(element.texte) {
                            element.nouvelle?.let { n -> BoutonSuivi(ActionSuivi.NOUVELLE, n.libelle, compactIos = true) { onNouvelle(n) } }
                        }
                        is ElementFiche.Locale -> LigneLocale(element, onNouvelle)
                        is ElementFiche.Serveur -> LigneServeur(element, onFiche, onCarte, onNouvelle)
                    }
                }
            }
        }
    }
}

/** « Libellé » au-dessus de « valeur ». Android : lignes à marges verticales de 6 dp (12 et 15 sp).
 *  iOS : la carte translucide de GeoMys-IOS (11 et 14 pt). */
@Composable
private fun Proprietes(proprietes: List<Pair<String, String>>) {
    if (estIos) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(FOND_CARTE_IOS, FormeContinue(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            proprietes.forEach { (libelle, valeur) ->
                Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(libelle, fontSize = 11.sp, color = CouleursGeoMys.blanc70)
                    Text(valeur, fontSize = 14.sp)
                }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            proprietes.forEach { (libelle, valeur) ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(libelle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(valeur, fontSize = 15.sp)
                }
            }
        }
    }
}

/** Saisie locale pas encore envoyée, encadrée de rouge (`cadreColore(couleurErreur)` : trait de
 *  2 dp, coins de 8 dp ; sous iOS, rouge pastille aux coins continus). */
@Composable
private fun LigneLocale(element: ElementFiche.Locale, onNouvelle: (NouvelleSaisie) -> Unit) {
    if (estIos) {
        Row(
            Modifier
                .fillMaxWidth()
                .border(2.dp, CouleursGeoMys.rougePastille, FormeContinue(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(element.titre, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(element.sousTitre, fontSize = 11.sp, color = CouleursGeoMys.orange)
            }
            element.nouvelle?.let { n -> BoutonSuivi(ActionSuivi.NOUVELLE, n.libelle, compactIos = true, tailleIos = 15f) { onNouvelle(n) } }
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(8.dp)) {
                Text(element.titre, fontSize = 15.sp)
                // colorSecondary du thème sombre : l'orange de l'accent.
                Text(element.sousTitre, fontSize = 12.sp, color = CouleursGeoMys.orange)
            }
            element.nouvelle?.let { n -> BoutonSuivi(ActionSuivi.NOUVELLE, n.libelle) { onNouvelle(n) } }
        }
    }
}

/** Enfant du serveur : comme une ligne du détail du protocole ; sous iOS, dans une carte
 *  translucide aux coins de 10 pt. */
@Composable
private fun LigneServeur(
    element: ElementFiche.Serveur,
    onFiche: (ElementFiche.Serveur) -> Unit,
    onCarte: (ElementFiche.Serveur) -> Unit,
    onNouvelle: (NouvelleSaisie) -> Unit,
) {
    val boutons: @Composable () -> Unit = {
        BoutonSuivi(ActionSuivi.FICHE, "Détails") { if (element.id > 0) onFiche(element) }
        if (element.carte) BoutonSuivi(ActionSuivi.CARTE, "Voir sur carte") { if (element.id > 0) onCarte(element) }
        element.nouvelle?.let { n -> BoutonSuivi(ActionSuivi.NOUVELLE, n.libelle) { if (element.id > 0) onNouvelle(n) } }
    }
    if (estIos) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(FOND_CARTE_IOS, FormeContinue(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(element.nom, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (element.sousTitre.isNotEmpty()) {
                    Text(element.sousTitre, fontSize = 12.sp, color = CouleursGeoMys.blanc70, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { boutons() }
        }
    } else {
        LigneObjet(element.nom, element.sousTitre, boutons)
    }
}

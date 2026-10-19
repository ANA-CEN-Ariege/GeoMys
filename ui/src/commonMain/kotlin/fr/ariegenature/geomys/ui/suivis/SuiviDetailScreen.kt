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

// Le détail d'un protocole : fragment_suivi_detail.xml et SuiviDetailFragment (afficherListeSites) de
// GeoMys-Android @ cdd5196 sous Android, SuiviDetailView de GeoMys-IOS (UI/MonitoringViews.swift) sous iOS.

package fr.ariegenature.geomys.ui.suivis

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.monitoring.FilSegment
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys

@Composable
fun SuiviDetailScreen(
    vm: SuiviDetailViewModel,
    onNiveau: (List<FilSegment>) -> Unit,
    onFiche: (LigneObjetSuivi) -> Unit,
    onCarte: (LigneObjetSuivi) -> Unit,
    onNouvelle: (LigneObjetSuivi) -> Unit,
) {
    val ios = estIos
    PageSuivi(vm.fil, dernierCliquable = false, onNiveau = onNiveau) {
        Text(vm.titre, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        vm.description?.let {
            if (ios) Text(it, fontSize = 14.sp, color = CouleursGeoMys.blanc70)
            else Text(it, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
        }
        vm.resume?.let {
            Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = if (ios) Modifier else Modifier.padding(top = 12.dp))
        }
        vm.erreur?.let { ErreurSuivi(it) }
        if (vm.sections.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = if (ios) 0.dp else 16.dp)) {
                vm.sections.forEach { section ->
                    section.entete?.let { EnteteSuivi(it) }
                    section.lignes.forEach { ligne ->
                        LigneObjet(ligne.nom, ligne.sousTitre) {
                            BoutonSuivi(ActionSuivi.FICHE, "Détails") { if (ligne.id > 0) onFiche(ligne) }
                            if (ligne.carte) BoutonSuivi(ActionSuivi.CARTE, "Voir sur carte") { if (ligne.id > 0) onCarte(ligne) }
                            if (ligne.saisieEnfant != null) {
                                BoutonSuivi(ActionSuivi.NOUVELLE, ligne.libelleNouveau.orEmpty()) { if (ligne.id > 0) onNouvelle(ligne) }
                            }
                        }
                    }
                }
            }
        }
    }
}

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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/TaxonsListeFragment.kt,
// res/layout/fragment_taxons_liste.xml et item_taxon_list.xml) ; allure iOS de GeoMys-IOS
// (GeoMys-IOS/UI/TaxonsListeView.swift).

package fr.ariegenature.geomys.ui.parametres

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.store.ListeTaxons
import fr.ariegenature.geomys.store.TaxRefEntry
import fr.ariegenature.geomys.ui.composants.BalayageRetourIosDesactive
import fr.ariegenature.geomys.ui.composants.BoutonTexte
import fr.ariegenature.geomys.ui.composants.ChampTexte
import fr.ariegenature.geomys.ui.composants.IndicateurActivite
import fr.ariegenature.geomys.ui.composants.SeparateurIos
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys

/** Les taxons d'une liste du cache, avec une recherche mot à mot (nom français, nom scientifique,
 *  cd_nom). Ouvert depuis Paramètres › Détails › une liste. */
@Composable
fun TaxonsListeScreen(services: ServicesGeoNature, idListe: Int, nomListe: String, onRetour: () -> Unit) {
    var liste by remember { mutableStateOf<ListeTaxons?>(null) }
    var recherche by remember { mutableStateOf("") }
    LaunchedEffect(idListe) { liste = ListeTaxons.construire(services.taxRef, idListe, nomListe) }
    val construite = liste
    val filtres = remember(construite, recherche) { construite?.filtrer(recherche).orEmpty() }
    val titre = when {
        construite == null -> "Chargement…"
        recherche.isBlank() -> "${construite.titre} (${construite.entrees.size})"
        else -> "${construite.titre} (${filtres.size} / ${construite.entrees.size})"
    }
    // La liste revient en haut à chaque filtrage.
    val defilement = rememberLazyListState()
    LaunchedEffect(recherche) { defilement.scrollToItem(0) }
    val ios = estIos
    BalayageRetourIosDesactive()
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(titre, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                    BoutonTexte("Retour", CouleursGeoMys.jauneClair, onRetour)
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(titre, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(8.dp))
                    BoutonTexte("Retour", CouleursGeoMys.jauneClair, onRetour)
                }
            }
            ChampTexte(
                recherche,
                { recherche = it },
                "Rechercher dans la liste",
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                effacable = true,
            )
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (construite == null) {
                    IndicateurActivite(if (ios) Color.White else CouleursGeoMys.orange)
                } else {
                    LazyColumn(Modifier.fillMaxSize(), state = defilement, contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(filtres, key = { it.cdNom }) { entree ->
                            LigneTaxon(entree)
                            if (ios) SeparateurIos()
                        }
                    }
                }
            }
        }
    }
}

/** `item_taxon_list` : nom français 16 sp gras, nom scientifique 14 sp italique, cd_nom 12 sp,
 *  dans 12 dp de marge ; couleurs primaire et secondaire du thème sous Android, blanc et blanc
 *  70 % sous iOS. */
@Composable
private fun LigneTaxon(entree: TaxRefEntry) {
    val ios = estIos
    val principale = if (ios) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaire = if (ios) CouleursGeoMys.blanc70 else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = if (ios) Arrangement.spacedBy(2.dp) else Arrangement.Top,
    ) {
        Text(entree.nomFrOriginal ?: "—", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = principale)
        Text(entree.sciNom, fontSize = 14.sp, fontStyle = FontStyle.Italic, color = secondaire)
        Text("cd_nom: ${entree.cdNom}", fontSize = 12.sp, color = secondaire)
    }
}

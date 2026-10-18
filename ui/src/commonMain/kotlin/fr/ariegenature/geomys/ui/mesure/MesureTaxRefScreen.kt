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

package fr.ariegenature.geomys.ui.mesure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.ariegenature.geomys.outils.MesureMemoireTaxRef
import fr.ariegenature.geomys.outils.MesureMemoireTaxRef.Lecture
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/** Écran provisoire du lot 0 : lance [MesureMemoireTaxRef] sur l'appareil. On prépare d'abord
 *  les fichiers, puis chaque mesure se fait dans un lancement neuf de l'appli. Les résultats
 *  s'affichent et partent aussi dans le journal (logcat, console de l'iPhone), sur une ligne
 *  qui commence par `MESURE_TAXREF`.
 *
 *  [action] permet les essais automatisés : `preparer` (avec [taxons]) ou `mesurer` (avec
 *  [lecture], `flux` ou `chaine`). */
@Composable
fun MesureTaxRefScreen(onRetour: () -> Unit, action: String?, taxons: Int?, lecture: String?) {
    val portee = rememberCoroutineScope()
    var nombreTaxons by remember { mutableStateOf(taxons ?: 100_000) }
    var prepares by remember { mutableStateOf(MesureMemoireTaxRef.taxonsPrepares()) }
    var enCours by remember { mutableStateOf<String?>(null) }
    val journal = remember { mutableStateListOf<String>() }

    fun lancer(libelle: String, travail: suspend () -> String) {
        if (enCours != null) return
        enCours = libelle
        portee.launch {
            val ligne = try {
                travail()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                "MESURE_TAXREF erreur=${e::class.simpleName} ${e.message.orEmpty()}"
            } finally {
                enCours = null
            }
            println(ligne)
            journal.add(0, ligne)
            prepares = MesureMemoireTaxRef.taxonsPrepares()
        }
    }

    fun preparer() = lancer("Écriture des fichiers de $nombreTaxons taxons…") {
        MesureMemoireTaxRef.preparer(nombreTaxons).enLigne()
    }

    fun mesurer(lecture: Lecture) = lancer("Relecture (${lecture.id}) et construction des index…") {
        MesureMemoireTaxRef.mesurer(lecture).enLigne()
    }

    LaunchedEffect(Unit) {
        when (action) {
            "preparer" -> preparer()
            "mesurer" -> mesurer(Lecture.entries.firstOrNull { it.id == lecture } ?: Lecture.FLUX)
        }
    }

    FondGeoMys {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRetour) { Text("← Accueil", color = CouleursGeoMys.jauneClair) }
                Text("Mesure mémoire TaxRef", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Panneau {
                Text("1. Préparer les fichiers (synchronisation simulée)", color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(50_000, 100_000, 200_000).forEach { n ->
                        FilterChip(
                            selected = n == nombreTaxons,
                            onClick = { nombreTaxons = n },
                            label = { Text("${n / 1000} 000") },
                        )
                    }
                }
                OutlinedButton(onClick = { preparer() }, enabled = enCours == null) {
                    Text("Écrire les fichiers", color = CouleursGeoMys.jauneClair)
                }
                Text(
                    prepares?.let { "En place : $it taxons." } ?: "Aucun fichier en place.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Panneau {
                Text("2. Mesurer, une fois par lancement de l'appli", color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { mesurer(Lecture.FLUX) }, enabled = enCours == null && prepares != null) {
                        Text("En flux", color = CouleursGeoMys.jauneClair)
                    }
                    OutlinedButton(onClick = { mesurer(Lecture.CHAINE) }, enabled = enCours == null && prepares != null) {
                        Text("En chaînes", color = CouleursGeoMys.jauneClair)
                    }
                }
            }
            enCours?.let { Text(it, color = CouleursGeoMys.jauneClair) }
            journal.forEach { ligne ->
                Panneau { Text(ligne.removePrefix("MESURE_TAXREF ").replace(' ', '\n'), color = Color.White, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun Panneau(contenu: @Composable () -> Unit) {
    Surface(color = Color.Black.copy(alpha = 0.55f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { contenu() }
    }
}

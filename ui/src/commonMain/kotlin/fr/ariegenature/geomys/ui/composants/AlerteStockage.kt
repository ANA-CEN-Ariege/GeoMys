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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/AlerteStockage.kt) : le
// dialogue devient un composable, mêmes textes.

package fr.ariegenature.geomys.ui.composants

import androidx.compose.runtime.Composable

/** Conduite à tenir par défaut après un échec d'écriture. */
const val CONSIGNE_STOCKAGE = "Libérez de l'espace (photos, cache de cartes) puis réessayez."

/** Alerte BLOQUANTE d'échec d'écriture disque d'un store (stockage plein, I/O…). Tout site
 *  d'écriture visible de l'utilisateur doit vérifier le retour du store et afficher cette alerte
 *  sur échec, SANS quitter l'écran (les données ne vivent plus qu'en mémoire tant que l'écriture
 *  n'a pas abouti). [consigne] complète le constat par la conduite à tenir propre à l'écran. */
@Composable
fun AlerteEchecEcriture(visible: Boolean, onFermer: () -> Unit, consigne: String = CONSIGNE_STOCKAGE) {
    DialogueAlerte(
        visible,
        "Enregistrement impossible",
        "L'écriture sur l'appareil a échoué (stockage plein ?). La saisie n'a PAS été enregistrée.\n\n$consigne",
        listOf(BoutonAlerte("OK")),
        onFermer,
    )
}

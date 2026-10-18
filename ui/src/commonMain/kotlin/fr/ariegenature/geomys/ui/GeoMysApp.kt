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

package fr.ariegenature.geomys.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.ariegenature.geomys.InfosApp
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.ui.accueil.AccueilScreen
import fr.ariegenature.geomys.ui.carte.prototype.PrototypeCarteScreen
import fr.ariegenature.geomys.ui.connexion.ConnexionScreen
import fr.ariegenature.geomys.ui.mesure.MesureTaxRefScreen
import fr.ariegenature.geomys.ui.theme.GeoMysTheme

/** Racine de l'interface, identique sur Android (MainActivity) et iOS (MainViewController).
 *
 *  [lancement] : paramètres de lancement pour les essais automatisés (le simulateur iOS ne sait
 *  pas simuler de toucher) : `ecran=prototype-carte`, `fond=<id>`, `zoom=<n>`, `action=hors-ligne` ;
 *  `ecran=mesure-taxref`, `action=preparer`, `taxons=<n>` ou `action=mesurer`, `lecture=flux|chaine` ;
 *  `ecran=connexion`, `url=<url>`, `login=<identifiant>`, `mdp=<mot de passe>`, `action=tester`.
 *  Passés en extras d'intent sur Android, en arguments `-cle valeur` sur iOS. */
@Composable
fun GeoMysApp(infos: InfosApp, services: ServicesGeoNature, lancement: Map<String, String> = emptyMap()) {
    GeoMysTheme {
        // Navigation provisoire, en attendant navigation-compose (lot 5).
        var ecran by remember {
            mutableStateOf(Ecran.entries.firstOrNull { it.id == lancement["ecran"] } ?: Ecran.ACCUEIL)
        }
        when (ecran) {
            Ecran.ACCUEIL -> AccueilScreen(
                infos,
                onPrototypeCarte = { ecran = Ecran.PROTOTYPE_CARTE },
                onMesureTaxRef = { ecran = Ecran.MESURE_TAXREF },
                onConnexion = { ecran = Ecran.CONNEXION },
            )
            Ecran.PROTOTYPE_CARTE -> PrototypeCarteScreen(
                onRetour = { ecran = Ecran.ACCUEIL },
                fondInitial = lancement["fond"],
                zoomInitial = lancement["zoom"]?.toDoubleOrNull(),
                telechargerAuDemarrage = lancement["action"] == "hors-ligne",
            )
            Ecran.MESURE_TAXREF -> MesureTaxRefScreen(
                onRetour = { ecran = Ecran.ACCUEIL },
                action = lancement["action"],
                taxons = lancement["taxons"]?.toIntOrNull(),
                lecture = lancement["lecture"],
            )
            Ecran.CONNEXION -> ConnexionScreen(services, onRetour = { ecran = Ecran.ACCUEIL }, lancement = lancement)
        }
    }
}

private enum class Ecran(val id: String) {
    ACCUEIL("accueil"),
    PROTOTYPE_CARTE("prototype-carte"),
    MESURE_TAXREF("mesure-taxref"),
    CONNEXION("connexion"),
}

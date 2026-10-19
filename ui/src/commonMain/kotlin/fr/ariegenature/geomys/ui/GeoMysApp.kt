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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.ariegenature.geomys.InfosApp
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.outils.ajouterReferentielsEssai
import fr.ariegenature.geomys.outils.ajouterSortiesEssai
import fr.ariegenature.geomys.outils.ajouterTaxRefEssai
import fr.ariegenature.geomys.outils.resumeDonnees
import fr.ariegenature.geomys.outils.synchroniserEnJournalisant
import fr.ariegenature.geomys.ui.carte.prototype.PrototypeCarteScreen
import fr.ariegenature.geomys.ui.composants.ZoneToasts
import fr.ariegenature.geomys.ui.mesure.MesureTaxRefScreen
import fr.ariegenature.geomys.ui.navigation.NavigationGeoMys
import fr.ariegenature.geomys.ui.theme.GeoMysTheme

/** Racine de l'interface, identique sur Android (MainActivity) et iOS (MainViewController).
 *
 *  [lancement] : paramètres de lancement pour les essais (le simulateur iOS ne sait pas simuler de
 *  toucher), passés en extras d'intent sur Android, en arguments `-cle valeur` sur iOS :
 *  - `ecran=accueil` ou `ecran=parametres` : écran d'ouverture, sans ou avec Paramètres ;
 *  - `ecran=prototype-carte`, `fond=<id>`, `zoom=<n>`, `action=hors-ligne` (lot 0) ;
 *  - `ecran=mesure-taxref`, `action=preparer`, `taxons=<n>` ou `action=mesurer`, `lecture=flux|chaine` (lot 0) ;
 *  - `action=synchroniser` ou `action=etat-donnees` : essai du lot 2, sans écran (DiagnosticSynchro) ;
 *  - `ecran=mes-saisies`, `action=sorties-essai` : essais du lot 6 (sorties fictives, SortiesEssai) ;
 *  - `action=taxref-essai` : essai du lot 6, un petit référentiel à la place du cache (TaxRefEssai) ;
 *  - `action=referentiels-essai` : essai du lot 6, jeux de données, observateurs et champs de relevé
 *    fictifs (ReferentielsEssai). */
@Composable
fun GeoMysApp(infos: InfosApp, services: ServicesGeoNature, lancement: Map<String, String> = emptyMap()) {
    LaunchedEffect(Unit) {
        // Préchauffe de TaxRef et balayage des médias orphelins, une fois par processus.
        services.lancerTravauxDeDemarrage()
        // Essai du lot 2 : synchro complète, ou état des données hors ligne, écrits dans le journal.
        when (lancement["action"]) {
            "synchroniser" -> services.synchroniserEnJournalisant(::println)
            "etat-donnees" -> println(services.resumeDonnees())
            "sorties-essai" -> services.ajouterSortiesEssai()
            "taxref-essai" -> services.ajouterTaxRefEssai()
            "referentiels-essai" -> services.ajouterReferentielsEssai()
        }
    }
    GeoMysTheme {
        ZoneToasts {
            // Les prototypes du lot 0 ne s'ouvrent que par paramètre de lancement.
            var prototype by remember { mutableStateOf(lancement["ecran"]?.takeIf { it in PROTOTYPES }) }
            when (prototype) {
                "prototype-carte" -> PrototypeCarteScreen(
                    onRetour = { prototype = null },
                    fondInitial = lancement["fond"],
                    zoomInitial = lancement["zoom"]?.toDoubleOrNull(),
                    telechargerAuDemarrage = lancement["action"] == "hors-ligne",
                )
                "mesure-taxref" -> MesureTaxRefScreen(
                    onRetour = { prototype = null },
                    action = lancement["action"],
                    taxons = lancement["taxons"]?.toIntOrNull(),
                    lecture = lancement["lecture"],
                )
                else -> NavigationGeoMys(infos, services, ecranInitial = lancement["ecran"])
            }
        }
    }
}

private val PROTOTYPES = setOf("prototype-carte", "mesure-taxref")

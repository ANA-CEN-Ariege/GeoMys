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

import SwiftUI
import Speech
import GeoMysUi

/// Les données d'entraînement du modèle de langue de la dictée (les noms d'espèces proposables).
/// `SFCustomLanguageModelData` n'existe qu'en Swift : le reste est dans ModeleLangueDictee.kt.
final class ExportModeleLangueIos: ExportModeleLangue {
    func exporter(phrases: [String], chemin: String, fin: ExportFini) {
        Task.detached(priority: .utility) {
            let donnees = SFCustomLanguageModelData(
                locale: Locale(identifier: "fr_FR"),
                identifier: "fr.ariegenature.geomys.especes",
                version: "1"
            )
            // Chaque nom pèse autant : aucune espèce n'est plus attendue qu'une autre.
            for phrase in phrases {
                donnees.insert(phraseCount: SFCustomLanguageModelData.PhraseCount(phrase: phrase, count: 100))
            }
            do {
                try await donnees.export(to: URL(fileURLWithPath: chemin))
                fin.fini(reussi: true)
            } catch {
                fin.fini(reussi: false)
            }
        }
    }
}

@main
struct iOSApp: App {
    init() {
        ModeleLangueDictee.shared.exportateur = ExportModeleLangueIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

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

// Essai d'affichage du lot 6, lancé par le paramètre caché `action=referentiels-essai` de GeoMysApp, sur
// l'émulateur et le simulateur seulement : jeux de données, observateurs, heures et date de fin, deux champs
// additionnels de relevé obligatoires, pour voir le dialogue « Détails du relevé » et l'écran intercalé sans
// serveur GeoNature.

package fr.ariegenature.geomys.outils

import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.model.AdditionalFieldDef
import fr.ariegenature.geomys.model.AdditionalFieldsObject
import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.model.GeoNatureObservateur
import fr.ariegenature.geomys.model.WidgetType
import fr.ariegenature.geomys.store.io.listeVersJson

/** Remplace les caches de jeux de données, d'observateurs et de champs additionnels par quelques
 *  valeurs fictives, et active l'heure et la date de fin des relevés. */
fun ServicesGeoNature.ajouterReferentielsEssai() {
    config.datasetsCacheJson = listeVersJson(
        GeoNatureDataset.serializer(),
        listOf(
            GeoNatureDataset(1, "Données opportunistes", moduleCodes = listOf("OCCTAX")),
            GeoNatureDataset(2, "Inventaire des mares de l'Ariège", moduleCodes = listOf("OCCTAX")),
            GeoNatureDataset(3, "Suivi des chiroptères en période estivale dans les gîtes", moduleCodes = listOf("OCCTAX")),
        ),
    )
    config.datasetsCreablesOcctax = emptySet()
    config.observateursCacheJson = listeVersJson(
        GeoNatureObservateur.serializer(),
        listOf(
            GeoNatureObservateur(11, "DURAND Émile"),
            GeoNatureObservateur(12, "ROUX Émilie"),
            GeoNatureObservateur(13, "MARTIN Alice"),
        ),
    )
    config.dateAvecHeures = true
    config.dateAvecFin = true
    config.additionalFieldsOcctaxJson = listeVersJson(
        AdditionalFieldDef.serializer(),
        listOf(
            AdditionalFieldDef(9001, "meteo", "Météo", WidgetType.TEXT, required = true, defaultValue = "Ensoleillé", objectsCode = listOf(AdditionalFieldsObject.RELEVE)),
            // Obligatoire et sans défaut : l'écran « Détails du relevé » s'intercale avant le relevé.
            AdditionalFieldDef(9002, "vent", "Vent", WidgetType.SELECT, fieldValues = listOf("Nul", "Faible", "Fort"), required = true, objectsCode = listOf(AdditionalFieldsObject.RELEVE)),
        ),
    )
}

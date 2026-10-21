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
// serveur GeoNature ; depuis le 20 octobre, des champs d'occurrence et de dénombrement des widgets du
// formulaire dynamique du web.

package fr.ariegenature.geomys.outils

import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.model.AdditionalFieldDef
import fr.ariegenature.geomys.model.AdditionalFieldsObject
import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.model.GeoNatureObservateur
import fr.ariegenature.geomys.model.WidgetType
import fr.ariegenature.geomys.store.NomValeur
import fr.ariegenature.geomys.store.TaxrefRestriction
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
    // Créables, comme le demande le formulaire du web pour les proposer.
    config.datasetsCreablesOcctax = setOf(1, 2, 3)
    config.observateursCacheJson = listeVersJson(
        GeoNatureObservateur.serializer(),
        listOf(
            GeoNatureObservateur(11, "DURAND Émile"),
            GeoNatureObservateur(12, "ROUX Émilie"),
            GeoNatureObservateur(13, "MARTIN Alice"),
        ),
    )
    // Un utilisateur connecté, sans connexion réelle : l'observateur par défaut des Paramètres.
    if (config.idRoleUtilisateur <= 0) {
        config.idRoleUtilisateur = 13
        config.nomUtilisateur = "MARTIN Alice"
    }
    // Quelques valeurs de nomenclature, toutes taxonomies : les listes du web (libellé `label_fr`,
    // triées par `label_default`, sans entrée vide).
    val tous = listOf(TaxrefRestriction("all", "all"))
    // (cd_nomenclature, libellé) à la suite.
    fun valeurs(vararg v: String) = v.toList().chunked(2).map { (cd, libelle) -> NomValeur(0, libelle, tous, cd, labelFr = libelle) }
    var id = 0
    nomenclatures.setAll(
        mapOf(
            "METH_OBS" to valeurs("0", "Vu", "1", "Entendu", "21", "Inconnu"),
            "ETA_BIO" to valeurs("2", "Observé vivant", "3", "Trouvé mort", "1", "Non renseigné"),
            "STATUT_OBS" to valeurs("Pr", "Présent", "No", "Non observé"),
            "STATUT_SOURCE" to valeurs("Te", "Terrain", "Li", "Littérature"),
            "DEE_FLOU" to valeurs("NON", "Non", "OUI", "Oui"),
            "PREUVE_EXIST" to valeurs("1", "Oui", "2", "Non", "0", "NSP"),
            "OBJ_DENBR" to valeurs("IND", "Individu", "CPL", "Couple", "NSP", "Inconnu"),
            "TYP_DENBR" to valeurs("Co", "Compté", "Es", "Estimé"),
            "STADE_VIE" to valeurs("2", "Adulte", "3", "Juvénile", "1", "Indéterminé"),
            "SEXE" to valeurs("2", "Femelle", "3", "Mâle", "6", "Non renseigné"),
            "TYP_GRP" to valeurs("OBS", "Observation", "PASS", "Passage"),
            "NAT_OBJ_GEO" to valeurs("St", "Stationnel", "In", "Inventoriel"),
            "TECHNIQUE_OBS" to valeurs("133", "Non renseigné", "59", "Observation directe"),
        ).mapValues { (_, liste) -> liste.map { it.copy(id = 9000 + id++) } },
    )
    config.dateAvecHeures = true
    config.dateAvecFin = true
    config.additionalFieldsOcctaxJson = listeVersJson(
        AdditionalFieldDef.serializer(),
        listOf(
            AdditionalFieldDef(9001, "meteo", "Météo", WidgetType.TEXT, required = true, defaultValue = "Ensoleillé", objectsCode = listOf(AdditionalFieldsObject.RELEVE)),
            // Obligatoire et sans défaut : l'écran « Détails du relevé » s'intercale avant le relevé.
            AdditionalFieldDef(9002, "vent", "Vent", WidgetType.SELECT, fieldValues = listOf("Nul", "Faible", "Fort"), required = true, objectsCode = listOf(AdditionalFieldsObject.RELEVE)),
            // Les widgets du formulaire dynamique du web, sur l'occurrence et le dénombrement.
            AdditionalFieldDef(
                9010, "consignes", "Consignes", WidgetType.HTML, objectsCode = listOf(AdditionalFieldsObject.OCCURRENCE),
                html = "<b>Notez</b> l'activité observée ; voir le <a href=\"https://geonature.fr\">guide</a>.",
            ),
            AdditionalFieldDef(
                9011, "abondance", "Abondance", WidgetType.RADIO, objectsCode = listOf(AdditionalFieldsObject.OCCURRENCE),
                fieldValues = listOf("Rare", "Commun"), fieldValueCodes = listOf("1", "2"), valeursBrutes = listOf("1", "2"),
                aide = "Un clic sur le choix coché le décoche.",
            ),
            AdditionalFieldDef(
                9012, "milieux", "Milieux", WidgetType.MULTISELECT, objectsCode = listOf(AdditionalFieldsObject.OCCURRENCE),
                fieldValues = listOf("Prairie", "Forêt", "Lande"), fieldValueCodes = listOf("p", "f", "l"), valeursBrutes = listOf("\"p\"", "\"f\"", "\"l\""),
            ),
            AdditionalFieldDef(9013, "date_contact", "Date du contact", WidgetType.DATE, objectsCode = listOf(AdditionalFieldsObject.OCCURRENCE)),
            AdditionalFieldDef(9014, "heure_contact", "Heure du contact", WidgetType.TIME, objectsCode = listOf(AdditionalFieldsObject.OCCURRENCE)),
            AdditionalFieldDef(
                9015, "code_terrain", "Code de terrain", WidgetType.TEXT, objectsCode = listOf(AdditionalFieldsObject.OCCURRENCE),
                motif = "[A-Z]{2}[0-9]+", messageMotif = "Deux majuscules puis des chiffres", definition = "Le code inscrit sur le carnet.",
            ),
            AdditionalFieldDef(9016, "chant", "Chant entendu", WidgetType.BOOL_CHECKBOX, objectsCode = listOf(AdditionalFieldsObject.OCCURRENCE)),
            AdditionalFieldDef(
                9017, "observateur_terrain", "Observateur de terrain", WidgetType.OBSERVERS, objectsCode = listOf(AdditionalFieldsObject.OCCURRENCE),
            ),
            AdditionalFieldDef(
                9020, "proportion_males", "Proportion de mâles", WidgetType.NUMBER, objectsCode = listOf(AdditionalFieldsObject.COUNTING),
                min = 0.0, max = 1.0,
            ),
        ),
    )
}

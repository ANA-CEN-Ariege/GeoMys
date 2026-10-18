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

package fr.ariegenature.geomys.outils

import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.model.AdditionalFieldDef
import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.model.GeoNatureListe
import fr.ariegenature.geomys.model.GeoNatureObservateur
import fr.ariegenature.geomys.store.configurationComplete
import fr.ariegenature.geomys.store.io.listeDepuisJson
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

// Essai de fin du lot 2 (synchro complète sur un serveur réel, en ligne et hors ligne), lancé par
// le paramètre caché `action` de GeoMysApp et lu dans logcat ou la console de devicectl, comme
// CONNEXION_GEONATURE au lot 1. Aucun écran : Paramètres sera reproduit au lot 5.

/** Une ligne `DONNEES_HORS_LIGNE …` : ce que l'appareil sait sans réseau. Lit les caches sur le
 *  disque (TaxRef compris), donc hors du fil principal. */
suspend fun ServicesGeoNature.resumeDonnees(): String = withContext(Dispatchers.Default) {
    buildString {
        append("DONNEES_HORS_LIGNE")
        append(" taxref_version=").append(taxRef.versionSauvegardee ?: "aucune")
        append(" taxons=").append(taxRef.nbTaxonsUniques)
        append(" cles_taxref=").append(taxRef.count)
        append(" listes_synchronisees=").append(taxRef.listesSynchronisees.size)
        append(" listes_incompletes=").append(taxRef.listesIncompletes.size)
        append(" nomenclatures=").append(nomenclatures.count)
        append(" jdd=").append(listeDepuisJson(GeoNatureDataset.serializer(), config.datasetsCacheJson).size)
        append(" listes=").append(listeDepuisJson(GeoNatureListe.serializer(), config.listesCacheJson).size)
        append(" observateurs=").append(listeDepuisJson(GeoNatureObservateur.serializer(), config.observateursCacheJson).size)
        append(" champs_additionnels=")
            .append(listeDepuisJson(AdditionalFieldDef.serializer(), config.additionalFieldsOcctaxJson).size)
        append(" habitats=").append(habitatsOcctax.count)
        append(" habitats_occhab=").append(habitatsOccHab.count)
        append(" stations_serveur=").append(stationsServeur.count)
        append(" protocoles=").append(monitoringModules.countModulesEnCache())
        append(" occhab=").append(config.occhabDisponible)
        append(" configuration_complete=").append(configurationComplete(config, taxRef))
    }
}

/** Lance la synchro comme le bouton « Recharger les données » (par ExecutionLongue), écrit chaque
 *  étape dans [sortie] (`SYNCHRO_ETAPE "…"`), puis la ligne finale `SYNCHRO_GEONATURE …` et
 *  [resumeDonnees]. */
suspend fun ServicesGeoNature.synchroniserEnJournalisant(sortie: (String) -> Unit) {
    synchro.accuserReception()
    val debut = TimeSource.Monotonic.markNow()
    synchro.lancer()
    var derniere = ""
    val fin = synchro.etat.first { etat ->
        if (etat.texte.isNotEmpty() && etat.texte != derniere) {
            derniere = etat.texte
            sortie("SYNCHRO_ETAPE \"${etat.texte}\"")
        }
        etat.termine
    }
    val resume = fin.resume.orEmpty().trim().replace("\n", " | ")
    sortie("SYNCHRO_GEONATURE succes=${fin.succes} duree=${debut.elapsedNow().inWholeSeconds}s resume=\"$resume\"")
    sortie(resumeDonnees())
}

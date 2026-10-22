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

package fr.ariegenature.geomys.ui

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.Socle
import fr.ariegenature.geomys.location.PositionGps
import fr.ariegenature.geomys.location.SourcePositions
import fr.ariegenature.geomys.location.SuiviGpsDirect
import fr.ariegenature.geomys.network.http.ClientsGeoNature
import fr.ariegenature.geomys.plateforme.ExecutionLongue
import fr.ariegenature.geomys.store.CoffreMotDePasse
import fr.ariegenature.geomys.store.FabriqueReglages
import fr.ariegenature.geomys.store.io.RacinesStockage
import fr.ariegenature.geomys.util.uuidAleatoire
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import okio.FileSystem
import okio.Path
import okio.SYSTEM

/** Les vrais services de l'appli pour les tests des écrans : dossier temporaire, réglages en
 *  mémoire, aucun réseau (toute requête échoue, sauf [serveur] fourni), aucun GPS. Le test appelle
 *  [nettoyer] à la fin. */
internal class ServicesEssai(serveur: MockEngine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }) {
    private val dossier = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-ui-test-${uuidAleatoire()}"
    private val reglages = mutableMapOf<String, MapSettings>()

    val services = ServicesGeoNature(
        Socle(
            racines = RacinesStockage(dossier / "fichiers", dossier / "cache"),
            reglages = FabriqueReglages { nom -> reglages.getOrPut(nom) { MapSettings() } },
            coffre = object : CoffreMotDePasse {
                private var mdp: String? = null
                override fun lire() = mdp
                override fun ecrire(mdp: String): Boolean {
                    this.mdp = mdp
                    return true
                }
                override fun effacer() {
                    mdp = null
                }
                override fun disponible() = true
            },
            clients = ClientsGeoNature(serveur),
            executionLongue = ExecutionLongue { _, _, _ -> },
            positions = object : SourcePositions {
                override fun brancher(recepteur: (PositionGps) -> Unit) {}
                override fun demarrer() = false
                override fun arreter() {}
                override fun disponible() = false
                override fun derniereConnue(): PositionGps? = null
                override fun demanderPosition() {}
                override fun enregistrement(actif: Boolean) {}
            },
            suiviGps = SuiviGpsDirect,
        ),
    )

    /** Les fichiers du cache TaxRef et leur contenu, en hexadécimal. */
    fun contenuCacheTaxRef(): Map<Path, String> =
        FileSystem.SYSTEM.listRecursively(dossier / "fichiers" / "taxref")
            .filter { FileSystem.SYSTEM.metadata(it).isRegularFile }
            .associateWith { FileSystem.SYSTEM.read(it) { readByteString().hex() } }

    fun nettoyer() {
        FileSystem.SYSTEM.deleteRecursively(dossier)
    }
}

/** À la fin d'un test, avant `Dispatchers.resetMain()` : laisse finir le travail de fond annulé (une
 *  recherche de taxons sur `Dispatchers.IO`, l'écriture d'un brouillon). Il revient sur le fil
 *  principal pour s'achever ; après la remise à zéro de celui-ci, il échouait et faisait échouer le
 *  test suivant (« Dispatchers.Main was accessed… », « used concurrently with setting it »), ou
 *  empêchait d'effacer le dossier du test. */
fun laisserFinirLeTravailDeFond() = Thread.sleep(150)

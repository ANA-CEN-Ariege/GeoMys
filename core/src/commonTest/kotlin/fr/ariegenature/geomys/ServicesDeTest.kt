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

package fr.ariegenature.geomys

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.location.SourcePositionsSimulee
import fr.ariegenature.geomys.location.SuiviGpsDirect
import fr.ariegenature.geomys.network.ServeurSimule
import fr.ariegenature.geomys.plateforme.ExecutionLongue
import fr.ariegenature.geomys.store.CoffreEnMemoire
import fr.ariegenature.geomys.store.FabriqueReglages
import fr.ariegenature.geomys.store.io.RacinesStockage
import fr.ariegenature.geomys.util.uuidAleatoire
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.Flow
import okio.FileSystem
import okio.SYSTEM

/** Les vrais services de l'appli sur des doublures : dossier temporaire, réglages en mémoire (un
 *  MapSettings par nom), coffre en mémoire, serveur simulé. C'est ce que donnaient les objets
 *  globaux et Robolectric sous Android. [redemarrer] simule un démarrage à froid sur les mêmes
 *  disque et réglages ; le test appelle [nettoyer] à la fin. Les travaux confiés à
 *  ExecutionLongue attendent dans [travauxLongs], et ceux qu'elle garde de la veille sont notés
 *  dans [protections] ; les positions GPS arrivent par [positions]. */
internal class ServicesDeTest(val serveur: ServeurSimule = ServeurSimule()) {
    private val systeme = FileSystem.SYSTEM
    val dossier = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-test-${uuidAleatoire()}"
    private val reglages = mutableMapOf<String, MapSettings>()
    val travauxLongs = mutableListOf<suspend () -> Unit>()
    val positions = SourcePositionsSimulee()

    /** Les noms des travaux menés par [ExecutionLongue.pendant], dans l'ordre. */
    val protections = mutableListOf<String>()
    private val protegesEnCours = atomic(0)

    /** Un travail gardé de la veille est en cours. */
    val protege: Boolean get() = protegesEnCours.value > 0

    private val socle = Socle(
        racines = RacinesStockage(dossier / "fichiers", dossier / "cache"),
        reglages = FabriqueReglages { nom -> reglages.getOrPut(nom) { MapSettings() } },
        coffre = CoffreEnMemoire(),
        clients = serveur.clients,
        executionLongue = object : ExecutionLongue {
            override fun lancer(nom: String, avancement: Flow<String>, travail: suspend () -> Unit) {
                travauxLongs += travail
            }

            override suspend fun <T> pendant(nom: String, travail: suspend () -> T): T {
                protections += nom
                protegesEnCours.incrementAndGet()
                try {
                    return travail()
                } finally {
                    protegesEnCours.decrementAndGet()
                }
            }
        },
        positions = positions,
        suiviGps = SuiviGpsDirect,
    )

    var services = ServicesGeoNature(socle)
        private set

    fun redemarrer() {
        services = ServicesGeoNature(socle)
    }

    fun nettoyer() {
        systeme.deleteRecursively(dossier)
    }
}

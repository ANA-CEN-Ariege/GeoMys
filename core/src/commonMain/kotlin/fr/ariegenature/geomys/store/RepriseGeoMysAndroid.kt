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

// Propre au portage : GeoMys remplace GeoMys-Android sur Google Play, comme une nouvelle version de la
// même appli (décision de l'utilisateur du 22 octobre 2026).

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.journal.Journal
import fr.ariegenature.geomys.store.io.FichierAtomique
import okio.FileSystem
import okio.Path

/**
 * La reprise des saisies que GeoMys-Android gardait dans ses préférences, au premier lancement de
 * GeoMys installé par-dessus lui (la même appli sur Google Play, décision du 22 octobre 2026) :
 * - les relevés OccTax : préférences `sorties_store`, clé `sorties_sauvegardees` ;
 * - les stations OccHab : préférences `occhab_store`, clé `saisies_sauvegardees`.
 *
 * Ce sont des tableaux JSON du format que lisent [SortieStore] et [OccHabStore] : ils sont recopiés
 * dans leurs fichiers tant que ceux-ci n'existent pas, donc jamais par-dessus des saisies de GeoMys,
 * ni pour faire revenir des saisies effacées depuis. L'original reste en place.
 *
 * Le reste est relu sur place, aux mêmes noms : réglages, mot de passe chiffré, visites des Suivis
 * (`monitoring_outbox.json`), photos, cartes MBTiles. Sans objet sous iOS, où ces préférences
 * n'existent pas.
 */
internal object RepriseGeoMysAndroid {

    private const val TAG = "RepriseGeoMysAndroid"

    /** Les saisies reprises : préférences et clé de GeoMys-Android, fichier de GeoMys. */
    private val REPRISES = listOf(
        Triple("sorties_store", "sorties_sauvegardees", "sorties_store.json"),
        Triple("occhab_store", "saisies_sauvegardees", "occhab_store.json"),
    )

    /** À faire au démarrage, avant toute lecture des saisies. */
    fun reprendre(reglages: FabriqueReglages, systeme: FileSystem, fichiers: Path) {
        for ((preferences, cle, nomFichier) in REPRISES) {
            val cible = fichiers / nomFichier
            if (systeme.exists(cible)) continue
            val json = reglages.reglages(preferences).getStringOrNull(cle)?.takeIf { it.isNotBlank() } ?: continue
            if (FichierAtomique(systeme, cible, TAG).ecrire(json)) {
                Journal.i(TAG, "Saisies de GeoMys-Android reprises dans $nomFichier (${json.length} caractères)")
            } else {
                Journal.w(TAG, "Reprise de $nomFichier impossible : les saisies restent dans les préférences $preferences")
            }
        }
    }
}

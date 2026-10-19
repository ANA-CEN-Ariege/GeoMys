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

// Les préférences de saisie reprennent GeoMys-Android @ cdd5196
// (app/src/main/java/fr/ariegenature/geomys/ui/saisie/PreferencesSaisie.kt), mêmes clés.

package fr.ariegenature.geomys.store

import com.russhwolf.settings.Settings
import fr.ariegenature.geomys.model.Taxon

/** Préférences de l'utilisateur hors connexion GeoNature, dans les réglages `GeoMys_prefs` comme
 *  sous Android. On ne mémorise que des valeurs d'interface, pas de donnée naturaliste. */
class PreferencesGeoMys(private val prefs: Settings) {

    /** Interrupteur « Enregistrer la trace GPS » de l'accueil (`enregistrer_trace`), oui par défaut. */
    var enregistrerTrace: Boolean
        get() = prefs.getBoolean("enregistrer_trace", true)
        set(value) = prefs.putBoolean("enregistrer_trace", value)

    /** Dernier groupe taxonomique choisi sur les écrans de saisie (`saisie_dernier_taxon`, nom de
     *  l'énumération), ou null. */
    fun dernierTaxon(): Taxon? {
        val nom = prefs.getStringOrNull(CLE_DERNIER_TAXON) ?: return null
        return runCatching { Taxon.valueOf(nom) }.getOrNull()
    }

    fun memoiserTaxon(taxon: Taxon) {
        prefs.putString(CLE_DERNIER_TAXON, taxon.name)
    }

    /** Interrupteur « noms scientifiques » des écrans de saisie (`saisie_nom_sci`). */
    fun rechercheNomSci(): Boolean = prefs.getBoolean(CLE_NOM_SCI, false)

    fun memoiserNomSci(actif: Boolean) {
        prefs.putBoolean(CLE_NOM_SCI, actif)
    }

    /** Fond de carte choisi (`fond_carte`) : nom du fond en ligne, ou `mbtiles:<fichier>` pour une
     *  carte hors ligne. Null tant que l'utilisateur n'a rien choisi. */
    var fondCarte: String?
        get() = prefs.getStringOrNull(CLE_FOND)
        set(value) {
            if (value == null) prefs.remove(CLE_FOND) else prefs.putString(CLE_FOND, value)
        }

    private companion object {
        const val CLE_DERNIER_TAXON = "saisie_dernier_taxon"
        const val CLE_NOM_SCI = "saisie_nom_sci"
        const val CLE_FOND = "fond_carte"
    }
}

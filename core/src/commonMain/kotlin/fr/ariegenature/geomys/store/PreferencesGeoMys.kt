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

// Les préférences de saisie reprennent GeoMys-Android @ cdd5196
// (app/src/main/java/fr/ariegenature/geomys/ui/saisie/PreferencesSaisie.kt), mêmes clés.

package fr.ariegenature.geomys.store

import com.russhwolf.settings.Settings
import fr.ariegenature.geomys.model.Taxon

/** Le mode de saisie, choisi en tête des Paramètres (demandes du 14 novembre 2026). */
enum class ModeSaisie {
    /** Les écrans de GeoMys-Android : caractérisation et dénombrements complets. */
    PRO,

    /** La saisie multi-taxons allégée : après chaque espèce, Détails, Comportement, Photo et Audio ;
     *  les autres champs gardent leur valeur par défaut. */
    SIMPLIFIE,
}

/** Préférences de l'utilisateur hors connexion GeoNature, dans les réglages `GeoMys_prefs` comme
 *  sous Android. On ne mémorise que des valeurs d'interface, pas de donnée naturaliste. Ni un
 *  changement de serveur ou de compte, ni « Vider le cache » n'y touchent. */
class PreferencesGeoMys(private val prefs: Settings) {

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

    /** Le mode de saisie (`mode_saisie`), enregistré au premier démarrage par [fixerModeInitial] ;
     *  sans lui (tests), [ModeSaisie.PRO], le défaut d'avant le 20 novembre 2026. */
    var modeSaisie: ModeSaisie
        get() = prefs.getStringOrNull(CLE_MODE)?.let { nom -> ModeSaisie.entries.firstOrNull { it.name == nom } } ?: ModeSaisie.PRO
        set(value) {
            prefs.putString(CLE_MODE, value.name)
        }

    /** Au démarrage, si aucun mode n'est enregistré : le mode simplifié pour une nouvelle
     *  installation (demande du 20 novembre 2026), le mode pro pour une appli [dejaUtilisee], qui
     *  l'avait par défaut, pour qu'une mise à jour ne change rien. Le mode est alors enregistré : il
     *  ne change plus que par les Paramètres. */
    fun fixerModeInitial(dejaUtilisee: Boolean) {
        if (prefs.getStringOrNull(CLE_MODE) != null) return
        modeSaisie = if (dejaUtilisee) ModeSaisie.PRO else ModeSaisie.SIMPLIFIE
    }

    private companion object {
        const val CLE_MODE = "mode_saisie"
        const val CLE_DERNIER_TAXON = "saisie_dernier_taxon"
        const val CLE_NOM_SCI = "saisie_nom_sci"
        const val CLE_FOND = "fond_carte"
    }
}

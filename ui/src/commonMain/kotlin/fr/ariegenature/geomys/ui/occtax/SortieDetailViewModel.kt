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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/SortieDetailFragment.kt) :
// l'état de l'écran ; ce que la carte montre est dans occtax/AffichageSortie.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.carte.FondChoisi
import fr.ariegenature.geomys.carte.chargerFondChoisi
import fr.ariegenature.geomys.carte.enregistrerFondChoisi
import fr.ariegenature.geomys.gpx.genererGPX
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.occtax.AffichageSortie
import fr.ariegenature.geomys.occtax.CadrageSortie
import fr.ariegenature.geomys.occtax.ReleveAffiche
import fr.ariegenature.geomys.ui.composants.FichierAPartager
import kotlinx.coroutines.launch

/** L'écran « Détail de la saisie » : la sortie sur la carte, la liste de ses espèces, son envoi et
 *  l'export GPX. La sortie est lue une fois, à l'ouverture. */
class SortieDetailViewModel(private val services: ServicesGeoNature, private val sortieId: String) : ViewModel() {

    /** La sortie, ou null tant qu'elle n'est pas lue. */
    var sortie by mutableStateOf<Sortie?>(null)
        private set

    /** Saisie introuvable (supprimée entre-temps) : l'écran revient à la liste. */
    var introuvable by mutableStateOf(false)
        private set

    var releves by mutableStateOf<List<ReleveAffiche>>(emptyList())
        private set
    var cadrage by mutableStateOf<CadrageSortie>(CadrageSortie.France)
        private set

    // Le fond partagé par toutes les cartes, carte MBTiles importée comprise.
    var fond by mutableStateOf(chargerFondChoisi(services.preferences, services.cartesMbtiles::existe))
        private set

    var boutonEnvoiVisible by mutableStateOf(false)
        private set
    var envoiEnCours by mutableStateOf(false)
        private set
    var information by mutableStateOf<Information?>(null)
        private set
    var especesVisibles by mutableStateOf(false)
        private set
    var choixFondVisible by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val lue = services.sortieStore.charger().firstOrNull { it.id == sortieId }
            if (lue == null) {
                introuvable = true
                return@launch
            }
            val affiches = AffichageSortie.releves(lue)
            releves = affiches
            cadrage = AffichageSortie.cadrage(lue, affiches)
            boutonEnvoiVisible = AffichageSortie.peutEnvoyer(lue, services.config.estConfiguree, services.config.occtaxPeutCreer)
            sortie = lue
        }
    }

    val aDesEspeces: Boolean get() = sortie?.observations?.any { !it.releveSansEspece } == true

    fun montrerEspeces() {
        especesVisibles = true
    }

    fun fermerEspeces() {
        especesVisibles = false
    }

    fun montrerChoixFond() {
        choixFondVisible = true
    }

    fun fermerChoixFond() {
        choixFondVisible = false
    }

    fun choisirFond(choisi: FondChoisi) {
        fond = choisi
        enregistrerFondChoisi(services.preferences, choisi)
    }

    fun fermerInformation() {
        information = null
    }

    /** Le GPX de la sortie, à partager. */
    fun fichierGpx(): FichierAPartager? = sortie?.let {
        FichierAPartager(
            nom = AffichageSortie.nomFichierGpx(it),
            contenu = genererGPX(it.observations, it.pointsParcours),
            typeMime = "application/gpx+xml",
            titre = "Exporter GPX",
        )
    }

    /** Envoi vers GeoNature, sous un voile : le bouton disparaît en cas de succès. Le résultat est
     *  aussi écrit dans le store (« Mes saisies » le montre à la réouverture). */
    fun envoyer() {
        val s = sortie ?: return
        if (envoiEnCours) return
        envoiEnCours = true
        viewModelScope.launch {
            try {
                val res = services.envoiSortie.envoyerSortieVersGeoNature(s, services.config)
                if (res.succes) boutonEnvoiVisible = false
                information = Information(if (res.succes) "GeoNature" else "Erreur d'envoi", res.message)
            } finally {
                envoiEnCours = false
            }
        }
    }
}

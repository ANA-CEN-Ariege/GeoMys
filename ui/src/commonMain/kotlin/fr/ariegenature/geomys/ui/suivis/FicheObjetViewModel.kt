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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/FicheObjetFragment.kt) : le
// chargement de l'objet et du schéma, le rechargement au retour quand la file d'envoi a changé, et ce que le
// fragment posait sur ses vues ; les règles sont dans monitoring/FicheObjet et monitoring/AffichageSuivis.

package fr.ariegenature.geomys.ui.suivis

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.monitoring.AffichageSuivis
import fr.ariegenature.geomys.monitoring.ElementFiche
import fr.ariegenature.geomys.monitoring.FIL_TYPE_MODULE
import fr.ariegenature.geomys.monitoring.FicheObjet
import fr.ariegenature.geomys.monitoring.FilSegment
import fr.ariegenature.geomys.network.MonitoringApi.LabelResolver
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringObjet
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringSchemaObjet
import fr.ariegenature.geomys.network.MonitoringSync
import kotlinx.coroutines.launch

/** La fiche d'un objet de suivi (`FicheObjetFragment`) : son type, son nom, ses propriétés et ses
 *  enfants (saisies locales comprises). [fil] : son fil d'Ariane, dont il est le dernier niveau. */
class FicheObjetViewModel(
    private val services: ServicesGeoNature,
    val moduleCode: String,
    private val objectType: String,
    private val id: Int,
    val fil: List<FilSegment>,
) : ViewModel() {

    var chargement by mutableStateOf(true)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set
    var type by mutableStateOf("")
        private set
    var titre by mutableStateOf("")
        private set
    var proprietes by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    /** Les « Propriétés spécifiques » du web : les champs propres aux types d'un site. */
    var proprietesSpecifiques by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    var elements by mutableStateOf<List<ElementFiche>>(emptyList())
        private set

    /** Fiche reconstituée depuis son parent : ses saisies rattachées ne sont pas dans le cache, ou
     *  sont demandées au serveur. */
    var note by mutableStateOf<String?>(null)
        private set

    // Le dernier rendu, pour relister les enfants au retour sans refaire les requêtes.
    private var objet: MonitoringObjet? = null
    private var schema: Map<String, MonitoringSchemaObjet>? = null
    private var resolveur = LabelResolver()
    private var signature: String? = null

    init {
        charger()
    }

    private fun charger() {
        chargement = true
        erreur = null
        type = ""
        titre = ""
        proprietes = emptyList()
        proprietesSpecifiques = emptyList()
        elements = emptyList()
        note = null
        objet = null
        viewModelScope.launch {
            // Tout vient du cache de la synchro, rien n'est demandé au serveur (décision de
            // l'utilisateur du 20 octobre) — sauf pour une saisie (visite, observation), que la
            // synchro ne charge pas : avec du réseau, sa fiche se met à jour depuis le serveur
            // après s'être affichée (demande du même jour). Hors réseau, le cache reste affiché.
            val s = resultat { services.monitoring.lireSchemaProtocole(moduleCode) }.getOrNull()
            schema = s
            resolveur = s?.let { resultat { services.monitoringDatalists.lireResolveurLabels(services.config, it, moduleCode) }.getOrNull() }
                ?: LabelResolver()
            val enCache = resultat { lireFiche() }.getOrNull()
            val saisie = MonitoringSync.estSaisie(objectType, s)
            if (enCache != null) afficher(enCache, miseAJourEnCours = saisie)
            if (saisie) {
                val serveur = resultat { services.monitoring.chargerObjet(services.config, moduleCode, objectType, id) }.getOrNull()
                when {
                    serveur != null -> afficher(serveur, miseAJourEnCours = false)
                    enCache != null -> note = if (enCache.enfantsConnus) null else AffichageSuivis.ENFANTS_HORS_CACHE
                }
            }
            if (objet == null) {
                chargement = false
                erreur = AffichageSuivis.FICHE_HORS_CACHE
            }
        }
    }

    /** Affiche la fiche [o] ; [miseAJourEnCours] : sa version du serveur est demandée. */
    private suspend fun afficher(o: MonitoringObjet, miseAJourEnCours: Boolean) {
        chargement = false
        objet = o
        val schemaCe = schema?.get(o.type)
        titre = AffichageSuivis.nomObjet(o, schemaCe, resolveur)
        type = AffichageSuivis.libelleType(o.type, schemaCe)
        proprietes = AffichageSuivis.proprietesAffichees(o, schemaCe, resolveur)
        proprietesSpecifiques = o.valeursMultiples["types_site"]?.takeIf { o.type == "site" }?.let { types ->
            val champs = resultat { services.monitoringDatalists.lireChampsTypesSite(schemaCe, types) }.getOrNull().orEmpty()
            AffichageSuivis.proprietesSpecifiques(o, champs, resolveur)
        }.orEmpty()
        note = when {
            o.enfantsConnus -> null
            miseAJourEnCours -> AffichageSuivis.ENFANTS_EN_CHARGEMENT
            else -> AffichageSuivis.ENFANTS_HORS_CACHE
        }
        listerEnfants(o)
    }

    /** La fiche en cache ; à défaut, reconstituée depuis sa ligne chez son parent, le niveau
     *  précédent du fil (une visite passée : la synchro ne charge pas l'historique). */
    private suspend fun lireFiche(): MonitoringObjet? {
        services.monitoring.lireObjet(moduleCode, objectType, id)?.let { return it }
        val parent = fil.getOrNull(fil.lastIndex - 1) ?: return null
        return when {
            parent.type == FIL_TYPE_MODULE -> services.monitoring.lireObjetDepuisParent(moduleCode, objectType, id, null, -1)
            parent.id > 0 -> services.monitoring.lireObjetDepuisParent(moduleCode, objectType, id, parent.type, parent.id)
            else -> null
        }
    }

    private suspend fun listerEnfants(o: MonitoringObjet) {
        val toutes = services.outbox.tout()
        elements = FicheObjet.elements(
            o, schema, resolveur, toutes, fil,
            labelTypeEnCache = services.monitoringObjets::labelTypeEnCache,
            libelleNouveau = services.monitoringObjets::libelleNouveau,
            peutCreer = { module, type -> services.monitoringModules.peutCreer(services.config, module, type) },
        )
        signature = FicheObjet.signatureOutbox(o, toutes)
    }

    /** Retour sur la fiche : si une saisie rattachée a été ajoutée ou envoyée entre-temps, la fiche
     *  est relue (une visite envoyée n'est plus dans la file, mais dans le cache, où l'envoi l'a
     *  reportée) ; sinon ses enfants sont seulement relistés. */
    fun aLaReprise() {
        val o = objet ?: return
        viewModelScope.launch {
            if (FicheObjet.signatureOutbox(o, services.outbox.tout()) != signature) charger() else listerEnfants(o)
        }
    }
}

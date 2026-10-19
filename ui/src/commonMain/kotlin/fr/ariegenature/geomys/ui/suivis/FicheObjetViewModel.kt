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
import fr.ariegenature.geomys.monitoring.FicheObjet
import fr.ariegenature.geomys.monitoring.FilSegment
import fr.ariegenature.geomys.network.MonitoringApi.LabelResolver
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringObjet
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringSchemaObjet
import fr.ariegenature.geomys.network.humaniserErreurReseau
import kotlinx.coroutines.async
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
    var elements by mutableStateOf<List<ElementFiche>>(emptyList())
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
        elements = emptyList()
        viewModelScope.launch {
            val config = services.config
            val objetAttendu = async { resultat { services.monitoring.chargerObjet(config, moduleCode, objectType, id) } }
            val schemaAttendu = async { resultat { services.monitoring.chargerSchemaProtocole(config, moduleCode) } }
            val charge = objetAttendu.await().getOrElse { e ->
                chargement = false
                erreur = humaniserErreurReseau(e)
                return@launch
            }
            val s = schemaAttendu.await().getOrNull()
            resolveur = s?.let { resultat { services.monitoringDatalists.chargerResolveurLabels(config, moduleCode, it) }.getOrNull() }
                ?: LabelResolver()
            chargement = false
            objet = charge
            schema = s
            val schemaCe = s?.get(charge.type)
            titre = AffichageSuivis.nomObjet(charge, schemaCe)
            type = AffichageSuivis.libelleType(charge.type, schemaCe)
            proprietes = AffichageSuivis.proprietesAffichees(charge, schemaCe, resolveur)
            listerEnfants(charge)
        }
    }

    private suspend fun listerEnfants(o: MonitoringObjet) {
        val toutes = services.outbox.tout()
        elements = FicheObjet.elements(
            o, schema, resolveur, toutes, fil,
            labelTypeEnCache = services.monitoringObjets::labelTypeEnCache,
            libelleNouveau = services.monitoringObjets::libelleNouveau,
        )
        signature = FicheObjet.signatureOutbox(o, toutes)
    }

    /** Retour sur la fiche : si une saisie rattachée a été ajoutée ou envoyée entre-temps, la fiche
     *  est rechargée (une visite envoyée n'est plus dans la file, mais sur le serveur) ; sinon ses
     *  enfants sont seulement relistés. */
    fun aLaReprise() {
        val o = objet ?: return
        viewModelScope.launch {
            if (FicheObjet.signatureOutbox(o, services.outbox.tout()) != signature) charger() else listerEnfants(o)
        }
    }
}

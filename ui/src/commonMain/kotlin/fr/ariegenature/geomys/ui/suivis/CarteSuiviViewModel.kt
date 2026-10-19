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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/CarteGeometrieFragment.kt) : le
// chargement de la carte d'un objet (sa géométrie et celles de ses enfants) ou d'un protocole (ses sites), les
// actions au toucher d'un objet, le fond de carte et la position. La lecture des géométries et les règles sont
// dans monitoring/CarteSuivis.

package fr.ariegenature.geomys.ui.suivis

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.carte.FondChoisi
import fr.ariegenature.geomys.carte.chargerFondChoisi
import fr.ariegenature.geomys.carte.enregistrerFondChoisi
import fr.ariegenature.geomys.geo.Emprise
import fr.ariegenature.geomys.monitoring.ActionCarte
import fr.ariegenature.geomys.monitoring.CarteSuivis
import fr.ariegenature.geomys.monitoring.FilSegment
import fr.ariegenature.geomys.monitoring.ObjetCarte
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringEnfant
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringSchemaObjet
import fr.ariegenature.geomys.network.humaniserErreurReseau
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Le menu d'un objet touché. */
data class ActionsObjet(val objet: ObjetCarte, val actions: List<ActionCarte>, val fil: List<FilSegment>)

sealed interface EvenementCarteSuivi {
    data class Toast(val texte: String) : EvenementCarteSuivi
    data class Fiche(val objectType: String, val id: Int, val fil: List<FilSegment>) : EvenementCarteSuivi
    data class NouvelleSaisie(val ouverture: OuvertureNouvelleVisite) : EvenementCarteSuivi
}

/** La carte des géométries d'un objet de suivi ([objectType] « module » : celle d'un protocole). */
class CarteSuiviViewModel(
    private val services: ServicesGeoNature,
    val moduleCode: String,
    private val objectType: String,
    private val id: Int,
    val titre: String,
    val fil: List<FilSegment>,
) : ViewModel() {

    var chargement by mutableStateOf(true)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set
    var objets by mutableStateOf<List<ObjetCarte>>(emptyList())
        private set

    /** Le cadrage, une fois les géométries connues. */
    var emprise by mutableStateOf<Emprise?>(null)
        private set
    var menu by mutableStateOf<ActionsObjet?>(null)
        private set
    var fond by mutableStateOf(chargerFondChoisi(services.preferences, services.cartesMbtiles::existe))
        private set
    var choixFondVisible by mutableStateOf(false)
        private set

    val tracker = services.locationTracker

    private val _evenements = Channel<EvenementCarteSuivi>(Channel.BUFFERED)
    val evenements = _evenements.receiveAsFlow()

    /** Le schéma du protocole : il dit si le toucher d'un objet propose une nouvelle saisie. */
    private var schema: Map<String, MonitoringSchemaObjet>? = null
    private var positionDemarree = false

    init {
        viewModelScope.launch { if (objectType == "module") chargerProtocole() else chargerObjet() }
    }

    /** La géométrie d'un enfant : l'API ne la donne pas toujours avec la liste, il faut alors
     *  charger l'enfant lui-même (au mieux : un échec isolé ne prive pas la carte du reste). */
    private suspend fun geometries(enfants: List<Pair<String, MonitoringEnfant>>): List<Triple<String, MonitoringEnfant, String?>> = coroutineScope {
        val config = services.config
        enfants.map { (type, e) ->
            async {
                val geo = e.geometrieGeoJson?.takeIf { it.isNotEmpty() }
                    ?: resultat { services.monitoring.chargerObjet(config, moduleCode, type, e.id).geometrieGeoJson }.getOrNull()
                Triple(type, e, geo)
            }
        }.awaitAll()
    }

    /** La carte d'un objet : sa géométrie, puis celles de ses enfants. */
    private suspend fun chargerObjet() = coroutineScope {
        val config = services.config
        val schemaAttendu = async { resultat { services.monitoring.chargerSchemaProtocole(config, moduleCode) }.getOrNull() }
        val objet = resultat { services.monitoring.chargerObjet(config, moduleCode, objectType, id) }.getOrElse { e ->
            chargement = false
            erreur = humaniserErreurReseau(e)
            return@coroutineScope
        }
        schema = schemaAttendu.await()
        val geo = objet.geometrieGeoJson
        if (geo.isNullOrEmpty()) {
            chargement = false
            erreur = CarteSuivis.PAS_DE_GEOMETRIE
            return@coroutineScope
        }
        val enfants = geometries(objet.enfants.flatMap { (type, liste) -> liste.map { type to it } })
        chargement = false
        val principal = try {
            ObjetCarte(objectType, id, titre.ifEmpty { objectType }, enfant = false, geometries = CarteSuivis.lire(geo))
        } catch (e: Exception) {
            erreur = CarteSuivis.geometrieIllisible(e.message)
            return@coroutineScope
        }
        terminer(listOf(principal) + enfants.mapNotNull { (type, e, gj) -> lireObjet(type, e, gj, enfant = true) })
    }

    /** La carte d'un protocole : ses sites « macro ». */
    private suspend fun chargerProtocole() {
        val config = services.config
        val parType = resultat { services.monitoring.chargerEnfants(config, moduleCode) }.getOrElse { e ->
            chargement = false
            erreur = humaniserErreurReseau(e)
            return
        }
        schema = resultat { services.monitoring.chargerSchemaProtocole(config, moduleCode) }.getOrNull()
        val aCharger = CarteSuivis.typesMacro(parType.keys.toList(), schema).flatMap { type -> parType[type].orEmpty().map { type to it } }
        if (aCharger.isEmpty()) {
            chargement = false
            erreur = CarteSuivis.AUCUN_SITE
            return
        }
        val sites = geometries(aCharger)
        chargement = false
        terminer(sites.mapNotNull { (type, e, gj) -> lireObjet(type, e, gj, enfant = false) })
    }

    /** Un enfant illisible est écarté sans priver la carte des autres. */
    private fun lireObjet(type: String, e: MonitoringEnfant, geoJson: String?, enfant: Boolean): ObjetCarte? {
        if (geoJson.isNullOrEmpty()) return null
        return try {
            ObjetCarte(type, e.id, e.nom, enfant, CarteSuivis.lire(geoJson))
        } catch (_: Exception) {
            null
        }
    }

    private fun terminer(liste: List<ObjetCarte>) {
        val points = CarteSuivis.points(liste)
        if (points.isEmpty()) {
            erreur = CarteSuivis.AUCUNE_GEOMETRIE
            return
        }
        objets = liste
        emprise = CarteSuivis.emprise(points)
    }

    // ─── Toucher d'un objet ───

    /** Les actions sur l'objet touché ; sans action possible, son nom en message bref, pour dire
     *  que le toucher a été pris. */
    fun toucher(objet: ObjetCarte) {
        val courant = objet.type == objectType && objet.id == id
        val actions = CarteSuivis.actions(objet, courant, schema) { services.monitoringObjets.libelleNouveau(moduleCode, it) }
        if (actions.isEmpty()) {
            _evenements.trySend(EvenementCarteSuivi.Toast(objet.label))
            return
        }
        val labelModule = services.monitoringModules.labelModuleEnCache(moduleCode) ?: moduleCode
        menu = ActionsObjet(objet, actions, CarteSuivis.filPourObjet(objet, courant, fil, labelModule))
    }

    fun fermerMenu() {
        menu = null
    }

    fun choisir(m: ActionsObjet, action: ActionCarte) {
        when (action) {
            ActionCarte.Fiche -> _evenements.trySend(EvenementCarteSuivi.Fiche(m.objet.type, m.objet.id, m.fil))
            is ActionCarte.NouvelleSaisie -> _evenements.trySend(
                EvenementCarteSuivi.NouvelleSaisie(
                    OuvertureNouvelleVisite(
                        moduleCode = moduleCode,
                        parentObjectType = m.objet.type,
                        parentId = m.objet.id,
                        titreSite = m.objet.label,
                        fil = m.fil,
                        childObjectType = action.childObjectType,
                    ),
                ),
            )
        }
    }

    // ─── Fond, position ───

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

    /** La position s'affiche tant que la carte est à l'écran. Un parcours en cours (saisie OccTax)
     *  garde son récepteur : la carte ne l'arrête pas. */
    fun reprendre() {
        if (tracker.estEnCours.value) return
        tracker.demarrer()
        positionDemarree = true
    }

    fun suspendre() {
        if (!positionDemarree || tracker.estEnCours.value) return
        tracker.arreter()
        positionDemarree = false
    }
}

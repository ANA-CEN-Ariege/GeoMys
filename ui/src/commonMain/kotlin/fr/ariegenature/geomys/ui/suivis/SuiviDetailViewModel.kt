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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/SuiviDetailFragment.kt) : le
// chargement des objets du protocole et de son schéma, et ce que le fragment posait sur ses vues, devient un
// état observable ; les règles d'affichage sont dans monitoring/AffichageSuivis.

package fr.ariegenature.geomys.ui.suivis

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.monitoring.AffichageSuivis
import fr.ariegenature.geomys.monitoring.FilSegment
import fr.ariegenature.geomys.monitoring.filRacineSuivis
import fr.ariegenature.geomys.network.MonitoringApi.LabelResolver
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringEnfant
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringSchemaObjet
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Une ligne d'objet d'un protocole (site, groupe de sites…) et ses actions. */
data class LigneObjetSuivi(
    val type: String,
    val id: Int,
    val nom: String,
    val sousTitre: String,
    /** Le type a une géométrie déclarée : bouton « voir sur carte ». */
    val carte: Boolean,
    /** La saisie que crée le « + » de la ligne (visite, observation…), null sans « + ». */
    val saisieEnfant: String?,
    /** « Nouvelle visite », « Nouveau passage »… : la description du « + ». */
    val libelleNouveau: String?,
)

/** Les objets d'un type ; [entete] seulement quand plusieurs types se partagent la liste. */
data class SectionSuivi(val entete: String?, val lignes: List<LigneObjetSuivi>)

/** Le détail d'un protocole (`SuiviDetailFragment`) : ses objets « macro » (sites, groupes de
 *  sites), leur résumé et leurs actions. Le protocole est lu dans la liste déjà chargée. */
class SuiviDetailViewModel(private val services: ServicesGeoNature, val moduleCode: String) : ViewModel() {
    private val module = services.monitoringModules.moduleParCode(moduleCode)

    val titre: String = module?.moduleLabel ?: moduleCode
    val description: String? = module?.moduleDesc

    /** « 🏠 › Monitoring › Protocole » : le protocole est l'écran courant. */
    val fil: List<FilSegment> = filRacineSuivis(module?.let { it.moduleLabel.ifEmpty { it.moduleCode } } ?: moduleCode)

    /** « Points d'écoute : 3 » ; « Sites : … » pendant le chargement ; null sans objet. */
    var resume by mutableStateOf(if (module != null) AffichageSuivis.RESUME_EN_COURS else null)
        private set
    var erreur by mutableStateOf(if (module == null) AffichageSuivis.MESSAGE_PROTOCOLE_INCONNU else null)
        private set
    var sections by mutableStateOf<List<SectionSuivi>>(emptyList())
        private set

    init {
        if (module != null) charger()
    }

    /** Le fil d'une fiche, d'une carte ou d'une nouvelle saisie ouvertes depuis [ligne]. */
    fun filDe(ligne: LigneObjetSuivi): List<FilSegment> = fil + FilSegment(ligne.type, ligne.id, ligne.nom)

    private fun charger() {
        viewModelScope.launch {
            // Tout vient du cache de la synchro : rien n'est demandé au serveur (décision de
            // l'utilisateur du 20 octobre).
            val enfantsBruts = resultat { services.monitoring.lireEnfants(moduleCode) }.getOrNull()
            if (enfantsBruts == null) {
                resume = null
                erreur = AffichageSuivis.PROTOCOLE_HORS_CACHE
                return@launch
            }
            if (enfantsBruts.isEmpty()) {
                resume = null
                return@launch
            }
            // Les centaines de lignes d'un gros protocole se préparent hors du fil de l'interface.
            val (resumeListe, sectionsListe) = withContext(Dispatchers.Default) {
                val schema = resultat { services.monitoring.lireSchemaProtocole(moduleCode) }.getOrNull()
                // Résolveur des libellés (id_role, id_nomenclature, id_dataset → nom lisible).
                val resolveur = schema?.let { s ->
                    resultat { services.monitoringDatalists.lireResolveurLabels(services.config, s) }.getOrNull()
                } ?: LabelResolver()
                lignes(enfantsBruts, schema, resolveur)
            }
            resume = resumeListe
            sections = sectionsListe
        }
    }

    /** Le résumé et les sections de la liste des [enfantsBruts]. */
    private fun lignes(
        enfantsBruts: Map<String, List<MonitoringEnfant>>,
        schema: Map<String, MonitoringSchemaObjet>?,
        resolveur: LabelResolver,
    ): Pair<String, List<SectionSuivi>> {
        val enfants = AffichageSuivis.enfantsDuProtocole(enfantsBruts, schema)
        // Sur un protocole avec hiérarchie (sites_group → site), seul le niveau macro est
        // listé ; les niveaux inférieurs restent visibles dans la fiche de leur parent.
        val types = AffichageSuivis.typesAfficherEnListe(enfants.keys.toList(), schema)
        val plusieursTypes = types.size > 1
        val sections = types.mapNotNull { type ->
            val items = enfants[type].orEmpty().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val schemaType = schema?.get(type)
            val saisie = AffichageSuivis.typeSaisieEnfantServeur(type, schemaType)
            // « Nouvelle visite » : le même pour toutes les lignes du type.
            val libelleNouveau = saisie?.let { services.monitoringObjets.libelleNouveau(moduleCode, it) }
            SectionSuivi(
                entete = if (plusieursTypes) AffichageSuivis.libelleListe(type, schema) else null,
                lignes = items.map { e ->
                    LigneObjetSuivi(
                        type = type,
                        id = e.id,
                        nom = e.nom,
                        sousTitre = AffichageSuivis.sousTitrePourEnfant(e, schemaType, resolveur),
                        carte = schemaType?.geometryType != null,
                        saisieEnfant = saisie,
                        libelleNouveau = libelleNouveau,
                    )
                },
            )
        }
        return AffichageSuivis.resume(types, schema, enfants.mapValues { it.value.size }) to sections
    }
}

/** [bloc] en [Result], sans avaler l'annulation de la coroutine. */
internal suspend fun <T> resultat(bloc: suspend () -> T): Result<T> = try {
    Result.success(bloc())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

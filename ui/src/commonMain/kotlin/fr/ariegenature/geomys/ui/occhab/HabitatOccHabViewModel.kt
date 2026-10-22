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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/OccHabHabitatFragment.kt) : les
// champs construits par programme deviennent un état observable ; les règles sont dans occhab/HabitatOccHab.

package fr.ariegenature.geomys.ui.occhab

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.model.HabitatSuggestion
import fr.ariegenature.geomys.occhab.EditionAnaEval
import fr.ariegenature.geomys.occhab.HabitatOccHab
import fr.ariegenature.geomys.occhab.ListeNomenclatureOccHab
import fr.ariegenature.geomys.occhab.NomenclatureHabitat
import fr.ariegenature.geomys.occhab.SaisiesHabitat
import fr.ariegenature.geomys.occhab.SessionOccHab
import fr.ariegenature.geomys.occhab.StationOccHab
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface EvenementHabitatOccHab {
    data class Message(val texte: String, val longue: Boolean = false) : EvenementHabitatOccHab

    /** L'habitat est enregistré : retour à la station. */
    data object Retour : EvenementHabitatOccHab

    /** Saisie interrompue (application relancée) : retour à l'accueil. */
    data object Accueil : EvenementHabitatOccHab
}

/** Une liste de nomenclature du formulaire, si `OCCHAB.formConfig` la montre. */
class ChampNomenclatureHabitat(val definition: NomenclatureHabitat, val liste: ListeNomenclatureOccHab, private val initiale: Int?) {
    var position by mutableStateOf(liste.position(initiale))

    /** La valeur retenue ([ListeNomenclatureOccHab.retenue]). */
    val valeur: Int? get() = liste.retenue(initiale, position)
}

/** Le formulaire de création ([habitatId] null) ou d'édition d'UN habitat de la station
 *  courante. « Valider l'habitat » l'ajoute à la station, enregistrée au fil de l'eau. */
class HabitatOccHabViewModel(
    private val services: ServicesGeoNature,
    private val sessionVm: SessionOccHabViewModel,
    habitatId: String?,
) : ViewModel() {
    private val session: SessionOccHab get() = sessionVm.session
    private val config = services.config
    private val existant = session.station.habitats.firstOrNull { it.id == habitatId }
    private val nouveau = existant == null

    private fun visible(cle: String) = config.occhabChampVisible(cle)

    // ─── Habitat HABREF : autocomplétion du cache OccHab, sinon du serveur ───

    /** Le champ montre le nom cité de l'habitat édité, comme le web. */
    var texteHabitat by mutableStateOf(existant?.nomCite?.takeIf { it.isNotBlank() } ?: existant?.habitatLabel.orEmpty())
        private set
    var cdHab by mutableStateOf(existant?.cdHab?.takeIf { it > 0 })
        private set
    val resultats = mutableStateListOf<HabitatSuggestion>()
    var resultatsDeroules by mutableStateOf(false)

    /** La dernière recherche n'a rien trouvé : « Aucun élément trouvé » sous le champ, comme le
     *  web, qui ne le dit qu'à partir de 3 caractères, que le champ ait le focus ou non. */
    var aucunResultat by mutableStateOf(false)
        private set

    val aucunResultatAffiche: Boolean get() = aucunResultat && texteHabitat.length >= 3
    private var habitatChoisi = false
    private var tache: Job? = null

    /** Frappe : l'habitat choisi est oublié ; recherche 400 ms après la dernière frappe, dès 2
     *  caractères, 20 résultats, comme l'autocomplétion du web. */
    fun saisirHabitat(texte: String) {
        if (texte == texteHabitat) return
        texteHabitat = texte
        cdHab = null
        tache?.cancel()
        aucunResultat = false
        resultatsDeroules = resultats.isNotEmpty()
        if (texte.length < 2) return
        tache = viewModelScope.launch {
            delay(400)
            val trouves = services.habitats.rechercher(texte, 20, occhab = true)
            resultats.clear()
            resultats.addAll(trouves)
            aucunResultat = trouves.isEmpty()
            resultatsDeroules = true
        }
    }

    /** Re-toucher un habitat déjà choisi vide le champ, pour en chercher un autre — même geste que
     *  le champ JDD ; la liste ne se rouvre pas (`setText("", false)`). */
    fun toucherHabitat() {
        if (cdHab == null) return
        tache?.cancel()
        texteHabitat = ""
        cdHab = null
    }

    fun choisirHabitat(h: HabitatSuggestion) {
        cdHab = h.cdHab
        texteHabitat = h.libelle
        habitatChoisi = true
        resultatsDeroules = false
    }

    // ─── Les autres champs, selon OCCHAB.formConfig ───

    private fun nomenclature(d: NomenclatureHabitat, existante: Int?): ChampNomenclatureHabitat? =
        if (!visible(d.cle)) null
        else ChampNomenclatureHabitat(
            d,
            ListeNomenclatureOccHab(services.nomenclatures.get(d.type), HabitatOccHab.avecEntreeVide(d)),
            HabitatOccHab.valeurInitiale(existante, d.type, nouveau, config),
        )

    val interet = nomenclature(HabitatOccHab.INTERET, existant?.idNomInteretCommunautaire)
    val determinateurVisible = visible("determiner")
    var determinateur by mutableStateOf(existant?.determiner.orEmpty())
    val typeDetermination = nomenclature(HabitatOccHab.TYPE_DETERMINATION, existant?.idNomTypeDetermination)
    val technique = nomenclature(HabitatOccHab.TECHNIQUE, existant?.idNomTechniqueCollecte)
    val precisionVisible = visible("technical_precision")
    var precision by mutableStateOf(existant?.precisionTechnique.orEmpty())
    val recouvrementVisible = visible("recovery_percentage")
    var recouvrement by mutableStateOf(HabitatOccHab.texteRecouvrement(existant?.recouvrement))
    val abondance = nomenclature(HabitatOccHab.ABONDANCE, existant?.idNomAbondance)

    /** La section ANA-EVAL : seulement quand l'habitat édité porte un bloc (habitat lu du serveur). */
    val anaEval: EtatSectionAnaEval? = EditionAnaEval.habitat(existant?.anaEvalJson)?.let(::EtatSectionAnaEval)

    /** Les valeurs saisissables, pour la garde de sortie. */
    private fun valeurs(): List<Any?> = listOf(
        texteHabitat, cdHab, determinateur, precision, recouvrement,
        interet?.position, typeDetermination?.position, technique?.position, abondance?.position, anaEval?.json(),
    )

    private val initiales = valeurs()

    /** L'écran a changé depuis son ouverture. */
    fun modifie(): Boolean = valeurs() != initiales

    /** La valeur d'une liste : celle retenue si le champ est affiché (l'entrée vide la vide), sinon
     *  celle de l'habitat édité, ou pour un nouvel habitat le défaut que le web pose aussi sur un
     *  champ masqué. */
    private fun choisie(champ: ChampNomenclatureHabitat?, d: NomenclatureHabitat, existante: Int?): Int? =
        champ?.valeur ?: if (champ != null) null else HabitatOccHab.valeurInitiale(existante, d.type, nouveau, config)

    private val idTechnique: Int? get() = choisie(technique, HabitatOccHab.TECHNIQUE, existant?.idNomTechniqueCollecte)

    /** Précision requise : technique de collecte = « Autre, préciser », la précision affichée ou
     *  non (masquée, elle reste vide et le web bloque). */
    val precisionRequise: Boolean
        get() = HabitatOccHab.techniqueEstAutre(idTechnique, services.nomenclatures.get(HabitatOccHab.TECHNIQUE.type))

    /** Les champs requis du web encore vides, marqués de la barre rouge : l'habitat HABREF, la
     *  technique de collecte affichée, et sa précision pour « Autre, préciser ». */
    val habitatManquant: Boolean get() = (cdHab ?: 0) <= 0
    val techniqueManquante: Boolean get() = technique != null && idTechnique == null
    val precisionManquante: Boolean get() = precisionVisible && precisionRequise && precision.isBlank()

    /** « Valider cet habitat » actif quand les champs requis sont remplis. */
    val peutValider: Boolean
        get() = HabitatOccHab.peutValider(cdHab, idTechnique, precisionRequise, precision.takeIf { precisionVisible })

    /** Mort du process pendant la saisie (session reconstruite VIERGE) : valider un habitat créerait
     *  une station fantôme (0,0) envoyable — retour à l'accueil (audit 2026-08-27). */
    fun ouverture() {
        if (session.station.geometrieDefinie()) return
        _evenements.trySend(EvenementHabitatOccHab.Message(StationOccHab.SAISIE_INTERROMPUE, longue = true))
        _evenements.trySend(EvenementHabitatOccHab.Accueil)
    }

    /** Écriture du store échouée (disque plein ?) : on RESTE sur l'écran. */
    var echecEcriture by mutableStateOf(false)
        private set

    private val _evenements = Channel<EvenementHabitatOccHab>(Channel.BUFFERED)
    val evenements = _evenements.receiveAsFlow()

    /** « Valider l'habitat » : gardes MIROIR du bouton grisé, puis l'habitat ajouté ou mis à jour
     *  sur la station, et la station enregistrée au fil de l'eau. */
    fun valider() {
        val precisionSaisie = precision.takeIf { precisionVisible }
        HabitatOccHab.manque(cdHab, idTechnique, precisionRequise, precisionSaisie)?.let {
            _evenements.trySend(EvenementHabitatOccHab.Message(it))
            return
        }
        val habitat = HabitatOccHab.habitat(
            existant,
            SaisiesHabitat(
                cdHab = cdHab,
                libelle = texteHabitat,
                determinateur = determinateur.takeIf { determinateurVisible },
                precision = precisionSaisie,
                recouvrement = recouvrement.takeIf { recouvrementVisible },
                interet = choisie(interet, HabitatOccHab.INTERET, existant?.idNomInteretCommunautaire),
                typeDetermination = choisie(typeDetermination, HabitatOccHab.TYPE_DETERMINATION, existant?.idNomTypeDetermination),
                technique = idTechnique,
                abondance = choisie(abondance, HabitatOccHab.ABONDANCE, existant?.idNomAbondance),
                anaEval = anaEval?.let { e -> { e.json() } },
                choisiDansLaListe = habitatChoisi,
            ),
        )
        session.ajouterOuMajHabitat(habitat)
        val station = session.stationAEnregistrer()
        val saisieId = session.saisieId
        viewModelScope.launch {
            // Écriture disque échouée : rester sur l'écran — naviguer laisserait croire que l'habitat
            // est enregistré alors qu'il ne vit qu'en mémoire (audit 2026-08-23).
            if (!services.occHabStore.upsertStation(saisieId, station)) {
                echecEcriture = true
                return@launch
            }
            _evenements.send(EvenementHabitatOccHab.Retour)
        }
    }

    fun fermerEchecEcriture() {
        echecEcriture = false
    }
}

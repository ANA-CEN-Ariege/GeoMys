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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/DetailsReleveDialog.kt) : le
// dialogue « Détails du relevé », ses champs gardés dans un état ([EtatDetailsReleve]) au lieu de ses vues ; les
// règles (listes, dates, résultat) sont dans occtax/DetailsReleveFormulaire. Depuis le 21 octobre, un écran
// plein, comme la caractérisation et le dénombrement, et non plus un dialogue. Sous iOS, les sections des
// détails d'une station OccHab (demande du 20 octobre), et non plus l'allure de DetailsReleveView de
// GeoMys-IOS (UI/Saisie/DetailsReleveView.swift).

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import fr.ariegenature.geomys.model.HabitatSuggestion
import fr.ariegenature.geomys.occtax.ChampNomenclature
import fr.ariegenature.geomys.occtax.DetailsReleveFormulaire
import fr.ariegenature.geomys.occtax.DetailsReleveResult
import fr.ariegenature.geomys.occtax.DonneesDetailsReleve
import fr.ariegenature.geomys.occtax.FormulairesEspece
import fr.ariegenature.geomys.saisie.EtatChampsAdditionnels
import fr.ariegenature.geomys.store.GroupeTaxonomique
import fr.ariegenature.geomys.store.NomenclatureCache
import fr.ariegenature.geomys.store.OcctaxFieldsConfig
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.store.champFormVisible
import fr.ariegenature.geomys.ui.composants.BoutonCadreIos
import fr.ariegenature.geomys.ui.composants.BoutonIos
import fr.ariegenature.geomys.ui.composants.CaseDate
import fr.ariegenature.geomys.ui.composants.ChampDateHeure
import fr.ariegenature.geomys.ui.composants.ChampJour
import fr.ariegenature.geomys.ui.composants.ChampTexteFormulaire
import fr.ariegenature.geomys.ui.composants.ClavierNombresEtPonctuation
import fr.ariegenature.geomys.ui.composants.ClavierNumeriqueSigne
import fr.ariegenature.geomys.ui.composants.DeroulantAndroid
import fr.ariegenature.geomys.ui.composants.FOND_LIGNE_FEUILLE_IOS
import fr.ariegenature.geomys.ui.composants.INVITE_IOS
import fr.ariegenature.geomys.ui.composants.IndicateurActivite
import fr.ariegenature.geomys.ui.composants.LigneChoixIos
import fr.ariegenature.geomys.ui.composants.LigneIos
import fr.ariegenature.geomys.ui.composants.LigneListeAndroid
import fr.ariegenature.geomys.ui.composants.LigneNombreIos
import fr.ariegenature.geomys.ui.composants.LigneTexteIos
import fr.ariegenature.geomys.ui.composants.LigneTexteSeulIos
import fr.ariegenature.geomys.ui.composants.LignesObservateursIos
import fr.ariegenature.geomys.ui.composants.LignesRechercheIos
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.ObservateursAndroid
import fr.ariegenature.geomys.ui.composants.PopupListeAndroid
import fr.ariegenature.geomys.ui.composants.SectionIos
import fr.ariegenature.geomys.ui.composants.SeparateurIos
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.barreChampObligatoire
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.styleSaisieIos
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.util.debutDuJour
import fr.ariegenature.geomys.util.maintenantMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Les saisies de l'écran « Détails du relevé », ouvert sur [donnees]. Un état neuf à chaque
 *  ouverture : le retour n'écrit rien. [chercherHabitats] : la recherche HABREF, lancée dans
 *  [portee]. */
class EtatDetailsReleve(
    val donnees: DonneesDetailsReleve,
    private val nomenclatures: NomenclatureCache,
    private val portee: CoroutineScope,
    /** Le TaxRef local, qui propose les taxons des champs additionnels `taxonomy`. */
    private val taxRef: TaxRefCache? = null,
    private val chercherHabitats: suspend (String) -> List<HabitatSuggestion>,
) {
    // ── Jeu de données ──

    /** Le jeu de données choisi (id, libellé) ; au départ la sélection portée, avec le meilleur
     *  libellé connu (celui de la liste, sinon le nom du jeu de la configuration, sinon l'id). */
    var dataset by mutableStateOf(
        donnees.idDataset?.let { id ->
            id to (donnees.datasets.firstOrNull { it.first == id }?.second ?: donnees.nomDataset?.takeIf { it.isNotBlank() } ?: id.toString())
        },
    )
        private set

    /** Un autre jeu de données : ses champs additionnels remplacent ceux de l'ancien, comme sur le
     *  web, et les champs communs aux deux gardent leur valeur. */
    fun choisirDataset(position: Int) {
        val choisi = donnees.datasets.getOrNull(position) ?: return
        if (choisi.first != dataset?.first) {
            additionnels = etatAdditionnels(choisi.first, donnees.additionnels + additionnels?.collecter().orEmpty())
        }
        dataset = choisi
    }

    /** La croix de la liste des jeux de données du web. */
    fun effacerDataset() {
        dataset = null
    }

    // ── Observateurs : la liste, ou un texte libre quand le serveur le demande
    // (`OCCTAX.observers_txt`) ──

    val observateursEnTexte = donnees.observateursEnTexte
    var observateursTexte by mutableStateOf(donnees.observateursTexte)

    /** Les observateurs choisis, dans l'ordre d'ajout, un seul par id. */
    val observateurs = mutableStateListOf<Pair<Int, String>>().apply {
        donnees.idsObservateurs.forEachIndexed { i, id ->
            val nom = donnees.observateurs.firstOrNull { it.first == id }?.second
                ?: donnees.nomsObservateurs.getOrNull(i)?.takeIf { it.isNotBlank() }
                ?: id.toString()
            poser(this, id to nom)
        }
    }

    /** Aucun observateur (ou un texte vide) : obligatoire sur le web, le champ porte la barre rouge. */
    val observateursManquants: Boolean get() = if (observateursEnTexte) observateursTexte.isBlank() else observateurs.isEmpty()

    /** Ajoute l'observateur proposé n° [position] ; déjà choisi, il garde son rang. */
    fun ajouterObservateur(position: Int) {
        donnees.observateurs.getOrNull(position)?.let { poser(observateurs, it) }
    }

    fun retirerObservateur(id: Int) {
        observateurs.removeAll { it.first == id }
    }

    // ── Dates et heures, comme le web : le « + » révèle la date de fin et les heures, facultatives ;
    // choisir une date (ou taper une heure) recopie l'autre tant qu'on n'y a pas touché
    // (synchronizeMax / synchronizeMin) ──

    // Sans date, celle du jour, sauf si le serveur la veut vide (`DATE_FORM_WITH_TODAY` à faux) : elle
    // est alors à choisir, avec la barre rouge, comme sur le web.
    var debut by mutableStateOf(donnees.dateDebut ?: maintenantMillis().takeIf { donnees.dateDuJour })
        private set
    var fin by mutableStateOf(donnees.dateFin ?: debut)
        private set

    /** La date de début reste à choisir : la barre rouge d'un champ obligatoire vide. */
    val dateManquante: Boolean get() = debut == null
    var heureDebut by mutableStateOf(donnees.heureDebut.orEmpty())
        private set
    var heureFin by mutableStateOf(donnees.heureFin.orEmpty())
        private set

    /** `form_fields.date_min` : la date de début se montre ; masquée, elle reste exigée et prise par
     *  défaut, comme sur le web. */
    val dateDebutVisible = champFormVisible(donnees.formFieldsJson, "date_min")

    /** Le « + » est-il ouvert ? D'emblée si le relevé porte une date de fin distincte ou une heure. */
    var plusOuvert by mutableStateOf(
        (donnees.dateFin != null && donnees.dateFin != donnees.dateDebut) || donnees.heureDebut != null || donnees.heureFin != null,
    )
        private set

    private var debutTouche = false
    private var finTouchee = donnees.dateFin != null && donnees.dateFin != donnees.dateDebut
    private var heureDebutTouchee = donnees.heureDebut != null
    private var heureFinTouchee = donnees.heureFin != null

    /** Le début a-t-il vraiment changé ? (saisie a posteriori) */
    private var dateModifiee = false

    fun basculerPlus() {
        plusOuvert = !plusOuvert
    }

    fun changerDebut(millis: Long) {
        if (millis != debut) dateModifiee = true
        debut = millis
        debutTouche = true
        if (!finTouchee) fin = millis
    }

    fun changerFin(millis: Long) {
        fin = millis
        finTouchee = true
        if (!debutTouche && millis != debut) {
            debut = millis
            dateModifiee = true
        }
    }

    fun saisirHeureDebut(texte: String) {
        heureDebut = texte
        heureDebutTouchee = true
        if (!heureFinTouchee) heureFin = texte
    }

    fun saisirHeureFin(texte: String) {
        heureFin = texte
        heureFinTouchee = true
        if (!heureDebutTouchee) heureDebut = texte
    }

    // ── Nomenclatures du relevé, champs simples, champs additionnels ──

    /** TYP_GRP, NAT_OBJ_GEO et TECHNIQUE_OBS que le serveur montre, sans filtre de groupe. */
    val nomenclaturesReleve: List<ChampNomenclature> = DetailsReleveFormulaire.NOMENCLATURES
        .filter { (_, cle, _) -> champFormVisible(donnees.formFieldsJson, cle) }
        .mapNotNull { (code, _, _) -> OcctaxFieldsConfig.parCode[code] }
        .map { FormulairesEspece.champ(it, nomenclatures, GroupeTaxonomique.AUCUN, filtresActifs = false) }

    /** Position choisie de chaque nomenclature, par code ; au départ la valeur portée, sinon le
     *  défaut du serveur. */
    val positions = mutableStateMapOf<String, Int>().apply {
        nomenclaturesReleve.forEach { c -> put(c.champ.code, FormulairesEspece.positionInitiale(c, valeurReleve(c.champ.code), nomenclatures)) }
    }

    /** Les positions à l'ouverture ([FormulairesEspece.valeurRetenue]). */
    private val positionsInitiales: Map<String, Int> = positions.toMap()

    /** La valeur portée d'une nomenclature du relevé. */
    private fun valeurReleve(code: String): String {
        val cleExtra = DetailsReleveFormulaire.NOMENCLATURES.first { it.first == code }.third
        return if (cleExtra == null) donnees.typGrp else donnees.champsExtra[cleExtra].orEmpty()
    }

    /** Les champs simples montrés : (clé, libellé, numérique ?). */
    val champsSimples = DetailsReleveFormulaire.CHAMPS_SIMPLES.filter {
        champFormVisible(donnees.formFieldsJson, DetailsReleveFormulaire.cleVisibilite(it.first))
    }

    /** Le champ simple [cle], s'il est montré. */
    fun simple(cle: String): Triple<String, String, Boolean>? = champsSimples.firstOrNull { it.first == cle }

    /** La nomenclature du relevé [code], si elle est montrée. */
    fun nomenclature(code: String): ChampNomenclature? = nomenclaturesReleve.firstOrNull { it.champ.code == code }

    /** Texte de chaque champ simple montré, par clé. */
    val textesSimples = mutableStateMapOf<String, String>().apply {
        champsSimples.forEach { put(it.first, donnees.champsExtra[it.first].orEmpty()) }
    }

    /** Les champs additionnels de relevé du jeu [idDataset], s'il y en a, sur [valeurs]. */
    private fun etatAdditionnels(idDataset: Int?, valeurs: Map<String, String>): EtatChampsAdditionnels? =
        donnees.defs(idDataset).takeIf { it.isNotEmpty() }?.let { EtatChampsAdditionnels(it, valeurs, donnees.observateurs, taxRef) }

    /** Champs additionnels de relevé du jeu de données choisi, s'il y en a. */
    var additionnels: EtatChampsAdditionnels? by mutableStateOf(etatAdditionnels(dataset?.first, donnees.additionnels))
        private set

    // ── Habitat ──

    val habitatVisible = champFormVisible(donnees.formFieldsJson, "habitat")

    /** Le texte du champ habitat : le libellé de l'habitat choisi, ou la recherche en cours. */
    var habitatTexte by mutableStateOf(donnees.habitatLabel.orEmpty())
        private set
    private var cdHab: Int? = donnees.cdHab

    /** Les habitats trouvés par la dernière recherche. */
    val habitats = mutableStateListOf<HabitatSuggestion>()

    /** La liste des habitats est-elle déroulée (le champ ayant le focus) ? */
    var habitatsDeroules by mutableStateOf(false)
    var rechercheHabitat by mutableStateOf(false)
        private set
    private var tacheHabitat: Job? = null

    /** Frappe : l'habitat choisi est oublié jusqu'au prochain choix ; recherche 400 ms après la
     *  dernière frappe, dès 2 caractères, comme le `pnx-autocomplete` du web. La liste déjà chargée
     *  reste proposée entre-temps. */
    fun saisirHabitat(texte: String) {
        if (texte == habitatTexte) return
        habitatTexte = texte
        cdHab = null
        tacheHabitat?.cancel()
        habitatsDeroules = habitats.isNotEmpty()
        if (texte.length < 2) {
            rechercheHabitat = false
            aucunHabitat = false
            return
        }
        rechercherHabitats(texte, attente = 400)
    }

    /** Toucher le champ : la liste chargée, sinon une recherche sur le texte (l'habitat choisi
     *  reste). */
    fun toucherHabitat() {
        if (habitats.isNotEmpty()) {
            habitatsDeroules = true
            return
        }
        val terme = habitatTexte
        if (terme.length < 2) return
        tacheHabitat?.cancel()
        rechercherHabitats(terme, attente = 0)
    }

    /** La dernière recherche n'a rien trouvé : « Aucun élément trouvé » sous le champ, comme le web,
     *  qui ne le dit qu'à partir de 3 caractères. */
    var aucunHabitat by mutableStateOf(false)
        private set

    val aucunHabitatAffiche: Boolean get() = aucunHabitat && !rechercheHabitat && habitatTexte.length >= 3

    private fun rechercherHabitats(terme: String, attente: Long) {
        rechercheHabitat = true
        aucunHabitat = false
        tacheHabitat = portee.launch {
            if (attente > 0) delay(attente)
            val resultats = chercherHabitats(terme)
            habitats.clear()
            habitats.addAll(resultats)
            habitatsDeroules = habitats.isNotEmpty()
            aucunHabitat = resultats.isEmpty()
            rechercheHabitat = false
        }
    }

    fun choisirHabitat(habitat: HabitatSuggestion) {
        cdHab = habitat.cdHab
        habitatTexte = habitat.libelle
        habitatsDeroules = false
    }

    // ── Commentaire ──

    val commentaireVisible = champFormVisible(donnees.formFieldsJson, "comment_releve")
    var commentaire by mutableStateOf(donnees.commentaire)

    // ── Garde de sortie ──

    /** Ce que l'écran saisit, pour savoir s'il a changé. */
    private fun saisies(): List<Any?> = listOf(
        dataset, observateursTexte, observateurs.toList(), debut, fin, heureDebut, heureFin,
        positions.toMap(), textesSimples.toMap(), additionnels?.collecter(), habitatTexte, cdHab, commentaire,
    )

    private val initiales = saisies()

    /** L'écran a changé depuis son ouverture : le quitter sans valider le demande d'abord. */
    fun modifie(): Boolean = saisies() != initiales

    /** « Valider » : les saisies relues, ou le message d'un contrôle du web ou d'un champ
     *  obligatoire vide (le dialogue reste ouvert). */
    fun valider(): Pair<DetailsReleveResult?, String?> {
        val observateursPresents = !observateursManquants
        DetailsReleveFormulaire.refus(
            debut, fin, heureDebut, heureFin, observateursPresents, observateursEnTexte, textesSimples.toMap(),
            datasetChoisi = dataset != null,
        )?.let { return null to it }
        additionnels?.refus()?.let { return null to it }
        val valeurs = nomenclaturesReleve.associate { c ->
            val code = c.champ.code
            code to FormulairesEspece.valeurRetenue(c, valeurReleve(code), nomenclatures.defautPour(code), positionsInitiales[code] ?: 0, positions[code] ?: 0)
        }
        // Le contrôle a refusé une date de début vide.
        val dateDebut = checkNotNull(debut)
        val resultat = DetailsReleveFormulaire.resultat(
            donnees,
            dataset,
            observateurs.toList(),
            additionnels?.collecter(),
            commentaire,
            cdHab,
            habitatTexte,
            textesSimples.toMap(),
            valeurs,
            dateDebut to (fin ?: dateDebut),
            dateModifiee,
            heures = heureDebut to heureFin,
            observateursTexte = observateursTexte.takeIf { observateursEnTexte },
        )
        return resultat to null
    }

    /** Le dialogue se ferme : plus de recherche d'habitat. */
    fun fermer() {
        tacheHabitat?.cancel()
    }

    private companion object {
        /** `LinkedHashMap.put` : remplace sur place un id déjà présent, sinon ajoute à la fin. */
        fun poser(liste: MutableList<Pair<Int, String>>, observateur: Pair<Int, String>) {
            val i = liste.indexOfFirst { it.first == observateur.first }
            if (i >= 0) liste[i] = observateur else liste.add(observateur)
        }
    }
}

/** « Détails du relevé » (ou « Détails des relevés ») ouvert sur [etat], fermé si null : un écran
 *  plein, comme la caractérisation et le dénombrement (demande du 21 octobre). Le bandeau du flux
 *  ([typeSaisieLabel], la maison [onAccueil]), le titre, les champs qui défilent, la coche de
 *  validation en haut à droite. [onValider] reçoit les saisies acceptées ; un champ obligatoire vide
 *  garde l'écran ouvert, avec un message. Le retour (bouton d'Android, balayage depuis le bord sous
 *  iOS) l'annule ([onAnnuler]), après « Quitter sans enregistrer ? » s'il a été modifié. */
@Composable
fun EcranDetailsReleve(
    etat: EtatDetailsReleve?,
    typeSaisieLabel: String,
    onAccueil: () -> Unit,
    onAnnuler: () -> Unit,
    onValider: (DetailsReleveResult) -> Unit,
) {
    // Le dernier état reste dessiné pendant la fermeture animée.
    val dernier = remember { arrayOfNulls<EtatDetailsReleve>(1) }
    if (etat != null) dernier[0] = etat
    val e = etat ?: dernier[0] ?: return
    val toasts = LocalToasts.current
    FormulaireEnEcranPlein(
        visible = etat != null,
        titre = e.donnees.titre,
        typeSaisieLabel = typeSaisieLabel,
        titreGarde = "Détails non enregistrés",
        modifie = e::modifie,
        onAccueil = onAccueil,
        onAnnuler = onAnnuler,
        onValider = {
            val (resultat, message) = e.valider()
            if (message != null) toasts.afficher(message, longue = true) else if (resultat != null) onValider(resultat)
        },
    ) {
        if (estIos) ContenuIos(e) else ContenuAndroid(e)
    }
}

// ─── Android : les vues de DetailsReleveDialog ───

/** Les champs, dans l'ordre du formulaire du relevé du web (releve.component.html) ; l'écran
 *  donne les marges. */
@Composable
private fun ContenuAndroid(e: EtatDetailsReleve) {
    val d = e.donnees
    Column(Modifier.fillMaxWidth()) {
        e.simple("precision")?.let { SimpleAndroid(e, it, DetailsReleveFormulaire.INVITE_PRECISION) }
        e.simple("place_name")?.let { SimpleAndroid(e, it) }

        // Observateurs et jeu de données sont obligatoires : vides, la barre rouge du web.
        TitreAndroid(DetailsReleveFormulaire.LIBELLE_OBSERVATEURS)
        Column(Modifier.fillMaxWidth().barreChampObligatoire(e.observateursManquants)) {
            if (e.observateursEnTexte) {
                ChampTexteFormulaire(
                    e.observateursTexte,
                    { e.observateursTexte = it },
                    clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    compactAndroid = true,
                )
            } else {
                ObservateursAndroid(DetailsReleveFormulaire.INVITE_OBSERVATEURS, d.observateurs, e.observateurs, e::ajouterObservateur, e::retirerObservateur)
            }
        }

        // Jeu de données : liste déroulante, ou ligne en lecture seule sans cache.
        if (d.datasets.isEmpty()) {
            LigneLectureAndroid(DetailsReleveFormulaire.LIBELLE_JDD, e.dataset?.second ?: "—")
        } else {
            TitreAndroid(DetailsReleveFormulaire.LIBELLE_JDD)
            Box(Modifier.fillMaxWidth().barreChampObligatoire(e.dataset == null)) {
                DeroulantAndroid(
                    DetailsReleveFormulaire.INVITE_JDD, d.datasets.map { it.second }, e.dataset?.second.orEmpty(),
                    restaurer = true, onChoix = e::choisirDataset, onEffacer = e::effacerDataset,
                )
            }
        }

        if (e.dateDebutVisible) TitreAndroid(DetailsReleveFormulaire.LIBELLE_DATE_DEBUT)
        Row(Modifier.padding(top = if (e.dateDebutVisible) 0.dp else 8.dp, bottom = 8.dp)) {
            if (e.dateDebutVisible) {
                ChampDateReleve(e.debut, e::changerDebut)
                Spacer(Modifier.width(8.dp))
            }
            CaseDate(if (e.plusOuvert) "−" else "＋", description = if (e.plusOuvert) "Masquer la date de fin et les heures" else "Date de fin et heures", onClick = e::basculerPlus)
        }
        if (e.plusOuvert) {
            if (d.avecFin) {
                TitreAndroid(DetailsReleveFormulaire.LIBELLE_DATE_FIN)
                Row(Modifier.padding(bottom = 8.dp)) { ChampDateReleve(e.fin, e::changerFin) }
            }
            if (d.heures) {
                TitreAndroid(DetailsReleveFormulaire.LIBELLE_HEURE_DEBUT)
                ChampHeureAndroid(e.heureDebut, e::saisirHeureDebut)
            }
            if (d.heureFinVisible) {
                TitreAndroid(DetailsReleveFormulaire.LIBELLE_HEURE_FIN)
                ChampHeureAndroid(e.heureFin, e::saisirHeureFin)
            }
        }

        for (cle in listOf("altitude_min", "altitude_max", "depth_min", "depth_max")) e.simple(cle)?.let { SimpleAndroid(e, it) }

        e.nomenclature("TYP_GRP")?.let { c -> ChampNomenclatureVue(c, e.positions[c.champ.code] ?: 0, { e.positions[c.champ.code] = it }) }
        e.simple("grp_method")?.let { SimpleAndroid(e, it) }
        for (code in listOf("NAT_OBJ_GEO", "TECHNIQUE_OBS")) {
            e.nomenclature(code)?.let { c -> ChampNomenclatureVue(c, e.positions[c.champ.code] ?: 0, { e.positions[c.champ.code] = it }) }
        }

        if (e.habitatVisible) {
            TitreAndroid(DetailsReleveFormulaire.LIBELLE_HABITAT)
            ChampHabitatAndroid(e)
            // « Aucun élément trouvé » de l'autocomplétion du web, sous le champ.
            if (e.aucunHabitatAffiche) {
                Text(DetailsReleveFormulaire.AUCUN_HABITAT, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }

        if (e.commentaireVisible) {
            TitreAndroid(DetailsReleveFormulaire.LIBELLE_COMMENTAIRE)
            ChampCommentaire(e)
        }

        // Les champs additionnels en dernier, sans titre, comme le web.
        e.additionnels?.takeIf { it.defsAffiches.isNotEmpty() }?.let { ChampsAdditionnelsVue(it) }
    }
}

/** Un champ simple sous son titre. */
@Composable
private fun SimpleAndroid(e: EtatDetailsReleve, champ: Triple<String, String, Boolean>, indication: String? = null) {
    TitreAndroid(champ.second)
    ChampSimple(e, champ.first, champ.third, indication)
}

/** Une heure du relevé : texte libre « hh:mm », comme le web. */
@Composable
private fun ChampHeureAndroid(valeur: String, onChange: (String) -> Unit) {
    // Une heure mal formée porte la barre rouge, comme sur le web.
    Box(Modifier.fillMaxWidth().barreChampObligatoire(DetailsReleveFormulaire.heureMalFormee(valeur))) {
        ChampTexteFormulaire(
            valeur,
            // Le pavé numérique n'a pas de « : » : « 1430 » ou « 14.30 » se lisent 14:30 (normaliserHeure).
            { onChange(it.filter { c -> c.isDigit() || c == ':' || c == '.' || c == 'h' || c == 'H' }) },
            clavier = KeyboardOptions(keyboardType = KeyboardType.Number),
            compactAndroid = true,
            indication = DetailsReleveFormulaire.INVITE_HEURE,
        )
    }
}

/** Titre d'un champ : 12 sp colorOnSurfaceVariant, 8 dp au-dessus. */
@Composable
private fun TitreAndroid(texte: String) {
    Text(texte, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
}

/** Une ligne en lecture seule : libellé de 12 sp, valeur de 15 sp, 8 dp dessous. */
@Composable
private fun LigneLectureAndroid(libelle: String, valeur: String) {
    Text(libelle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(valeur, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
}

/** Habitat : MaterialAutoCompleteTextView souligné, « Rechercher un habitat… » ; la liste des
 *  résultats se déroule sous le champ tant qu'il a le focus. Toucher le champ quand il a déjà le
 *  focus la rouvre (ou relance la recherche). */
@Composable
private fun ChampHabitatAndroid(e: EtatDetailsReleve) {
    val focus = LocalFocusManager.current
    val interactions = remember { MutableInteractionSource() }
    val focalise by interactions.collectIsFocusedAsState()
    val focaliseActuel by rememberUpdatedState(focalise)
    var ancre by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(interactions) {
        // Le premier toucher ne fait que donner le focus (clic d'une EditText).
        var avaitFocus = false
        interactions.interactions.collect {
            when (it) {
                is PressInteraction.Press -> avaitFocus = focaliseActuel
                is PressInteraction.Release -> if (avaitFocus) e.toucherHabitat()
            }
        }
    }
    Box(
        Modifier
            .onGloballyPositioned { ancre = Rect(it.positionInWindow(), it.size.toSize()) }
            .onFocusChanged { if (!it.hasFocus) e.habitatsDeroules = false },
    ) {
        ChampTexteFormulaire(
            e.habitatTexte,
            e::saisirHabitat,
            compactAndroid = true,
            indication = DetailsReleveFormulaire.INVITE_HABITAT,
            interactions = interactions,
        )
        val zone = ancre
        if (focalise && e.habitatsDeroules && e.habitats.isNotEmpty() && zone != null) {
            PopupListeAndroid(zone, e.habitats.toList(), onFermer = { e.habitatsDeroules = false }) { h ->
                LigneListeAndroid(h.libelle) {
                    e.choisirHabitat(h)
                    focus.clearFocus()
                }
            }
        }
    }
}

/** Champ simple : EditText d'une ligne ; un nombre entier signé si [numerique], comme
 *  l'`<input type="number">` du web. */
@Composable
private fun ChampSimple(e: EtatDetailsReleve, cle: String, numerique: Boolean, indication: String? = null) {
    ClavierNumeriqueSigne {
        ChampTexteFormulaire(
            e.textesSimples[cle].orEmpty(),
            { e.textesSimples[cle] = if (numerique) entierSigne(it) else it },
            clavier = if (numerique) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            compactAndroid = true,
            indication = indication,
        )
    }
}

/** Un entier tapé, signe compris. */
private fun entierSigne(texte: String): String = texte.filterIndexed { i, c -> c.isDigit() || (c == '-' && i == 0) }

/** Commentaire : deux lignes au moins. */
@Composable
private fun ChampCommentaire(e: EtatDetailsReleve) {
    ChampTexteFormulaire(
        e.commentaire,
        { e.commentaire = it },
        lignesMin = 2,
        uneLigne = false,
        clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        compactAndroid = true,
    )
}

// ─── iOS : les sections des détails d'une station OccHab ───

@Composable
private fun ContenuIos(e: EtatDetailsReleve) {
    val d = e.donnees
    val chiffres = ClavierNombresEtPonctuation

    SimpleIos(e, "precision", DetailsReleveFormulaire.INVITE_PRECISION)
    SimpleIos(e, "place_name")

    // Observateurs et jeu de données sont obligatoires : vides, la barre rouge du web.
    SectionIos(DetailsReleveFormulaire.LIBELLE_OBSERVATEURS, barre = e.observateursManquants) {
        if (e.observateursEnTexte) {
            LigneTexteIos(e.observateursTexte, { e.observateursTexte = it }, "", clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        } else {
            LignesObservateursIos(DetailsReleveFormulaire.INVITE_OBSERVATEURS, d.observateurs, e.observateurs, e::ajouterObservateur, e::retirerObservateur)
        }
    }

    SectionIos(DetailsReleveFormulaire.LIBELLE_JDD, barre = d.datasets.isNotEmpty() && e.dataset == null) {
        if (d.datasets.isEmpty()) LigneTexteSeulIos(e.dataset?.second ?: "—")
        else LignesRechercheIos(DetailsReleveFormulaire.INVITE_JDD, d.datasets.map { it.second }, e.dataset?.second.orEmpty(), e::choisirDataset, e::effacerDataset)
    }

    val boutonPlus: @Composable () -> Unit = {
        BoutonCadreIos(
            if (e.plusOuvert) "minus" else "plus", CouleursGeoMys.jauneClair,
            if (e.plusOuvert) "Masquer la date de fin et les heures" else "Date de fin et heures", e::basculerPlus,
        )
    }
    if (e.dateDebutVisible) {
        SectionIos(DetailsReleveFormulaire.LIBELLE_DATE_DEBUT, barre = e.dateManquante) { LigneDateIos(e.debut, false, e::changerDebut, boutonPlus) }
    } else {
        boutonPlus()
    }
    if (e.plusOuvert) {
        if (d.avecFin) SectionIos(DetailsReleveFormulaire.LIBELLE_DATE_FIN) { LigneDateIos(e.fin, false, e::changerFin) {} }
        if (d.heures) {
            SectionIos(DetailsReleveFormulaire.LIBELLE_HEURE_DEBUT, barre = DetailsReleveFormulaire.heureMalFormee(e.heureDebut)) {
                LigneTexteIos(e.heureDebut, { e.saisirHeureDebut(it.filter { c -> c.isDigit() || c == ':' }) }, DetailsReleveFormulaire.INVITE_HEURE, clavier = chiffres)
            }
        }
        if (d.heureFinVisible) {
            SectionIos(DetailsReleveFormulaire.LIBELLE_HEURE_FIN, barre = DetailsReleveFormulaire.heureMalFormee(e.heureFin)) {
                LigneTexteIos(e.heureFin, { e.saisirHeureFin(it.filter { c -> c.isDigit() || c == ':' }) }, DetailsReleveFormulaire.INVITE_HEURE, clavier = chiffres)
            }
        }
    }

    for (cle in listOf("altitude_min", "altitude_max", "depth_min", "depth_max")) SimpleIos(e, cle)

    NomenclatureIos(e, "TYP_GRP")
    SimpleIos(e, "grp_method")
    NomenclatureIos(e, "NAT_OBJ_GEO")
    NomenclatureIos(e, "TECHNIQUE_OBS")

    if (e.habitatVisible) SectionIos(DetailsReleveFormulaire.LIBELLE_HABITAT) { LignesHabitatIos(e) }

    if (e.commentaireVisible) {
        SectionIos(DetailsReleveFormulaire.LIBELLE_COMMENTAIRE) {
            LigneTexteIos(
                e.commentaire,
                { e.commentaire = it },
                "",
                lignesMin = 2,
                lignesMax = 5,
                clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
    }

    // Les champs additionnels en dernier, sans titre, comme le web.
    e.additionnels?.takeIf { it.defsAffiches.isNotEmpty() }?.let {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) { ChampsAdditionnelsSectionsIos(it) }
    }
}

/** Un champ simple dans sa section ; un nombre entier signé si numérique. */
@Composable
private fun SimpleIos(e: EtatDetailsReleve, cle: String, indication: String = "—") {
    val (_, libelle, numerique) = e.simple(cle) ?: return
    SectionIos(libelle) {
        val texte = e.textesSimples[cle].orEmpty()
        if (numerique) {
            LigneNombreIos("", texte, { e.textesSimples[cle] = entierSigne(it) }, ClavierNombresEtPonctuation)
        } else {
            LigneTexteIos(texte, { e.textesSimples[cle] = it }, indication, clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        }
    }
}

/** Une nomenclature du relevé dans sa section, sans entrée vide, comme le web (cf.
 *  [ChampNomenclatureVue]). */
@Composable
private fun NomenclatureIos(e: EtatDetailsReleve, code: String) {
    val c = e.nomenclature(code) ?: return
    SectionIos(c.libelle) {
        LigneChoixIos("", c.libelles.drop(1), (e.positions[code] ?: 0) - 1, invite = c.libelle, onEffacer = { e.positions[code] = 0 }) {
            e.positions[code] = it + 1
        }
    }
}

/** Une date du relevé sur sa ligne : le sélecteur natif, peint du fond de la ligne, puis [bouton]
 *  (le « ＋ » ou le « ✕ » de la date de fin). Pas encore choisie ([millis] null) : « Choisir une
 *  date », qui pose le jour même et montre le sélecteur. */
@Composable
private fun LigneDateIos(millis: Long?, heures: Boolean, onChange: (Long) -> Unit, bouton: @Composable () -> Unit) {
    LigneIos {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (millis != null) {
                ChampDateHeure(millis, heures, onChange, Modifier.weight(1f), fondIos = FOND_LIGNE_FEUILLE_IOS)
            } else {
                BoutonIos({ onChange(debutDuJour()) }, Modifier.weight(1f), opaciteAppui = 0.6f) {
                    Text(CHOISIR_DATE, fontSize = 17.sp, color = SECONDAIRE_IOS)
                }
            }
            bouton()
        }
    }
}

/** Une date du relevé sous Android : la case du jour, ou, pas encore choisie, « — » à la barre rouge,
 *  qui ouvre le sélecteur. */
@Composable
private fun ChampDateReleve(millis: Long?, onChange: (Long) -> Unit) {
    if (millis != null) ChampDateHeure(millis, false, onChange)
    else ChampJour(null, onChange, Modifier.barreChampObligatoire(true))
}

/** L'invite d'une date de relevé pas encore choisie, sous iOS. */
internal const val CHOISIR_DATE = "Choisir une date"

/** Habitat : `AutocompleteField` au style système de HabitatPicker (loupe, champ nu, roue pendant
 *  la recherche) sur sa ligne, les résultats en lignes dessous tant que le champ a le focus. */
@Composable
private fun LignesHabitatIos(e: EtatDetailsReleve) {
    val focus = LocalFocusManager.current
    val interactions = remember { MutableInteractionSource() }
    val focalise by interactions.collectIsFocusedAsState()
    val focaliseActuel by rememberUpdatedState(focalise)
    LaunchedEffect(interactions) {
        var avaitFocus = false
        interactions.interactions.collect {
            when (it) {
                is PressInteraction.Press -> avaitFocus = focaliseActuel
                is PressInteraction.Release -> if (avaitFocus) e.toucherHabitat()
            }
        }
    }
    LigneIos {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SymboleSf("magnifyingglass", 17f, listOf(SECONDAIRE_IOS))
            BasicTextField(
                value = e.habitatTexte,
                onValueChange = e::saisirHabitat,
                singleLine = true,
                textStyle = styleSaisieIos,
                cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                interactionSource = interactions,
                modifier = Modifier.weight(1f).onFocusChanged { if (!it.isFocused) e.habitatsDeroules = false },
                decorationBox = { champ ->
                    Box {
                        if (e.habitatTexte.isEmpty()) Text(DetailsReleveFormulaire.INVITE_HABITAT, style = styleSaisieIos.copy(color = INVITE_IOS))
                        champ()
                    }
                },
            )
            if (e.rechercheHabitat) IndicateurActivite(Color.White, petitIos = true)
        }
    }
    if (e.aucunHabitatAffiche) {
        SeparateurIos(Modifier.padding(start = 16.dp))
        LigneTexteSeulIos(DetailsReleveFormulaire.AUCUN_HABITAT, couleur = CouleursGeoMys.blanc70)
    }
    if (focalise && e.habitatsDeroules && e.habitats.isNotEmpty()) {
        LazyColumn(Modifier.heightIn(max = 220.dp)) {
            items(e.habitats.toList()) { h ->
                Column {
                    SeparateurIos(Modifier.padding(start = 16.dp))
                    LigneIos(Modifier.clickable {
                        e.choisirHabitat(h)
                        focus.clearFocus()
                    }) {
                        Text(h.libelle, fontSize = 17.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

/** `.secondary` d'iOS en mode sombre (secondaryLabel). */
private val SECONDAIRE_IOS = Color(0x99EBEBF5)

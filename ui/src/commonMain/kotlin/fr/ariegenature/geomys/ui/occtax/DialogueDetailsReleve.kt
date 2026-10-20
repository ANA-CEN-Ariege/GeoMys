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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/DetailsReleveDialog.kt) : le
// dialogue « Détails du relevé », ses champs gardés dans un état ([EtatDetailsReleve]) au lieu de ses vues ; les
// règles (listes, dates, résultat) sont dans occtax/DetailsReleveFormulaire. Sous iOS, les sections des
// détails d'une station OccHab, dans la carte centrée des dialogues de détails (demande du 20 octobre), et non
// plus l'allure de DetailsReleveView de GeoMys-IOS (UI/Saisie/DetailsReleveView.swift).

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
import fr.ariegenature.geomys.store.NomenclatureCache
import fr.ariegenature.geomys.store.OcctaxFieldsConfig
import fr.ariegenature.geomys.store.champFormVisible
import fr.ariegenature.geomys.ui.composants.BoutonCadreIos
import fr.ariegenature.geomys.ui.composants.CaseDate
import fr.ariegenature.geomys.ui.composants.ChampDateHeure
import fr.ariegenature.geomys.ui.composants.ChampTexteFormulaire
import fr.ariegenature.geomys.ui.composants.DeroulantAndroid
import fr.ariegenature.geomys.ui.composants.DialogueFormulaire
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
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.styleSaisieIos
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.util.maintenantMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Les saisies du dialogue « Détails du relevé », ouvert sur [donnees]. Un état neuf à chaque
 *  ouverture : « Annuler » n'écrit rien. [chercherHabitats] : la recherche HABREF, lancée dans
 *  [portee]. */
class EtatDetailsReleve(
    val donnees: DonneesDetailsReleve,
    nomenclatures: NomenclatureCache,
    private val portee: CoroutineScope,
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

    fun choisirDataset(position: Int) {
        donnees.datasets.getOrNull(position)?.let { dataset = it }
    }

    // ── Observateurs ──

    /** Les observateurs choisis, dans l'ordre d'ajout, un seul par id. */
    val observateurs = mutableStateListOf<Pair<Int, String>>().apply {
        donnees.idsObservateurs.forEachIndexed { i, id ->
            val nom = donnees.observateurs.firstOrNull { it.first == id }?.second
                ?: donnees.nomsObservateurs.getOrNull(i)?.takeIf { it.isNotBlank() }
                ?: id.toString()
            poser(this, id to nom)
        }
    }

    /** Ajoute l'observateur proposé n° [position] ; déjà choisi, il garde son rang. */
    fun ajouterObservateur(position: Int) {
        donnees.observateurs.getOrNull(position)?.let { poser(observateurs, it) }
    }

    fun retirerObservateur(id: Int) {
        observateurs.removeAll { it.first == id }
    }

    // ── Dates ──

    var debut by mutableStateOf(donnees.dateDebut ?: maintenantMillis())
        private set
    var fin by mutableStateOf(donnees.dateFin ?: debut)
        private set

    /** La date de fin est-elle ajoutée (« ＋ ») ? */
    var finActive by mutableStateOf(DetailsReleveFormulaire.finActiveAuDepart(donnees))
        private set

    /** Le début a-t-il vraiment changé ? (saisie a posteriori) */
    private var dateModifiee = false

    fun changerDebut(millis: Long) {
        if (millis != debut) dateModifiee = true
        debut = millis
    }

    fun changerFin(millis: Long) {
        fin = millis
    }

    /** « ＋ » : la fin démarre sur le début. */
    fun ajouterFin() {
        fin = debut
        finActive = true
    }

    fun retirerFin() {
        finActive = false
    }

    // ── Nomenclatures du relevé, champs simples, champs additionnels ──

    /** TYP_GRP, NAT_OBJ_GEO et TECHNIQUE_OBS que le serveur montre, sans filtre de groupe. */
    val nomenclaturesReleve: List<ChampNomenclature> = DetailsReleveFormulaire.NOMENCLATURES
        .filter { (_, cle, _) -> champFormVisible(donnees.formFieldsJson, cle) }
        .mapNotNull { (code, _, _) -> OcctaxFieldsConfig.parCode[code] }
        .map { FormulairesEspece.champ(it, nomenclatures, emptySet(), "") }

    /** Position choisie de chaque nomenclature, par code ; au départ la valeur portée, sinon le
     *  défaut du serveur. */
    val positions = mutableStateMapOf<String, Int>().apply {
        nomenclaturesReleve.forEach { c ->
            val cleExtra = DetailsReleveFormulaire.NOMENCLATURES.first { it.first == c.champ.code }.third
            val valeur = if (cleExtra == null) donnees.typGrp else donnees.champsExtra[cleExtra].orEmpty()
            put(c.champ.code, FormulairesEspece.positionInitiale(c, valeur, nomenclatures))
        }
    }

    /** Les champs simples montrés : (clé, libellé, numérique ?). */
    val champsSimples = DetailsReleveFormulaire.CHAMPS_SIMPLES.filter { champFormVisible(donnees.formFieldsJson, it.first) }

    /** Texte de chaque champ simple montré, par clé. */
    val textesSimples = mutableStateMapOf<String, String>().apply {
        champsSimples.forEach { put(it.first, donnees.champsExtra[it.first].orEmpty()) }
    }

    /** Champs additionnels de relevé, s'il y en a. */
    val additionnels: EtatChampsAdditionnels? = donnees.defs.takeIf { it.isNotEmpty() }?.let { EtatChampsAdditionnels(it, donnees.additionnels) }

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

    /** Frappe : l'habitat choisi est oublié jusqu'au prochain choix ; recherche 300 ms après la
     *  dernière frappe, dès 2 caractères. La liste déjà chargée reste proposée entre-temps. */
    fun saisirHabitat(texte: String) {
        if (texte == habitatTexte) return
        habitatTexte = texte
        cdHab = null
        tacheHabitat?.cancel()
        habitatsDeroules = habitats.isNotEmpty()
        if (texte.trim().length < 2) {
            rechercheHabitat = false
            return
        }
        rechercherHabitats(texte, attente = 300)
    }

    /** Toucher le champ : la liste chargée, sinon une recherche sur le texte (l'habitat choisi
     *  reste). */
    fun toucherHabitat() {
        if (habitats.isNotEmpty()) {
            habitatsDeroules = true
            return
        }
        val terme = habitatTexte.trim()
        if (terme.length < 2) return
        tacheHabitat?.cancel()
        rechercherHabitats(terme, attente = 0)
    }

    private fun rechercherHabitats(terme: String, attente: Long) {
        rechercheHabitat = true
        tacheHabitat = portee.launch {
            if (attente > 0) delay(attente)
            val resultats = chercherHabitats(terme)
            habitats.clear()
            habitats.addAll(resultats)
            habitatsDeroules = habitats.isNotEmpty()
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

    /** « Valider » : les saisies relues, ou le message d'un champ obligatoire vide (le dialogue
     *  reste ouvert). */
    fun valider(): Pair<DetailsReleveResult?, String?> {
        val manquants = additionnels?.champsObligatoiresVides().orEmpty()
        if (manquants.isNotEmpty()) return null to "Champs obligatoires à renseigner : ${manquants.joinToString(", ")}"
        val valeurs = nomenclaturesReleve.associate { c -> c.champ.code to c.code(positions[c.champ.code] ?: 0) }
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
            DetailsReleveFormulaire.dates(debut, fin, finActive, donnees.heures, donnees.avecFin),
            dateModifiee,
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

/** Le dialogue « Détails du relevé » (ou « Détails des relevés ») ouvert sur [etat], fermé si
 *  null. [onValider] reçoit les saisies acceptées ; un champ obligatoire vide garde le dialogue
 *  ouvert, avec un message. */
@Composable
fun DialogueDetailsReleve(etat: EtatDetailsReleve?, onAnnuler: () -> Unit, onValider: (DetailsReleveResult) -> Unit) {
    // Le dernier état reste dessiné pendant la fermeture animée.
    val dernier = remember { arrayOfNulls<EtatDetailsReleve>(1) }
    if (etat != null) dernier[0] = etat
    val e = etat ?: dernier[0] ?: return
    val toasts = LocalToasts.current
    DialogueFormulaire(
        visible = etat != null,
        titre = e.donnees.titre,
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

/** La racine du dialogue : marges de 16 × 8 dp. */
@Composable
private fun ContenuAndroid(e: EtatDetailsReleve) {
    val d = e.donnees
    val erreur = MaterialTheme.colorScheme.error
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Jeu de données : liste déroulante, ou ligne en lecture seule sans cache.
        if (d.datasets.isEmpty()) {
            LigneLectureAndroid("Jeu de données", e.dataset?.second ?: "—")
        } else {
            TitreAndroid("Jeu de données")
            DeroulantAndroid("Rechercher un jeu de données", d.datasets.map { it.second }, e.dataset?.second.orEmpty(), restaurer = true, onChoix = e::choisirDataset)
        }

        TitreAndroid("Observateurs")
        ObservateursAndroid(d.observateurs, e.observateurs, e::ajouterObservateur, e::retirerObservateur)

        d.infos.forEach { (libelle, valeur) -> if (valeur.isNotBlank()) LigneLectureAndroid(libelle, valeur) }

        TitreAndroid(if (d.avecFin) "Date de début" else "Date du relevé")
        Row(Modifier.padding(bottom = 8.dp)) {
            ChampDateHeure(e.debut, d.heures, e::changerDebut)
            if (d.avecFin && !e.finActive) {
                Spacer(Modifier.width(8.dp))
                CaseDate("＋", onClick = e::ajouterFin)
            }
        }
        if (e.finActive) {
            TitreAndroid("Date de fin")
            Row(Modifier.padding(bottom = 8.dp)) {
                ChampDateHeure(e.fin, d.heures, e::changerFin)
                Spacer(Modifier.width(8.dp))
                CaseDate("✕", couleur = erreur, contour = false, onClick = e::retirerFin)
            }
        }

        e.nomenclaturesReleve.forEach { c ->
            ChampNomenclatureVue(c, e.positions[c.champ.code] ?: 0, { e.positions[c.champ.code] = it })
        }

        e.champsSimples.forEach { (cle, libelle, numerique) ->
            TitreAndroid(libelle)
            ChampSimple(e, cle, numerique)
        }

        e.additionnels?.let {
            TitreAndroid("Champs additionnels")
            ChampsAdditionnelsVue(it)
        }

        if (e.habitatVisible) {
            TitreAndroid("Habitat")
            ChampHabitatAndroid(e)
        }

        if (e.commentaireVisible) {
            TitreAndroid("Commentaire")
            ChampCommentaire(e)
        }
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
            indication = "Rechercher un habitat…",
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

/** Champ simple : EditText d'une ligne, chiffres seulement si [numerique]. */
@Composable
private fun ChampSimple(e: EtatDetailsReleve, cle: String, numerique: Boolean) {
    ChampTexteFormulaire(
        e.textesSimples[cle].orEmpty(),
        { e.textesSimples[cle] = if (numerique) it.filter(Char::isDigit) else it },
        clavier = if (numerique) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        compactAndroid = true,
    )
}

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
    SectionIos("Jeu de données") {
        if (d.datasets.isEmpty()) LigneTexteSeulIos(e.dataset?.second ?: "—")
        else LignesRechercheIos("Rechercher un jeu de données", d.datasets.map { it.second }, e.dataset?.second.orEmpty(), e::choisirDataset)
    }

    SectionIos("Observateurs") { LignesObservateursIos(d.observateurs, e.observateurs, e::ajouterObservateur, e::retirerObservateur) }

    d.infos.forEach { (libelle, valeur) ->
        if (valeur.isNotBlank()) SectionIos(libelle) { LigneTexteSeulIos(valeur) }
    }

    SectionIos(if (d.avecFin) "Date de début" else "Date du relevé") {
        LigneDateIos(e.debut, d.heures, e::changerDebut) {
            if (d.avecFin && !e.finActive) BoutonCadreIos("plus", CouleursGeoMys.jauneClair, "Ajouter une date de fin", e::ajouterFin)
        }
    }
    if (e.finActive) {
        SectionIos("Date de fin") {
            LigneDateIos(e.fin, d.heures, e::changerFin) {
                BoutonCadreIos("xmark", CouleursGeoMys.rougePastille, "Retirer la date de fin", e::retirerFin)
            }
        }
    }

    e.nomenclaturesReleve.forEach { c ->
        SectionIos(c.libelle) { LigneChoixIos("", c.libelles, e.positions[c.champ.code] ?: 0) { e.positions[c.champ.code] = it } }
    }

    e.champsSimples.forEach { (cle, libelle, numerique) ->
        SectionIos(libelle) {
            val texte = e.textesSimples[cle].orEmpty()
            if (numerique) {
                LigneNombreIos("", texte, { e.textesSimples[cle] = it.filter(Char::isDigit) }, KeyboardOptions(keyboardType = KeyboardType.Number))
            } else {
                LigneTexteIos(texte, { e.textesSimples[cle] = it }, "—", clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            }
        }
    }

    e.additionnels?.let {
        Column {
            Text(
                "Champs additionnels",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) { ChampsAdditionnelsSectionsIos(it) }
        }
    }

    if (e.habitatVisible) SectionIos("Habitat") { LignesHabitatIos(e) }

    if (e.commentaireVisible) {
        SectionIos("Commentaire") {
            LigneTexteIos(
                e.commentaire,
                { e.commentaire = it },
                "Commentaire",
                lignesMin = 2,
                lignesMax = 5,
                clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
    }
}

/** Une date du relevé sur sa ligne : le sélecteur natif, peint du fond de la ligne, puis [bouton]
 *  (le « ＋ » ou le « ✕ » de la date de fin). */
@Composable
private fun LigneDateIos(millis: Long, heures: Boolean, onChange: (Long) -> Unit, bouton: @Composable () -> Unit) {
    LigneIos {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ChampDateHeure(millis, heures, onChange, Modifier.weight(1f), fondIos = FOND_LIGNE_FEUILLE_IOS)
            bouton()
        }
    }
}

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
                        if (e.habitatTexte.isEmpty()) Text("Rechercher un habitat…", style = styleSaisieIos.copy(color = INVITE_IOS))
                        champ()
                    }
                },
            )
            if (e.rechercheHabitat) IndicateurActivite(Color.White, petitIos = true)
        }
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

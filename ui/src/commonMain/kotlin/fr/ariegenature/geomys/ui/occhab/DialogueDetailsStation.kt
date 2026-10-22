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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/OccHabDetailsDialog.kt) : le
// dialogue « Informations obligatoires » / « Détails de la station », ses saisies gardées dans un état
// ([EtatDetailsStation]) au lieu de ses vues ; les règles sont dans occhab/DetailsStation. Depuis le 21 octobre,
// un écran plein, comme les détails d'un relevé, et non plus un dialogue. Sous iOS, les sections
// d'OccHabInfosStationSheet de GeoMys-IOS (demande du 20 octobre).

package fr.ariegenature.geomys.ui.occhab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.occhab.CarteOccHab
import fr.ariegenature.geomys.occhab.DetailsStation
import fr.ariegenature.geomys.occhab.EditionAnaEval
import fr.ariegenature.geomys.occhab.ListeNomenclatureOccHab
import fr.ariegenature.geomys.occhab.OccHabDetailsSession
import fr.ariegenature.geomys.occhab.SaisiesDetailsStation
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.NomenclatureCache
import fr.ariegenature.geomys.ui.composants.BoutonCadreIos
import fr.ariegenature.geomys.ui.composants.CaseDate
import fr.ariegenature.geomys.ui.composants.ChampJour
import fr.ariegenature.geomys.ui.composants.ChampTexteFormulaire
import fr.ariegenature.geomys.ui.composants.ClavierNombresEtPonctuation
import fr.ariegenature.geomys.ui.composants.ClavierNumeriqueSigne
import fr.ariegenature.geomys.ui.composants.DeroulantAndroid
import fr.ariegenature.geomys.ui.composants.LigneChoixIos
import fr.ariegenature.geomys.ui.composants.LigneIos
import fr.ariegenature.geomys.ui.composants.LigneNombreIos
import fr.ariegenature.geomys.ui.composants.LigneTexteIos
import fr.ariegenature.geomys.ui.composants.LigneTexteSeulIos
import fr.ariegenature.geomys.ui.composants.LignesRechercheIos
import fr.ariegenature.geomys.ui.composants.ListeDeroulante
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.SectionIos
import fr.ariegenature.geomys.ui.composants.afficher
import fr.ariegenature.geomys.ui.composants.barreChampObligatoire
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.occtax.FormulaireEnEcranPlein
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.util.debutDuJour
import fr.ariegenature.geomys.util.maintenantMillis

/** Les saisies de l'écran des détails d'une station, ouvert sur les [details] de la station
 *  sélectionnée et sur sa [station] (altitudes, surface, bloc ANA-EVAL). Un état neuf à chaque
 *  ouverture : le retour n'écrit rien.
 *  [demarrage] : formulaire de DÉMARRAGE d'une station (« Informations obligatoires ») —
 *  uniquement les champs requis ; sinon les « Détails » complets. */
class EtatDetailsStation(
    val demarrage: Boolean,
    details: OccHabDetailsSession,
    station: OccHabStation,
    private val config: GeoNatureConfig,
    nomenclatures: NomenclatureCache,
) {
    val titre = if (demarrage) DetailsStation.TITRE_DEMARRAGE else DetailsStation.TITRE_DETAILS

    // ── Jeu de données : choisi UNIQUEMENT dans la liste ──

    val datasets = DetailsStation.datasets(config)
    private var idDataset: Int? by mutableStateOf(details.idDataset)
    private var nomDataset: String? = details.nomDataset

    /** Le jeu inactif d'une station reprise : le web le montre en lecture seule (« Jeux de
     *  données: » et son nom). */
    val jeuInactif: String? = DetailsStation.nomJeuInactif(config, details.idDataset)

    /** Le libellé du jeu choisi, que le champ réaffiche dès qu'il perd le focus. */
    var texteDataset by mutableStateOf(DetailsStation.libelleJdd(details, datasets).orEmpty())
        private set

    fun choisirDataset(position: Int) {
        val (id, libelle) = datasets.getOrNull(position) ?: return
        idDataset = id
        nomDataset = libelle
        texteDataset = libelle
    }

    /** La croix de la liste des jeux de données du web. */
    fun effacerDataset() {
        idDataset = null
        nomDataset = null
        texteDataset = ""
    }

    init {
        // Un seul jeu proposé, le champ vide : le web le choisit d'office.
        if (idDataset == null && jeuInactif == null && datasets.size == 1) choisirDataset(0)
    }

    /** Aucun jeu de données choisi : obligatoire sur le web, le champ porte la barre rouge. */
    val jddManquant: Boolean get() = jeuInactif == null && datasets.isNotEmpty() && idDataset == null

    // ── Observateur : choisi seul dans la liste d'OccHab, comme le web ; ou en texte libre quand
    // le serveur le demande (`OCCHAB.OBSERVER_AS_TXT`) ──

    val observateursEnTexte = config.occhabObservateursEnTexte
    var observateursTexte by mutableStateOf(details.observateursTxt.orEmpty())
    val observateursProposes: List<Pair<Int, String>> = DetailsStation.observateurs(config)

    /** L'observateur choisi ; une station qui en porte plusieurs montre le premier, comme le web, qui
     *  ne garde que lui à la réédition. */
    var observateur: Pair<Int, String>? by mutableStateOf(
        details.observateursIds.firstOrNull()?.let { id ->
            id to (observateursProposes.firstOrNull { it.first == id }?.second ?: details.observateursNoms.firstOrNull()?.takeIf { it.isNotBlank() } ?: id.toString())
        },
    )
        private set

    fun choisirObservateur(position: Int) {
        observateursProposes.getOrNull(position)?.let { observateur = it }
    }

    /** La croix du champ, comme celle de la liste du web. */
    fun effacerObservateur() {
        observateur = null
    }

    /** Aucun observateur (ou un texte vide) : obligatoire sur le web, le champ porte la barre rouge. */
    val observateursManquants: Boolean get() = if (observateursEnTexte) observateursTexte.isBlank() else observateur == null

    // ── Dates : la date du jour seulement dans un champ affiché (pnx-date `defaultToday`) ; choisir
    // l'une recopie l'autre tant qu'on n'y a pas touché (synchronizeMax / synchronizeMin du web) ──

    val dateMinVisible = config.occhabChampVisible("date_min")
    val dateMaxVisible = config.occhabChampVisible("date_max")
    var dateMin by mutableStateOf(details.dateMin ?: if (dateMinVisible) maintenantMillis() else null)
        private set
    var dateMax by mutableStateOf(details.dateMax ?: if (dateMaxVisible) maintenantMillis() else null)
        private set
    // Comme le web (occhab-form.component.ts) : une station aux dates différentes les a toutes deux
    // « modifiées » à l'ouverture, et changer l'une ne recopie plus l'autre.
    private var dateMinTouchee = run {
        val debut = details.dateMin
        val fin = details.dateMax
        debut != null && fin != null && debutDuJour(debut) != debutDuJour(fin)
    }
    private var dateMaxTouchee = dateMinTouchee

    fun choisirDateMin(jour: Long) {
        dateMin = jour
        dateMinTouchee = true
        if (!dateMaxTouchee) dateMax = jour
    }

    fun choisirDateMax(jour: Long) {
        dateMax = jour
        dateMaxTouchee = true
        if (!dateMinTouchee) dateMin = jour
    }

    // ── Nature de l'objet géographique ──

    val natureVisible = config.occhabChampVisible("geographic_object")
    val natures = ListeNomenclatureOccHab(nomenclatures.get("NAT_OBJ_GEO"))
    var positionNature by mutableStateOf(natures.position(details.idNomObjetGeographique))

    /** Aucune nature choisie dans le champ affiché : obligatoire sur le web, la barre rouge. */
    val natureManquante: Boolean get() = natureVisible && positionNature < 0

    /** Les valeurs à l'ouverture des listes, gardées tant qu'on n'y touche pas
     *  ([ListeNomenclatureOccHab.retenue]). */
    private val initiales = details.copy()

    // ── Détails complets : type de sol, altitudes et surface PAR STATION, profondeurs, exposition,
    // méthode, commentaire, ANA-EVAL ──

    val typeSolVisible = !demarrage && config.occhabChampVisible("type_sol")
    val typesSol = ListeNomenclatureOccHab(nomenclatures.get("TYPE_SOL"))
    var positionTypeSol by mutableStateOf(typesSol.position(details.idNomTypeSol))
    val altitudeMinVisible = !demarrage && config.occhabChampVisible("altitude_min")
    val altitudeMaxVisible = !demarrage && config.occhabChampVisible("altitude_max")
    val profondeurMinVisible = !demarrage && config.occhabChampVisible("depth_min")
    val profondeurMaxVisible = !demarrage && config.occhabChampVisible("depth_max")
    val expositionVisible = !demarrage && config.occhabChampVisible("exposure")
    val surfaceVisible = !demarrage && config.occhabChampVisible("area")
    val methodeVisible = !demarrage && config.occhabChampVisible("area_surface_calculation")
    val commentaireVisible = !demarrage && config.occhabChampVisible("comment")
    var altitudeMin by mutableStateOf(station.altitudeMin?.toString().orEmpty())
    var altitudeMax by mutableStateOf(station.altitudeMax?.toString().orEmpty())
    var profondeurMin by mutableStateOf(details.profondeurMin?.toString().orEmpty())
    var profondeurMax by mutableStateOf(details.profondeurMax?.toString().orEmpty())

    /** Le « + » qui suit les altitudes sur le web : offert si le serveur montre une profondeur
     *  (`formConfig` `depth_min` ou `depth_max`), il ouvre et referme leur bloc, replié à chaque
     *  ouverture du dialogue (`isCollapseDepth`), même quand la station en porte. Repliées, elles
     *  gardent leur valeur. */
    val profondeursProposees = profondeurMinVisible || profondeurMaxVisible
    var profondeursDepliees by mutableStateOf(false)
        private set

    fun basculerProfondeurs() {
        profondeursDepliees = !profondeursDepliees
    }

    /** Ce que le « + » annonce aux lecteurs d'écran : il reste « + » à l'œil une fois ouvert, comme
     *  sur le web. */
    val descriptionProfondeurs get() = if (profondeursDepliees) DetailsStation.MASQUER_PROFONDEURS else DetailsStation.AFFICHER_PROFONDEURS

    val expositions = ListeNomenclatureOccHab(nomenclatures.get("EXPOSITION"))
    var positionExposition by mutableStateOf(expositions.position(details.idNomExposition))
    var surface by mutableStateOf(station.surface?.toString().orEmpty())
    val methodes = ListeNomenclatureOccHab(nomenclatures.get("METHOD_CALCUL_SURFACE"))
    var positionMethode by mutableStateOf(methodes.position(details.idNomCalculSurface))
    var commentaire by mutableStateOf(details.comment.orEmpty())

    /** La section ANA-EVAL : seulement dans les détails complets d'une station qui porte un bloc. */
    val anaEval: EtatSectionAnaEval? = if (demarrage) null else EditionAnaEval.station(station.anaEvalJson)?.let(::EtatSectionAnaEval)

    /** Les saisies relues pour « Valider ». Une nature masquée garde sa valeur (le défaut du
     *  serveur pour une nouvelle station), requise comme sur le web. */
    fun saisies() = SaisiesDetailsStation(
        idDataset = idDataset,
        nomDataset = nomDataset,
        observateurs = listOfNotNull(observateur),
        observateursTexte = observateursTexte.takeIf { observateursEnTexte },
        dateMin = dateMin,
        dateMax = dateMax,
        natureVisible = natureVisible,
        nature = if (natureVisible) natures.retenue(initiales.idNomObjetGeographique, positionNature) else initiales.idNomObjetGeographique,
        methodeVisible = methodeVisible,
        methode = methodes.retenue(initiales.idNomCalculSurface, positionMethode),
        altitudeMin = altitudeMin.takeIf { altitudeMinVisible },
        altitudeMax = altitudeMax.takeIf { altitudeMaxVisible },
        profondeurMin = profondeurMin.takeIf { profondeurMinVisible },
        profondeurMax = profondeurMax.takeIf { profondeurMaxVisible },
        expositionVisible = expositionVisible,
        exposition = expositions.retenue(initiales.idNomExposition, positionExposition),
        surface = surface.takeIf { surfaceVisible },
        typeSolVisible = typeSolVisible,
        typeSol = typesSol.retenue(initiales.idNomTypeSol, positionTypeSol),
        commentaire = commentaire.takeIf { commentaireVisible },
        anaEval = anaEval?.let { e -> { e.json() } },
    )

    /** Ce qui bloque « Valider » (champ requis vide, dates ou altitudes incohérentes, entier ANA
     *  hors bornes), ou null si la saisie est acceptée. */
    fun refus(): String? {
        val s = saisies()
        DetailsStation.champManquant(s, config)?.let { return it }
        DetailsStation.datesIncoherentes(s.dateMin.takeIf { dateMinVisible }, s.dateMax.takeIf { dateMaxVisible })?.let { return it }
        if (demarrage) return null
        DetailsStation.altitudesIncoherentes(s.altitudeMin, s.altitudeMax)?.let { return it }
        return anaEval?.erreur()
    }

    // ── Garde de sortie ──

    /** Ce que l'écran saisit, pour savoir s'il a changé ; le bloc ANA-EVAL par son JSON. */
    private fun empreinte(): Pair<SaisiesDetailsStation, String?> = saisies().copy(anaEval = null) to anaEval?.json()

    private val empreinteInitiale = empreinte()

    /** L'écran a changé depuis son ouverture : le quitter sans valider le demande d'abord. */
    fun modifie(): Boolean = empreinte() != empreinteInitiale
}

/** Les détails d'une station (ou ses informations obligatoires) ouverts sur [etat], fermés si null :
 *  un écran plein, comme les détails d'un relevé (demande du 21 octobre), au bandeau d'OccHab, coche
 *  en haut à droite. [onValider] reçoit les saisies acceptées ; un champ requis vide garde l'écran
 *  ouvert, avec un message bref. Le retour l'annule ([onAnnuler]), après « Quitter sans
 *  enregistrer ? » s'il a été modifié ; la maison mène ensuite à l'accueil ([onAccueil]). */
@Composable
fun EcranDetailsStation(etat: EtatDetailsStation?, onAccueil: () -> Unit, onAnnuler: () -> Unit, onValider: (SaisiesDetailsStation) -> Unit) {
    // Le dernier état reste dessiné pendant la fermeture animée.
    val dernier = remember { arrayOfNulls<EtatDetailsStation>(1) }
    if (etat != null) dernier[0] = etat
    val e = etat ?: dernier[0] ?: return
    val toasts = LocalToasts.current
    FormulaireEnEcranPlein(
        visible = etat != null,
        titre = e.titre,
        typeSaisieLabel = CarteOccHab.LIBELLE,
        titreGarde = if (e.demarrage) "Informations non enregistrées" else "Détails non enregistrés",
        modifie = e::modifie,
        onAccueil = onAccueil,
        onAnnuler = onAnnuler,
        onValider = {
            val refus = e.refus()
            if (refus != null) toasts.afficher(refus) else onValider(e.saisies())
        },
    ) {
        if (estIos) ContenuIos(e) else ContenuAndroid(e)
    }
}

// ─── Android : la vue construite par ouvrirDialogDetailsOccHab, dans l'ordre du formulaire web ───

/** Les champs ; l'écran donne les marges. */
@Composable
private fun ContenuAndroid(e: EtatDetailsStation) {
    Column(Modifier.fillMaxWidth()) {
        // Les champs obligatoires du web, vides, portent la barre rouge.
        LibelleOccHabAndroid(DetailsStation.LIBELLE_OBSERVATEURS)
        Box(Modifier.fillMaxWidth().barreChampObligatoire(e.observateursManquants)) {
            if (e.observateursEnTexte) {
                ChampTexteFormulaire(e.observateursTexte, { e.observateursTexte = it }, clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            } else {
                DeroulantAndroid(
                    DetailsStation.INVITE_OBSERVATEURS,
                    e.observateursProposes.map { it.second },
                    e.observateur?.second.orEmpty(),
                    restaurer = true,
                    onChoix = e::choisirObservateur,
                    onEffacer = e::effacerObservateur,
                )
            }
        }
        val inactif = e.jeuInactif
        if (inactif != null) {
            // Le jeu inactif d'une station : en lecture seule, comme le web.
            Text(
                buildAnnotatedString {
                    append(DetailsStation.LIBELLE_JDD + ": ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inactif) }
                },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            LibelleOccHabAndroid(DetailsStation.LIBELLE_JDD)
            if (e.datasets.isEmpty()) {
                Text(DetailsStation.AUCUN_JDD, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            } else {
                Box(Modifier.fillMaxWidth().barreChampObligatoire(e.jddManquant)) {
                    DeroulantAndroid(DetailsStation.INVITE_JDD, e.datasets.map { it.second }, e.texteDataset, restaurer = true, onChoix = e::choisirDataset, onEffacer = e::effacerDataset)
                }
            }
        }
        if (e.dateMinVisible) {
            LibelleOccHabAndroid(DetailsStation.LIBELLE_DATE_MIN)
            Box(Modifier.barreChampObligatoire(e.dateMin == null)) { ChampJour(e.dateMin, e::choisirDateMin) }
        }
        if (e.dateMaxVisible) {
            LibelleOccHabAndroid(DetailsStation.LIBELLE_DATE_MAX)
            Box(Modifier.barreChampObligatoire(e.dateMax == null)) { ChampJour(e.dateMax, e::choisirDateMax) }
        }
        // Le « ＋ » des profondeurs au bout de la dernière altitude, comme sur le web ; seul sur sa
        // ligne si le serveur masque les deux altitudes.
        val plus: (@Composable () -> Unit)? = if (e.profondeursProposees) {
            { CaseDate("＋", description = e.descriptionProfondeurs, onClick = e::basculerProfondeurs) }
        } else {
            null
        }
        if (e.altitudeMinVisible) {
            ChampNombreAndroid(DetailsStation.LIBELLE_ALTITUDE_MIN, e.altitudeMin, if (e.altitudeMaxVisible) null else plus) { e.altitudeMin = it }
        }
        if (e.altitudeMaxVisible) ChampNombreAndroid(DetailsStation.LIBELLE_ALTITUDE_MAX, e.altitudeMax, plus) { e.altitudeMax = it }
        if (!e.altitudeMinVisible && !e.altitudeMaxVisible && plus != null) Box(Modifier.padding(top = 12.dp)) { plus() }
        if (e.profondeursDepliees) {
            if (e.profondeurMinVisible) ChampNombreAndroid(DetailsStation.LIBELLE_PROFONDEUR_MIN, e.profondeurMin) { e.profondeurMin = it }
            if (e.profondeurMaxVisible) ChampNombreAndroid(DetailsStation.LIBELLE_PROFONDEUR_MAX, e.profondeurMax) { e.profondeurMax = it }
        }
        if (e.expositionVisible) {
            LibelleOccHabAndroid(DetailsStation.LIBELLE_EXPOSITION)
            ListeDeroulante(e.expositions.libelles, e.positionExposition, { e.positionExposition = it })
        }
        if (e.surfaceVisible) ChampNombreAndroid(DetailsStation.LIBELLE_SURFACE, e.surface) { e.surface = it }
        if (e.methodeVisible) {
            LibelleOccHabAndroid(DetailsStation.LIBELLE_METHODE)
            ListeDeroulante(e.methodes.libelles, e.positionMethode, { e.positionMethode = it })
        }
        if (e.natureVisible) {
            LibelleOccHabAndroid(DetailsStation.LIBELLE_NATURE)
            Box(Modifier.fillMaxWidth().barreChampObligatoire(e.natureManquante)) {
                ListeDeroulante(e.natures.libelles, e.positionNature, { e.positionNature = it })
            }
        }
        if (e.typeSolVisible) {
            LibelleOccHabAndroid(DetailsStation.LIBELLE_TYPE_SOL)
            ListeDeroulante(e.typesSol.libelles, e.positionTypeSol, { e.positionTypeSol = it })
        }
        if (e.commentaireVisible) {
            // Sans libellé sur le web : son indication seule.
            ChampTexteFormulaire(
                e.commentaire,
                { e.commentaire = it },
                modifier = Modifier.padding(top = 12.dp),
                lignesMin = 2,
                uneLigne = false,
                clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                indication = DetailsStation.INVITE_COMMENTAIRE,
            )
        }
        e.anaEval?.let { SectionAnaEval(it) }
    }
}

/** `champNombre` : EditText numérique (TYPE_CLASS_NUMBER et signe, comme l'`<input type="number">`
 *  du web), sous son libellé ; [apres] à sa droite (le « ＋ » des profondeurs). */
@Composable
private fun ChampNombreAndroid(libelle: String, valeur: String, apres: (@Composable () -> Unit)? = null, onChange: (String) -> Unit) {
    LibelleOccHabAndroid(libelle)
    val clavier = KeyboardOptions(keyboardType = KeyboardType.Number)
    ClavierNumeriqueSigne {
        if (apres == null) {
            ChampTexteFormulaire(valeur, { onChange(entierSigne(it)) }, clavier = clavier)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChampTexteFormulaire(valeur, { onChange(entierSigne(it)) }, Modifier.weight(1f), clavier = clavier)
                Spacer(Modifier.width(8.dp))
                apres()
            }
        }
    }
}

/** Un entier tapé, signe compris : les altitudes et les profondeurs peuvent être négatives sur le
 *  web. */
private fun entierSigne(texte: String): String =
    texte.filterIndexed { i, c -> c.isDigit() || (c == '-' && i == 0) }

// ─── iOS : OccHabInfosStationSheet de GeoMys-IOS, dans l'ordre du formulaire web ───

@Composable
private fun ContenuIos(e: EtatDetailsStation) {
    // Les champs obligatoires du web, vides, portent la barre rouge.
    SectionIos(DetailsStation.LIBELLE_OBSERVATEURS, barre = e.observateursManquants) {
        if (e.observateursEnTexte) {
            LigneTexteIos(
                e.observateursTexte,
                { e.observateursTexte = it },
                "",
                clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        } else {
            LignesRechercheIos(
                DetailsStation.INVITE_OBSERVATEURS,
                e.observateursProposes.map { it.second },
                e.observateur?.second.orEmpty(),
                e::choisirObservateur,
                e::effacerObservateur,
            )
        }
    }
    val inactif = e.jeuInactif
    if (inactif != null) {
        // Le jeu inactif d'une station : en lecture seule, comme le web.
        SectionIos(DetailsStation.LIBELLE_JDD + ":") { LigneTexteSeulIos(inactif) }
    } else {
        SectionIos(DetailsStation.LIBELLE_JDD, barre = e.jddManquant) {
            if (e.datasets.isEmpty()) LigneTexteSeulIos(DetailsStation.AUCUN_JDD, couleur = CouleursGeoMys.rougePastille, taille = 13f)
            else LignesRechercheIos(DetailsStation.INVITE_JDD, e.datasets.map { it.second }, e.texteDataset, e::choisirDataset, e::effacerDataset)
        }
    }
    if (e.dateMinVisible) SectionIos(DetailsStation.LIBELLE_DATE_MIN, barre = e.dateMin == null) { LigneIos { ChampJour(e.dateMin, e::choisirDateMin, fondIos = FOND_LIGNE) } }
    if (e.dateMaxVisible) SectionIos(DetailsStation.LIBELLE_DATE_MAX, barre = e.dateMax == null) { LigneIos { ChampJour(e.dateMax, e::choisirDateMax, fondIos = FOND_LIGNE) } }
    // Altitudes et surface comme les autres champs : le libellé sur le fond, au-dessus de sa ligne
    // (demande du 20 octobre ; elles partageaient une section, leur libellé dans la ligne).
    val chiffres = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    // Le « ＋ » des profondeurs au bout de la ligne de la dernière altitude, comme celui de la date
    // de fin d'un relevé ; seul si le serveur masque les deux altitudes.
    val plus: (@Composable () -> Unit)? = if (e.profondeursProposees) {
        { BoutonCadreIos("plus", CouleursGeoMys.jauneClair, e.descriptionProfondeurs, e::basculerProfondeurs) }
    } else {
        null
    }
    if (e.altitudeMinVisible) {
        SectionIos(DetailsStation.LIBELLE_ALTITUDE_MIN) {
            LigneNombreIos("", e.altitudeMin, { e.altitudeMin = entierSigne(it) }, ClavierNombresEtPonctuation, if (e.altitudeMaxVisible) null else plus)
        }
    }
    if (e.altitudeMaxVisible) {
        SectionIos(DetailsStation.LIBELLE_ALTITUDE_MAX) { LigneNombreIos("", e.altitudeMax, { e.altitudeMax = entierSigne(it) }, ClavierNombresEtPonctuation, plus) }
    }
    if (!e.altitudeMinVisible && !e.altitudeMaxVisible && plus != null) Box(Modifier.padding(start = 16.dp)) { plus() }
    if (e.profondeursDepliees && e.profondeurMinVisible) {
        SectionIos(DetailsStation.LIBELLE_PROFONDEUR_MIN) { LigneNombreIos("", e.profondeurMin, { e.profondeurMin = entierSigne(it) }, ClavierNombresEtPonctuation) }
    }
    if (e.profondeursDepliees && e.profondeurMaxVisible) {
        SectionIos(DetailsStation.LIBELLE_PROFONDEUR_MAX) { LigneNombreIos("", e.profondeurMax, { e.profondeurMax = entierSigne(it) }, ClavierNombresEtPonctuation) }
    }
    if (e.expositionVisible) {
        SectionIos(DetailsStation.LIBELLE_EXPOSITION) { LigneChoixIos("", e.expositions.libelles, e.positionExposition) { e.positionExposition = it } }
    }
    if (e.surfaceVisible) {
        SectionIos(DetailsStation.LIBELLE_SURFACE) { LigneNombreIos("", e.surface, { e.surface = it.filter(Char::isDigit) }, chiffres) }
    }
    if (e.methodeVisible) {
        SectionIos(DetailsStation.LIBELLE_METHODE) { LigneChoixIos("", e.methodes.libelles, e.positionMethode) { e.positionMethode = it } }
    }
    if (e.natureVisible) {
        SectionIos(DetailsStation.LIBELLE_NATURE, barre = e.natureManquante) { LigneChoixIos("", e.natures.libelles, e.positionNature) { e.positionNature = it } }
    }
    if (e.typeSolVisible) {
        SectionIos(DetailsStation.LIBELLE_TYPE_SOL) { LigneChoixIos("", e.typesSol.libelles, e.positionTypeSol) { e.positionTypeSol = it } }
    }
    if (e.commentaireVisible) {
        // Sans libellé sur le web : son indication seule.
        SectionIos(null) {
            LigneTexteIos(
                e.commentaire,
                { e.commentaire = it },
                DetailsStation.INVITE_COMMENTAIRE,
                lignesMin = 2,
                lignesMax = 5,
                clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
    }
    e.anaEval?.let { SectionAnaEval(it) }
}

/** Fond des lignes d'une feuille, sous le sélecteur de date (vue native). */
private val FOND_LIGNE = Color(0xFF2C2C2E)

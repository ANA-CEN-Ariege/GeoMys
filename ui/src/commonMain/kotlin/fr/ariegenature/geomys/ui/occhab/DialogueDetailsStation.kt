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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/OccHabDetailsDialog.kt) : le
// dialogue « Informations obligatoires » / « Détails de la station », ses saisies gardées dans un état
// ([EtatDetailsStation]) au lieu de ses vues ; les règles sont dans occhab/DetailsStation. Sous iOS, l'allure
// d'OccHabInfosStationSheet de GeoMys-IOS.

package fr.ariegenature.geomys.ui.occhab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.occhab.DetailsStation
import fr.ariegenature.geomys.occhab.EditionAnaEval
import fr.ariegenature.geomys.occhab.ListeNomenclatureOccHab
import fr.ariegenature.geomys.occhab.OccHabDetailsSession
import fr.ariegenature.geomys.occhab.SaisiesDetailsStation
import fr.ariegenature.geomys.occtax.DetailsReleveFormulaire
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.NomenclatureCache
import fr.ariegenature.geomys.ui.composants.ChampJour
import fr.ariegenature.geomys.ui.composants.ChampTexteFormulaire
import fr.ariegenature.geomys.ui.composants.DeroulantAndroid
import fr.ariegenature.geomys.ui.composants.DialogueFeuilleFormulaire
import fr.ariegenature.geomys.ui.composants.GraisseSymbole
import fr.ariegenature.geomys.ui.composants.INVITE_IOS
import fr.ariegenature.geomys.ui.composants.LigneChoixIos
import fr.ariegenature.geomys.ui.composants.LigneIos
import fr.ariegenature.geomys.ui.composants.LigneNombreIos
import fr.ariegenature.geomys.ui.composants.LigneTexteIos
import fr.ariegenature.geomys.ui.composants.LigneTexteSeulIos
import fr.ariegenature.geomys.ui.composants.ListeDeroulante
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.MenuSysteme
import fr.ariegenature.geomys.ui.composants.ObservateursAndroid
import fr.ariegenature.geomys.ui.composants.SectionIos
import fr.ariegenature.geomys.ui.composants.SeparateurIos
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.afficher
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.styleSaisieIos
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.util.maintenantMillis

/** Les saisies du dialogue des détails d'une station, ouvert sur les [details] de la station
 *  sélectionnée et sur sa [station] (altitudes, surface, bloc ANA-EVAL). Un état neuf à chaque
 *  ouverture : « Annuler » n'écrit rien.
 *  [demarrage] : formulaire de DÉMARRAGE d'une station (« Informations obligatoires ») —
 *  uniquement les champs requis et l'interrupteur des stations du serveur ; sinon le dialogue
 *  « Détails » complet. [annulable] faux : jeu de données obligatoire, `setCancelable(false)`. */
class EtatDetailsStation(
    val demarrage: Boolean,
    val annulable: Boolean,
    details: OccHabDetailsSession,
    station: OccHabStation,
    private val config: GeoNatureConfig,
    nomenclatures: NomenclatureCache,
) {
    val titre = if (demarrage) DetailsStation.TITRE_DEMARRAGE else DetailsStation.TITRE_DETAILS

    // ── Jeu de données : choisi UNIQUEMENT dans la liste ──

    val datasets = DetailsStation.datasets(config)
    private var idDataset: Int? = details.idDataset
    private var nomDataset: String? = details.nomDataset

    /** Le libellé du jeu choisi, que le champ réaffiche dès qu'il perd le focus. */
    var texteDataset by mutableStateOf(DetailsStation.libelleJdd(details, datasets).orEmpty())
        private set

    fun choisirDataset(position: Int) {
        val (id, libelle) = datasets.getOrNull(position) ?: return
        idDataset = id
        nomDataset = libelle
        texteDataset = libelle
    }

    // ── Observateurs : dans l'ordre d'ajout, un seul par id ──

    val observateursProposes: List<Pair<Int, String>> = DetailsStation.observateurs(config)
    val observateurs: SnapshotStateList<Pair<Int, String>> = mutableStateListOf<Pair<Int, String>>().apply {
        details.observateursIds.forEachIndexed { i, id ->
            val nom = observateursProposes.firstOrNull { it.first == id }?.second
                ?: details.observateursNoms.getOrNull(i)?.takeIf { it.isNotBlank() }
                ?: id.toString()
            poser(this, id to nom)
        }
    }

    fun ajouterObservateur(position: Int) {
        observateursProposes.getOrNull(position)?.let { poser(observateurs, it) }
    }

    fun retirerObservateur(id: Int) {
        observateurs.removeAll { it.first == id }
    }

    // ── Dates : la date affichée fait foi, figée si rien n'était encore choisi ──

    val dateMinVisible = config.occhabChampVisible("date_min")
    val dateMaxVisible = config.occhabChampVisible("date_max")
    var dateMin by mutableStateOf(details.dateMin ?: if (dateMinVisible) maintenantMillis() else null)
    var dateMax by mutableStateOf(details.dateMax ?: if (dateMaxVisible) details.dateMin ?: maintenantMillis() else null)

    // ── Nature de l'objet géographique ──

    val natureVisible = config.occhabChampVisible("geographic_object")
    val natures = ListeNomenclatureOccHab(nomenclatures.get("NAT_OBJ_GEO"))
    var positionNature by mutableStateOf(natures.position(details.idNomObjetGeographique))

    // ── Détails complets : altitudes et surface PAR STATION, méthode, commentaire, ANA-EVAL ──

    val altitudeMinVisible = !demarrage && config.occhabChampVisible("altitude_min")
    val altitudeMaxVisible = !demarrage && config.occhabChampVisible("altitude_max")
    val surfaceVisible = !demarrage && config.occhabChampVisible("area")
    val methodeVisible = !demarrage && config.occhabChampVisible("area_surface_calculation")
    val commentaireVisible = !demarrage && config.occhabChampVisible("comment")
    var altitudeMin by mutableStateOf(station.altitudeMin?.toString().orEmpty())
    var altitudeMax by mutableStateOf(station.altitudeMax?.toString().orEmpty())
    var surface by mutableStateOf(station.surface?.toString().orEmpty())
    val methodes = ListeNomenclatureOccHab(nomenclatures.get("METHOD_CALCUL_SURFACE"))
    var positionMethode by mutableStateOf(methodes.position(details.idNomCalculSurface))
    var commentaire by mutableStateOf(details.comment.orEmpty())

    /** La section ANA-EVAL : seulement dans les détails complets d'une station qui porte un bloc. */
    val anaEval: EtatSectionAnaEval? = if (demarrage) null else EditionAnaEval.station(station.anaEvalJson)?.let(::EtatSectionAnaEval)

    /** Les saisies relues pour « Valider ». */
    fun saisies() = SaisiesDetailsStation(
        idDataset = idDataset,
        nomDataset = nomDataset,
        observateurs = observateurs.toList(),
        dateMin = dateMin,
        dateMax = dateMax,
        natureVisible = natureVisible,
        nature = natures.id(positionNature),
        methodeVisible = methodeVisible,
        methode = methodes.id(positionMethode),
        altitudeMin = altitudeMin.takeIf { altitudeMinVisible },
        altitudeMax = altitudeMax.takeIf { altitudeMaxVisible },
        surface = surface.takeIf { surfaceVisible },
        commentaire = commentaire.takeIf { commentaireVisible },
        anaEval = anaEval?.let { e -> { e.json() } },
    )

    /** Ce qui bloque « Valider » (champ requis vide, altitudes incohérentes, entier ANA hors
     *  bornes), ou null si la saisie est acceptée. */
    fun refus(): String? {
        val s = saisies()
        DetailsStation.champManquant(s, config)?.let { return it }
        if (demarrage) return null
        DetailsStation.altitudesIncoherentes(s.altitudeMin, s.altitudeMax)?.let { return it }
        return anaEval?.erreur()
    }

    private companion object {
        /** `LinkedHashMap.put` : remplace sur place un id déjà présent, sinon ajoute à la fin. */
        fun poser(liste: MutableList<Pair<Int, String>>, observateur: Pair<Int, String>) {
            val i = liste.indexOfFirst { it.first == observateur.first }
            if (i >= 0) liste[i] = observateur else liste.add(observateur)
        }
    }
}

/** Le dialogue des détails d'une station ouvert sur [etat], fermé si null. [onValider] reçoit les
 *  saisies acceptées ; un champ requis vide garde le dialogue ouvert, avec un message bref. */
@Composable
fun DialogueDetailsStation(etat: EtatDetailsStation?, onAnnuler: () -> Unit, onValider: (SaisiesDetailsStation) -> Unit) {
    // Le dernier état reste dessiné pendant la fermeture animée.
    val dernier = remember { arrayOfNulls<EtatDetailsStation>(1) }
    if (etat != null) dernier[0] = etat
    val e = etat ?: dernier[0] ?: return
    val toasts = LocalToasts.current
    DialogueFeuilleFormulaire(
        visible = etat != null,
        titre = e.titre,
        onAnnuler = onAnnuler,
        onValider = {
            val refus = e.refus()
            if (refus != null) toasts.afficher(refus) else onValider(e.saisies())
        },
        annulable = e.annulable,
    ) {
        if (estIos) ContenuIos(e) else ContenuAndroid(e)
    }
}

// ─── Android : la vue construite par ouvrirDialogDetailsOccHab ───

/** La racine : marges de 16 dp, sauf en bas. */
@Composable
private fun ContenuAndroid(e: EtatDetailsStation) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp)) {
        LibelleOccHabAndroid(DetailsStation.LIBELLE_JDD)
        if (e.datasets.isEmpty()) {
            Text(DetailsStation.AUCUN_JDD, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        } else {
            DeroulantAndroid(DetailsStation.INVITE_JDD, e.datasets.map { it.second }, e.texteDataset, restaurer = true, onChoix = e::choisirDataset)
        }
        LibelleOccHabAndroid(DetailsStation.LIBELLE_OBSERVATEURS)
        ObservateursAndroid(e.observateursProposes, e.observateurs, e::ajouterObservateur, e::retirerObservateur)
        if (e.dateMinVisible) {
            LibelleOccHabAndroid(DetailsStation.LIBELLE_DATE_MIN)
            ChampJour(e.dateMin, { e.dateMin = it })
        }
        if (e.dateMaxVisible) {
            LibelleOccHabAndroid(DetailsStation.LIBELLE_DATE_MAX)
            ChampJour(e.dateMax, { e.dateMax = it })
        }
        if (e.natureVisible) {
            LibelleOccHabAndroid(DetailsStation.LIBELLE_NATURE)
            ListeDeroulante(e.natures.libelles, e.positionNature, { e.positionNature = it })
        }
        if (e.altitudeMinVisible) ChampNombreAndroid(DetailsStation.LIBELLE_ALTITUDE_MIN, e.altitudeMin) { e.altitudeMin = it }
        if (e.altitudeMaxVisible) ChampNombreAndroid(DetailsStation.LIBELLE_ALTITUDE_MAX, e.altitudeMax) { e.altitudeMax = it }
        if (e.surfaceVisible) ChampNombreAndroid(DetailsStation.LIBELLE_SURFACE, e.surface) { e.surface = it }
        if (e.methodeVisible) {
            LibelleOccHabAndroid(DetailsStation.LIBELLE_METHODE)
            ListeDeroulante(e.methodes.libelles, e.positionMethode, { e.positionMethode = it })
        }
        if (e.commentaireVisible) {
            LibelleOccHabAndroid(DetailsStation.LIBELLE_COMMENTAIRE)
            ChampTexteFormulaire(
                e.commentaire,
                { e.commentaire = it },
                lignesMin = 2,
                uneLigne = false,
                clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
        e.anaEval?.let { SectionAnaEval(it) }
    }
}

/** `champNombre` : EditText aux chiffres seulement (TYPE_CLASS_NUMBER), sous son libellé. */
@Composable
private fun ChampNombreAndroid(libelle: String, valeur: String, onChange: (String) -> Unit) {
    LibelleOccHabAndroid(libelle)
    ChampTexteFormulaire(valeur, { onChange(it.filter(Char::isDigit)) }, clavier = KeyboardOptions(keyboardType = KeyboardType.Number))
}

// ─── iOS : OccHabInfosStationSheet de GeoMys-IOS ───

@Composable
private fun ContenuIos(e: EtatDetailsStation) {
    SectionIos(DetailsStation.LIBELLE_JDD) {
        if (e.datasets.isEmpty()) LigneTexteSeulIos(DetailsStation.AUCUN_JDD, couleur = CouleursGeoMys.rougePastille, taille = 13f)
        else ChampJddIos(e)
    }
    SectionIos(DetailsStation.LIBELLE_OBSERVATEURS) {
        if (e.observateurs.isEmpty()) LigneTexteSeulIos("—", couleur = CouleursGeoMys.blanc70)
        e.observateurs.forEachIndexed { i, (id, nom) ->
            if (i > 0) SeparateurIos(Modifier.padding(start = 16.dp))
            LigneIos {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(nom, fontSize = 17.sp, color = Color.White, modifier = Modifier.weight(1f))
                    Box(Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { e.retirerObservateur(id) }) {
                        SymboleSf("xmark", 13f, listOf(CouleursGeoMys.rougePastille), graisse = GraisseSymbole.DEMI_GRASSE, description = "Retirer $nom")
                    }
                }
            }
        }
        if (e.observateursProposes.isNotEmpty()) {
            SeparateurIos(Modifier.padding(start = 16.dp))
            LigneIos {
                Box(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Ajouter un observateur", fontSize = 17.sp, color = CouleursGeoMys.blanc70, modifier = Modifier.weight(1f))
                        SymboleSf("chevron.down", 12f, listOf(CouleursGeoMys.blanc70), graisse = GraisseSymbole.DEMI_GRASSE)
                    }
                    MenuSysteme(e.observateursProposes.map { it.second }, -1, e::ajouterObservateur, Modifier.matchParentSize())
                }
            }
        }
    }
    if (e.dateMinVisible) SectionIos(DetailsStation.LIBELLE_DATE_MIN) { LigneIos { ChampJour(e.dateMin, { e.dateMin = it }, fondIos = FOND_LIGNE) } }
    if (e.dateMaxVisible) SectionIos(DetailsStation.LIBELLE_DATE_MAX) { LigneIos { ChampJour(e.dateMax, { e.dateMax = it }, fondIos = FOND_LIGNE) } }
    if (e.natureVisible) {
        SectionIos(DetailsStation.LIBELLE_NATURE) { LigneChoixIos("", e.natures.libelles, e.positionNature) { e.positionNature = it } }
    }
    val chiffres = KeyboardOptions(keyboardType = KeyboardType.Number)
    val lignesDerivees = buildList<@Composable () -> Unit> {
        if (e.altitudeMinVisible) add { LigneNombreIos(DetailsStation.LIBELLE_ALTITUDE_MIN, e.altitudeMin, { e.altitudeMin = it.filter(Char::isDigit) }, chiffres) }
        if (e.altitudeMaxVisible) add { LigneNombreIos(DetailsStation.LIBELLE_ALTITUDE_MAX, e.altitudeMax, { e.altitudeMax = it.filter(Char::isDigit) }, chiffres) }
        if (e.surfaceVisible) add { LigneNombreIos(DetailsStation.LIBELLE_SURFACE, e.surface, { e.surface = it.filter(Char::isDigit) }, chiffres) }
        if (e.methodeVisible) add { LigneChoixIos(DetailsStation.LIBELLE_METHODE, e.methodes.libelles, e.positionMethode) { e.positionMethode = it } }
    }
    if (lignesDerivees.isNotEmpty()) {
        SectionIos(null) {
            lignesDerivees.forEachIndexed { i, ligne ->
                if (i > 0) SeparateurIos(Modifier.padding(start = 16.dp))
                ligne()
            }
        }
    }
    if (e.commentaireVisible) {
        SectionIos(DetailsStation.LIBELLE_COMMENTAIRE) {
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
    e.anaEval?.let { SectionAnaEval(it) }
}

/** Fond des lignes d'une feuille, sous le sélecteur de date (vue native). */
private val FOND_LIGNE = Color(0xFF2C2C2E)

/** Le jeu de données : champ à filtre (certaines instances en exposent des dizaines), la liste
 *  filtrée dessous (cinq au plus) tant qu'il a le focus. Le toucher le vide et ouvre la liste ;
 *  quitté sans choix, il réaffiche le choix courant. */
@Composable
private fun ChampJddIos(e: EtatDetailsStation) {
    val focus = LocalFocusManager.current
    var recherche by remember { mutableStateOf(e.texteDataset) }
    var ouverte by remember { mutableStateOf(false) }
    LigneIos {
        BasicTextField(
            value = recherche,
            onValueChange = { recherche = it },
            singleLine = true,
            textStyle = styleSaisieIos,
            cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
            modifier = Modifier.fillMaxWidth().onFocusChanged {
                if (it.isFocused) {
                    recherche = ""
                    ouverte = true
                } else {
                    recherche = e.texteDataset
                    ouverte = false
                }
            },
            decorationBox = { champ ->
                Box {
                    if (recherche.isEmpty()) Text(DetailsStation.INVITE_JDD, style = styleSaisieIos.copy(color = INVITE_IOS))
                    champ()
                }
            },
        )
    }
    if (ouverte) {
        DetailsReleveFormulaire.filtrer(e.datasets.map { it.second }, recherche).take(5).forEach { i ->
            SeparateurIos(Modifier.padding(start = 16.dp))
            LigneIos(Modifier.clickable {
                e.choisirDataset(i)
                focus.clearFocus()
            }) {
                Text(e.datasets[i].second, fontSize = 17.sp, color = Color.White)
            }
        }
    }
}

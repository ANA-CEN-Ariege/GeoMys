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

// Extrait du dialogue « Détails du relevé » du lot 6 (ui/occtax/DialogueDetailsReleve.kt), dont le dialogue
// des détails d'une station OccHab reprend les champs : le champ « menu déroulant » à recherche de
// GeoMys-Android (champ_dropdown_releve.xml) et, sous iOS, les lignes du jeu de données et des observateurs
// des détails d'une station (OccHabInfosStationSheet de GeoMys-IOS), que les deux dialogues partagent depuis
// le 20 octobre.

package fr.ariegenature.geomys.ui.composants

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import fr.ariegenature.geomys.occtax.DetailsReleveFormulaire
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.mdc_fleche_bas
import fr.ariegenature.geomys.ui.ressources.mdc_fleche_haut
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import org.jetbrains.compose.resources.painterResource

/** Nombre de valeurs à partir duquel une liste de choix se filtre au clavier ; en dessous, elle se
 *  choisit sans clavier, en touchant le champ (demande du 21 octobre 2026). */
internal const val SEUIL_RECHERCHE_LISTE = 20

// ─── Android ───

/** Le champ « menu déroulant » (champ_dropdown_releve.xml : TextInputLayout OutlinedBox
 *  ExposedDropdownMenu, 4 dp sous son titre). Toucher vide le texte et déroule toute la liste ; la
 *  frappe la filtre (accents et casse ignorés) ; un choix ferme le clavier. Moins de
 *  [SEUIL_RECHERCHE_LISTE] valeurs : le champ n'ouvre pas le clavier, le toucher déroule toute la
 *  liste. Refermée sans choix, la liste rend au champ le libellé choisi si [restaurer]. [onEffacer] :
 *  la croix des listes du web, qui vide le choix. */
@Composable
internal fun DeroulantAndroid(
    indication: String,
    libelles: List<String>,
    texteChoisi: String,
    restaurer: Boolean,
    onChoix: (Int) -> Unit,
    onEffacer: (() -> Unit)? = null,
) {
    val focus = LocalFocusManager.current
    val choisi by rememberUpdatedState(texteChoisi)
    val sansClavier = libelles.size < SEUIL_RECHERCHE_LISTE
    var texte by remember(texteChoisi) { mutableStateOf(texteChoisi) }
    var ouvert by remember { mutableStateOf(false) }
    var ancre by remember { mutableStateOf<Rect?>(null) }
    val positions = remember(libelles, texte, sansClavier) {
        if (sansClavier) libelles.indices.toList() else DetailsReleveFormulaire.filtrer(libelles, texte)
    }
    val interactions = remember { MutableInteractionSource() }
    LaunchedEffect(interactions, sansClavier) {
        interactions.interactions.collect {
            if (it is PressInteraction.Release) {
                if (!sansClavier) texte = ""
                ouvert = true
            }
        }
    }
    fun fermer() {
        ouvert = false
        if (restaurer) texte = choisi
    }
    Box(Modifier.padding(top = 4.dp)) {
        OutlinedTextField(
            value = texte,
            onValueChange = {
                texte = it
                // Seuil de 1 : la liste filtrée se déroule dès le premier caractère.
                ouvert = it.isNotEmpty()
            },
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { ancre = Rect(it.positionInWindow(), it.size.toSize()) }
                .onFocusChanged { if (!it.isFocused && ouvert) fermer() }
                .onInterceptKeyBeforeSoftKeyboard { touche ->
                    if (touche.key != Key.Back || !ouvert) return@onInterceptKeyBeforeSoftKeyboard false
                    if (touche.type == KeyEventType.KeyUp) fermer()
                    true
                },
            label = { Text(indication) },
            textStyle = styleSaisieMaterial(),
            singleLine = true,
            readOnly = sansClavier,
            interactionSource = interactions,
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onEffacer != null && choisi.isNotEmpty() && !ouvert) {
                        CaseDate("✕", couleur = MaterialTheme.colorScheme.error, contour = false, description = "Effacer", onClick = onEffacer)
                    }
                    Icon(painterResource(if (ouvert) Res.drawable.mdc_fleche_haut else Res.drawable.mdc_fleche_bas), contentDescription = null)
                }
            },
        )
        val zone = ancre
        if (ouvert && positions.isNotEmpty() && zone != null) {
            PopupListeAndroid(zone, positions, onFermer = ::fermer) { i ->
                LigneListeAndroid(libelles[i]) {
                    ouvert = false
                    texte = if (restaurer) libelles[i] else ""
                    focus.clearFocus()
                    onChoix(i)
                }
            }
        }
    }
}

/** Ligne de la liste d'un AutoCompleteTextView (`simple_dropdown_item_1line`). */
@Composable
internal fun LigneListeAndroid(texte: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(texte, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Le sélecteur multiple d'observateurs (`construireSelecteurMultiObservateurs`) des détails d'un
 *  relevé OccTax : un champ de recherche, d'invite [invite], qui AJOUTE à la liste affichée dessous,
 *  chaque entrée retirable par « ✕ ». Sans [proposes] (cache non chargé), les observateurs [choisis]
 *  restent affichés, retirables, sans champ d'ajout — « — » si rien. */
@Composable
internal fun ObservateursAndroid(
    invite: String,
    proposes: List<Pair<Int, String>>,
    choisis: List<Pair<Int, String>>,
    onAjouter: (Int) -> Unit,
    onRetirer: (Int) -> Unit,
) {
    if (proposes.isEmpty()) {
        if (choisis.isEmpty()) Text("—", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        DeroulantAndroid(invite, proposes.map { it.second }, "", restaurer = false, onChoix = onAjouter)
    }
    val erreur = MaterialTheme.colorScheme.error
    choisis.forEach { (id, nom) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(nom, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(
                "✕",
                fontSize = 16.sp,
                color = erreur,
                modifier = Modifier
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onRetirer(id) }
                    .padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
            )
        }
    }
}

// ─── iOS ───

/** Le pendant de [DeroulantAndroid] dans une [SectionIos]. Moins de [SEUIL_RECHERCHE_LISTE] valeurs :
 *  la ligne de choix et son menu, sans clavier. Au-delà, certaines instances exposant des dizaines de
 *  jeux de données : un champ à filtre, et toute la liste filtrée dessous, qui défile, tant qu'il a le
 *  focus. Le toucher le vide et ouvre la liste ; quitté, avec ou sans choix, il affiche [texteChoisi].
 *  [onEffacer] : la croix des listes du web, qui vide le choix. */
@Composable
internal fun LignesRechercheIos(
    indication: String,
    libelles: List<String>,
    texteChoisi: String,
    onChoix: (Int) -> Unit,
    onEffacer: (() -> Unit)? = null,
) {
    if (libelles.size < SEUIL_RECHERCHE_LISTE) {
        // Une valeur hors de la liste reste affichée, grisée, comme l'invite.
        LigneChoixIos("", libelles, libelles.indexOf(texteChoisi), invite = texteChoisi.ifEmpty { indication }, onEffacer = onEffacer, onChoix = onChoix)
        return
    }
    val focus = LocalFocusManager.current
    var focalise by remember { mutableStateOf(false) }
    var recherche by remember { mutableStateOf("") }
    // Le choix fait, le champ quitte le focus avant que [texteChoisi] change : il le lit à l'affichage.
    val texte = if (focalise) recherche else texteChoisi
    LigneIos {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = texte,
                onValueChange = { recherche = it },
                singleLine = true,
                textStyle = styleSaisieIos,
                cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
                modifier = Modifier.weight(1f).onFocusChanged {
                    if (it.isFocused != focalise) {
                        focalise = it.isFocused
                        recherche = ""
                    }
                },
                decorationBox = { champ ->
                    Box {
                        if (texte.isEmpty()) Text(indication, style = styleSaisieIos.copy(color = INVITE_IOS))
                        champ()
                    }
                },
            )
            if (onEffacer != null && texteChoisi.isNotEmpty() && !focalise) {
                Box(Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onEffacer)) {
                    SymboleSf("xmark", 13f, listOf(CouleursGeoMys.rougePastille), graisse = GraisseSymbole.DEMI_GRASSE, description = "Effacer")
                }
            }
        }
    }
    if (focalise) {
        // Toute la liste filtrée, qui défile sur cinq lignes et demie : seules les cinq premières
        // valeurs se voyaient.
        val trouvees = DetailsReleveFormulaire.filtrer(libelles, recherche)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
            items(trouvees) { i ->
                SeparateurIos(Modifier.padding(start = 16.dp))
                LigneIos(Modifier.clickable {
                    onChoix(i)
                    focus.clearFocus()
                }) {
                    Text(libelles[i], fontSize = 17.sp, color = Color.White)
                }
            }
        }
    }
}

/** Les observateurs dans une [SectionIos] : un par ligne, retirable par sa croix, puis la ligne
 *  d'ajout, d'invite [invite], et le menu des [proposes]. Comme [ObservateursAndroid] : sans [proposes]
 *  (cache non chargé), pas de ligne d'ajout, et « — » si personne n'est choisi. */
@Composable
internal fun LignesObservateursIos(
    invite: String,
    proposes: List<Pair<Int, String>>,
    choisis: List<Pair<Int, String>>,
    onAjouter: (Int) -> Unit,
    onRetirer: (Int) -> Unit,
) {
    if (proposes.isEmpty() && choisis.isEmpty()) LigneTexteSeulIos("—", couleur = CouleursGeoMys.blanc70)
    choisis.forEachIndexed { i, (id, nom) ->
        if (i > 0) SeparateurIos(Modifier.padding(start = 16.dp))
        LigneIos {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(nom, fontSize = 17.sp, color = Color.White, modifier = Modifier.weight(1f))
                Box(Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { onRetirer(id) }) {
                    SymboleSf("xmark", 13f, listOf(CouleursGeoMys.rougePastille), graisse = GraisseSymbole.DEMI_GRASSE, description = "Retirer $nom")
                }
            }
        }
    }
    if (proposes.isNotEmpty()) {
        if (choisis.isNotEmpty()) SeparateurIos(Modifier.padding(start = 16.dp))
        LigneIos {
            Box(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(invite, fontSize = 17.sp, color = CouleursGeoMys.blanc70, modifier = Modifier.weight(1f))
                    SymboleSf("chevron.down", 12f, listOf(CouleursGeoMys.blanc70), graisse = GraisseSymbole.DEMI_GRASSE)
                }
                MenuSysteme(proposes.map { it.second }, -1, onAjouter, Modifier.matchParentSize())
            }
        }
    }
}

/** Bouton « ＋ » / « ✕ » de la date de fin : symbole de 14 pt gras dans un cadre de 40 × 36 pt
 *  au filet de sa couleur à 60 %, coins de 8 pt. */
@Composable
internal fun BoutonCadreIos(symbole: String, couleur: Color, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp, 36.dp)
            .border(1.dp, couleur.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SymboleSf(symbole, 14f, listOf(couleur), graisse = GraisseSymbole.GRASSE, description = description)
    }
}

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

// Propre au portage : le champ « Serveur GeoNature » des Paramètres, qui remplace « URL du serveur
// GeoNature » (demande du 13 novembre 2026) : un serveur de la liste, ou une adresse saisie.

package fr.ariegenature.geomys.ui.parametres

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import fr.ariegenature.geomys.network.ServeurGeoNature
import fr.ariegenature.geomys.network.ServeursGeoNature
import fr.ariegenature.geomys.ui.composants.ChampAvecLibelleIos
import fr.ariegenature.geomys.ui.composants.FOND_LIGNE_FEUILLE_IOS
import fr.ariegenature.geomys.ui.composants.GraisseSymbole
import fr.ariegenature.geomys.ui.composants.INVITE_IOS
import fr.ariegenature.geomys.ui.composants.PopupListeAndroid
import fr.ariegenature.geomys.ui.composants.SeparateurIos
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.TypeChamp
import fr.ariegenature.geomys.ui.composants.clavier
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.styleSaisieIos
import fr.ariegenature.geomys.ui.composants.styleSaisieMaterial
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.mdc_fleche_bas
import fr.ariegenature.geomys.ui.ressources.mdc_fleche_haut
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import org.jetbrains.compose.resources.painterResource

private const val LIBELLE = "Serveur GeoNature"
private const val INVITE = "Choisir dans la liste ou saisir une URL"

/** Le serveur : la saisie est l'adresse, et filtre la liste des [ServeursGeoNature] par nom ou par
 *  adresse ; un serveur choisi y met son adresse. Toucher le champ déroule toute la liste.
 *  - Android : le champ URL d'avant (TextInputLayout OutlinedBox, [aide] dessous), sa liste celle
 *    d'un AutoCompleteTextView ([PopupListeAndroid]).
 *  - iOS : le champ de `ChampTexteSaisie`, la liste dessous, dans la page, tant qu'il a le focus ou
 *    que le chevron l'ouvre ; l'avertissement http est affiché par l'écran.
 *  [versionDeTest] : la liste propose aussi les serveurs des tests, jamais dans une release. */
@Composable
internal fun ChampServeur(
    valeur: String,
    onChange: (String) -> Unit,
    aide: String? = null,
    couleurAide: Color = Color.Unspecified,
    versionDeTest: Boolean = false,
) {
    if (estIos) ChampServeurIos(valeur, onChange, versionDeTest) else ChampServeurAndroid(valeur, onChange, aide, couleurAide, versionDeTest)
}

@Composable
private fun ChampServeurAndroid(valeur: String, onChange: (String) -> Unit, aide: String?, couleurAide: Color, versionDeTest: Boolean) {
    val focus = LocalFocusManager.current
    var ouvert by remember { mutableStateOf(false) }
    var filtre by remember { mutableStateOf("") }
    var ancre by remember { mutableStateOf<Rect?>(null) }
    val proposes = remember(filtre, versionDeTest) { ServeursGeoNature.filtrer(filtre, versionDeTest) }
    val interactions = remember { MutableInteractionSource() }
    LaunchedEffect(interactions) {
        interactions.interactions.collect {
            if (it is PressInteraction.Release) {
                filtre = ""
                ouvert = true
            }
        }
    }
    Box {
        OutlinedTextField(
            value = valeur,
            onValueChange = {
                onChange(it)
                filtre = it
                // La liste, sans focus, se referme à chaque touche du clavier : la frappe la rouvre.
                ouvert = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { ancre = Rect(it.positionInWindow(), it.size.toSize()) }
                .onFocusChanged { if (!it.isFocused) ouvert = false }
                // Retour ferme d'abord la liste, avant le clavier.
                .onInterceptKeyBeforeSoftKeyboard { touche ->
                    if (touche.key != Key.Back || !ouvert) return@onInterceptKeyBeforeSoftKeyboard false
                    if (touche.type == KeyEventType.KeyUp) ouvert = false
                    true
                },
            label = { Text(LIBELLE) },
            placeholder = { Text(INVITE) },
            textStyle = styleSaisieMaterial(),
            singleLine = true,
            keyboardOptions = TypeChamp.URL.clavier(),
            interactionSource = interactions,
            supportingText = aide?.let { { Text(it, color = couleurAide) } },
            trailingIcon = {
                Icon(painterResource(if (ouvert) Res.drawable.mdc_fleche_haut else Res.drawable.mdc_fleche_bas), contentDescription = null)
            },
        )
        val zone = ancre
        if (ouvert && proposes.isNotEmpty() && zone != null) {
            PopupListeAndroid(zone, proposes, onFermer = { ouvert = false }) { serveur ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onChange(serveur.url)
                            ouvert = false
                            focus.clearFocus()
                        }
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(serveur.nom, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(serveur.url, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ChampServeurIos(valeur: String, onChange: (String) -> Unit, versionDeTest: Boolean) {
    val focus = LocalFocusManager.current
    var ouvert by remember { mutableStateOf(false) }
    var filtre by remember { mutableStateOf("") }
    ChampAvecLibelleIos(LIBELLE) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = valeur,
                onValueChange = {
                    onChange(it)
                    filtre = it
                    ouvert = true
                },
                singleLine = true,
                textStyle = styleSaisieIos,
                cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
                keyboardOptions = TypeChamp.URL.clavier(),
                modifier = Modifier.weight(1f).onFocusChanged {
                    if (it.isFocused) filtre = ""
                    ouvert = it.isFocused
                },
                decorationBox = { champ ->
                    Box {
                        if (valeur.isEmpty()) Text(INVITE, style = styleSaisieIos.copy(color = INVITE_IOS), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        champ()
                    }
                },
            )
            Box(
                Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "Liste des serveurs") {
                    filtre = ""
                    ouvert = !ouvert
                },
            ) {
                SymboleSf(if (ouvert) "chevron.up" else "chevron.down", 13f, listOf(CouleursGeoMys.blanc70), graisse = GraisseSymbole.DEMI_GRASSE)
            }
        }
        val proposes = ServeursGeoNature.filtrer(filtre, versionDeTest)
        if (ouvert && proposes.isNotEmpty()) {
            val forme = RoundedCornerShape(10.dp)
            LazyColumn(
                Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .heightIn(max = 250.dp)
                    .clip(forme)
                    .background(FOND_LIGNE_FEUILLE_IOS, forme),
            ) {
                itemsIndexed(proposes) { i, serveur ->
                    if (i > 0) SeparateurIos(Modifier.padding(start = 12.dp))
                    LigneServeurIos(serveur) {
                        onChange(serveur.url)
                        ouvert = false
                        focus.clearFocus()
                    }
                }
            }
        }
    }
}

@Composable
private fun LigneServeurIos(serveur: ServeurGeoNature, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(serveur.nom, fontSize = 15.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(serveur.url, fontSize = 12.sp, color = CouleursGeoMys.blanc70, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

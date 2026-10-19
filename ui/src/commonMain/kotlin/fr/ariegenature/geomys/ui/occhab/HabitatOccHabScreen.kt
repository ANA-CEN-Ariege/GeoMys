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

// Le formulaire d'un habitat OccHab : fragment_occhab_habitat.xml et OccHabHabitatFragment de GeoMys-Android
// @ cdd5196 sous Android, OccHabHabitatView de GeoMys-IOS sous iOS.

package fr.ariegenature.geomys.ui.occhab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import fr.ariegenature.geomys.occhab.CarteOccHab
import fr.ariegenature.geomys.occhab.HabitatOccHab
import fr.ariegenature.geomys.occhab.StationOccHab
import fr.ariegenature.geomys.ui.composants.AlerteEchecEcriture
import fr.ariegenature.geomys.ui.composants.BandeauNavigation
import fr.ariegenature.geomys.ui.composants.BoutonIos
import fr.ariegenature.geomys.ui.composants.BoutonMaterial
import fr.ariegenature.geomys.ui.composants.ChampTexteFormulaire
import fr.ariegenature.geomys.ui.composants.FOND_LIGNE_FORMULAIRE_IOS
import fr.ariegenature.geomys.ui.composants.INVITE_IOS
import fr.ariegenature.geomys.ui.composants.LigneChoixIos
import fr.ariegenature.geomys.ui.composants.LigneIos
import fr.ariegenature.geomys.ui.composants.LigneListeAndroid
import fr.ariegenature.geomys.ui.composants.LigneTexteIos
import fr.ariegenature.geomys.ui.composants.LigneTexteSeulIos
import fr.ariegenature.geomys.ui.composants.ListeDeroulante
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.PopupListeAndroid
import fr.ariegenature.geomys.ui.composants.SectionIos
import fr.ariegenature.geomys.ui.composants.SeparateurIos
import fr.ariegenature.geomys.ui.composants.afficher
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.styleLibelleMaterial
import fr.ariegenature.geomys.ui.composants.styleSaisieIos
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys

/** `?attr/colorControlHighlight` du thème sombre : le filet sous le bandeau. */
private val SURBRILLANCE = Color(0x33FFFFFF)

/** L'écran d'un habitat ; [onRetour] : l'habitat est enregistré, retour à la station. Le retour
 *  système (Android) ou le balayage depuis le bord (iOS) laisse la station sans cet habitat. */
@Composable
fun HabitatOccHabScreen(vm: HabitatOccHabViewModel, onAccueil: () -> Unit, onRetour: () -> Unit) {
    val toasts = LocalToasts.current
    LaunchedEffect(vm) {
        vm.ouverture()
        vm.evenements.collect { evenement ->
            when (evenement) {
                is EvenementHabitatOccHab.Message -> toasts.afficher(evenement.texte, evenement.longue)
                EvenementHabitatOccHab.Retour -> onRetour()
                EvenementHabitatOccHab.Accueil -> onAccueil()
            }
        }
    }
    FondGeoMys {
        if (estIos) ContenuIos(vm, onAccueil) else ContenuAndroid(vm, onAccueil)
        AlerteEchecEcriture(vm.echecEcriture, vm::fermerEchecEcriture, StationOccHab.CONSIGNE_HABITAT)
    }
}

/** Le recouvrement tapé : chiffres et séparateur décimal (la virgule des claviers français vaut le
 *  point à la lecture) — le DigitsKeyListener d'un EditText `number|numberDecimal`. */
private fun filtrerRecouvrement(texte: String) = texte.filter { it.isDigit() || it == '.' || it == ',' }

/** Re-toucher le champ habitat quand il a déjà le focus (le premier toucher ne fait que le lui
 *  donner, comme le clic d'une EditText). */
@Composable
private fun SuivreRetouche(interactions: MutableInteractionSource, focalise: Boolean, onRetouche: () -> Unit) {
    val focaliseActuel by rememberUpdatedState(focalise)
    LaunchedEffect(interactions) {
        var avaitFocus = false
        interactions.interactions.collect {
            when (it) {
                is PressInteraction.Press -> avaitFocus = focaliseActuel
                is PressInteraction.Release -> if (avaitFocus) onRetouche()
            }
        }
    }
}

// ─── Android : fragment_occhab_habitat.xml, les champs construits par construireFormulaire ───

@Composable
private fun ContenuAndroid(vm: HabitatOccHabViewModel, onAccueil: () -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).union(WindowInsets.ime))) {
        BandeauNavigation(CarteOccHab.LIBELLE, onAccueil, Modifier.padding(start = 12.dp, top = 12.dp))
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(1.dp).background(SURBRILLANCE))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(HabitatOccHab.TITRE, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            LibelleOccHabAndroid(HabitatOccHab.LIBELLE_HABITAT)
            ChampHabitatAndroid(vm)
            Text(
                HabitatOccHab.AIDE_FACULTATIF,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            vm.interet?.let { NomenclatureAndroid(it) }
            if (vm.determinateurVisible) {
                LibelleOccHabAndroid(HabitatOccHab.LIBELLE_DETERMINATEUR)
                ChampTexteFormulaire(
                    vm.determinateur,
                    { vm.determinateur = it },
                    indication = HabitatOccHab.LIBELLE_DETERMINATEUR,
                    clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            }
            vm.typeDetermination?.let { NomenclatureAndroid(it) }
            vm.technique?.let { NomenclatureAndroid(it) }
            if (vm.precisionVisible) {
                LibelleOccHabAndroid(HabitatOccHab.libellePrecision(vm.precisionRequise))
                ChampTexteFormulaire(
                    vm.precision,
                    { vm.precision = it },
                    uneLigne = false,
                    indication = HabitatOccHab.libellePrecision(false),
                    clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            if (vm.recouvrementVisible) {
                LibelleOccHabAndroid(HabitatOccHab.LIBELLE_RECOUVREMENT)
                ChampTexteFormulaire(
                    vm.recouvrement,
                    { vm.recouvrement = filtrerRecouvrement(it) },
                    indication = HabitatOccHab.INVITE_RECOUVREMENT,
                    clavier = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            vm.abondance?.let { NomenclatureAndroid(it) }
            vm.anaEval?.let { SectionAnaEval(it) }
        }
        // Barre du bas (colorSurface) : « Valider l'habitat » vert, au rendu désactivé de Material 3
        // tant que l'obligatoire n'est pas rempli (sélecteurs occhab_valider_tint / _texte).
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 10.dp)) {
            val actif = vm.peutValider
            val surface = MaterialTheme.colorScheme.onSurface
            val contenu = if (actif) Color.White else surface.copy(alpha = 0.38f)
            BoutonMaterial(
                onClick = vm::valider,
                modifier = Modifier.fillMaxWidth(),
                actif = actif,
                fond = if (actif) CouleursGeoMys.vert85 else surface.copy(alpha = 0.12f),
                couleurContenu = contenu,
            ) { Text(HabitatOccHab.VALIDER, style = styleLibelleMaterial(contenu)) }
        }
    }
}

/** Une nomenclature : son libellé, puis le Spinner « — Non renseigné — » et ses valeurs. */
@Composable
private fun NomenclatureAndroid(champ: ChampNomenclatureHabitat) {
    LibelleOccHabAndroid(champ.definition.libelle)
    ListeDeroulante(champ.liste.libelles, champ.position, { champ.position = it })
}

/** Habitat : MaterialAutoCompleteTextView (EditText souligné), « Rechercher un habitat (HABREF)… » ;
 *  les résultats se déroulent sous le champ tant qu'il a le focus. */
@Composable
private fun ChampHabitatAndroid(vm: HabitatOccHabViewModel) {
    val focus = LocalFocusManager.current
    val interactions = remember { MutableInteractionSource() }
    val focalise by interactions.collectIsFocusedAsState()
    SuivreRetouche(interactions, focalise, vm::toucherHabitat)
    var ancre by remember { mutableStateOf<Rect?>(null) }
    Box(
        Modifier
            .onGloballyPositioned { ancre = Rect(it.positionInWindow(), it.size.toSize()) }
            .onFocusChanged { if (!it.hasFocus) vm.resultatsDeroules = false },
    ) {
        ChampTexteFormulaire(
            vm.texteHabitat,
            vm::saisirHabitat,
            indication = HabitatOccHab.INVITE_HABITAT,
            interactions = interactions,
        )
        val zone = ancre
        if (focalise && vm.resultatsDeroules && vm.resultats.isNotEmpty() && zone != null) {
            PopupListeAndroid(zone, vm.resultats.toList(), onFermer = { vm.resultatsDeroules = false }) { h ->
                LigneListeAndroid(h.libelle) {
                    vm.choisirHabitat(h)
                    focus.clearFocus()
                }
            }
        }
    }
}

// ─── iOS : OccHabHabitatView de GeoMys-IOS ───

@Composable
private fun ContenuIos(vm: HabitatOccHabViewModel, onAccueil: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        BandeauNavigation(CarteOccHab.LIBELLE, onAccueil)
        // Le `Form` : une section par champ, sur des lignes #1C1C1E.
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SectionIos(HabitatOccHab.LIBELLE_HABITAT, fond = FOND_LIGNE_FORMULAIRE_IOS) {
                ChampHabitatIos(vm)
                SeparateurIos(Modifier.padding(start = 16.dp))
                LigneTexteSeulIos(HabitatOccHab.AIDE_FACULTATIF, couleur = CouleursGeoMys.blanc70, taille = 12f)
            }
            vm.interet?.let { NomenclatureIos(it) }
            if (vm.determinateurVisible) {
                SectionIos(HabitatOccHab.LIBELLE_DETERMINATEUR, fond = FOND_LIGNE_FORMULAIRE_IOS) {
                    LigneTexteIos(
                        vm.determinateur,
                        { vm.determinateur = it },
                        HabitatOccHab.LIBELLE_DETERMINATEUR,
                        clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                }
            }
            vm.typeDetermination?.let { NomenclatureIos(it) }
            vm.technique?.let { NomenclatureIos(it) }
            if (vm.precisionVisible) {
                SectionIos(HabitatOccHab.libellePrecision(vm.precisionRequise), fond = FOND_LIGNE_FORMULAIRE_IOS) {
                    LigneTexteIos(
                        vm.precision,
                        { vm.precision = it },
                        HabitatOccHab.libellePrecision(false),
                        lignesMin = 2,
                        lignesMax = 4,
                        clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
            }
            if (vm.recouvrementVisible) {
                SectionIos(HabitatOccHab.LIBELLE_RECOUVREMENT, fond = FOND_LIGNE_FORMULAIRE_IOS) {
                    LigneTexteIos(
                        vm.recouvrement,
                        { vm.recouvrement = filtrerRecouvrement(it) },
                        HabitatOccHab.INVITE_RECOUVREMENT,
                        clavier = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
            vm.abondance?.let { NomenclatureIos(it) }
            vm.anaEval?.let { SectionAnaEval(it, fondIos = FOND_LIGNE_FORMULAIRE_IOS) }
        }
        // Pleine largeur, au ras du bas : le vert estompé tant que l'obligatoire n'est pas rempli.
        val actif = vm.peutValider
        BoutonIos(vm::valider, Modifier.fillMaxWidth(), actif, opaciteAppui = 0.6f) {
            Box(
                Modifier.fillMaxWidth().background(CouleursGeoMys.vert.copy(alpha = if (actif) 1f else 0.4f)).padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(HabitatOccHab.VALIDER, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

/** Une nomenclature : Picker en menu, « — Non renseigné — » en tête. */
@Composable
private fun NomenclatureIos(champ: ChampNomenclatureHabitat) {
    SectionIos(champ.definition.libelle, fond = FOND_LIGNE_FORMULAIRE_IOS) {
        LigneChoixIos("", champ.liste.libelles, champ.position) { champ.position = it }
    }
}

/** Habitat : TextField nu de la section ; les résultats en lignes dessous tant qu'il a le focus. */
@Composable
private fun ChampHabitatIos(vm: HabitatOccHabViewModel) {
    val focus = LocalFocusManager.current
    val interactions = remember { MutableInteractionSource() }
    val focalise by interactions.collectIsFocusedAsState()
    SuivreRetouche(interactions, focalise, vm::toucherHabitat)
    LigneIos {
        BasicTextField(
            value = vm.texteHabitat,
            onValueChange = vm::saisirHabitat,
            singleLine = true,
            textStyle = styleSaisieIos,
            cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            interactionSource = interactions,
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) vm.resultatsDeroules = false },
            decorationBox = { champ ->
                Box {
                    if (vm.texteHabitat.isEmpty()) Text(HabitatOccHab.INVITE_HABITAT, style = styleSaisieIos.copy(color = INVITE_IOS))
                    champ()
                }
            },
        )
    }
    if (focalise && vm.resultatsDeroules) {
        vm.resultats.toList().forEach { h ->
            SeparateurIos(Modifier.padding(start = 16.dp))
            LigneIos(Modifier.clickable {
                vm.choisirHabitat(h)
                focus.clearFocus()
            }) {
                Text(h.libelle, fontSize = 17.sp, color = Color.White)
            }
        }
    }
}

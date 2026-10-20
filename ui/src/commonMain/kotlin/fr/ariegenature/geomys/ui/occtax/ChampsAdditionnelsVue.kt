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

// Les widgets des champs additionnels OccTax : buildWidget d'AdditionalFieldsRenderer (GeoMys-Android
// @ cdd5196, ui/saisie/AdditionalFieldsRenderer.kt) sous Android, AdditionalFieldsRenderer de GeoMys-IOS
// (UI/Saisie/AdditionalFieldsRenderer.swift) sous iOS. L'état et l'encodage des valeurs sont dans le socle
// (saisie/ChampsAdditionnels.kt). Dans la carte des détails d'un relevé, sous iOS, chaque champ a sa section,
// comme les champs d'une station OccHab (demande du 20 octobre).

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.model.AdditionalFieldDef
import fr.ariegenature.geomys.model.WidgetType
import fr.ariegenature.geomys.saisie.ChampsAdditionnels
import fr.ariegenature.geomys.saisie.EtatChampsAdditionnels
import fr.ariegenature.geomys.ui.composants.ClavierNombresEtPonctuation
import fr.ariegenature.geomys.ui.composants.ClavierNumeriqueSigne
import fr.ariegenature.geomys.ui.composants.FOND_LIGNE_FEUILLE_IOS
import fr.ariegenature.geomys.ui.composants.GRIS_TITRE_CHAMP
import fr.ariegenature.geomys.ui.composants.Interrupteur
import fr.ariegenature.geomys.ui.composants.LigneChoixIos
import fr.ariegenature.geomys.ui.composants.LigneIos
import fr.ariegenature.geomys.ui.composants.LigneNombreIos
import fr.ariegenature.geomys.ui.composants.LigneTexteIos
import fr.ariegenature.geomys.ui.composants.LigneTexteSeulIos
import fr.ariegenature.geomys.ui.composants.ListeDeroulante
import fr.ariegenature.geomys.ui.composants.MenuSysteme
import fr.ariegenature.geomys.ui.composants.SectionIos
import fr.ariegenature.geomys.ui.composants.SeparateurIos
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.styleSaisieIos
import fr.ariegenature.geomys.ui.composants.styleSaisieMaterial
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys

/** `.secondary` de SwiftUI en mode sombre : libellés et descriptions des champs additionnels. */
private val SECONDAIRE_IOS = Color(0x99EBEBF5)

/** Les champs additionnels [etat], dans l'ordre de leurs définitions. L'état du socle n'étant pas
 *  observable, chaque saisie fait relire les champs : la liste se recompose, et chaque champ avec
 *  elle (ils ne sont pas sautables, leurs paramètres ne changeant pas). */
@Composable
fun ChampsAdditionnelsVue(etat: EtatChampsAdditionnels, modifier: Modifier = Modifier) {
    var revision by remember(etat) { mutableIntStateOf(0) }
    val relire: () -> Unit = { revision++ }
    // Lu ici : chaque saisie recompose la liste.
    revision.let {}
    if (estIos) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (def in etat.defs) ChampAdditionnelIos(def, etat, relire)
        }
    } else {
        Column(modifier) {
            for (def in etat.defs) ChampAdditionnelAndroid(def, etat, relire)
        }
    }
}

/** Les champs additionnels [etat] dans la carte des détails d'un relevé, sous iOS : une [SectionIos]
 *  par champ, son libellé sur le fond, sa description dessous. Les widgets d'[ChampAdditionnelIos], en
 *  lignes de formulaire. */
@Composable
internal fun ChampsAdditionnelsSectionsIos(etat: EtatChampsAdditionnels) {
    var revision by remember(etat) { mutableIntStateOf(0) }
    val relire: () -> Unit = { revision++ }
    // Lu ici : chaque saisie recompose les sections.
    revision.let {}
    for (def in etat.defs) SectionChampAdditionnelIos(def, etat, relire)
}

private fun libelleChamp(def: AdditionalFieldDef) = def.fieldLabel + if (def.required) " *" else ""

@NonSkippableComposable
@Composable
private fun ChampAdditionnelAndroid(def: AdditionalFieldDef, etat: EtatChampsAdditionnels, relire: () -> Unit) {
    val nom = def.fieldName
    val couleurs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(libelleChamp(def), fontSize = 13.sp, color = GRIS_TITRE_CHAMP)
        when (def.widget) {
            WidgetType.SELECT, WidgetType.NOMENCLATURE ->
                ListeDeroulante(ChampsAdditionnels.libellesListe(def), etat.position(nom), { etat.choisirPosition(nom, it); relire() })
            WidgetType.RADIO -> {
                val codes = ChampsAdditionnels.codesListe(def)
                def.fieldValues.forEachIndexed { i, libelle ->
                    val code = codes.getOrElse(i) { libelle }
                    Row(
                        Modifier.fillMaxWidth().clickable { etat.cliquerRadio(nom, code); relire() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = etat.selectionRadio(nom) == code,
                            onClick = { etat.cliquerRadio(nom, code); relire() },
                            colors = RadioButtonDefaults.colors(selectedColor = CouleursGeoMys.orange, unselectedColor = couleurs.onSurfaceVariant),
                        )
                        Text(libelle, fontSize = 14.sp)
                    }
                }
            }
            WidgetType.CHECKBOX -> {
                val couleursCase = CheckboxDefaults.colors(checkedColor = CouleursGeoMys.orange, uncheckedColor = couleurs.onSurfaceVariant)
                if (ChampsAdditionnels.estCasesMultiples(def)) {
                    val cases = etat.cases(nom)
                    def.fieldValues.forEachIndexed { i, libelle ->
                        Row(
                            Modifier.fillMaxWidth().clickable { etat.cocherCase(nom, i, !cases[i]); relire() },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(cases.getOrElse(i) { false }, { etat.cocherCase(nom, i, it); relire() }, colors = couleursCase)
                            Text(libelle, fontSize = 14.sp)
                        }
                    }
                } else {
                    Checkbox(etat.cases(nom).firstOrNull() == true, { etat.cocher(nom, it); relire() }, colors = couleursCase)
                }
            }
            // Nombre : le clavier signé et décimal d'AdditionalFieldsRenderer.
            else -> ClavierNumeriqueSigne {
                OutlinedTextField(
                    value = etat.texte(nom),
                    onValueChange = { etat.saisirTexte(nom, it); relire() },
                    modifier = Modifier.fillMaxWidth(),
                    label = etat.indication(def)?.let { { Text(it) } },
                    textStyle = styleSaisieMaterial(),
                    singleLine = def.widget != WidgetType.TEXTAREA,
                    minLines = if (def.widget == WidgetType.TEXTAREA) 2 else 1,
                    keyboardOptions = if (def.widget == WidgetType.NUMBER) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
                )
            }
        }
        def.description?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 11.sp, color = GRIS_TITRE_CHAMP) }
    }
}

@NonSkippableComposable
@Composable
private fun ChampAdditionnelIos(def: AdditionalFieldDef, etat: EtatChampsAdditionnels, relire: () -> Unit) {
    val nom = def.fieldName
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(libelleChamp(def), fontSize = 12.sp, color = SECONDAIRE_IOS)
        when (def.widget) {
            WidgetType.SELECT, WidgetType.NOMENCLATURE -> {
                val libelles = ChampsAdditionnels.libellesListe(def)
                if (libelles.size <= 1) {
                    Text(
                        if (def.widget == WidgetType.SELECT) "— pas d'options disponibles —" else "— nomenclature non chargée —",
                        fontSize = 11.sp,
                        color = Color(0xFFFF9500),
                    )
                } else {
                    // Picker en menu : la valeur choisie et les chevrons, teintés.
                    Box {
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(libelles.getOrNull(etat.position(nom)).orEmpty(), fontSize = 17.sp, color = CouleursGeoMys.jauneClair)
                            Spacer(Modifier.width(4.dp))
                            SymboleSf("chevron.up.chevron.down", 13f, listOf(CouleursGeoMys.jauneClair))
                        }
                        MenuSysteme(libelles, etat.position(nom), { etat.choisirPosition(nom, it); relire() }, Modifier.matchParentSize())
                    }
                }
            }
            WidgetType.RADIO -> {
                val codes = ChampsAdditionnels.codesListe(def)
                val options = def.fieldValues.mapIndexed { i, libelle -> codes.getOrElse(i) { libelle } to libelle }
                val pastille: @Composable (Pair<String, String>) -> Unit = { (code, libelle) ->
                    val choisi = etat.selectionRadio(nom) == code
                    Row(
                        Modifier.clickable { etat.cliquerRadio(nom, code); relire() },
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SymboleSf(if (choisi) "largecircle.fill.circle" else "circle", 18f, listOf(if (choisi) CouleursGeoMys.vert else SECONDAIRE_IOS))
                        Text(libelle, color = Color.White)
                    }
                }
                // Horizontal jusqu'à 4 options, vertical au-delà.
                if (options.size > 4) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { pastille(it) } }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { options.forEach { pastille(it) } }
                }
            }
            WidgetType.CHECKBOX -> if (ChampsAdditionnels.estCasesMultiples(def)) {
                val cases = etat.cases(nom)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    def.fieldValues.forEachIndexed { i, libelle ->
                        val coche = cases.getOrElse(i) { false }
                        Row(
                            Modifier.clickable { etat.cocherCase(nom, i, !coche); relire() },
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SymboleSf(if (coche) "checkmark.square.fill" else "square", 17f, listOf(if (coche) CouleursGeoMys.vert else SECONDAIRE_IOS))
                            Text(libelle, color = Color.White)
                        }
                    }
                }
            } else {
                Interrupteur(etat.cases(nom).firstOrNull() == true, { etat.cocher(nom, it); relire() }, teinteIos = CouleursGeoMys.vert)
            }
            WidgetType.TEXTAREA -> BasicTextField(
                value = etat.texte(nom),
                onValueChange = { etat.saisirTexte(nom, it); relire() },
                textStyle = styleSaisieIos,
                cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 120.dp).background(Color.Black).padding(4.dp),
            )
            WidgetType.NUMBER -> BasicTextField(
                value = etat.texte(nom),
                // La virgule des claviers français est gardée : l'envoi la convertit en point.
                onValueChange = { v -> etat.saisirTexte(nom, v.filter { it in "0123456789.,-" }); relire() },
                singleLine = true,
                textStyle = styleSaisieIos,
                cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
                keyboardOptions = ClavierNombresEtPonctuation,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> BasicTextField(
                value = etat.texte(nom),
                onValueChange = { etat.saisirTexte(nom, it); relire() },
                singleLine = true,
                textStyle = styleSaisieIos,
                cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        def.description?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 11.sp, color = SECONDAIRE_IOS) }
    }
}

@NonSkippableComposable
@Composable
private fun SectionChampAdditionnelIos(def: AdditionalFieldDef, etat: EtatChampsAdditionnels, relire: () -> Unit) {
    val nom = def.fieldName
    Column {
        SectionIos(libelleChamp(def)) {
            when (def.widget) {
                WidgetType.SELECT, WidgetType.NOMENCLATURE -> {
                    val libelles = ChampsAdditionnels.libellesListe(def)
                    if (libelles.size <= 1) {
                        LigneTexteSeulIos(
                            if (def.widget == WidgetType.SELECT) "— pas d'options disponibles —" else "— nomenclature non chargée —",
                            couleur = Color(0xFFFF9500),
                            taille = 13f,
                        )
                    } else {
                        LigneChoixIos("", libelles, etat.position(nom)) { etat.choisirPosition(nom, it); relire() }
                    }
                }
                WidgetType.RADIO -> {
                    val codes = ChampsAdditionnels.codesListe(def)
                    def.fieldValues.forEachIndexed { i, libelle ->
                        val code = codes.getOrElse(i) { libelle }
                        if (i > 0) SeparateurIos(Modifier.padding(start = 16.dp))
                        LigneOptionIos(libelle, etat.selectionRadio(nom) == code, "largecircle.fill.circle", "circle") {
                            etat.cliquerRadio(nom, code)
                            relire()
                        }
                    }
                }
                WidgetType.CHECKBOX -> if (ChampsAdditionnels.estCasesMultiples(def)) {
                    val cases = etat.cases(nom)
                    def.fieldValues.forEachIndexed { i, libelle ->
                        val coche = cases.getOrElse(i) { false }
                        if (i > 0) SeparateurIos(Modifier.padding(start = 16.dp))
                        LigneOptionIos(libelle, coche, "checkmark.square.fill", "square") {
                            etat.cocherCase(nom, i, !coche)
                            relire()
                        }
                    }
                } else {
                    LigneIos {
                        Interrupteur(
                            etat.cases(nom).firstOrNull() == true,
                            { etat.cocher(nom, it); relire() },
                            teinteIos = CouleursGeoMys.vert,
                            fondIos = FOND_LIGNE_FEUILLE_IOS,
                        )
                    }
                }
                WidgetType.TEXTAREA -> LigneTexteIos(
                    etat.texte(nom),
                    { etat.saisirTexte(nom, it); relire() },
                    etat.indication(def).orEmpty(),
                    lignesMin = 2,
                    lignesMax = 5,
                    clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                // La virgule des claviers français est gardée : l'envoi la convertit en point.
                WidgetType.NUMBER -> LigneNombreIos("", etat.texte(nom), { v -> etat.saisirTexte(nom, v.filter { it in "0123456789.,-" }); relire() })
                else -> LigneTexteIos(
                    etat.texte(nom),
                    { etat.saisirTexte(nom, it); relire() },
                    etat.indication(def).orEmpty(),
                    clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
        }
        def.description?.takeIf { it.isNotEmpty() }?.let {
            Text(it, fontSize = 13.sp, color = SECONDAIRE_IOS, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
        }
    }
}

/** Une option d'un bouton radio ou d'une case multiple, sur sa ligne : le symbole [plein] (vert) ou
 *  [vide], puis le libellé. */
@Composable
private fun LigneOptionIos(libelle: String, choisie: Boolean, plein: String, vide: String, onClick: () -> Unit) {
    LigneIos(Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SymboleSf(if (choisie) plein else vide, 20f, listOf(if (choisie) CouleursGeoMys.vert else SECONDAIRE_IOS))
            Text(libelle, fontSize = 17.sp, color = Color.White)
        }
    }
}

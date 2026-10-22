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

// Les lignes d'un `Form` groupé de SwiftUI, sous l'allure de GeoMys-IOS : choix en menu (Picker
// `.menu`), champ de texte, nombre, texte. À poser dans une [SectionIos] ; le filet entre deux lignes
// est [SeparateurIos]. Le libellé d'un champ est au-dessus de lui, comme sous Android (demande du
// 19 octobre), et non à côté comme dans GeoMys-IOS.

package fr.ariegenature.geomys.ui.composants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys

/** Une ligne de formulaire : 44 pt au moins, marges de 16 × 11 pt. */
@Composable
internal fun LigneIos(modifier: Modifier = Modifier, contenu: @Composable () -> Unit) {
    Box(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.CenterStart,
    ) { contenu() }
}

/** Picker en menu : [libelle] au-dessus, en blanc 70 % (rien s'il est vide), puis la valeur choisie
 *  et ses chevrons, calés à gauche comme sous Android (demande du 20 octobre), en secondaire ; le
 *  toucher ouvre le menu du système. Sans choix (-1), [invite] ; [onEffacer] : la croix des listes
 *  du web au bout d'un choix. */
@Composable
internal fun LigneChoixIos(
    libelle: String,
    options: List<String>,
    selection: Int,
    invite: String? = null,
    onEffacer: (() -> Unit)? = null,
    onChoix: (Int) -> Unit,
) {
    val effacer = onEffacer?.takeIf { selection in options.indices }
    LigneIos {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                ContenuLigneChoixIos(libelle, options.getOrNull(selection) ?: invite.orEmpty())
                MenuSysteme(options, selection, onChoix, Modifier.matchParentSize())
            }
            if (effacer != null) CroixEffacerIos(effacer, Modifier.padding(start = 8.dp))
        }
    }
}

/** Ce que montre la ligne de choix, sous son menu. Une valeur trop longue pour la ligne finit en
 *  « … », les chevrons restent visibles. */
@Composable
internal fun ContenuLigneChoixIos(libelle: String, valeur: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (libelle.isNotEmpty()) Text(libelle, fontSize = 17.sp, color = CouleursGeoMys.blanc70)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                valeur,
                fontSize = 17.sp,
                color = SECONDAIRE_FEUILLE_IOS,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(4.dp))
            SymboleSf("chevron.up.chevron.down", 13f, listOf(SECONDAIRE_FEUILLE_IOS))
        }
    }
}

/** Champ de texte d'une ligne : nu, invite [indication] en gris, de [lignesMin] à [lignesMax]
 *  lignes (une seule si [lignesMax] vaut 1) ; [titre] au-dessus, en blanc 70 % ; [apres] au bout
 *  de la ligne (le « ＋ » des profondeurs d'une station). */
@Composable
internal fun LigneTexteIos(
    valeur: String,
    onChange: (String) -> Unit,
    indication: String,
    lignesMin: Int = 1,
    lignesMax: Int = 1,
    clavier: KeyboardOptions = KeyboardOptions.Default,
    titre: String? = null,
    apres: (@Composable () -> Unit)? = null,
) {
    LigneIos {
        if (apres == null) {
            ColonneTexteIos(valeur, onChange, indication, lignesMin, lignesMax, clavier, titre)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ColonneTexteIos(valeur, onChange, indication, lignesMin, lignesMax, clavier, titre, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                apres()
            }
        }
    }
}

/** Le [titre] et le champ d'une [LigneTexteIos]. */
@Composable
private fun ColonneTexteIos(
    valeur: String,
    onChange: (String) -> Unit,
    indication: String,
    lignesMin: Int,
    lignesMax: Int,
    clavier: KeyboardOptions,
    titre: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (titre != null) Text(titre, fontSize = 17.sp, color = CouleursGeoMys.blanc70)
        ChampNuIos(valeur, onChange, indication, lignesMin, lignesMax, clavier)
    }
}

@Composable
private fun ChampNuIos(valeur: String, onChange: (String) -> Unit, indication: String, lignesMin: Int, lignesMax: Int, clavier: KeyboardOptions) {
    BasicTextField(
        value = valeur,
        onValueChange = onChange,
        singleLine = lignesMax == 1,
        minLines = lignesMin,
        maxLines = lignesMax,
        textStyle = styleSaisieIos,
        cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
        keyboardOptions = clavier,
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { champ ->
            Box {
                if (valeur.isEmpty()) Text(indication, style = styleSaisieIos.copy(color = INVITE_IOS))
                champ()
            }
        },
    )
}

/** Nombre : [libelle] au-dessus, en blanc 70 % (rien s'il est vide), puis le champ (invite « — ») et
 *  [apres] au bout de la ligne. */
@Composable
internal fun LigneNombreIos(
    libelle: String,
    valeur: String,
    onChange: (String) -> Unit,
    clavier: KeyboardOptions = ClavierNombresEtPonctuation,
    apres: (@Composable () -> Unit)? = null,
) {
    LigneTexteIos(valeur, onChange, "—", clavier = clavier, titre = libelle.ifEmpty { null }, apres = apres)
}

/** Un texte sur une ligne, [couleur] blanc par défaut ; [titre] au-dessus, en blanc 70 %. */
@Composable
internal fun LigneTexteSeulIos(texte: String, titre: String? = null, couleur: Color = Color.White, taille: Float = 17f) {
    LigneIos {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (titre != null) Text(titre, fontSize = 17.sp, color = CouleursGeoMys.blanc70)
            Text(texte, fontSize = taille.sp, color = couleur)
        }
    }
}

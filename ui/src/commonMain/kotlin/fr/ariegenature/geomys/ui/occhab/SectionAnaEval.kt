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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/OccHabDetailsDialog.kt,
// construireSectionAnaEval) : la section « Évaluation ANA / Natura 2000 » des détails d'une station et du
// formulaire d'un habitat, ses saisies gardées dans un état ; les règles sont dans occhab/EditionAnaEval.
// Sous iOS, l'allure d'AnaEvalSectionView de GeoMys-IOS.

package fr.ariegenature.geomys.ui.occhab

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.occhab.EditionAnaEval
import fr.ariegenature.geomys.ui.composants.ChampTexteFormulaire
import fr.ariegenature.geomys.ui.composants.FOND_LIGNE_FEUILLE_IOS
import fr.ariegenature.geomys.ui.composants.LigneChoixIos
import fr.ariegenature.geomys.ui.composants.LigneNombreIos
import fr.ariegenature.geomys.ui.composants.LigneTexteIos
import fr.ariegenature.geomys.ui.composants.LigneTexteSeulIos
import fr.ariegenature.geomys.ui.composants.ListeDeroulante
import fr.ariegenature.geomys.ui.composants.SectionIos
import fr.ariegenature.geomys.ui.composants.SeparateurIos
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys

/** Les saisies de la section ANA-EVAL, ouverte sur [edition] ; rien n'est appliqué avant
 *  « Valider ». */
class EtatSectionAnaEval(val edition: EditionAnaEval) {
    /** Position choisie de chaque champ à référentiel, par clé. */
    val positions = mutableStateMapOf<String, Int>().apply {
        edition.champs.filter { it.referentiel != null }.forEach { put(it.cle, edition.positionInitiale(it)) }
    }

    /** Texte de chaque champ libre, par clé. */
    val textes = mutableStateMapOf<String, String>().apply {
        edition.champs.filter { it.referentiel == null }.forEach { put(it.cle, edition.texteInitial(it)) }
    }

    /** Les trois taxons PEE (habitat). */
    val pee = mutableStateListOf<String>().apply { if (edition.avecPee) addAll(edition.peeInitiaux()) }

    /** Premier contrôle bloquant (entier hors bornes), ou null. */
    fun erreur(): String? = edition.premiereErreur(textes)

    /** Le bloc mis à jour, ou null s'il ne reste rien. */
    fun json(): String? = edition.fusionner(positions, textes, pee)
}

/** La section « Évaluation ANA / Natura 2000 », à la suite des autres champs.
 *  - Android : titre de 16 sp gras, puis un libellé gras par champ, une liste déroulante pour un
 *    code fermé, un champ de texte sinon (chiffres pour un entier) ; arbitrages du botaniste en
 *    lecture seule ;
 *  - iOS : une section de `Form`, Pickers en menu, nombre aligné à droite, texte libre sous son
 *    libellé, ses lignes sur [fondIos] (#2C2C2E d'une feuille, #1C1C1E d'un écran). */
@Composable
fun SectionAnaEval(etat: EtatSectionAnaEval, fondIos: Color = FOND_LIGNE_FEUILLE_IOS) {
    if (estIos) SectionAnaEvalIos(etat, fondIos) else SectionAnaEvalAndroid(etat)
}

@Composable
private fun SectionAnaEvalAndroid(etat: EtatSectionAnaEval) {
    val e = etat.edition
    Text(EditionAnaEval.TITRE, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 2.dp))
    e.champs.forEach { champ ->
        LibelleOccHabAndroid(champ.libelle)
        val ref = champ.referentiel
        when {
            ref != null -> ListeDeroulante(
                listOf(EditionAnaEval.NON_RENSEIGNE) + ref.map { it.second },
                etat.positions[champ.cle] ?: 0,
                { etat.positions[champ.cle] = it },
            )
            champ.entier -> ChampTexteFormulaire(
                etat.textes[champ.cle].orEmpty(),
                { etat.textes[champ.cle] = it },
                clavier = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            else -> ChampTexteFormulaire(
                etat.textes[champ.cle].orEmpty(),
                { etat.textes[champ.cle] = it },
                uneLigne = false,
                clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
    }
    if (e.avecPee) {
        LibelleOccHabAndroid(EditionAnaEval.LIBELLE_PEE)
        etat.pee.indices.forEach { i ->
            ChampTexteFormulaire(etat.pee[i], { etat.pee[i] = it }, indication = "Taxon ${i + 1}")
        }
    }
    e.determination()?.let {
        LibelleOccHabAndroid(EditionAnaEval.LIBELLE_DETERMINATION)
        Text(it, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp))
    }
    e.correspondances().takeIf { it.isNotEmpty() }?.let { lignes ->
        LibelleOccHabAndroid(EditionAnaEval.LIBELLE_CORRESPONDANCES)
        Text(lignes.joinToString("\n"), fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp))
    }
}

@Composable
private fun SectionAnaEvalIos(etat: EtatSectionAnaEval, fond: Color) {
    val e = etat.edition
    SectionIos(EditionAnaEval.TITRE, fond = fond) {
        e.champs.forEachIndexed { i, champ ->
            if (i > 0) SeparateurIos(Modifier.padding(start = 16.dp))
            val ref = champ.referentiel
            when {
                ref != null -> LigneChoixIos(
                    champ.libelle,
                    listOf(EditionAnaEval.NON_RENSEIGNE) + ref.map { it.second },
                    etat.positions[champ.cle] ?: 0,
                ) { etat.positions[champ.cle] = it }
                champ.entier -> LigneNombreIos(
                    champ.libelle,
                    etat.textes[champ.cle].orEmpty(),
                    { etat.textes[champ.cle] = it },
                    clavier = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                else -> LigneTexteIos(etat.textes[champ.cle].orEmpty(), { etat.textes[champ.cle] = it }, champ.libelle, lignesMax = 4, titre = champ.libelle)
            }
        }
        if (e.avecPee) {
            SeparateurIos(Modifier.padding(start = 16.dp))
            LigneTexteSeulIos(EditionAnaEval.LIBELLE_PEE, couleur = CouleursGeoMys.blanc70)
            etat.pee.indices.forEach { i ->
                SeparateurIos(Modifier.padding(start = 16.dp))
                LigneTexteIos(etat.pee[i], { etat.pee[i] = it }, "Taxon ${i + 1}")
            }
        }
        e.determination()?.let {
            SeparateurIos(Modifier.padding(start = 16.dp))
            LigneTexteSeulIos(it, titre = EditionAnaEval.LIBELLE_DETERMINATION, taille = 13f)
        }
        e.correspondances().takeIf { it.isNotEmpty() }?.let { lignes ->
            SeparateurIos(Modifier.padding(start = 16.dp))
            LigneTexteSeulIos(lignes.joinToString("\n"), titre = EditionAnaEval.LIBELLE_CORRESPONDANCES, taille = 13f)
        }
    }
}

/** Libellé d'un champ des formulaires OccHab de GeoMys-Android (`label()` : TextView gras, à la
 *  taille du thème, 12 dp au-dessus, 2 dp dessous). */
@Composable
internal fun LibelleOccHabAndroid(texte: String, modifier: Modifier = Modifier) {
    Text(
        texte,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

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

// Les dénombrements d'une espèce : fragment_denombrement.xml, item_denombrement.xml, item_photo_denombrement.xml et
// DenombrementFragment de GeoMys-Android @ cdd5196 sous Android, DenombrementView, CarteSaisie, CompteurEffectif et
// MediaPickerView de GeoMys-IOS sous iOS.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.model.Denombrement
import fr.ariegenature.geomys.ui.composants.BoutonAlerte
import fr.ariegenature.geomys.ui.composants.BoutonIos
import fr.ariegenature.geomys.ui.composants.BoutonMaterial
import fr.ariegenature.geomys.ui.composants.BoutonTexte
import fr.ariegenature.geomys.ui.composants.ChampLibelle
import fr.ariegenature.geomys.ui.composants.ChampTexteFormulaire
import fr.ariegenature.geomys.ui.composants.DialogueActions
import fr.ariegenature.geomys.ui.composants.DialogueGardeSortie
import fr.ariegenature.geomys.ui.composants.GRIS_TITRE_CHAMP
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.barreChampObligatoire
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.nomMedia
import fr.ariegenature.geomys.ui.composants.rememberAjoutMedias
import fr.ariegenature.geomys.ui.composants.rememberGardeSortie
import fr.ariegenature.geomys.ui.composants.styleLibelleMaterial
import fr.ariegenature.geomys.ui.composants.styleSaisieIos
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.ic_menu_delete
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import org.jetbrains.compose.resources.painterResource

/** Les dénombrements de l'espèce ; [onValide] reçoit la liste, le n° 0 en tête. */
@Composable
fun DenombrementScreen(
    vm: DenombrementViewModel,
    typeSaisieLabel: String,
    onAccueil: () -> Unit,
    onRetour: () -> Unit,
    onValide: (List<Denombrement>) -> Unit,
) {
    val ios = estIos
    val toasts = LocalToasts.current
    // Le dénombrement qui attend le retour d'un sélecteur de médias (-1 : aucun).
    var cible by rememberSaveable { mutableIntStateOf(-1) }
    var choixPhoto by remember { mutableStateOf(false) }
    val medias = rememberAjoutMedias(
        vm.dossierMedias,
        onImportes = { uris -> vm.ajouterMedias(cible, uris) },
        onErreur = { toasts.afficher(it, true) },
    )
    val garde = rememberGardeSortie(vm::modifie, onRetour)
    EcranFormulaireEspece(
        titre = "Dénombrement",
        sousTitre = vm.nomEspece,
        typeSaisieLabel = typeSaisieLabel,
        onAccueil = { garde.demander(onAccueil) },
        onValider = {
            val (liste, message) = vm.valider()
            if (liste != null) onValide(liste) else if (message != null) toasts.afficher(message, true)
        },
    ) {
        vm.cartes.forEachIndexed { index, carte ->
            CarteVue(vm, carte, index, onPhoto = {
                cible = index
                // Sans appareil photo (simulateur), la galerie directement.
                if (medias.appareilPhoto) choixPhoto = true else medias.choisirPhotos()
            }, onAudio = {
                cible = index
                medias.choisirSons()
            })
        }
        if (ios) {
            BoutonTexteJauneIos("＋ Ajouter un dénombrement", vm::ajouter)
        } else {
            BoutonTexte("＋ Ajouter un dénombrement", CouleursGeoMys.jauneClair, vm::ajouter, Modifier.fillMaxWidth().padding(top = 16.dp))
        }
    }
    DialogueActions(
        visible = choixPhoto,
        titre = "Ajouter une photo",
        actions = listOfNotNull(
            BoutonAlerte("Prendre une photo") { medias.prendrePhoto() },
            BoutonAlerte("Choisir dans la galerie") { medias.choisirPhotos() },
            if (ios) BoutonAlerte("Annuler", annulation = true) else null,
        ),
        onFermer = { choixPhoto = false },
    )
    DialogueGardeSortie(garde, "Dénombrement non enregistré", onAbandon = vm::abandonner)
}

@Composable
private fun CarteVue(vm: DenombrementViewModel, carte: CarteDenombrement, index: Int, onPhoto: () -> Unit, onAudio: () -> Unit) {
    val ios = estIos
    val forme = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = if (ios) 0.dp else 12.dp)
            .then(if (ios) Modifier.background(Color.White.copy(alpha = 0.06f), forme) else Modifier)
            .border(1.dp, if (ios) Color.White.copy(alpha = 0.25f) else Color(0x40888888), forme)
            .padding(12.dp),
        verticalArrangement = if (ios) Arrangement.spacedBy(10.dp) else Arrangement.Top,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Dénombrement #${index + 1}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (ios) Color.White else Color.Unspecified,
                modifier = Modifier.weight(1f),
            )
            if (vm.cartes.size > 1) BoutonSupprimer(36, "Supprimer") { vm.supprimer(index) }
        }
        // L'ordre du web (counting.component.html) : objet et type, effectifs, stade et sexe, champs
        // additionnels, médias.
        for (champ in vm.champsAvantEffectifs) {
            ChampNomenclatureVue(champ, carte.positions[champ.champ.code] ?: 0, { carte.positions[champ.champ.code] = it })
        }
        // Effectifs : « − » | champ | « ＋ ».
        if (vm.minVisible || vm.maxVisible) {
            Row(Modifier.padding(top = if (ios) 0.dp else 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (vm.minVisible) {
                    Compteur("Nombre min", carte.min, { vm.saisirMin(carte, it) }, { vm.ajusterMin(carte, it) }, Modifier.weight(1f))
                }
                if (vm.maxVisible) {
                    Compteur("Nombre max", carte.max, { carte.max = it }, { vm.ajusterMax(carte, it) }, Modifier.weight(1f))
                }
            }
        }
        for (champ in vm.champsApresEffectifs) {
            ChampNomenclatureVue(champ, carte.positions[champ.champ.code] ?: 0, { carte.positions[champ.champ.code] = it })
        }
        if (carte.additionnels.defsAffiches.isNotEmpty()) ChampsAdditionnelsVue(carte.additionnels)
        // Le bloc Médias, si le serveur l'active (`OCCTAX.ENABLE_MEDIAS`).
        if (vm.mediasVisibles) ChampLibelle("Médias", margeHautAndroid = 10, tailleAndroid = 12) {
            Column(Modifier.padding(top = if (ios) 0.dp else 2.dp), verticalArrangement = Arrangement.spacedBy(if (ios) 8.dp else 0.dp)) {
                for (uri in carte.medias) LigneMedia(uri) { vm.retirerMedia(carte, uri) }
                Row(Modifier.padding(top = if (ios) 0.dp else 4.dp), horizontalArrangement = Arrangement.spacedBy(if (ios) 12.dp else 0.dp)) {
                    if (ios) {
                        BoutonTexteJauneIos("＋ Photo", onPhoto, Modifier.weight(1f))
                        BoutonTexteJauneIos("＋ Audio", onAudio, Modifier.weight(1f))
                    } else {
                        BoutonTexte("＋ Photo", CouleursGeoMys.jauneClair, onPhoto, Modifier.weight(1f))
                        BoutonTexte("＋ Audio", CouleursGeoMys.jauneClair, onAudio, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Un effectif.
 *  - Android : libellé de 12 sp gris, MaterialButton contour de 44 dp aux coins de 8 dp (« − » en
 *    20 sp, « ＋ » en 18 sp) de part et d'autre d'un EditText numérique centré ;
 *  - iOS : `CompteurEffectif` de GeoMys-IOS (boutons de 44 × 40 pt au contour blanc 35 %, champ
 *    centré sur blanc 8 %).
 *  Un effectif est obligatoire, comme sur le web : vide, il porte la barre rouge. */
@Composable
private fun Compteur(libelle: String, valeur: String, onValeur: (String) -> Unit, onAjuster: (Int) -> Unit, modifier: Modifier) {
    val clavier = KeyboardOptions(keyboardType = KeyboardType.Number)
    val chiffres = { texte: String -> onValeur(texte.filter { it.isDigit() }) }
    if (estIos) {
        ChampLibelle(libelle, modifier) {
            Row(Modifier.barreChampObligatoire(valeur.isBlank()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BoutonEffectifIos("minus", "Diminuer $libelle") { onAjuster(-1) }
                BasicTextField(
                    value = valeur,
                    onValueChange = chiffres,
                    singleLine = true,
                    textStyle = styleSaisieIos.copy(textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
                    keyboardOptions = clavier,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minWidth = 56.dp)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(vertical = 8.dp),
                )
                BoutonEffectifIos("plus", "Augmenter $libelle") { onAjuster(+1) }
            }
        }
    } else {
        Column(modifier) {
            Text(libelle, fontSize = 12.sp, color = GRIS_TITRE_CHAMP)
            Row(Modifier.barreChampObligatoire(valeur.isBlank()), verticalAlignment = Alignment.CenterVertically) {
                BoutonEffectifAndroid("−", 20) { onAjuster(-1) }
                ChampTexteFormulaire(valeur, chiffres, Modifier.weight(1f), clavier = clavier, centre = true)
                BoutonEffectifAndroid("＋", 18) { onAjuster(+1) }
            }
        }
    }
}

@Composable
private fun BoutonEffectifAndroid(texte: String, taille: Int, onClick: () -> Unit) {
    BoutonMaterial(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        contour = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        marges = PaddingValues(0.dp),
        couleurContenu = MaterialTheme.colorScheme.primary,
        hauteurMin = 44.dp,
        largeurMin = 0.dp,
        retraitVertical = 0.dp,
        forme = RoundedCornerShape(8.dp),
    ) { Text(texte, style = styleLibelleMaterial(MaterialTheme.colorScheme.primary, taille.toFloat())) }
}

@Composable
private fun BoutonEffectifIos(symbole: String, description: String, onClick: () -> Unit) {
    BoutonIos(onClick, opaciteAppui = 0.6f) {
        Box(
            Modifier.size(44.dp, 40.dp).border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { SymboleSf(symbole, 16f, listOf(Color.White), graisse = fr.ariegenature.geomys.ui.composants.GraisseSymbole.DEMI_GRASSE, description = description) }
    }
}

/** Un média du dénombrement : son nom (coupé au milieu : les noms générés ne diffèrent que par la
 *  fin), et sa suppression. */
@Composable
private fun LigneMedia(uri: String, onSupprimer: () -> Unit) {
    val nom = nomMedia(uri)
    if (estIos) {
        val audio = nom.substringAfterLast('.', "").lowercase() in setOf("m4a", "mp3", "aac", "ogg", "wav")
        Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                SymboleSf(if (audio) "waveform" else "photo", 17f, listOf(Color(0x99EBEBF5)))
            }
            Text(nom, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color.White, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f))
            BoutonIos(onSupprimer, opaciteAppui = 0.6f) { SymboleSf("trash", 17f, listOf(CouleursGeoMys.rougePastille), description = "Supprimer le média") }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(nom, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f))
            BoutonSupprimer(32, "Supprimer la photo", onSupprimer)
        }
    }
}

/** ImageButton de suppression : `@android:drawable/ic_menu_delete` (32 dp) sans fond ; sous iOS, la
 *  corbeille rouge. */
@Composable
private fun BoutonSupprimer(taille: Int, description: String, onClick: () -> Unit) {
    if (estIos) {
        BoutonIos(onClick, opaciteAppui = 0.6f) { SymboleSf("trash", 17f, listOf(CouleursGeoMys.rougePastille), description = description) }
    } else {
        Box(
            Modifier
                .size(taille.dp)
                .clickable(remember { MutableInteractionSource() }, ripple(bounded = false), role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Image(painterResource(Res.drawable.ic_menu_delete), contentDescription = description, modifier = Modifier.size(32.dp)) }
    }
}

/** `BoutonTexteJaune` de GeoMys-IOS : texte jaune clair de 15 pt medium, pleine largeur. */
@Composable
private fun BoutonTexteJauneIos(texte: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BoutonIos(onClick, modifier.fillMaxWidth(), opaciteAppui = 0.6f) {
        Text(texte, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = CouleursGeoMys.jauneClair, modifier = Modifier.padding(vertical = 10.dp))
    }
}

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

// Porté de GeoMys-Android @ cdd5196 (res/layout/fragment_config_geonature.xml) ; allure iOS de
// GeoMys-IOS (GeoMys-IOS/UI/ConfigGeoNatureView.swift).

package fr.ariegenature.geomys.ui.parametres

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.store.ReglesSelectionOcctax
import fr.ariegenature.geomys.ui.composants.BalayageRetourIosDesactive
import fr.ariegenature.geomys.ui.composants.BandeauAvertissement
import fr.ariegenature.geomys.ui.composants.BoutonAlerte
import fr.ariegenature.geomys.ui.composants.BoutonContour
import fr.ariegenature.geomys.ui.composants.BoutonPlein
import fr.ariegenature.geomys.ui.composants.BoutonTexte
import fr.ariegenature.geomys.ui.composants.CarteSection
import fr.ariegenature.geomys.ui.composants.ChampAvecLibelleIos
import fr.ariegenature.geomys.ui.composants.ChampMotDePasse
import fr.ariegenature.geomys.ui.composants.ChampSelection
import fr.ariegenature.geomys.ui.composants.ChampTexte
import fr.ariegenature.geomys.ui.composants.DialogueConfirmation
import fr.ariegenature.geomys.ui.composants.DialogueListe
import fr.ariegenature.geomys.ui.composants.IndicateurActivite
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.RetourSysteme
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.TypeChamp
import fr.ariegenature.geomys.ui.composants.barreChampObligatoire
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.margesAndroid
import fr.ariegenature.geomys.ui.composants.rememberDemandeNotifications
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.coche
import fr.ariegenature.geomys.ui.ressources.coche_orange
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.painterResource

/** Couleur d'un message de l'écran selon l'allure : `Couleurs.kt` et le thème Material sous
 *  Android, la palette de ConfigGeoNatureView sous iOS. */
@Composable
private fun couleur(nature: NatureMessage): Color {
    val ios = estIos
    val theme = MaterialTheme.colorScheme
    return when (nature) {
        NatureMessage.NEUTRE -> if (ios) CouleursGeoMys.blanc70 else theme.onSurface
        NatureMessage.SUCCES -> CouleursGeoMys.succes
        NatureMessage.ERREUR -> if (ios) CouleursGeoMys.rougePastille else theme.error
        NatureMessage.AVERTISSEMENT -> if (ios) CouleursGeoMys.orange else CouleursGeoMys.avertissement
        NatureMessage.TERMINE -> if (ios) CouleursGeoMys.succes else theme.onSurface
        NatureMessage.ECHEC -> CouleursGeoMys.orange
        NatureMessage.MAJ_DISPONIBLE -> if (ios) CouleursGeoMys.orange else CouleursGeoMys.holoOrangeFonce
    }
}

/** Écran Paramètres : connexion au serveur, chargement des données, sélections par défaut. On ne
 *  le quitte que par la coche (ou Retour sous Android), et seulement une fois la configuration
 *  complète. [defilerEnBas] : ouvert au bas de l'écran (essais, `-ecran parametres-selections`). */
@Composable
fun ParametresScreen(vm: ParametresViewModel, onFermer: () -> Unit, onTaxonsListe: (Int, String) -> Unit, defilerEnBas: Boolean = false) {
    val toasts = LocalToasts.current
    val demanderNotifications = rememberDemandeNotifications()
    LaunchedEffect(vm) {
        vm.evenements.collect { evenement ->
            when (evenement) {
                is EvenementParametres.Toast -> toasts.afficher(evenement.texte, evenement.longue)
                EvenementParametres.Fermer -> onFermer()
                EvenementParametres.DemanderNotifications -> demanderNotifications()
            }
        }
    }
    val ios = estIos
    if (ios) BalayageRetourIosDesactive() else RetourSysteme(onRetour = vm::retour)

    val defilement = rememberScrollState()
    if (defilerEnBas) {
        LaunchedEffect(vm.sectionDonneesVisible) {
            snapshotFlow { defilement.maxValue }.first { it in 1 until Int.MAX_VALUE }
            defilement.scrollTo(defilement.maxValue)
        }
    }

    FondGeoMys {
        Column(
            Modifier
                .fillMaxSize()
                .then(if (ios) Modifier.safeDrawingPadding() else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
                .verticalScroll(defilement)
                .padding(16.dp),
            verticalArrangement = if (ios) Arrangement.spacedBy(16.dp) else Arrangement.Top,
        ) {
            Text(
                "Paramètres",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (ios) Color.White else Color.Unspecified,
                modifier = if (ios) Modifier.padding(end = 44.dp) else Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            vm.bandeau?.let { BandeauAvertissement(it) }
            CarteSection("Connexion au serveur", Modifier.margesAndroid(bas = 16.dp)) { SectionConnexion(vm) }
            if (vm.sectionChargerVisible) {
                CarteSection("Chargement des données", Modifier.margesAndroid(bas = 16.dp)) { SectionChargement(vm) }
            }
            if (vm.sectionDonneesVisible) {
                CarteSection("Sélections par défaut", Modifier.margesAndroid(bas = 16.dp), margeTitreAndroid = 4.dp) {
                    SectionSelections(vm)
                }
            }
            Statut(vm.configurationComplete)
        }
        Coche(vm.configurationComplete, vm::valider)
        val contenu = vm.taxonsParListes
        DialogueListe(
            visible = contenu != null,
            titre = "Taxons par listes",
            sousTitre = contenu?.sousTitre.orEmpty(),
            lignes = contenu?.lignes?.map { it.libelle }.orEmpty(),
            onChoisir = { i ->
                val ligne = contenu?.lignes?.getOrNull(i)
                vm.fermerTaxonsParListes()
                if (ligne != null) onTaxonsListe(ligne.idListe, ligne.nomListe)
            },
            onFermer = vm::fermerTaxonsParListes,
        )
        DialogueConfirmation(
            vm.confirmationVidage,
            "Vider le cache ?",
            "Les référentiels et les données chargées depuis GeoNature seront effacés : sans réseau, aucune " +
                "saisie ne sera possible avant un nouveau chargement. Les saisies en attente d'envoi sont conservées.",
            listOf(BoutonAlerte("Annuler", annulation = true), BoutonAlerte("Vider le cache", destructif = true, action = vm::viderLeCache)),
            vm::fermerVidage,
        )
    }
}

// ─── Boîte 1 : connexion au serveur ───

@Composable
private fun ColumnScope.SectionConnexion(vm: ParametresViewModel) {
    val ios = estIos
    if (ios) {
        ChampTexte(vm.url, vm::changerUrl, "URL du serveur GeoNature", type = TypeChamp.URL)
        // iOS refuse le trafic en clair (ATS) et l'appli ne demande aucune dérogation : une
        // instance en http:// reste injoignable. Texte de GeoMys-IOS.
        if (vm.urlEnClair) {
            Text(
                "⚠ Adresse non sécurisée : iOS refuse le http. Utilisez l'adresse https de votre instance GeoNature.",
                fontSize = 13.sp,
                color = CouleursGeoMys.orange,
            )
        }
    } else {
        ChampTexte(
            vm.url, vm::changerUrl, "URL du serveur GeoNature",
            type = TypeChamp.URL,
            aide = if (vm.urlEnClair) "⚠ Connexion NON chiffrée (http) : votre mot de passe transite en clair. Préférez https://." else null,
            couleurAide = CouleursGeoMys.avertissement,
        )
    }
    ChampTexte(vm.login, vm::changerLogin, "Identifiant", Modifier.margesAndroid(haut = 12.dp), type = TypeChamp.IDENTIFIANT)
    ChampMotDePasse(vm.motDePasse, vm::changerMotDePasse, "Mot de passe", Modifier.margesAndroid(haut = 12.dp))
    BoutonContour(
        "Tester la connexion",
        onClick = vm::testerConnexion,
        modifier = Modifier.fillMaxWidth().margesAndroid(haut = 16.dp),
        actif = !vm.testEnCours && !vm.synchroEnCours,
        enCours = vm.testEnCours,
        texteEnCours = "Test en cours…",
    )
    if (vm.testEnCours && !ios) ProgressionAndroid(haut = 8)
    vm.resultatTest?.let { Text(it.texte, fontSize = 13.sp, color = couleur(it.nature), modifier = Modifier.margesAndroid(haut = 8.dp)) }
    vm.versionServeur?.let {
        Text(
            it,
            fontSize = 12.sp,
            color = if (ios) CouleursGeoMys.blanc70 else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.margesAndroid(haut = 4.dp),
        )
    }
}

/** ProgressBar Android de 24 dp de haut, dans sa boîte de 48 dp de large (`wrap_content`). */
@Composable
private fun ProgressionAndroid(haut: Int) {
    Box(Modifier.padding(top = haut.dp).width(48.dp), contentAlignment = Alignment.Center) {
        IndicateurActivite(CouleursGeoMys.orange, tailleAndroid = 24f)
    }
}

// ─── Boîte 2 : chargement des données ───

@Composable
private fun ColumnScope.SectionChargement(vm: ParametresViewModel) {
    val ios = estIos
    BoutonPlein(
        vm.libelleCharger,
        onClick = { vm.charger() },
        modifier = Modifier.fillMaxWidth(),
        actif = !vm.synchroEnCours,
        onLongClick = { vm.charger(forcerTaxRef = true) },
        enCours = vm.synchroEnCours,
        texteEnCours = "Synchronisation…",
    )
    if (vm.synchroEnCours && !ios) ProgressionAndroid(haut = 4)
    vm.texteSynchro?.let { Text(it.texte, fontSize = 13.sp, color = couleur(it.nature), modifier = Modifier.margesAndroid(haut = 4.dp)) }
    if (!vm.resumeCacheVisible) return
    val compteurs = vm.compteurs
    val lignes = buildList {
        add("Taxons" to compteurs?.taxons)
        // Aucun protocole accessible : ligne masquée, comme la tuile « Monitoring » de l'accueil.
        if ((compteurs?.protocoles ?: 0) > 0) add("Protocoles" to compteurs?.protocoles)
        add("Nomenclatures" to compteurs?.nomenclatures)
        add("Listes" to compteurs?.listes)
        add("Observateurs" to compteurs?.observateurs)
        if (vm.occhabDisponible) add("Stations OccHab" to compteurs?.stationsOccHab)
    }
    if (ios) {
        for ((libelle, valeur) in lignes) LigneCompteur(libelle, valeur)
        vm.versionTaxRef?.let { Text(it.texte, fontSize = 12.sp, color = couleur(it.nature)) }
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            BoutonContour("Détails", vm::montrerTaxonsParListes, Modifier.width(140.dp), actif = compteurs?.detailsActif == true)
        }
        BoutonTexte("Vider le cache", CouleursGeoMys.holoRougeFonce, vm::demanderVidage, actif = compteurs?.viderActif == true)
    } else {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            for ((libelle, valeur) in lignes) LigneCompteur(libelle, valeur)
            BoutonContour(
                "Détails", vm::montrerTaxonsParListes,
                Modifier.align(Alignment.End).padding(top = 8.dp),
                actif = compteurs?.detailsActif == true,
            )
            vm.versionTaxRef?.let { Text(it.texte, fontSize = 12.sp, color = couleur(it.nature), modifier = Modifier.padding(top = 4.dp)) }
            BoutonTexte(
                "Vider le cache", CouleursGeoMys.holoRougeFonce, vm::demanderVidage,
                Modifier.padding(top = 4.dp), actif = compteurs?.viderActif == true,
            )
        }
    }
}

/** Ligne du résumé : libellé à gauche, valeur en gras à droite (« — » tant qu'elle n'est pas
 *  comptée). 14 sp sous Android ; 14 pt, libellé blanc 70 % et valeur blanche sous iOS. */
@Composable
private fun LigneCompteur(libelle: String, valeur: Int?) {
    val ios = estIos
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(libelle, fontSize = 14.sp, color = if (ios) CouleursGeoMys.blanc70 else Color.Unspecified, modifier = Modifier.weight(1f))
        Text(valeur?.toString() ?: "—", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (ios) Color.White else Color.Unspecified)
    }
}

// ─── Boîte 3 : sélections par défaut ───

/** Les trois sélections par défaut, obligatoires : jeu de données et liste sans valeur au départ,
 *  à choisir, observateur = l'utilisateur connecté. Vides, elles portent la barre rouge. */
@Composable
private fun ColumnScope.SectionSelections(vm: ParametresViewModel) {
    val ios = estIos
    // Jeu de données.
    if (ios && vm.datasets.isEmpty() && vm.tousDatasets.isNotEmpty()) {
        ChampAvecLibelleIos("Jeu de données") {
            // Rien à choisir : la configuration reste incomplète, la barre le dit.
            Text(
                "Aucun proposé (droits CRUVED)",
                fontSize = 14.sp,
                color = CouleursGeoMys.blanc70,
                modifier = Modifier.barreChampObligatoire(vm.jddManquant),
            )
        }
    } else if (vm.tousDatasets.isNotEmpty() || !ios) {
        ChampSelectionOuTitre(
            visible = vm.tousDatasets.isNotEmpty(),
            titre = "Jeu de données",
            margeHaut = 12,
        ) {
            ChampSelection(
                "Jeu de données", "Rechercher un jeu de données", vm.datasets, ReglesSelectionOcctax::libelle,
                vm.datasetChoisi, vm::choisirDataset, margeHautAndroid = 12.dp, viderAuToucher = true, barre = vm.jddManquant,
            )
        }
    }
    // Liste de taxons : un jeu de données peut imposer la sienne.
    val listes = vm.listes
    val imposee = listes.listes.firstOrNull()?.takeIf { listes.imposee }
    if (ios && imposee != null) {
        ChampAvecLibelleIos("Listes") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(ReglesSelectionOcctax.libelle(imposee), fontSize = 15.sp, color = Color.White)
                Text("Liste imposée par le jeu de données", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CouleursGeoMys.vert)
            }
        }
    } else if (listes.listes.isNotEmpty() || !ios) {
        ChampSelectionOuTitre(visible = listes.listes.isNotEmpty(), titre = "Listes", margeHaut = 20) {
            ChampSelection(
                "Listes", "Rechercher une liste", listes.listes, ReglesSelectionOcctax::libelle,
                imposee ?: vm.listeChoisie, vm::choisirListe, margeHautAndroid = 20.dp, actif = imposee == null,
                barre = vm.listeManquante,
            )
        }
        if (imposee != null && !ios) {
            Text(
                "Liste imposée par le jeu de données",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
    vm.messageListe?.let { message ->
        val couleurTexte = when {
            message.avertissement -> if (ios) CouleursGeoMys.orange else CouleursGeoMys.avertissement
            ios -> CouleursGeoMys.blanc70
            else -> MaterialTheme.colorScheme.onSurface
        }
        Text(
            message.texte,
            fontSize = 13.sp,
            fontWeight = if (ios) FontWeight.SemiBold else FontWeight.Bold,
            color = couleurTexte,
            modifier = Modifier.fillMaxWidth().margesAndroid(haut = 6.dp),
        )
    }
    // Observateur par défaut : l'utilisateur connecté, sauf autre choix.
    if (vm.observateurs.isNotEmpty() || !ios) {
        ChampSelectionOuTitre(visible = vm.observateurs.isNotEmpty(), titre = "Observateur par défaut", margeHaut = 20) {
            ChampSelection(
                "Observateur par défaut", "Rechercher un observateur", vm.observateurs, { it.nomComplet },
                vm.observateurChoisi, vm::choisirObservateur, margeHautAndroid = 20.dp, viderAuToucher = true,
                barre = vm.observateurManquant,
            )
        }
    }
}

/** Sous Android, le titre de section reste affiché même quand son menu est masqué (rien en
 *  cache) : seul le TextInputLayout est `gone`. */
@Composable
private fun ChampSelectionOuTitre(visible: Boolean, titre: String, margeHaut: Int, champ: @Composable () -> Unit) {
    if (visible) {
        champ()
    } else {
        Text(titre, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = margeHaut.dp, bottom = 4.dp))
    }
}

// ─── Statut et coche ───

/** « ✓ Configuration complète » / « ⚠ Configuration incomplète » (14 sp, vert succès ou orange
 *  avertissement) ; sous iOS, `Label` à symbole SF de GeoMys-IOS. */
@Composable
private fun Statut(complete: Boolean) {
    if (estIos) {
        val couleurStatut = if (complete) CouleursGeoMys.succes else CouleursGeoMys.orange
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            SymboleSf(if (complete) "checkmark.circle.fill" else "exclamationmark.triangle.fill", 14f, listOf(couleurStatut))
            Text(if (complete) "Configuration complète" else "Configuration incomplète", fontSize = 14.sp, color = couleurStatut)
        }
    } else {
        Text(
            if (complete) "✓ Configuration complète" else "⚠ Configuration incomplète",
            fontSize = 14.sp,
            color = if (complete) CouleursGeoMys.succes else CouleursGeoMys.avertissement,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Coche « Valider et fermer », fixe en haut à droite pendant le défilement : `fab_valider`
 *  (32 dp, à 16 dp du coin, drawable coche / coche_orange) sous Android, « checkmark.circle.fill »
 *  de 30 pt en palette blanc / vert ou orange sous iOS. */
@Composable
private fun Coche(complete: Boolean, onValider: () -> Unit) {
    if (estIos) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopEnd) {
            Box(
                Modifier
                    .padding(top = 10.dp, end = 14.dp)
                    .clickable(onClickLabel = if (complete) "Configuration complète — retour" else "Configuration incomplète — retour", onClick = onValider),
            ) {
                SymboleSf(
                    "checkmark.circle.fill",
                    30f,
                    listOf(Color.White, if (complete) CouleursGeoMys.vert else CouleursGeoMys.orange),
                    description = "Valider et fermer",
                )
            }
        }
    } else {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopEnd) {
            Image(
                painterResource(if (complete) Res.drawable.coche else Res.drawable.coche_orange),
                contentDescription = "Valider et fermer",
                modifier = Modifier
                    .padding(16.dp)
                    .size(32.dp)
                    .clickable(remember { MutableInteractionSource() }, ripple(bounded = false), onClick = onValider),
            )
        }
    }
}

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

// Porté de GeoMys-Android @ d72b8da (app/src/main/java/fr/ariegenature/geomys/ui/SaisieRapideFragment.kt,
// res/layout/fragment_saisie_rapide.xml) : les réglages de la série (panneau_config), puis la carte où chaque
// « + » enregistre une observation (panneau_actif). Sous iOS, l'allure de SaisieRapideView de GeoMys-IOS
// (UI/Saisie/SaisieRapideView.swift).

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.network.TaxRefStatut
import fr.ariegenature.geomys.occtax.FinSaisie
import fr.ariegenature.geomys.occtax.SaisieMono
import fr.ariegenature.geomys.ui.carte.ANCRE_GOUTTE_IOS
import fr.ariegenature.geomys.ui.carte.AttributionFond
import fr.ariegenature.geomys.ui.carte.BoutonCarte
import fr.ariegenature.geomys.ui.carte.CarteGeoMys
import fr.ariegenature.geomys.ui.carte.CerclePrecision
import fr.ariegenature.geomys.ui.carte.DialogueChoixFond
import fr.ariegenature.geomys.ui.carte.MarqueurDeplacable
import fr.ariegenature.geomys.ui.carte.PointPosition
import fr.ariegenature.geomys.ui.carte.SommetDeplacable
import fr.ariegenature.geomys.ui.carte.StyleBoutonCarte
import fr.ariegenature.geomys.ui.carte.SuiviCap
import fr.ariegenature.geomys.ui.carte.animerCamera
import fr.ariegenature.geomys.ui.carte.enPosition
import fr.ariegenature.geomys.ui.carte.rememberStyleFond
import fr.ariegenature.geomys.ui.composants.AlerteEchecEcriture
import fr.ariegenature.geomys.ui.composants.BandeauNavigation
import fr.ariegenature.geomys.ui.composants.BoutonAlerte
import fr.ariegenature.geomys.ui.composants.BoutonContour
import fr.ariegenature.geomys.ui.composants.BoutonIos
import fr.ariegenature.geomys.ui.composants.BoutonPlein
import fr.ariegenature.geomys.ui.composants.DialogueActions
import fr.ariegenature.geomys.ui.composants.DialogueAlerte
import fr.ariegenature.geomys.ui.composants.DialogueChoixLignesAndroid
import fr.ariegenature.geomys.ui.composants.DialogueConfirmation
import fr.ariegenature.geomys.ui.composants.DialogueMaterialIos
import fr.ariegenature.geomys.ui.composants.GraisseSymbole
import fr.ariegenature.geomys.ui.composants.HAUTEUR_BANDEAU_IOS
import fr.ariegenature.geomys.ui.composants.Interrupteur
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.RetourSysteme
import fr.ariegenature.geomys.ui.composants.SeparateurIos
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.afficher
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.fondEnRelief
import fr.ariegenature.geomys.ui.composants.rememberDemandeLocalisation
import fr.ariegenature.geomys.ui.composants.rememberDictee
import fr.ariegenature.geomys.ui.composants.rememberVibration
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.coche
import fr.ariegenature.geomys.ui.ressources.ic_add
import fr.ariegenature.geomys.ui.ressources.ic_bird_marker
import fr.ariegenature.geomys.ui.ressources.ic_info
import fr.ariegenature.geomys.ui.ressources.ic_location_off
import fr.ariegenature.geomys.ui.ressources.ic_location_on
import fr.ariegenature.geomys.ui.ressources.ic_map
import fr.ariegenature.geomys.ui.ressources.ic_remove
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys
import fr.ariegenature.geomys.util.FormatDate
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.rememberMapState

/** colorPrimaryVariant : l'indicateur des réglages sous iOS. */
private val BLEU_VARIANTE = Color(0xFF1565C0)

/** France entière, quand ni la carte ni le GPS n'ont encore de position. */
private val FRANCE = LatLon(46.5, 2.5)

/** Le « + » du panneau : 48 dp, pour des doigts gantés. */
private val TAILLE_PLUS = 48.dp

/** Au-dessus et au-dessous du « + », dans le panneau du bas. */
private val MARGE_PANNEAU = 8.dp

/** Les boutons de la carte, 12 dp au-dessus du panneau du bas (une ligne, 64 dp depuis le 25 novembre
 *  2026 ; ils étaient à 180 dp, au-dessus de ses lignes de confirmation et d'avertissement). En
 *  paysage, la colonne de droite et ses quatre boutons tiennent ainsi sous la coche « Terminer ». */
private val AU_DESSUS_DU_PANNEAU = TAILLE_PLUS + MARGE_PANNEAU * 2 + 12.dp

/** La saisie mono-taxon. [onFicheSerie] ouvre la fiche de l'espèce de la série (son choix,
 *  « Démarrer », la reprise d'une série dont un champ obligatoire est vide, le « i » de la carte),
 *  [onOuvrirFiche] la fiche d'une observation enregistrée (marqueur touché, « Éditer »), [onQuitter]
 *  revient à l'écran précédent. [auPremierPlan] : la saisie est l'écran du dessus de la pile. */
@Composable
fun SaisieMonoScreen(
    vm: SaisieMonoViewModel,
    onAccueil: () -> Unit,
    onFicheSerie: () -> Unit,
    onOuvrirFiche: (EvenementSaisieMono.OuvrirFiche) -> Unit,
    onQuitter: () -> Unit,
    auPremierPlan: () -> Boolean = { true },
) {
    val toasts = LocalToasts.current
    LaunchedEffect(vm) {
        vm.evenements.collect { evenement ->
            when (evenement) {
                is EvenementSaisieMono.Message -> toasts.afficher(evenement.texte)
                EvenementSaisieMono.FicheSerie -> onFicheSerie()
                is EvenementSaisieMono.OuvrirFiche -> onOuvrirFiche(evenement)
                EvenementSaisieMono.Quitter -> onQuitter()
            }
        }
    }
    // Localisation demandée à l'ouverture, puis suivi GPS (sans trace).
    val demandeLocalisation = rememberDemandeLocalisation { vm.demarrerSuivi() }
    LaunchedEffect(Unit) { demandeLocalisation() }
    // Le retour système passe par « Terminer la saisie », pour libérer le GPS comme la coche. Pas
    // tant qu'une fiche s'ouvre par-dessus (relecture du 25 novembre 2026), comme en multi-taxons.
    RetourSysteme { if (auPremierPlan()) vm.demanderTerminer() }

    FondGeoMys {
        if (vm.modeActif) EcranCarteMono(vm, onAccueil) else EcranReglages(vm, onAccueil)
        // Pendant l'écriture de la fin, l'écran ne répond plus, sans voile (comme en multi-taxons).
        if (vm.finEnCours) {
            Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {})
        }

        EcranDetailsReleve(vm.details, vm.session.typeSaisieLabel, onAccueil, vm::fermerDetails, vm::validerDetails)
        DialoguesObservationTouchee(vm)
        DialogueChoixFond(vm.choixFondVisible, vm.fond, vm::choisirFond, vm::fermerChoixFond)
        DialogueActions(
            visible = vm.confirmerTerminer,
            titre = FinSaisie.TITRE,
            actions = FinSaisie.options(vm.peutEnvoyer).map { option ->
                when (option) {
                    FinSaisie.ENREGISTRER_QUITTER -> BoutonAlerte(option) { vm.terminer(envoyer = false) }
                    FinSaisie.ENREGISTRER_ENVOYER -> BoutonAlerte(option) { vm.terminer(envoyer = true) }
                    else -> BoutonAlerte(option, annulation = true)
                }
            },
            onFermer = vm::fermerTerminer,
        )
        val resultat = vm.resultatEnvoi
        DialogueAlerte(
            visible = resultat != null,
            titre = resultat?.titre.orEmpty(),
            message = resultat?.message.orEmpty(),
            boutons = listOf(BoutonAlerte("OK", action = vm::quitter)),
            onFermer = vm::fermerResultatEnvoi,
        )
        AlerteEchecEcriture(vm.echecFin, vm::fermerEchecFin, FinSaisie.CONSIGNE_ECHEC)
        // Échec de l'auto-save du brouillon (disque plein ?) : alerte BLOQUANTE.
        val echecBrouillon by vm.session.echecEcritureBrouillon.collectAsState()
        AlerteEchecEcriture(echecBrouillon, vm.session::acquitterEchecEcriture, FinSaisie.CONSIGNE_ECHEC_BROUILLON)
    }
}

// ─── Réglages (panneau_config) ───

/** Les réglages de la série : groupe, détails, espèce, « Annuler » et « Démarrer la saisie ». Depuis le
 *  24 novembre 2026, l'espèce choisie ouvre sa fiche, comme en multi-taxons, dont les valeurs valent
 *  pour chaque « + » : plus de boutons « Caractérisation » et « Dénombrement ». La ligne sous le champ
 *  espèce résume ces valeurs. */
@Composable
private fun EcranReglages(vm: SaisieMonoViewModel, onAccueil: () -> Unit) {
    val ios = estIos
    val toasts = LocalToasts.current
    val champ = vm.champ
    val ligne by champ.recherche.ligne.collectAsState()
    val modele by vm.saisie.modele.collectAsState()
    // Seule une espèce RÉELLEMENT proposée ouvre la saisie.
    val demarrable = champ.texte.trim().isNotEmpty() && ligne.statut is TaxRefStatut.Trouve
    val dictee = rememberDictee(
        onPartiel = vm::dicterPartiel,
        onFinal = vm::dicter,
        onErreur = { message, longue -> toasts.afficher(message, longue) },
    )
    // Les espèces proposables changent avec le groupe et la liste : la dictée d'iOS en refait son modèle.
    val nomsDictee = champ.nomsDictee
    LaunchedEffect(dictee, nomsDictee) { dictee.preparer(nomsDictee) }
    val indicateur = remember(modele) { vm.saisie.indicateurDetails() }
    val champEspece: @Composable (Modifier) -> Unit = { modifier ->
        ChampEspece(
            texte = champ.texte,
            onTexte = vm::saisir,
            libelle = champ.libelleChamp,
            suggestions = champ.suggestions,
            onChoisir = { suggestion ->
                dictee.arreter()
                vm.choisirSuggestion(suggestion)
            },
            ligne = ligne,
            synchroEnCours = champ.synchroEnCours,
            nomValide = champ.nomValide,
            entendu = champ.entendu,
            dictee = dictee,
            onMicro = { dictee.basculer(vm.vocabulaire()) },
            modifier = modifier,
        )
    }
    val demarrer = {
        dictee.arreter()
        vm.demarrer()
    }
    if (ios) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            BandeauNavigation(vm.session.typeSaisieLabel, onAccueil)
            Column(
                // 20 pt entre le bandeau, les groupes, « Nom scientifique » et le champ espèce, comme en
                // saisie multi-taxons (demande du 19 octobre).
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PaletteGroupes(champ.groupes, champ.taxon, vm::choisirTaxon, Modifier)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Nom scientifique", fontSize = 13.sp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Interrupteur(champ.nomScientifique, vm::basculerNomScientifique, teinteIos = CouleursGeoMys.vert)
                    Spacer(Modifier.weight(1f))
                    BoutonDetailsIos(vm::ouvrirDetails)
                }
                champEspece(Modifier)
                indicateur?.let { Text(it, fontSize = 12.sp, color = BLEU_VARIANTE, modifier = Modifier.fillMaxWidth()) }
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoutonContour("Annuler", vm::annuler, Modifier.weight(1f))
                    BoutonPlein("Démarrer la saisie →", demarrer, Modifier.weight(1f), actif = demarrable)
                }
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 84.dp, bottom = 24.dp),
            ) {
                PaletteGroupes(champ.groupes, champ.taxon, vm::choisirTaxon, Modifier.padding(bottom = 10.dp))
                // L'interrupteur à gauche, « Détails » à droite.
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.defaultMinSize(minHeight = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Nom scientifique", fontSize = 13.sp)
                        Spacer(Modifier.width(16.dp))
                        Interrupteur(champ.nomScientifique, vm::basculerNomScientifique)
                    }
                    Spacer(Modifier.weight(1f))
                    BoutonContour(
                        "Détails",
                        vm::ouvrirDetails,
                        margesAndroid = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        hauteurMinAndroid = 36.dp,
                    )
                }
                champEspece(Modifier)
                indicateur?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp)) }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    BoutonContour("Annuler", vm::annuler, Modifier.padding(end = 8.dp))
                    BoutonPlein("Démarrer la saisie →", demarrer, Modifier.weight(1f), actif = demarrable)
                }
            }
            // Bandeau flottant « 🏠 › Saisie mono-taxon », dégagé par la marge haute du panneau.
            BandeauNavigation(vm.session.typeSaisieLabel, onAccueil, Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(12.dp))
        }
    }
}

// ─── Carte (panneau_actif) ───

@Composable
private fun EcranCarteMono(vm: SaisieMonoViewModel, onAccueil: () -> Unit) {
    val ios = estIos
    val portee = rememberCoroutineScope()
    val vibrer = rememberVibration()
    val etat = CarteMono(vm)
    val densite = LocalDensity.current
    val haut = if (ios) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier.windowInsetsPadding(WindowInsets.statusBars)
    val bas = if (ios) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    val enregistrer = {
        val centre = etat.cameraPosition.center.let { LatLon(it.latitude, it.longitude) }
        val position = vm.enregistrerIci(centre)
        vibrer()
        // La carte se recale sur le GPS pour l'observation suivante.
        if (position != null) portee.launch { etat.animerCamera(CameraUpdate(center = position.enPosition())) }
        Unit
    }

    Box(Modifier.fillMaxSize()) {
        // ─── Haut : bandeau, consigne, coche « Terminer » ───
        if (ios) {
            Column(haut.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BandeauNavigation(vm.session.typeSaisieLabel, onAccueil)
                // Sur deux lignes depuis le 25 novembre 2026 (une observation touchée s'édite) : centrées,
                // comme la consigne de la carte multi-taxons.
                Text(
                    SaisieMono.CONSIGNE,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(start = 12.dp, end = 12.dp, top = 18.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            BoutonIos(vm::demanderTerminer, Modifier.align(Alignment.TopEnd).then(haut).padding(end = 12.dp).height(HAUTEUR_BANDEAU_IOS), opaciteAppui = 0.6f) {
                SymboleSf("checkmark.circle.fill", 30f, listOf(Color.White, CouleursGeoMys.vert), description = "Terminer la saisie")
            }
        } else {
            BandeauNavigation(vm.session.typeSaisieLabel, onAccueil, haut.padding(12.dp))
            Box(haut.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 84.dp), contentAlignment = Alignment.Center) {
                Text(
                    SaisieMono.CONSIGNE,
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color(0xA6000000), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            // Zone de 48 dp (gants, froid), coche de 32 dp.
            Image(
                painterResource(Res.drawable.coche),
                contentDescription = "Terminer la saisie",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .then(haut)
                    .padding(8.dp)
                    .size(48.dp)
                    .clickable(onClick = vm::demanderTerminer)
                    .padding(8.dp),
            )
        }

        // ─── Bas droite : fiche de la série, centrer, boussole, fond ; bas gauche : zoom (au-dessus du panneau) ───
        Column(
            Modifier.align(Alignment.BottomEnd).then(bas).padding(end = 12.dp, bottom = AU_DESSUS_DU_PANNEAU),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Le « i » de la carte multi-taxons, à la même place (demande du 25 novembre 2026) : ici, la
            // fiche de la série, dont les valeurs valent pour la suite.
            BoutonCarte(Res.drawable.ic_info, "Fiche du relevé", StyleBoutonCarte.CONTROLE_SAISIE, vm::ouvrirFicheSerie)
            BoutonCarte(
                if (vm.suivrePosition || ios) Res.drawable.ic_location_on else Res.drawable.ic_location_off,
                "Recentrer sur ma position",
                StyleBoutonCarte.CONTROLE_SAISIE,
                onClick = {
                    val cible = vm.centrer()
                    if (cible != null) portee.launch { etat.animerCamera(CameraUpdate(center = cible.enPosition())) }
                },
                teinteIos = if (vm.suivrePosition) CouleursGeoMys.primaire else GRIS_INACTIF_IOS,
            )
            Boussole(etat, vm::basculerBoussole)
            BoutonCarte(Res.drawable.ic_map, "Changer fond", StyleBoutonCarte.CONTROLE, vm::montrerChoixFond)
        }
        Column(
            Modifier.align(Alignment.BottomStart).then(bas).padding(start = 12.dp, bottom = AU_DESSUS_DU_PANNEAU),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BoutonCarte(Res.drawable.ic_add, "Zoom avant", StyleBoutonCarte.CONTROLE_SAISIE, {
                portee.launch { etat.animerCamera(CameraUpdate(zoom = etat.cameraPosition.zoom + 1)) }
            })
            BoutonCarte(Res.drawable.ic_remove, "Zoom arrière", StyleBoutonCarte.CONTROLE_SAISIE, {
                portee.launch { etat.animerCamera(CameraUpdate(zoom = etat.cameraPosition.zoom - 1)) }
            })
        }

        // ─── Bas : résumé, compteur et « + » ; la mention des données au-dessus ───
        var hautPanneau by remember { mutableStateOf(0.dp) }
        AttributionFond(vm.fond, Modifier.align(Alignment.BottomCenter).padding(bottom = hautPanneau).padding(horizontal = 60.dp))
        PanneauMono(vm, enregistrer, Modifier.align(Alignment.BottomCenter).onSizeChanged { hautPanneau = with(densite) { it.height.toDp() } })
    }
}

/** La carte : les observations de la saisie (marqueurs déplaçables), le point posé au doigt, la
 *  position et sa précision. Rend son état, que les commandes de l'écran pilotent. */
@Composable
private fun CarteMono(vm: SaisieMonoViewModel): MapState {
    val ios = estIos
    val observations by vm.session.observations.collectAsState()
    // Au premier affichage : zoom 15 sur la position connue ; au retour d'un sous-écran, la caméra
    // quittée.
    val camera = remember {
        val centre = vm.centreMemorise ?: vm.tracker.position.value?.let { LatLon(it.latitude, it.longitude) } ?: FRANCE
        CameraPosition(center = centre.enPosition(), zoom = vm.zoomMemorise ?: 15.0)
    }
    val etat = rememberMapState(baseStyle = rememberStyleFond(vm.fond), initialCameraPosition = camera) {
        val position by vm.tracker.position.collectAsState()
        position?.let { CerclePrecision(LatLon(it.latitude, it.longitude), it.precision.toDouble()) }
    }
    LaunchedEffect(etat) {
        etat.setCameraPosition(camera)
        // « Démarrer » : zoom 19 sur la position.
        vm.cibleDemarrage?.let { etat.setCameraPosition(CameraPosition(center = it.enPosition(), zoom = 19.0)) }
        vm.cibleDemarrage = null
    }
    // Suivi de position : la carte glisse vers chaque nouveau point.
    LaunchedEffect(etat) {
        vm.tracker.position.collect { p ->
            if (p != null && vm.suivrePosition) etat.animerCamera(CameraUpdate(center = LatLon(p.latitude, p.longitude).enPosition()))
        }
    }
    // Déplacer la carte au doigt coupe le suivi de position.
    LaunchedEffect(etat) {
        snapshotFlow { etat.isCameraMoving && etat.cameraMoveReason == CameraMoveReason.Gesture }.collect { geste ->
            if (geste) vm.suivrePosition = false
        }
    }
    SuiviCap(etat, vm.carteSuitBoussole)
    // La carte quittée (onPause) : centre et zoom, repris au retour d'un sous-écran.
    DisposableEffect(etat) {
        onDispose {
            val c = etat.cameraPosition
            vm.centreMemorise = LatLon(c.center.latitude, c.center.longitude)
            vm.zoomMemorise = c.zoom
        }
    }

    CarteGeoMys(etat, Modifier.fillMaxSize(), onClic = vm::toucherCarte) {
        val position by vm.tracker.position.collectAsState()
        // Sous Android, le point de position est sous les marqueurs ; sous iOS, au-dessus (MapKit).
        if (!ios) position?.let { PointPosition(LatLon(it.latitude, it.longitude)) }
        // Un marqueur par observation : le toucher ouvre son popup (éditer, supprimer ; plusieurs au
        // même point, on choisit d'abord laquelle), le glisser la déplace.
        observations.forEach { obs ->
            MarqueurDeplacable(
                etat = etat,
                position = LatLon(obs.latitude, obs.longitude),
                icone = if (ios) Res.drawable.ic_location_on else Res.drawable.ic_bird_marker,
                taille = if (ios) 40.dp else 32.dp,
                ancre = if (ios) ANCRE_GOUTTE_IOS else BiasAlignment(0f, 1f),
                teinte = if (ios) CouleursGeoMys.rougePastille else null,
                description = obs.espece,
                onClic = { vm.toucherObservation(obs) },
                onFin = { p -> vm.deplacerObservation(obs, p) },
            )
        }
        // Le point de la prochaine observation, déplaçable.
        vm.pointManuel?.let { SommetDeplacable(etat, it, onDeplacement = vm::deplacerPointManuel, onFin = vm::deplacerPointManuel) }
        if (ios) position?.let { PointPosition(LatLon(it.latitude, it.longitude)) }
    }
    return etat
}

/** Le panneau du bas, sur une seule ligne depuis le 25 novembre 2026 (demande de l'utilisateur :
 *  « diminuer la hauteur du bandeau du bas ») : le groupe, ce que chaque « + » enregistre (« Merle
 *  noir × 2 ») et, dessous, le compteur des observations de la saisie, puis « + ». Le même jour, à la
 *  demande de l'utilisateur, il a perdu « Modifier », qui ramenait aux réglages (le « i » de la carte
 *  rouvre la fiche de la série), les codes du sexe et du stade sous l'espèce, l'avertissement
 *  « Donnée exceptionnelle » de la dernière observation et la ligne de confirmation de chaque « + »,
 *  avec son « Modifier » (une observation touchée s'édite : la consigne en haut de la carte le dit).
 *  - Android : `panel_bg` bleu nuit jusque sous la barre de navigation ;
 *  - iOS : le panneau de SaisieRapideView, sur le même bleu nuit. */
@Composable
private fun PanneauMono(vm: SaisieMonoViewModel, onEnregistrer: () -> Unit, modifier: Modifier) {
    val ios = estIos
    val modele by vm.saisie.modele.collectAsState()
    val resume = remember(modele) { vm.saisie.resume() }
    // Toutes les observations de la saisie, reprises comprises, comptées comme « Mes saisies ».
    val observations by vm.session.observations.collectAsState()
    val nombre = remember(observations) { vm.nombreObservations(observations) }
    val gris = if (ios) CouleursGeoMys.blanc70 else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .fillMaxWidth()
            .then(if (ios) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)) else Modifier)
            .background(FOND_PANNEAU)
            .then(if (ios) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            .padding(horizontal = 16.dp, vertical = MARGE_PANNEAU),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val taxon = modele.taxon
        Box(Modifier.size(if (ios) 28.dp else 24.dp), contentAlignment = Alignment.Center) {
            Image(
                painterResource(taxon.icone),
                contentDescription = null,
                colorFilter = ColorFilter.tint(taxon.couleur),
                modifier = Modifier.size(if (ios) 22.dp else 24.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (ios) 2.dp else 0.dp)) {
            Text(
                resume,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (ios) Color.White else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (nombre > 0) Text(SaisieMono.compteur(nombre), fontSize = 13.sp, color = gris, maxLines = 1)
        }
        if (ios) {
            BoutonIos(onEnregistrer, opaciteAppui = 0.6f) {
                Box(Modifier.size(TAILLE_PLUS).fondEnRelief(CouleursGeoMys.primaire, CircleShape), contentAlignment = Alignment.Center) {
                    SymboleSf("plus", 22f, listOf(Color.White), graisse = GraisseSymbole.GRASSE, description = "Enregistrer ici")
                }
            }
        } else {
            BoutonRondAndroid(Res.drawable.ic_add, CouleursGeoMys.primaire, BLEU_APPUYE, TAILLE_PLUS, "Enregistrer ici", onEnregistrer)
        }
    }
}

/** Marqueur touché (demande du 24 novembre 2026) : le popup de l'observation, comme celui d'un relevé
 *  de la carte multi-taxons (AlertDialog d'AppCompat sous Android, alerte système sous iOS) :
 *  « Éditer » ouvre sa fiche, « Supprimer » demande confirmation, comme la corbeille d'une espèce du
 *  relevé. Plusieurs observations au même point : la liste de choix d'abord (« HH:mm  espèce · N
 *  ind. » sous Android ; sous iOS, la liste de GeoMys-IOS : icône du groupe, espèce, heure et
 *  effectif). Elle listait toutes les observations à 30 m et ouvrait l'écran du relevé. */
@Composable
private fun DialoguesObservationTouchee(vm: SaisieMonoViewModel) {
    val auPoint = vm.observationsAuPoint
    val titreListe = SaisieMono.titreObservationsAuPoint(auPoint?.size ?: 0)
    if (!estIos) {
        DialogueChoixLignesAndroid(
            visible = auPoint != null,
            titre = titreListe,
            lignes = auPoint.orEmpty().map(SaisieMono::ligneObservationAuPoint),
            onChoisir = { i -> auPoint?.getOrNull(i)?.let(vm::choisirObservationAuPoint) },
            onFermer = vm::fermerObservationsAuPoint,
        )
    } else {
        DialogueMaterialIos(auPoint != null, titreListe, vm::fermerObservationsAuPoint, boutons = {
            BoutonIos(vm::fermerObservationsAuPoint, opaciteAppui = 0.4f) {
                Text("Fermer", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = CouleursGeoMys.blanc70)
            }
        }) {
            auPoint.orEmpty().forEachIndexed { i, obs ->
                val taxon = obs.taxon ?: Taxon.OISEAU
                Row(
                    Modifier.fillMaxWidth().clickable { vm.choisirObservationAuPoint(obs) }.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(painterResource(taxon.icone), null, colorFilter = ColorFilter.tint(taxon.couleur), modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(obs.espece, fontSize = 15.sp, color = Color.White)
                        Text("${FormatDate.heure(obs.date)} · ${obs.totalIndividus} ind.", fontSize = 12.sp, color = CouleursGeoMys.blanc70)
                    }
                    SymboleSf("chevron.right", 14f, listOf(CouleursGeoMys.blanc70))
                }
                if (i < auPoint.orEmpty().lastIndex) SeparateurIos()
            }
        }
    }
    val touchee = vm.observationTouchee
    val (titre, message) = touchee?.let(SaisieMono::optionsObservation) ?: ("" to "")
    DialogueAlerte(
        visible = touchee != null,
        titre = titre,
        message = message,
        // Un relevé sans espèce n'a pas de fiche à éditer.
        boutons = listOfNotNull(
            BoutonAlerte("Supprimer", destructif = true, action = vm::demanderSuppression),
            BoutonAlerte("Éditer", action = vm::editerObservationTouchee).takeIf { touchee?.releveSansEspece != true },
            BoutonAlerte("Fermer", annulation = true),
        ),
        onFermer = vm::fermerObservationTouchee,
    )
    val aSupprimer = vm.suppressionDemandee
    DialogueConfirmation(
        visible = aSupprimer != null,
        titre = SaisieMono.TITRE_SUPPRESSION,
        message = aSupprimer?.let(SaisieMono::messageSuppression).orEmpty(),
        boutons = listOf(
            BoutonAlerte("Annuler", annulation = true),
            BoutonAlerte("Supprimer", destructif = true, action = vm::confirmerSuppression),
        ),
        onFermer = vm::annulerSuppression,
    )
}

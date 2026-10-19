# Lot 6 : les écrans OccTax

*19 octobre 2026. Portage depuis GeoMys-Android `cdd5196`, et `d72b8da` pour ce que touchent ses deux commits du 19 octobre (§ 2). Allure iOS reprise de GeoMys-IOS `64d303b` ; son commit suivant, `5fda84c`, ne touche pas aux écrans OccTax. La logique est dans `core`, les écrans dans `ui`.*

Le lot 6 porte les écrans de saisie OccTax (`INVENTAIRE.md` § 10) :
- la **carte multi-taxons** : trace GPS, relevé ponctuel, linéaire ou surfacique, édition des sommets, détails communs, fin de saisie ;
- le **relevé** : palette des groupes, champ espèce et ses propositions, dictée, liste des espèces, détails du relevé ;
- la **caractérisation** et le **dénombrement** d'une espèce, avec ses photos et ses sons ;
- l'**écran intercalé** des champs de relevé obligatoires ;
- la **liste des observations** de la saisie en cours ;
- la **saisie mono-taxon** ;
- **Mes saisies** (envoi groupé, import GPX) et le **détail d'une saisie** (carte, export GPX).

Les écrans suivent la décision 15 ([`LOT-5.md`](LOT-5.md) § 2) : l'allure de GeoMys-Android sous Android, celle de GeoMys-IOS sous iOS ; les éléments, les textes et les comportements de GeoMys-Android sur les deux.

**État.** Tout le contenu du lot est porté. Les tests du socle passent de 797 à 890 sur la JVM, et de 787 à 880 sur le simulateur iOS. S'y ajoutent les premiers tests d'écran : 8, sur la JVM (§ 7).

**Critère de sortie** (recette terrain OccTax) : **pas encore atteint**. Les écrans ont été essayés sur l'émulateur (Pixel 10) et sur le simulateur (iPhone 17 Pro), avec des données d'essai. La recette sur le Galaxy A12 et l'iPhone 16, contre le serveur GeoNature des lots précédents, reste à faire (§ 8).

## 1. Ce qui est porté

| GeoMys-Android | GeoMys-IOS (`64d303b`) | GeoMys Common |
|---|---|---|
| `TraceFragment.kt`, `fragment_trace.xml` | `UI/TraceView.swift` | `ui/…/occtax/SaisieMultiScreen.kt` et `SaisieMultiViewModel.kt` ; calculs dans `core/…/occtax/CarteSaisie.kt`, fin de saisie dans `FinSaisie.kt` |
| `TraceViewModel.kt` | — | `core/…/occtax/SessionSaisieOcctax.kt`, tenue par `ui/…/occtax/SessionSaisieViewModel.kt` |
| `SaisieObservationFragment.kt`, `fragment_saisie_observation.xml`, `item_pending_obs.xml` | `UI/Saisie/SaisieObservationView.swift`, `LigneEspeceView.swift` | `ui/…/occtax/SaisieReleveScreen.kt`, `SaisieReleveViewModel.kt`, `ChampEspece.kt`, `EtatChampEspece.kt` ; le relevé dans `core/…/occtax/ReleveEnCours.kt` |
| `SaisieRapideFragment.kt` (`d72b8da`), `fragment_saisie_rapide.xml` | `UI/Saisie/SaisieRapideView.swift` | `ui/…/occtax/SaisieMonoScreen.kt` et `SaisieMonoViewModel.kt` ; la série dans `core/…/occtax/SaisieMono.kt` |
| `CaracterisationFragment.kt`, `saisie/OcctaxFieldsRenderer.kt` | `UI/Saisie/CaracterisationView.swift` | `ui/…/occtax/CaracterisationScreen.kt`, `CaracterisationViewModel.kt`, `EcranFormulaireEspece.kt` ; règles dans `core/…/occtax/FormulairesEspece.kt` |
| `DenombrementFragment.kt`, `item_denombrement.xml`, `MediaImport.kt`, `PhotoCapture.kt` | `DenombrementView` (`CaracterisationView.swift`), `UI/Saisie/MediaPickerView.swift` | `ui/…/occtax/DenombrementScreen.kt`, `DenombrementViewModel.kt` ; médias dans `ui/…/composants/Medias.kt` |
| `saisie/AdditionalFieldsRenderer.kt` (`d72b8da`) : les widgets | `UI/Saisie/AdditionalFieldsRenderer.swift` | `ui/…/occtax/ChampsAdditionnelsVue.kt` ; valeurs et encodage au lot 4 (`saisie/ChampsAdditionnels.kt`) |
| `DetailsReleveDialog.kt` | `UI/Saisie/DetailsReleveView.swift` | `ui/…/occtax/DialogueDetailsReleve.kt` ; règles dans `core/…/occtax/DetailsReleveFormulaire.kt` et `DetailsReleve.kt` |
| `DetailsReleveFragment.kt`, `fragment_details_releve.xml` | `UI/Saisie/DetailsReleveIntercalaireView.swift` | `ui/…/occtax/DetailsReleveIntercalaireScreen.kt` |
| `ObservationsFragment.kt`, `fragment_observations.xml`, `item_observation.xml` | `UI/Saisie/ObservationsView.swift` | `ui/…/occtax/ObservationsScreen.kt` ; textes dans `core/…/occtax/ListeObservations.kt`, regroupement dans `Releve.kt` |
| `SortiesFragment.kt`, `fragment_sorties.xml`, `item_sortie.xml`, `EnvoiGroupeUi.kt` | `SortiesView` (`UI/PlaceholderViews.swift`) | `ui/…/occtax/MesSaisiesScreen.kt` et `MesSaisiesViewModel.kt` ; règles dans `core/…/occtax/MesSaisies.kt`, envoi groupé dans `core/…/envoi/EnvoiGroupe.kt` |
| `SortieDetailFragment.kt`, `fragment_sortie_detail.xml`, `infowindow_obs.xml` | `UI/SortieDetailView.swift` | `ui/…/occtax/SortieDetailScreen.kt` et `SortieDetailViewModel.kt` ; carte dans `core/…/occtax/AffichageSortie.kt` |
| `saisie/TaxRefLookup.kt`, `saisie/ChampsTaxon.kt`, `saisie/PreferencesSaisie.kt` | — | `core/…/saisie/RechercheTaxRef.kt` et `ChampsTaxon.kt` ; `core/…/store/PreferencesGeoMys.kt` (mêmes clés) |
| `saisie/TaxonPalette.kt`, `saisie/TaxonSelector.kt` | `UI/Saisie/TaxonPalette.swift` | `PaletteGroupes` (`SaisieReleveScreen.kt`), `ui/…/occtax/GroupesTaxons.kt`, `core/…/occtax/GroupesTaxons.kt` |
| `saisie/SpeechToTextHelper.kt` | `UI/Saisie/DicteeVocale.swift` | `ui/…/composants/Dictee.kt` ; vocabulaire de la dictée iOS dans `core/…/saisie/VocabulaireDictee.kt` |
| `location/LocationTracker.kt`, `location/LocationForegroundService.kt` | `Location/LocationTracker.swift` | `core/…/location/` (`LocationTracker`, `SourcePositions` pour Android et iOS) ; `androidApp/…/LocationForegroundService.kt` |
| `MapUtils.kt` (fonds), `CompassView.kt`, `MapCompassController.kt` | `UI/Map/MapView.swift` | `core/…/carte/FondsCarte.kt` ; `ui/…/carte/` (`CarteGeoMys`, `SourcesFonds`, `ElementsCarte`, `Boussole`, `BoutonsCarte`) |
| `gpx/GpxUtils.kt` | — | `core/…/gpx/GpxUtils.kt` ; lecture XML commune dans `LecteurXml.kt` |
| `util/GeoJsonCoords.kt`, `store/NidificationOiseaux.kt` | — | mêmes noms dans `core/…/util/` et `core/…/store/` |
| `AlerteStockage.kt` | — | `ui/…/composants/AlerteStockage.kt` |
| `MainActivity.kt` : sortie du flux de saisie | — | `ui/…/navigation/NavigationGeoMys.kt` (§ 4) |

**Pas dans ce lot.** `SaisiesEnAttenteFragment` relève des Suivis (inventaire OccTax, § 2) : il vient au lot 7.

## 2. GeoMys-Android a avancé pendant le lot

Deux commits du 19 octobre au matin, reportés :
- **`cad466b`**, « Saisie mono-taxon : la naturalite et les champs form_fields ne se perdent plus ». Il corrige le défaut n° 1 de l'inventaire (§ 8.2), qui était l'écart 8a de l'inventaire OccTax. La saisie mono le suit : la question de ce défaut ne se pose plus. Sa garde, `CaracterisationPariteMonoMultiTest`, lisait le texte des deux fragments ; ici, elle vérifie les observations elles-mêmes (§ 7).
- **`d72b8da`**, « Champs additionnels : des noms JSON stables pour les options de nomenclature ». `OptionNomenclature(code, label)` remplace le `Pair` dont R8 renommait les champs (défaut n° 2). Il est porté dans `model/Models.kt`, `model/AdditionalFields.kt`, `network/AdditionalFields.kt` et `saisie/ChampsAdditionnels.kt`, avec sa garde sur les noms JSON.

Les fichiers touchés par ces deux commits portent `@ d72b8da` dans leur en-tête ; les autres restent à `cdd5196`.

## 3. Deux allures

Ce que chaque plateforme fait elle-même (`expect`/`actual`) :

| Élément | Android | iOS |
|---|---|---|
| Date et heure du relevé | cases ouvrant `DatePickerDialog` (jusqu'à aujourd'hui) et `TimePickerDialog` (24 h) | `UIDatePicker` compact de UIKit |
| Dictée du nom d'espèce | `SpeechRecognizer` du système | `SFSpeechRecognizer` (fr-FR), avec les espèces proposées comme vocabulaire |
| Photos et sons | appareil photo (intention système), galerie, fichiers son ; photos recompressées à 2 048 px, JPEG 85 % | `UIImagePickerController`, `PHPickerViewController`, `UIDocumentPickerViewController` ; même recompression |
| Listes de choix | `Spinner` Material, liste déroulante d'`AutoCompleteTextView` | menu système (`UIMenu`) |
| Confirmations | `MaterialAlertDialog` (Material 3) ou AlertDialog d'AppCompat ; boutons empilés s'ils ne tiennent pas sur une ligne | `UIAlertController`, en alerte ou en feuille d'actions ; bouton destructif en rouge |
| Confirmation d'une observation (mono) | vibration de 60 ms | retour haptique moyen (`UIImpactFeedbackGenerator`) |
| GPX | partage par intention ; import par le sélecteur de documents | feuille de partage ; `UIDocumentPickerViewController` |
| Suivi GPS | `LocationManager` (GPS et réseau, 2 s, 5 m) ; service au premier plan « suivi GPS actif » pendant une trace | `CLLocationManager` (5 m) ; positions en arrière-plan le temps d'une trace |
| Champ nombre des champs additionnels | pavé décimal | clavier chiffres et ponctuation, comme GeoMys-IOS (le pavé décimal d'iOS n'a pas le signe moins) |

**La carte.** Une seule carte MapLibre (maplibre-compose) remplace osmdroid et MapKit. Elle prend l'allure de chacun pour ce qu'elle dessine elle-même :
- point de position et cercle de précision : bleu translucide d'osmdroid sous Android, `#007AFF` à 15 % sous iOS ;
- boutons ronds de GeoMys-Android (`btn_circle_white`, `btn_circle_blue`) ou de GeoMys-IOS (`BoutonCarteRond`) ;
- boussole : `CompassView` sous Android, `BoussoleBouton` sous iOS.

Le point bleu vient du suivi GPS unique (`LocationTracker`) : le second flux de position d'osmdroid (inventaire OccTax, § 4-12) disparaît. Le cap vient du fournisseur de maplibre-compose, lissé comme dans le prototype du lot 0 (`LissageCap`).

**Le `UIDatePicker` sous la toile.** Comme l'interrupteur du lot 5, il est placé sous la toile Compose, dans une découpe transparente. Son fond est peint de la couleur du dialogue ; sinon, le fond de la fenêtre paraîtrait autour.

**Composants nouveaux** (`ui/…/composants`) : `ChampDateHeure`, `ChampTexteFormulaire` (champ compact des formulaires), `ListeDeroulante`, `DialogueFormulaire` (MaterialAlertDialog de Material 3 sous Android, `DialogueMaterial` sous iOS), `DialogueConfirmation`, `FeuilleActionsSysteme` (avec message), `BoutonIcone`, `Medias`, `Dictee`, `Fichiers`, `Partage`, `Vibration`, `ClavierNombresEtPonctuation`, `AlerteStockage`.

## 4. Navigation et session

- **Routes typées.** `RouteSaisieMulti(sortieId)`, `RouteReleve`, `RouteCaracterisation(index, comportementSeul, mono)`, `RouteDenombrement(index, mono)`, `RouteDetailsReleve(géométrie, mono)`, `RouteObservations`, `RouteSaisieMono(additionnelsJson)`, `RouteMesSaisies`, `RouteSortieDetail(sortieId)`. `RouteAVenir` ne sert plus qu'aux Suivis, à OccHab, à l'Explorer et aux cartes.
- **Session.** `SessionSaisieViewModel` vit au-dessus du graphe, comme le `TraceViewModel` partagé (`activityViewModels()`) de GeoMys-Android. Les écrans ne se passent plus de JSON par `savedStateHandle` (inventaire OccTax, § 4-4). Ils lisent la session et le ViewModel de l'écran qui les a ouverts.
- **Sortie du flux.** Les sept écrans de saisie forment `DESTINATIONS_SAISIE`, comme dans `MainActivity`. Les quitter dans l'appli enregistre le brouillon, puis arrête la trace et le suivi GPS. Passer en arrière-plan ne les arrête pas.
- **Double toucher.** `naviguerDepuis` n'empile l'écran suivant que si l'écran de départ est encore au premier plan (`naviguerSur` de GeoMys-Android).
- **Saisie suivante.** Au retour d'un relevé terminé, la carte repart en positionnement seulement si l'écran précédent est la carte multi, comme le drapeau qu'Android pose sur l'écran précédent.
- **Accueil.** « Saisie multi-taxons » et « Saisie mono-taxons » repartent d'une session vide. La saisie mono intercale l'écran des détails quand un champ de relevé obligatoire n'a pas de valeur par défaut.
- **Pas de passerelle** entre saisie mono et multi (décision produit d'Android du 17 octobre, § 8.3 de l'inventaire) : elle est verrouillée par un test (§ 7).

## 5. La logique sort des écrans

Sous Android, l'état et les règles vivaient dans les Fragments, leurs vues et leurs tags. Ils deviennent des classes du socle, testées sans écran :
- `SessionSaisieOcctax` (`TraceViewModel`). Les `LiveData` deviennent des `StateFlow` sur des listes non modifiables (inventaire OccTax, § 4-2). L'auto-save écrit à chaque modification, hors de la composition, et signale un échec d'écriture (§ 4-3).
- `ReleveEnCours` et `SaisieMono` : reprise d'un relevé, valeurs de départ, enregistrement au fil de la saisie. Les champs d'occurrence d'une espèce passent à l'observation par une seule fonction (`EspeceReleve.surObservation`), commune aux deux saisies.
- `FormulairesEspece`, `DetailsReleveFormulaire`, `CarteSaisie`, `FinSaisie`, `MesSaisies`, `EnvoiGroupe`, `AffichageSortie`, `ListeObservations`, `RechercheTaxRef`, `GroupesTaxons`.

**Doublons factorisés** (inventaire OccTax, § 4-10) :
- le champ espèce des deux saisies (`EtatChampEspece`) : groupe, mode « nom scientifique », propositions, recherche, libellé ;
- les valeurs de départ des détails (`DetailsReleveFormulaire`) ;
- « Terminer » (`FinSaisie`) ;
- la liste des espèces d'une saisie, pour Mes saisies et le détail d'une saisie (`MesSaisies.especesAgregees`).

## 6. Écarts assumés

**Défauts d'Android reproduits, à trancher** (décision 5 de l'inventaire) :
- la date des « Détails communs » vaut l'heure d'ouverture du dialogue, et chaque nouveau relevé en hérite, même après une date a posteriori (§ 8.2, n° 4) ;
- le total d'individus compte tous les dénombrements dans le relevé, le premier seulement dans les listes (n° 5) ;
- « Continuer la saisie » ouvre toujours la carte multi, même pour une saisie mono (n° 7) ;
- la boussole suit le nord magnétique (§ 8.3, à trancher).

**Particularités d'Android reproduites :**
- au retour d'un sous-écran, la palette revient au groupe de la première espèce du relevé ;
- « Détails du relevé » n'enregistre qu'au prochain changement de la liste des espèces, ou à la coche : un retour système les perd ;
- l'indicateur de la saisie mono montre les codes des nomenclatures ;
- en saisie mono, le panneau et un message bref peuvent chevaucher les commandes de la carte ;
- en saisie mono, le déterminateur reste vide tant qu'on ne passe pas par la caractérisation.

**Pas reproduit :**
- la bulle « Sommet N » qu'osmdroid affiche au toucher d'un sommet en cours : un artefact de la bibliothèque ;
- le code mort relevé par l'inventaire (inventaire OccTax, § 4-8 e) ;
- de GeoMys-IOS, ce qu'Android n'a pas : la liste de « candidats vocaux » de la dictée (les hypothèses sont résolues comme sous Android, par `rechercherParmiCandidats`), « (sans nom) » dans la liste des observations, le lien vers la carte du bandeau de cette liste.

**Comportements d'Android sous iOS :**
- coche de la carte multi toujours active : sans toucher, le point est le centre de la carte ; trop peu de sommets, le message bref « Au moins N sommets requis » ;
- consigne de la carte : le texte d'Android (« appui long ») ;
- champs obligatoires vides en caractérisation et en dénombrement : le message bref d'Android, au lieu de l'alerte de GeoMys-IOS ;
- textes d'Android partout : « Date du relevé », « Date de début », « Détails du relevé », « Les champs marqués d'un * sont obligatoires. » ; la ligne « Géométrie » des détails existe aussi sous iOS ;
- coordonnées de la liste des observations au format d'Android en français : « 42,9649, 1,6056 ».

**Allure de GeoMys-IOS :**
- titre du relevé : le dernier niveau du fil d'Ariane (sous Android, toujours « Nouveau relevé », même en reprise, comme le layout) ;
- détails du relevé : jeu de données et « Ajouter un observateur » en menu système, sans recherche, la liste des observateurs au-dessus ; le champ habitat a l'allure du `HabitatPicker`, mais la règle d'Android (le libellé choisi s'inscrit dans le champ, la frappe annule le choix) ;
- écran intercalé : « Annuler » (élément d'Android) en contour, « → Continuer » en vert.

**Choix du portage :**
- **Médias.** Ils sont enregistrés en URI `file:` absolues sous `fichiers/medias`, comme sous Android. Le conteneur d'une appli iOS change de chemin à chaque mise à jour : à l'envoi et à la suppression, un média introuvable est cherché par son nom dans le dossier des médias (`GeoNatureUpload.fichierMedia`).
- **Alerte d'échec d'écriture** (`AlerteStockage`) : sur la carte multi, la saisie mono (fin et brouillon) et Mes saisies (import GPX). Pas sur le relevé : Android ne l'y montre pas, elle paraît au retour sur la carte.
- **Cap de la carte.** Seul le cap est commandé, sans animation, et il ne l'est pas pendant un geste. Une animation de la caméra interrompue par un geste ou une commande plus récente s'arrête sans erreur.
- **Cercle de précision** autour du point de position, sur les deux cartes de saisie : osmdroid le dessinait par défaut.
- **Sélecteurs de date d'Android** : l'en-tête gris du système, comme sous GeoMys-Android, et l'accent orange du thème (`android:colorPrimary`, `android:colorAccent`).

**Mort du processus (Android).** La restauration des écrans par l'état sauvegardé d'Android n'est pas portée (inventaire OccTax, § 4-7). La saisie en cours est dans son brouillon, écrit à chaque modification : elle se reprend par « Mes saisies ». Si Android ferme l'appli en arrière-plan, par exemple pendant une photo, l'appli rouverte retrouve sa pile d'écrans, mais avec une session vide :
- un sous-écran dont l'espèce manque revient en arrière ;
- un relevé rouvert pour modification (depuis la liste ou la saisie mono), introuvable, se referme : sans cette règle, il devenait un nouveau relevé sans position (`ReleveEnCours.introuvable`) ;
- un nouveau relevé et la carte repartent vides, et une nouvelle sortie commence si l'on saisit encore ;
- la photo en cours est perdue.

GeoMys-Android fait de même pour la carte multi, mais restaurait le relevé, le dénombrement et la photo en cours. Pas de plantage attendu : les écrans cherchent leur espèce ou leur relevé avant de s'en servir. Ce cas n'a pas été reproduit sur un appareil configuré : la configuration de l'émulateur est incomplète, et l'appli y rouvre les Paramètres. Il est à éprouver pendant la recette, puis à trancher (décision 11 de l'inventaire, § 10).

## 7. Tests

**Socle : 93 cas de plus**, sur la JVM et le simulateur iOS. **Écrans : 8 cas**, sur la JVM (`ui`, nouveau jeu de tests `androidHostTest`).

| Origine | Cas | Fichiers |
|---|---|---|
| Portés de GeoMys-Android | 28 | `FondsCarteTest` (6 : `FondCarteTest` et `FondCartePersistanceTest`), `ChampsTaxonTest` (5 : `ChampsTaxonTest` et `GroupesEtRegnoPlanteTest`), `GeoJsonCoordsTest` (4), `GpxUtilsTest` (5 sur 10), `BasculerParcoursTest` (3 sur 4 : volet B de `DefautsPortageIosTest`), `LocationTrackerTest` (2 sur 5), `SessionSaisieOcctaxTest` (2 sur 7 : `TraceViewModelReleveTest`), `AdditionalFieldsParsingTest` (1 : le cas ajouté à `GsonRoundTripTest` par `d72b8da`) |
| Gardes textuelles réécrites | 10 | voir ci-dessous |
| Écrits pour le portage | 63 | `CarteSaisieTest` (6), `DetailsReleveFormulaireTest` (7), `FinSaisieTest` (5), `FormulairesEspeceTest` (6), `ListeObservationsTest` (4), `ReleveEnCoursTest` (8), `SaisieMonoTest` (3 sur 4), `RechercheTaxRefTest` (4), `VocabulaireDicteeTest` (3), `NidificationOiseauxTest` (3), `FichierMediaTest` (1), et les cas propres au portage de `GpxUtilsTest` (5 : la lecture XML commune lit et refuse ce que lisait et refusait le SAX d'Android), `LocationTrackerTest` (3) et `SessionSaisieOcctaxTest` (5 : l'auto-save) |

**Gardes textuelles réécrites en vrais tests** (inventaire des tests, § 7.3). Elles lisaient le texte des Fragments ; les règles, sorties des écrans, se vérifient en actionnant l'état et les ViewModels :

| Garde de GeoMys-Android | Règle | GeoMys Common |
|---|---|---|
| `DefautsPortageIosTest.le_bouton_de_trace_ne_redemarre_de_zero_que_sur_une_trace_vide` | ▶ continue une trace non vide | `BasculerParcoursTest` (socle) |
| `CaracterisationPariteMonoMultiTest` (`cad466b`) | chaque champ de caractérisation arrive sur l'observation, en mono comme en multi | `SaisieMonoTest` (socle) |
| `ChangementDeGroupeTest` (2 cas) | le texte tapé survit au changement de groupe et de mode | `ChampEspeceReglesTest` (écrans) |
| `ProposeEgaleAccepteTest.les_ecrans_retirent_les_propositions_perimees_avant_de_recalculer` | les propositions de l'ancien périmètre ne répondent plus | `ChampEspeceReglesTest` |
| `SuggestionPorteSonCdNomTest.les_ecrans_de_saisie_consomment_la_suggestion_et_ne_re_resolvent_pas_son_texte` | la suggestion choisie garde son cd_nom, au relevé et en mono | `ChampEspeceReglesTest` (2 cas) |
| `ResolutionPurementLocaleTest.aucun_chemin_de_saisie_n_ecrit_dans_le_cache_taxref`, part OccTax | frappe, choix, nom refusé, dictée : le cache TaxRef reste identique, octet pour octet | `ChampEspeceReglesTest` ; la part des Suivis est au lot 4 |
| `DefautsPariteLot2Test.aucune_passerelle_entre_saisie_mono_et_multi` | aucune sortie d'un écran ne mène à l'autre saisie | `PasserelleMonoMultiTest` (2 cas, `when` sans `else` : une passerelle ne compile pas) |

Restent les cinq gardes d'OccHab (lot 8).

**Tests d'écran.** `ServicesEssai` monte les vrais services sur des doublures : dossier temporaire, réglages en mémoire, aucun réseau (toute requête échoue), aucun GPS. `EtatChampEspece` reçoit son répartiteur de calcul, que les tests remplacent par l'horloge de `runTest`.

**Non porté.** `MediaImportTest` (3 cas : grande photo réduite et réencodée, petite photo pas agrandie, son copié tel quel). La recompression est propre à chaque plateforme (`Bitmap`, `UIImage`), et les tests JVM sans Robolectric ne savent pas la faire tourner. Elle est à vérifier sur les téléphones.

**Essais d'affichage**, avec des données d'essai (`action=taxref-essai`, `sorties-essai`, `referentiels-essai`, § 10), jamais sur les téléphones :
- émulateur Pixel 10 :
  - Mes saisies, détail d'une saisie, import GPX ;
  - carte multi : trace, polygone, glisser d'un sommet, reprise, dialogues ;
  - relevé : propositions, ajout, liste, coche ;
  - caractérisation, dénombrements, effectifs ;
  - dialogue des détails : listes filtrées sans accents, observateurs, date de fin ajoutée puis retirée, sélecteur de date du système, champ obligatoire, conservation des valeurs ;
  - écran intercalé, puis relevé, puis retour à la carte ;
  - liste des observations et sa confirmation ;
  - saisie mono : réglages, « Démarrer » au zoom 19, point au doigt, « + », confirmation et compteur, « 1 obs. ici », édition aller-retour, fin, trois dénombrements ;
- simulateur iPhone 17 Pro :
  - Mes saisies, détail, carte multi, reprise, relevé ;
  - caractérisation, dénombrement ;
  - dialogues des détails du relevé et des détails communs ;
  - écran intercalé, observations ;
  - réglages et carte de la saisie mono.

**Pas encore essayés** (il faut un appareil) : dictée, photos et sons, envoi réel, trace écran verrouillé.

**Corrigé en cours de lot.** Les champs additionnels ne se redessinaient pas après une saisie. Compose sautait leurs lignes, dont l'état et la définition restaient les mêmes objets ; elles sont marquées `@NonSkippableComposable`. Le défaut touchait aussi la caractérisation et le dénombrement.

## 8. Critère de sortie : la recette terrain

À faire sur le Galaxy A12 et l'iPhone 16, avec la configuration et les données chargées aux lots 2 et 5. L'utilisateur saisit lui-même ses identifiants ; aucune donnée d'essai n'est chargée sur les téléphones.

1. **Saisie multi-taxons.** Trace GPS en marchant, relevé ponctuel, linéaire et surfacique, plusieurs espèces par relevé, caractérisation, dénombrements avec une photo et un son, détails du relevé et détails communs, liste des observations, « Terminer » puis envoi.
2. **Saisie mono-taxon.** Réglages, « Démarrer », une série de « + » en marchant, détails, fin et envoi.
3. **Mes saisies.** Envoi groupé, continuer une saisie, détail, export puis import GPX.
4. **Dictée** du nom d'espèce sur les deux téléphones.
5. **Côté serveur** : les relevés, les observations et les médias arrivent dans GeoNature, sans doublon.
6. **Points à éprouver** :
   - la trace écran verrouillé (iOS) et la notification du suivi GPS (Android) ;
   - une photo prise pendant qu'Android manque de mémoire (§ 6, mort du processus) ;
   - le clavier des champs additionnels « nombre » sous iOS, si le serveur en a.

## 9. Reste à faire

- **La recette terrain** (§ 8), et avec elle les premiers envois réels depuis les écrans (`LOT-3.md`).
- **À trancher** :
  - les défauts d'Android reproduits (§ 6 ; inventaire, § 8.2, n° 4, 5 et 7) ;
  - le nord de la boussole (§ 8.3) ;
  - la mort du processus sous Android (§ 6).
- **Lot 7** : les Suivis, dont « Saisies en attente » et le champ espèce de leur formulaire, qui reprendra la liste déroulante de ce lot.
- **Lots 8 et 9** : OccHab, l'Explorer et les cartes (MBTiles, téléchargement de zones).

## 10. Outils et versions

- **Aucune dépendance nouvelle.** Le module `ui` gagne des tests sur la JVM (`withHostTest`), avec kotlin.test, kotlinx-coroutines-test, le `MockEngine` de Ktor, multiplatform-settings-test et okio.
- **Simulateur des tests iOS.** `-Pgeomys.simulateur=<UDID>` impose un simulateur aux tests iOS du socle ; `outils/verifier.sh --simulateur=<UDID>` le transmet. Un simulateur déjà ouvert reste ainsi tranquille.
- **Autorisations.**
  - Android : localisation précise et approximative, service au premier plan de localisation, micro, état du réseau, vibration ; visibilité du service de reconnaissance vocale (`<queries>`) ; `FileProvider` pour l'appareil photo et le partage du GPX.
  - iOS : localisation (pendant l'utilisation, et en arrière-plan pendant une trace : `UIBackgroundModes` `location`), micro, reconnaissance vocale, appareil photo. Les textes sont ceux de GeoMys-IOS, au nom de l'appli.
- **Ressources.** Reprises de GeoMys-Android :
  - les icônes des groupes et des cartes ;
  - `nidification_oiseaux.json` ;
  - `ic_bird_large`, remplie de blanc ;
  - `ic_menu_delete`, d'AOSP, en xxhdpi.
- **Paramètres de lancement** (essais d'affichage) :
  - `ecran=` :
    - `mes-saisies`, `detail-saisie` ;
    - `saisie-multi`, `saisie-suivante`, `reprise-saisie` ;
    - `releve-essai`, `releve-nouveau`, `caracterisation-essai`, `denombrement-essai` ;
    - `details-releve`, `details-communs`, `details-intercale` ;
    - `observations-essai`, `saisie-mono`, `saisie-mono-carte` ;
  - `action=` (émulateur et simulateur seulement) :
    - `sorties-essai` : des saisies fictives ;
    - `taxref-essai` : un petit référentiel ;
    - `referentiels-essai` : jeux de données, observateurs, heures et date de fin, deux champs de relevé obligatoires.

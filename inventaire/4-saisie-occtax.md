# 4. Écrans de saisie OccTax

> Détail de l'inventaire GeoMys. La synthèse, les chiffres globaux et les décisions à prendre sont dans [`../INVENTAIRE.md`](../INVENTAIRE.md).
> Source analysée en lecture seule : GeoMys-Android `cdd5196` (v1.4.7, 18 octobre 2026). Les équivalents iOS ont été cherchés dans GeoMys-IOS `64d303b`.
> La colonne « Cible » donne une destination indicative ; la structure proposée pour GeoMys est décrite au § 9 de la synthèse.

## 1. Inventaire fichier par fichier

Légende : « Lignes » = `wc -l` ; entre parenthèses, les lignes de code (non vides, hors commentaires), même convention que la colonne `code` de `releve-automatique.tsv`. Les chemins Android sont relatifs à `app/src/main/java/fr/ariegenature/geomys/ui/` et « l. » renvoie aux numéros de ligne au commit cdd5196. GeoMys est vide : la colonne « Cible » est donc une proposition. Elle suppose un module `:shared` (commonMain : modèles, règles, ViewModels) et un module `:composeApp` (écrans Compose en commonMain, glue plateforme en androidMain/iosMain) ; `…` = `fr/ariegenature/geomys`.

| Fichier | Lignes | Rôle (1 phrase) | Catégorie | À remplacer / dépendances plateforme (précis) | Cible dans GeoMys | Remarques |
|---|---|---|---|---|---|---|
| TraceViewModel.kt | 301 (168) | État partagé de la saisie OccTax (observations en cours, id de la sortie éditée, saisie a posteriori, détails communs), avec auto-save du brouillon dans SortieStore. | COMMUN-ADAPTÉ | `AndroidViewModel(Application)` (l.30) → `ViewModel` KMP (lifecycle ≥ 2.8 ; le projet est en 2.11.0) ; `LiveData/MutableLiveData` (l.23-24, 43-52) → `StateFlow` avec listes immuables (cf. §4-2) ; `SortieStore(application)` (l.41 ; SharedPreferences + filesDir, écriture `commit()` synchrone) → interface `SortieRepository` (suspend) injectée ; `(application as GeoMysApplication).locationTracker` (l.31 ; LocationManager + `LiveData<android.location.Location>`) → interface `LocationTracker` commune (StateFlow) ; `DetailsReleveResult` (défini dans DetailsReleveDialog.kt:196) → commonMain ; modèles `Sortie/Observation` (valeurs par défaut `UUID.randomUUID()` et `System.currentTimeMillis()`, model/Models.kt:34-174) → `kotlin.uuid.Uuid` et `Clock.System`. | `:shared` commonMain `…/occtax/session/SessionSaisieOcctax.kt` (VM du graphe de navigation « saisie ») | Aucun `Context` pour des ressources ni de Toast : l'`Application` ne sert qu'au store et au tracker. Test existant : TraceViewModelReleveTest (Robolectric), à réécrire en commonTest. iOS : Store/SessionSaisie.swift (118 l.). Il ne couvre que les détails communs et la saisie a posteriori : les vues iOS lisent et écrivent SortieStore elles-mêmes. |
| TraceFragment.kt | 1 226 (898) | Carte de la saisie multi-taxons : suivi GPS et tracé, pose de relevés point/ligne/polygone, déplacement et édition des géométries existantes, détails communs, fin et envoi de la sortie. | MIXTE | osmdroid (`MapView`, `Marker` déplaçable, `Polyline`, `Polygon`, `MapEventsOverlay`, `MyLocationNewOverlay`, `BoundingBox`, `CustomZoomButtonsController`, `IGeoPoint`, `Configuration.userAgentValue` l.134) → maplibre-compose. `GeoPoint` dans la logique (sommets, emprise, `GeomFinale`, `GeoJsonCoords`) → `LatLon`. Permissions `RequestMultiplePermissions` + `checkSelfPermission` (l.127-131, 1019-1027) → `PermissionLocalisation`. `LocationForegroundService.start/stop` (l.1031, 1041, 1068) → `SuiviArrierePlan`. `MapCompassController` (SensorManager) + `CompassView` (l.252-253, 358-369) → `Boussole` + composable. `getSharedPreferences("GeoMys_prefs").getBoolean("enregistrer_trace")` (l.158-160) → multiplatform-settings. `Gson().toJson(Map)` (l.404-405) → état partagé, ou kotlinx.serialization. `SimpleDateFormat("HH:mm")` + `Date` (l.943, 987) → kotlinx-datetime + format maison. `"%.0f m".format` (l.536) → format maison. `System.currentTimeMillis` (l.1083) → `Clock.System`. Toast (l.335, 725, 1149), `AlertDialog` (l.1001, 1055, 1179), `OnBackPressedCallback` (l.203-208), `findNavController/naviguerSur/Bundle/savedStateHandle` (l.166-167, 386-409, 1010), insets (l.1212-1224), `ContextCompat.getColor/ColorStateList/Paint`, `displayMetrics.density` (l.478-480) → équivalents Compose (Snackbar, AlertDialog, BackHandler, navigation-compose, WindowInsets, thème, Dp). | `:composeApp` `…/ui/occtax/carte/CarteMultiScreen.kt` + `CarteMultiViewModel` ; géométrie et emprise → `:shared` `…/geo/` | Réécriture Compose : **XL**. ≈ 253 l. de code de logique, ≈ 374 l. de carte, ≈ 212 l. de présentation. Dialogues : « Terminer la sortie » (liste), options d'un relevé, résultat d'envoi, détails communs (DetailsReleveDialog), choix du fond (MapUtils), alerte de stockage. Pas de bottom sheet. iOS : UI/TraceView.swift (987 l.) + UI/Map/MapView.swift (MKMapView). |
| SaisieRapideFragment.kt | 1 285 (999) | Saisie mono-taxon : on choisit le groupe et l'espèce (autocomplétion TaxRef, dictée) et les paramètres par défaut (caractérisation, dénombrement, détails du relevé) ; chaque « + » enregistre ensuite une observation au GPS ou au point tapé. | MIXTE | osmdroid (`MapView`, `Marker` déplaçable, `MapEventsOverlay`, `MyLocationNewOverlay` + cercle de précision l.287-288) → maplibre-compose. `Vibrator/VibrationEffect` 60 ms (l.949-957) → `RetourHaptique`. `SpeechToTextHelper` + `RequestPermission` micro (l.173-178, 223-230, 428) → `DicteeVocale`. Permissions et service de localisation (l.166-171, 332-345, 615, 1146, 1203, 1226). `InputMethodManager` (l.813-819) → `LocalSoftwareKeyboardController`. `android.location.Location.distanceBetween` (l.1100) → distance commune (kotlin.math). Gson `TypeToken<Map>` et `<List<Denombrement>>` (l.203-208, 217-220, 663-682, 729-767, 1176-1177) → kotlinx.serialization. `savedInstanceState` (clés rs_*, l.192-211, 1163-1178) → `SavedStateHandle` KMP. `savedStateHandle.getLiveData` (l.652-699) → état partagé. `PreferencesSaisie(Context)` (l.368, 377, 409-415) → settings. `SimpleDateFormat("HH:mm")` (l.1070, 1109). `AutoCompleteTextView`/`TextWatcher`/`createAutocompleteAdapter` (l.393-531) → TextField + menu déroulant. `ColorStateList` et `taxonCouleur(Context)` (l.1017-1018). Toast, AlertDialog, retour système (l.260-265). | `:composeApp` `…/ui/occtax/mono/SaisieMonoScreen.kt` + `SaisieMonoViewModel` ; construction de l'Observation → `:shared` `…/occtax/saisie/FabriqueObservation.kt` | Réécriture Compose : **L** (avec la carte, proche de XL). ≈ 437 l. de code de logique. Écart : `naturalite` et `occExtraJson`, renvoyés par Caractérisation, ne sont jamais lus (cf. §4-8a). Code mort : le 1er `setOnItemClickListener` (l.398-406) est écrasé par celui des l.433-445. iOS : UI/Saisie/SaisieRapideView.swift (753 l.). |
| SaisieObservationFragment.kt | 1 053 (774) | Saisie ou édition d'un relevé multi-taxons : liste des espèces en cours, détails du relevé, ouverture des sous-écrans, auto-save « au fil de l'eau », relevé sans espèce. | MIXTE | `java.util.UUID` (l.108, 156, 938) → `kotlin.uuid.Uuid`. `java.util.Calendar` (l.886-887) → kotlinx-datetime. `NidificationOiseaux.estEnPeriode(requireContext(), …)` (asset `nidification_oiseaux.json`, l.897) → ressource Compose ou fichier commun. Gson (11 instanciations : l.267-271, 526-527, 548, 579-592, 613-615, 644-668) → kotlinx.serialization. `LayoutInflater/findViewById` (item_pending_obs, l.372-412) → LazyColumn. `requireActivity().title` (l.254-256) → titre Compose (effet visible non vérifié). Dictée + permission micro (l.58-63, 339-342). `savedInstanceState` (l.567-616). `savedStateHandle` (l.618-686, 923-924). Toast (l.926). | `:composeApp` `…/ui/occtax/releve/SaisieReleveScreen.kt` + `SaisieReleveViewModel` ; `:shared` `…/occtax/saisie/ReleveEnCours.kt` (PendingObs, synchronisation, relevé sans espèce) | Réécriture Compose : **L**. ≈ 483 l. de code de logique, le fichier le plus « métier » du périmètre. Pas de carte. `obsInitialesIds` (l.150, 201-202) est rempli mais jamais lu. iOS : UI/Saisie/SaisieObservationView.swift (475 l.) + LigneEspeceView.swift (278 l.). |
| DenombrementFragment.kt | 449 (335) | Édition de la liste des dénombrements d'une espèce : min/max, nomenclatures, champs additionnels COUNTING, photos et audio par dénombrement. | MIXTE | `GetMultipleContents` image/* et audio/* (l.88-94), `TakePicture` + `FLAG_GRANT_WRITE_URI_PERMISSION` (l.99-116), `PhotoCapture` (FileProvider, cacheDir/captures, l.171) et `MediaImport` (contentResolver, BitmapFactory, EXIF, JPEG 85 / 2048 px, l.142) → interfaces `SelecteurMedias` + `ImportMedia` (androidMain/iosMain). `java.io.File` et `Uri.parse` (l.208, 370, 399) → okio/kotlinx-io + analyse maison des URI `file:`. `Dispatchers.IO` (l.141) : disponible en commun (coroutines ≥ 1.7). Gson (l.245-246, 279). `UUID` (l.433). État de capture via `onSaveInstanceState` (l.192-210). `AdditionalFieldsRenderer` et `OcctaxFieldsRenderer` (à base de Views) → composables. `EditText`/`TextWatcher`. Toast, AlertDialog. | `:composeApp` `…/ui/occtax/releve/DenombrementScreen.kt` ; règles → `:shared` `…/occtax/saisie/Denombrements.kt` ; médias → interfaces `…/plateforme/Medias.kt` | Réécriture Compose : **M**. ≈ 109 l. de logique, ≈ 94 l. de plateforme (médias), ≈ 95 l. de présentation. Dialogue : choix de la source photo. iOS : CaracterisationView.swift (`DenombrementView` l.407) + MediaPickerView.swift (PhotosPicker, `CameraPicker` l.210, `.fileImporter` audio l.99, `CompressionPhoto` l.249). |
| CaracterisationFragment.kt | 261 (194) | Formulaire de caractérisation de l'occurrence piloté par form_fields (champs de base + section « Plus de champs »), avec un mode « comportement seul » pour la nidification. | MIXTE | Gson `TypeToken<Map>` (l.80-84, 96-98, 248-250) → kotlinx. Vues construites en code (LinearLayout/TextView/EditText, `InputType`, `getColor(android.R.color…)`, density, l.141-212) → Compose. `AdditionalFieldsRenderer`/`OcctaxFieldsRenderer` (Views). Résultat renvoyé via `previousBackStackEntry.savedStateHandle` (l.223-251) → état partagé. Toast (l.218). | `:composeApp` `…/ui/occtax/releve/CaracterisationScreen.kt` ; listes de champs et routage des résultats → `:shared` `…/occtax/formulaire/ChampsOccurrence.kt` | Réécriture Compose : **M**. ≈ 77 l. de logique (l.59-134, 193-194, 223-251). iOS : CaracterisationView.swift (`CaracterisationView` l.116). |
| DetailsReleveDialog.kt | 716 (555) | Dialogue « Détails du relevé » partagé entre mono, multi et détails communs : jeu de données, observateurs multiples, dates, nomenclatures et champs relevé form_fields, champs additionnels, habitat HABREF. Le fichier contient aussi les helpers de listes associés. | MIXTE | `java.text.Normalizer` + `\p{Mn}` (l.53-56) → repli d'accents maison. `ArrayAdapter/Filter/MaterialAutoCompleteTextView/TextInputLayout` (l.62-81, 172-187, 328-348, 575-621) → ExposedDropdownMenuBox. `MaterialAlertDialogBuilder` (l.657-662) → Dialog Compose. `DatePickerDialog/TimePickerDialog` + `maxDate` (l.397-417) → DatePicker/TimePicker Material3. `java.util.Calendar`, `SimpleDateFormat(…, Locale.FRANCE)`, `System.currentTimeMillis` (l.373-490) → kotlinx-datetime. Gson `TypeToken<List<GeoNatureDataset/GeoNatureObservateur>>` (l.85-115) → @Serializable. `GradientDrawable`, couleurs, density → Compose. Toast (l.668). `masquerClavier` (l.181, 338, 616). | `:composeApp` `…/ui/occtax/releve/DetailsReleveDialog.kt` ; `:shared` `…/occtax/releve/DetailsReleve.kt` (DetailsReleveResult, options, valeurs initiales, dates) et `…/util/Texte.kt` | Réécriture Compose : **L**. ≈ 169 l. de logique. Partagé avec OccHab : `datasetsPourOccHab` (l.98-107), `construireSelecteurMultiObservateurs` (l.124-192) et `AdaptateurAutocomplete`, utilisés par OccHabDetailsDialog.kt:139/152/212 et OccHabStationFragment.kt:170. iOS : UI/Saisie/DetailsReleveView.swift (538 l., `HabitatPicker` l.438). |
| DetailsReleveFragment.kt | 113 (72) | Écran intercalé avant la saisie des espèces quand un champ additionnel RELEVE obligatoire n'a pas de valeur par défaut ; il bloque tant que les champs requis sont vides. | UI | `Gson().fromJson(json, Map::class.java)` (l.67) → kotlinx. `NavOptions.setPopUpTo` (l.86-88) → `popUpTo` de navigation-compose. `AdditionalFieldsRenderer` (Views). Arguments Bundle (latitude/longitude, geometry*, `mono`). | `:composeApp` `…/ui/occtax/releve/DetailsReleveIntercalaireScreen.kt` | Réécriture Compose : **S**. Logique : filtre des définitions (l.60-62), contrôle des requis (l.76-77), aiguillage mono/multi (l.91-104). iOS : DetailsReleveIntercalaireView.swift (91 l.). |
| ObservationsFragment.kt | 222 (175) | Liste des relevés de la sortie en cours, regroupés par releveId, avec édition, suppression et « tout effacer ». | MIXTE | `Releve` + `grouperEnReleves` (l.43-65) : Kotlin pur, va tel quel en COMMUN. `RecyclerView`/Adapter/ViewBinding → LazyColumn. `SimpleDateFormat("dd/MM HH:mm")` (l.159, 189) → kotlinx-datetime. `"%.4f, %.4f".format` (l.190 ; séparateur décimal selon la locale) → format maison. `R.string.observations_count` → ressources Compose. AlertDialog. `observe` LiveData → `collectAsState`. | `:shared` `…/occtax/Releve.kt` + `:composeApp` `…/ui/occtax/ObservationsScreen.kt` | Réécriture Compose : **S**. Règle : un relevé solo est supprimé tout de suite, les autres demandent confirmation (l.119-138). iOS : UI/Saisie/ObservationsView.swift (`Releve` l.24). |
| SaisiesEnAttenteFragment.kt | 888 (632) | « Mes visites » : file d'envoi **Monitoring** (pas OccTax) présentée en arbre protocole → site → visite → obs, avec onglets À envoyer/Envoyées, envoi par groupe ou total, réessai et suppression en cascade. | MIXTE | `RecyclerView` à 4 types de lignes, vues construites en code, `TabLayout`, `TypedValue`/`selectableItemBackgroundBorderless` → LazyColumn + TabRow. `org.json.JSONObject` (l.351) → `JsonElement`. `SimpleDateFormat(…, Locale.FRANCE)` (l.50, 649) → kotlinx-datetime. `activity?.runOnUiThread` (l.737, 787) → StateFlow/Dispatchers.Main. `bundleOf` (l.584-593) → arguments de navigation typés. AlertDialog, Toast. Dépendances hors périmètre : OutboxMonitoring, OutboxEnvoi, MonitoringModules, MonitoringObjets, et `estObjetDeNiveauVisite` / `MESSAGE_CHAMPS_OBLIGATOIRES` (définis dans NouvelleVisiteFragment.kt:1353 et 1362). | `:shared` `…/monitoring/outbox/ListeOutbox.kt` + `:composeApp` `…/ui/monitoring/SaisiesEnAttenteScreen.kt` | Réécriture Compose : **M**. ≈ 260 l. de logique. `resumeOutbox` (l.855-882) est déjà pur et couvert par ResumeOutboxTest (JUnit simple, transposable en commonTest). iOS : UI/Monitoring/SaisiesEnAttenteView.swift (675 l.). |
| SortiesFragment.kt | 405 (313) | « Mes saisies » OccTax : onglets À envoyer/Envoyées/Importées, suppression, reprise, envoi unitaire ou total, import GPX. | MIXTE | `StartActivityForResult` + `Intent.ACTION_OPEN_DOCUMENT` + `contentResolver.openInputStream` (l.56-90, 304-311) → `SelecteurFichier` (iOS : `.fileImporter`). `RecyclerView`/`TabLayout` → LazyColumn/TabRow. `SimpleDateFormat("dd/MM/yyyy HH:mm", FRANCE)` (l.201) et `("dd MMM yyyy HH:mm", Locale.getDefault())` (l.337, mois abrégés localisés) → kotlinx-datetime + table des mois. `"%.0f m".format` (l.355). Toast, AlertDialog. `importerGPX`/`lireBorne` (gpx/GpxUtils.kt, SAX `javax.xml`, hors périmètre). | `:shared` `…/occtax/sorties/MesSaisies.kt` + `:composeApp` `…/ui/occtax/sorties/MesSaisiesScreen.kt` | Réécriture Compose : **M**. La règle « envoyable » est écrite en plusieurs exemplaires (l.178-181, 397-398 ; SortieDetail l.114-116). « Continuer la saisie » ouvre toujours la carte multi (l.111-117), même pour une sortie saisie en mono. iOS : UI/PlaceholderViews.swift `SortiesView` (l.52-537). |
| SortieDetailFragment.kt | 333 (254) | Consultation d'une sortie sur la carte (trace, points, lignes et polygones avec bulles), liste des espèces, envoi, export GPX. | UI | osmdroid (`Polyline`, `Polygon`, `Marker`, `BoundingBox.fromGeoPoints/increaseByScale`, `InfoWindow` personnalisée + layout infowindow_obs) → maplibre-compose + popup Compose. `FileProvider` + `Intent.ACTION_SEND/createChooser` + `cacheDir` (l.298-309) → `PartageFichier`. `File.writeText` → okio. `SimpleDateFormat("HH:mm")` (l.184). `Paint.Cap`. AlertDialog. | `:composeApp` `…/ui/occtax/sorties/SortieDetailScreen.kt` ; libellés et regroupements → `:shared` `…/occtax/sorties/AffichageSortie.kt` | Réécriture Compose : **M** (carte en lecture seule). ≈ 45 l. de logique, ≈ 104 l. de carte. iOS : UI/SortieDetailView.swift (284 l. ; `ShareSheet` l.278 = UIActivityViewController). |
| EnvoiGroupeUi.kt | 128 (93) | Contrôleur générique « Tout envoyer (N) » : visibilité, confirmation, envoi séquentiel avec progression et récapitulatif ; le drapeau d'envoi est partagé avec l'envoi unitaire. | MIXTE | `Fragment.viewLifecycleOwner.lifecycleScope` (l.96) → `viewModelScope`. `Button/View/TextView` (l.51-63, 91-94, 119-122) → état Compose. Toast et AlertDialog (l.66-86, 113-116) → Snackbar/AlertDialog. `GeoNatureConfig(ctx)` (l.84) → injecté. | `:shared` `…/envoi/EnvoiGroupe.kt` (boucle, récapitulatif, état) + composable `BoutonToutEnvoyer` | Réécriture Compose : **S**. Aussi utilisé par OccHabStationsFragment.kt:61. iOS : écrit en ligne dans `SortiesView.envoyerTout` (PlaceholderViews.swift:502). |

### Accès plateforme à passer en interfaces

| Besoin | Android (fichier:ligne) | Interface commune proposée | iOS existant (GeoMys-IOS) |
|---|---|---|---|
| Permission de localisation | `RequestMultiplePermissions` FINE/COARSE + `checkSelfPermission` : Trace l.127-131, 1019-1027 ; SaisieRapide l.166-171, 337-345, 1146 | `PermissionLocalisation.demander(): Boolean` | `requestWhenInUseAuthorization` : Location/LocationTracker.swift:68 |
| Suivi GPS en arrière-plan | `LocationForegroundService.start/stop` : Trace l.1031, 1041, 1068 ; SaisieRapide l.334, 615, 1203, 1226 (+ MainActivity.kt:74-77, hors périmètre) | `SuiviArrierePlan.demarrer()/arreter()` | `allowsBackgroundLocationUpdates` : LocationTracker.swift:123-147 |
| Position, parcours, distance | `GeoMysApplication.locationTracker` (LocationManager, LiveData) : TraceViewModel l.31 et tous les écrans carte | `LocationTracker` (StateFlow position/parcours/distance/estEnCours ; demarrer, arreter, reprendre, rafraichir) | Location/LocationTracker.swift (199 l.) |
| Boussole (cap) | `MapCompassController` (SensorManager) : Trace l.252, 1189, 1197 ; SaisieRapide l.319, 1149, 1160 | `Boussole: Flow<Float>` | CLHeading : LocationTracker.swift:58, 106, 193 |
| Vibration courte | `VibrationEffect.createOneShot(60)` : SaisieRapide l.949-957 | `RetourHaptique.impulsion()` | `UIImpactFeedbackGenerator(.medium)` : SaisieRapideView.swift:718 |
| Dictée vocale + micro | `SpeechToTextHelper` (SpeechRecognizer) + `RequestPermission` : SaisieRapide l.173-178, 223-230, 428 ; SaisieObservation l.58-63, 339-342, 774 | `DicteeVocale.ecouter(): List<String>` (hypothèses) | SFSpeechRecognizer : UI/Saisie/DicteeVocale.swift |
| Photos (galerie, appareil), audio | `GetMultipleContents`, `TakePicture`, `PhotoCapture` (FileProvider), `MediaImport` (recompression) : Dénombrement l.79-183 | `SelecteurMedias` + `ImportMedia` (copie dans `medias/`) | MediaPickerView.swift (PhotosPicker, CameraPicker l.210, fileImporter audio l.99, CompressionPhoto l.249) ; Store/MediaLocal.swift |
| Partage de fichier (GPX) | cacheDir + FileProvider + `ACTION_SEND`/`createChooser` : SortieDetail l.298-309 | `PartageFichier.partager(chemin, mime)` | `ShareSheet` (UIActivityViewController) : SortieDetailView.swift:278-283 ; Network/GpxExport.swift |
| Ouverture de fichier (import GPX) | `ACTION_OPEN_DOCUMENT` + `contentResolver.openInputStream` + `lireBorne` (20 Mo) : Sorties l.56-90, 304-311 | `SelecteurFichier.ouvrir(types): ByteArray?` | `.fileImporter` : PlaceholderViews.swift:126 ; Network/GpxImport.swift |
| Fichiers locaux | `File(Uri.parse(uri).path).delete()/.name` : Dénombrement l.370, 398-400 | aucune : okio/kotlinx-io `FileSystem` + racine fournie par la plateforme | — |
| Restauration après mort du processus | `onSaveInstanceState`/`savedInstanceState` : SaisieRapide l.192-211, 1163-1178 ; SaisieObservation l.567-616 ; Dénombrement l.190-210 | `SavedStateHandle` KMP (sans interface), ou abandon assumé | sans équivalent direct |
| Retour système | `OnBackPressedCallback` : Trace l.203-208 ; SaisieRapide l.260-265 | BackHandler Compose multiplateforme (expérimental ; à vérifier selon la version CMP) | retour de la NavigationStack |
| Toast, dialogues, clavier, insets, ressources | Toast, AlertDialog, MaterialAlertDialogBuilder, DatePicker/TimePickerDialog, InputMethodManager, WindowInsets, `R.*`, assets (nidification) | aucune interface : Snackbar, AlertDialog, DatePicker/TimePicker Material3, `LocalSoftwareKeyboardController`, `WindowInsets`, Compose Resources (`Res.string/drawable/readBytes`) | — |
| Préférences | `GeoMys_prefs` : `enregistrer_trace` (Trace l.158-160) ; via PreferencesSaisie : `saisie_dernier_taxon`, `saisie_nom_sci` ; via MapUtils : `fond_carte` | multiplatform-settings | `@AppStorage`, mêmes clés et formats (TraceView.swift:44/73, SaisieRapideView.swift:63-64/92) |

## 2. Logique à extraire

### TraceViewModel.kt : est-il déjà portable ?

Non, pas tel quel. Mais il est presque entièrement métier (159 l. de code hors imports). Les blocages, par ordre d'importance :

1. **`AndroidViewModel(application)`** (l.30). L'`Application` ne sert qu'à construire `SortieStore(application)` (l.41) et à récupérer `GeoMysApplication.locationTracker` (l.31).
2. **`LiveData`** (l.43-52), qui n'existe que sur Android.
3. **Le store synchrone.** `persisterBrouillon` (l.216-249) relit (`charger()`) puis réécrit tout le store à chaque mutation, via `SharedPreferences.commit()` sur le thread UI.
4. **Le `LocationTracker` Android.** Le VM lit `parcours.value` et `distanceTotale.value` (l.218-219) et appelle `reinitialiser`, `restaurerParcours`, `definirDistance` et `arreter` (l.260, 290-291, 298).
5. **`DetailsReleveResult`**, déclaré dans un fichier UI.

`ViewModel`, `viewModelScope` et `onCleared` existent en commonMain (lifecycle-viewmodel KMP depuis 2.8). Il suffit donc d'injecter `SortieRepository` et `LocationTracker`, de passer à `StateFlow` et de rendre l'écriture `suspend` (avec un Mutex) : le fichier passe alors en commonMain presque ligne à ligne.

La portée pose une question à part. Le VM est aujourd'hui un `activityViewModels()` partagé par Trace, SaisieRapide, SaisieObservation, Dénombrement, Caractérisation, DetailsReleveFragment, Observations, Sorties, Accueil et MainActivity. En Compose, il faudra un VM rattaché au graphe de navigation « saisie », ou un singleton de session comme `SessionSaisie.shared` côté iOS. Enfin, `typeSaisieLabel` (l.37) est une chaîne de ressource posée par Trace l.177 et SaisieRapide l.251 : à remplacer par un enum `TypeSaisie`.

### TraceFragment.kt (1 226 l.)

**Logique → `CarteMultiViewModel` et session (≈ 253 l. de code) :**
- **État d'écran** (l.63-86, 119-121) : `suivrePosition`, `doitCentrerSurObs`, `reprendreDerniereEmprise`, `modePositionnement`, `obsARepositionnerIds`, `modeGeom`, `sommetsCourants`, `enregistrerTrace`, `envoiEnCours`.
- **Ouverture** : reprise d'une sortie depuis le store (l.147-156), préférence `enregistrer_trace` (l.158-160), drapeau `demarrerSaisieSuivante` (l.166-167).
- **Bouton ▶/⏸** (l.295-308) : arrêter, sinon continuer une trace non vide, sinon démarrer.
- **Validation de position** (l.371-411) : repositionnement en lot, construction de la géométrie, décision « écran relevé intercalé nécessaire » via `aDesChampsReleveRequisSansDefaut` (l.399-400), passage des additionnels communs.
- **Sommets** : `ajouterOuRemplacerSommet` (l.284-292), `changerMode` (l.579-584), `construireGeometrieFinale` + `GeomFinale` (l.713-743 : au moins 2 ou 3 sommets, `GeoJsonCoords.format`, centroïde = moyenne).
- **Calcul d'emprise** (l.437-454, 461-471) : on écarte les points (0,0), on inclut les sommets, un point unique (< 1e-4°) donne un centrage simple, une forme plate reçoit une épaisseur minimale de 5e-4°.
- **Regroupements** : `cleReleve` (l.813-814), filtres point / ligne-polygone (l.780-785, 820-822, 834-835), géométrie recalculée après un glisser (l.880-886), ids à déplacer (l.968-975).
- **Libellés** : sous-titre des marqueurs (l.943-950), titre et liste des options d'un relevé (l.986-1000), textes du mode (l.561-573), barre d'info (l.531-539).
- **Fin de sortie** : règle `peutEnvoyerGn` et options (l.1034-1053) ; `terminerSortie` (l.1066-1109 : fusion avec la sortie existante, écriture, gestion de l'échec) ; envoi (l.1157-1175).
- **Détails communs** : valeurs initiales (l.1115-1130) et mémorisation (l.1146-1148).

**Carte (≈ 374 l.) :**
- `setupMap` (l.211-279) ; boutons centrer, fond et boussole (l.328-369).
- Cadrage `zoomToBoundingBox` (l.455-483) et suivi de position (l.486-492).
- Rendu de la géométrie en cours : `resetSaisieGeom`, `rafraichirGeomEnCours`, `redessinerOverlay` (l.603-707).
- Trace incrémentale `updatePolyline` (l.745-772).
- Marqueurs et formes éditables : `updateMarkers`, `majGeometriesReleves`, `creerGeomReleveEditable`, `construireFormeReleve`, `redessinerFormeReleve` (l.774-932) ; `createObservationMarker` (l.938-979).
- Cycle de vie de la carte (l.1186-1198).

**Présentation (≈ 212 l.) :**
- Câblage dans `onViewCreated` (l.139-209) et observers (l.494-523).
- `updateParcoursButton`, `updateListeBadge`, visibilités de `updateModePositionnement` (l.525-576), `mettreEnEvidenceBoutonMode` (l.590-600).
- Dialogues : l.1001-1015, 1055-1063, 1177-1184 ; insets (l.1212-1224).

**Plateforme (≈ 24 l.) :** permissions (l.127-131, 1019-1027), service de premier plan (l.1029-1032), retour système (l.203-208).

### SaisieRapideFragment.kt (1 285 l.)

**Logique → `SaisieMonoViewModel` (≈ 437 l. de code) :**
- **État de la session mono** (l.85-145) : taxon, espèce, cd_nom et nombre ; les 9 champs de caractérisation ; les surcharges « Détails du relevé » (jeu de données, observateurs, commentaire, habitat, dates, typGrp, champs extra) ; le dénombrement n°0 et les additionnels ; le mode actif, le compteur et le point manuel.
- **Restauration** (l.192-221) : clés rs_* et argument `addReleveJson`.
- **Groupe taxonomique** : filtre par la liste de taxons et restauration du dernier groupe (l.363-370) ; au changement, mémorisation et invalidation du statut TaxRef (l.376-386, 409-425).
- **Espèce** :
  - suggestion choisie → `TaxRefStatut.Trouve`, avec le nom scientifique lu dans TaxRefCache (l.433-445) ;
  - règle d'activation de « Démarrer » (l.458-465) ;
  - résolution de la dictée (l.470-482) ;
  - calcul des propositions et signalement « liste absente du cache » (l.493-511).
- **Détails du relevé** : valeurs initiales (l.566-586) et application du résultat (l.596-608).
- **Retours de Caractérisation et de Dénombrement** (l.650-700) : aplatissement du dénombrement n°0.
- **Aides** :
  - `determinateurParDefaut` (l.702-705), `cdNomCourant` (l.709), `groupe2InpnDefaut` (l.713-726) ;
  - arguments des sous-écrans (l.728-770) ;
  - indicateur de détails (l.772-787).
- **Saisie** :
  - `demarrer` (l.791-799) ;
  - `enregistrerIci` (l.867-936) : priorité point manuel > GPS > centre de la carte, construction de l'Observation, jeu de données « session > sortie > config », défauts des champs relevé ;
  - `obsProches` dans un rayon de 30 m (l.1097-1105) ;
  - texte de la liste des espèces (l.1108-1115).
- **Fin** : terminer (l.1198-1213, 1225-1252) et envoi (l.1254-1260).
- **`Taxon.nomGroupe`** (l.1273-1284).

**Carte (≈ 186 l.) :** `setupMap` (l.270-330), point manuel (l.823-865), marqueurs déplaçables (l.1050-1095), suivi (l.1030-1034), cycle de vie (l.1143-1161), centrages (l.805-810, 937-946).

**Plateforme (≈ 77 l.) :** permissions localisation et micro (l.166-178, 337-345), service de premier plan (l.332-335, 613-617), dictée (l.223-239), clavier (l.813-819), vibration (l.949-957), sauvegarde d'état (l.1163-1178), retour système (l.260-265).

**Présentation (≈ 243 l.) :**
- Adapter d'autocomplétion (l.512-529) et hint (l.533-548).
- Câblage de `setupControls` (l.553-648).
- `updateModeUI`, `updateCompteur`, `updateResumeActif` (l.972-1026) ; bandeau de confirmation de 3 s (l.959-968).
- Dialogues : l.1116-1123, 1214-1222, 1263-1270.

### SaisieObservationFragment.kt (1 053 l.)

**Logique → `SaisieReleveViewModel` + `ReleveEnCours` commun (≈ 483 l. de code) :**
- **Modèle et état** : `PendingObs` (l.72-109) devient un modèle commun `EspeceEnCours` ; état du relevé (l.111-166) : surcharges relevé, géométrie, `releveIdEdite`, `releveIdSession` (UUID stable), `donneesChargees`.
- **Initialisation** (l.179-311) :
  - relevé existant lu dans le VM : conversion Observation → PendingObs (l.224-253), date repliée sur la date de création (l.218) ;
  - nouveau relevé : arguments, jeu de données de la sortie (l.280), date a posteriori (l.285-288), détails communs (l.294-306).
- **Liste** : total d'individus (l.405), suppression (l.418-422), `groupe2InpnPour` (l.424-437).
- **Détails du relevé** (l.446-497) : valeurs initiales, application du résultat, armement de la saisie a posteriori.
- **Sous-écrans** : construction des arguments (l.505-528, 535-555) et consommation des résultats (l.618-686).
- **Ajout d'espèces** :
  - mémorisation du groupe (l.690-702) ;
  - dictée → ajout (l.813-849), avec le 1er nom français d'une liste séparée par des virgules ;
  - ajout depuis une suggestion avec son cd_nom (l.861-873).
- **Défauts** : `determinateurParDefaut` (l.878-881) ; mois courant et ouverture automatique de la caractérisation en période de nidification (l.886-900).
- **Enregistrement** : `enregistrer` (l.905-928), `enregistrerReleveSansEspece` (l.934-973), `synchroniserBatch` (l.981-1044 : upsert par id stable).

**Plateforme (≈ 62 l.) :** micro (l.58-63), dictée et lookup (l.339-350), sauvegarde et restauration d'état (l.567-616).

**Présentation (≈ 196 l.) :** palette de groupes (l.313-337), lignes de la liste (l.372-412), adapter d'autocomplétion (l.704-751), hint (l.753-768), câblage (l.770-806).

**Attention :** `rafraichirListe` déclenche l'écriture disque (l.415). Cf. §4-3.

### DenombrementFragment.kt (449 l.)

**Logique (≈ 109 l.) :**
- État (l.66-77).
- Initialisation (l.212-251) : lecture des arguments ; définitions COUNTING visibles selon le jeu de données et les listes du taxon ; champs de nomenclature selon form_fields (obj_count, type_count, life_stage, sex) ; normalisation post-Gson.
- Règles de saisie :
  - recopie Min → Max seulement si Max « suivait » Min (l.315-328) ;
  - pas ±1 avec plancher : Min ≥ 1, Max ≥ Min (l.333-346) ;
  - libellé « Stade phénologique » pour les plantes (l.351-361).
- `collecter` (l.403-427) : bornes, et champs masqués préservés.
- `normaliser` (l.432-442).

**Plateforme (≈ 94 l.) :** sélecteurs et appareil photo (l.79-183), état de capture (l.190-210), suppression de fichier (l.398-400).

**Présentation (≈ 95 l.) :** `rafraichir` (l.284-396) ; la validation des requis lit les vues (l.264-274).

### DetailsReleveDialog.kt (716 l.)

**Logique (≈ 169 l.) :**
- **Filtrage** : `normaliserAccents` (l.53-56) et filtre « contient, insensible aux accents » (l.64-69).
- **Listes proposées** : `datasetsPourDetailsReleve` (l.85-94 : actif, module OCCTAX ou aucun, créable), `datasetsPourOccHab` (l.98-107), `observateursPourDetailsReleve` (l.110-115).
- **Sélections** : sélection initiale multi-observateurs, dédoublonnée par id (l.131-138) ; libellé de repli de la sélection initiale (l.308-313).
- **`DetailsReleveResult`** (l.194-223).
- **Dates** :
  - valeur par défaut « maintenant » (l.373-375) ;
  - `dateModifiee` n'est armé que par la date de début (l.399-403, 412-414) ;
  - date de fin repliée par défaut (l.423) ;
  - `getDates` (l.479-490) : journée 00:00 → 23:59 sans heures, et fin ≥ début.
- **Champs pilotés par form_fields** : visibilités (l.501-511, 564-565) et liste des champs simples (l.529-537).
- **Habitat** : recherche avec anti-rebond de 300 ms et au moins 2 caractères (l.595-613, 625-637).
- **Résultat** (l.673-709) : les champs extra masqués sont préservés ; routage NAT_OBJ_GEO et TECHNIQUE_OBS vers `champsExtra`.

**Présentation (≈ 357 l.) :** toutes les vues construites en code (l.139-191, 264-363, 384-477, 512-559, 575-652) et le dialogue (l.654-714).

### SaisiesEnAttenteFragment.kt (888 l.)

**Logique (≈ 260 l.) :**
- **État et règles d'affichage** : onglet et drapeau d'envoi (l.57-64) ; règle du bouton « Tout envoyer » (l.156-168 : PENDING/ERROR et module autorisé CRUVED C).
- **Accords grammaticaux** : `labelDuType`, `typeMasculin`, `voyelleInitiale`, `ceTypeDe`, `leTypeDe` (l.174-198).
- **Arbre** : aplatissement hiérarchique `peuplerListe` (l.200-273) : racines, `groupeToutEnvoye` (parcours en largeur), regroupement par module, parcours en profondeur avec profondeur.
- **Libellés** : chemin du bandeau de groupe (l.310-327), `nomTaxonDeSaisie` (l.349-360), `ordrePourTri` (l.362-366), titre de ligne (l.434-440).
- **Droits et actions** : droits mémoïsés (l.371-375) ; conditions d'actions (l.452-455, 488-495) ; fil d'Ariane d'édition (l.602-624) ; actions du menu (l.632-665).
- **Envois** : garde « à compléter » (l.773-777) et textes de récapitulatif (l.745-748, 798-804).
- **Résumé** : `ResumeOutbox` et `resumeOutbox` (l.830-882), déjà purs.

**Présentation (≈ 352 l.) :** adapter (l.98-135), lignes et en-têtes construits en code (l.279-343, 377-484), icônes d'action (l.501-569), dialogues et progression.

Cet écran relève fonctionnellement de Monitoring.

### SortiesFragment.kt (405 l.)

**Logique (≈ 67 l.) :**
- Filtres d'onglet et messages vides (l.156-168), `sortiesEnvoyables` (l.178-181), compteurs (l.186-189).
- Texte de confirmation de suppression (l.201-207) et configuration d'`EnvoiGroupeUi` (l.222-235).
- Agrégation des espèces hors relevé sans espèce (l.289-296).
- Règles de ligne : `peutEditer` / `peutEnvoyer`, compte des obs hors relevé sans espèce, 1re ligne d'erreur (l.355-366, 391-398).
- Côté store de l'import GPX (l.69-81).

**Plateforme (≈ 40 l.) :** sélecteur de document et lecture bornée (l.56-90, 304-311).

**Présentation (≈ 179 l.) :** onglets (l.135-148), adapter (l.327-405), dialogues (l.208-216, 248-253, 266-270, 297-301, 313-319).

### SortieDetailFragment.kt (333 l.)

**Logique (≈ 45 l.) :**
- Chargement (l.69-76) et règle `peutEnvoyer` (l.114-116 : non envoyée, non importée, configurée, CRUVED C, obs envoyable).
- Agrégation des espèces (l.124-131).
- Regroupement des obs par coordonnées arrondies à 1e-6° (l.188-190).
- Type de géométrie et libellés des bulles (l.197-222), points de l'emprise (l.271-273, 280).

**Carte (≈ 104 l.) :** l.168-187, 224-269, 275-287, et `ObsInfoWindow` (l.315-332).

**Plateforme (≈ 12 l.) :** export et partage GPX (l.298-309).

**Présentation (≈ 64 l.) :** l.64-121, 132-166.

## 3. Besoins carte

**Fonds et cycle de vie :**
- Fonds raster en ligne (OSM, OPENTOPO, TOPO, SCAN25, ORTHO, ESRI) et MBTiles hors ligne. Changement à chaud et mémorisation (`fond_carte`) : Trace l.212-213, 348-354 ; SaisieRapide l.271-272, 311-317 ; SortieDetail l.96-102, 169-170 (via `MapUtils.appliquerFond/choisirFondCarte`). Pour le MBTiles sous MapLibre : `mbtiles://`, à vérifier selon la version.
- En-tête User-Agent des requêtes de tuiles (osmdroid `userAgentValue = packageName` : Trace l.134, SaisieRapide l.181, SortieDetail l.59). Le moyen de le fixer via maplibre-compose est à vérifier.
- Pause et reprise de la carte : Trace l.1188/1196, SaisieRapide l.1145/1158, SortieDetail l.294-295.
- Gestes multi-touch (Trace l.214, SaisieRapide l.273, SortieDetail l.171). Les boutons de zoom natifs sont masqués au profit de boutons +/- maison (Trace l.218-221, SaisieRapide l.275-278).

**Caméra :**
- `setZoom`, `setCenter`, `animateTo`, `zoomIn/zoomOut`, plus lecture du centre et du zoom pour sauvegarder et restaurer l'emprise entre deux affichages : Trace l.220-246, 333, 490, 1194-1195 ; SaisieRapide l.277-286, 402-403, 632, 807-808, 945, 1033, 1156-1157.
- Cadrage sur une emprise avec marge en pixels (hauteur du panneau bas + 32 dp). Cas limites : point unique → zoom 18 ; forme plate → épaisseur minimale (Trace l.455-483). En consultation : marge ×1,4, zoom 15 si l'emprise est dégénérée, centre France (46.5 ; 2.5) zoom 6 si la sortie est vide (SortieDetail l.275-287).
- Rotation (cap) pilotée par la boussole, avec retour au nord : Trace l.252-254, 358-369 ; SaisieRapide l.319-329.
- Lecture du centre de l'écran (réticule de repositionnement, repli « point au centre ») : Trace l.372, 419, 719 ; SaisieRapide l.881.
- Événement « déplacement de la carte par un geste », qui coupe le suivi de position : Trace l.270-278, SaisieRapide l.303-309. Avec MapLibre, il faut distinguer geste et mouvement programmé (à vérifier dans maplibre-compose).

**Position :**
- Point bleu + flèche de cap (icône partagée, MapUtils.kt:229-238) ; cercle de précision en mono (SaisieRapide l.288).
- Activation et désactivation : SaisieRapide l.1146-1147, 1159.
- Suivi automatique tant que `suivrePosition` est vrai : Trace l.487-492, SaisieRapide l.1031-1034.

**Interactions :**
- Tap simple sur la carte → coordonnées, actif selon le mode et consommé : Trace l.261-268 ; SaisieRapide l.294-301.
- Marqueurs :
  - icône image (oiseau, goutte) ancrée en bas au centre ; titre + sous-titre en bulle ;
  - bulle personnalisée multi-lignes (SortieDetail l.315-332) ;
  - clic consommé sans bulle (SaisieRapide l.842-845, Trace l.869-870) ;
  - clic → action (Trace l.956-963, SaisieRapide l.1076-1080, SortieDetail l.261-264).
- **Marqueurs déplaçables** (glisser lancé par un appui long), avec rappels pendant le glisser et à la fin :
  - sommets en cours de saisie (Trace l.652-668) ;
  - sommets d'un relevé existant (Trace l.871-887) ;
  - relevé ponctuel (Trace l.964-977) ;
  - point manuel (SaisieRapide l.846-852) et observations (SaisieRapide l.1081-1089).
- Tap sur une ligne ou un polygone (hit-test) → menu d'options ou bulle ouverte à la position du tap : Trace l.898-901, 908, 916 ; SortieDetail l.237, 246. iOS a un équivalent : UI/Map/HitTestGeometrie.swift (point dans un polygone et distance à une ligne, écrits à la main : algorithme pur, transposable en commun).

**Dessin :**
- Polyligne de la trace GPS : bleue (0xCC2196F3), 6 px, extrémités rondes, avec **ajout incrémental** (Trace l.745-772). Statique en consultation (SortieDetail l.173-181).
- Ligne ou polygone en cours de saisie : rouge (0xCCFF3333), remplissage 0x55FF3333, redessiné en direct pendant le glisser (Trace l.613-707).
- Lignes et polygones des relevés existants : bleus (remplissage 0x402196F3), cliquables, sommets modifiables (Trace l.856-932) ; en lecture seule dans SortieDetail l.224-251.
- Ordre d'empilement : formes sous les marqueurs (Trace l.699-704, 861).
- Un seul marqueur par relevé, mis à jour par diff incrémental pour éviter le scintillement (Trace l.774-810 ; SaisieRapide l.1050-1095). Avec des sources GeoJSON déclaratives, ce diff devient inutile.

Hors carte, en surimpression Compose : réticule central, bandeau de positionnement, sélecteur Point/Ligne/Polygone, boussole (`CompassView`), boutons de zoom et de centrage.

## 4. Points durs

1. **Édition de géométries sur la carte.** Marqueurs et sommets déplaçables, avec redessin en direct de la forme : Trace l.641-674, 856-894, 952-977 ; SaisieRapide l.831-856, 1073-1090. À ma connaissance, maplibre-compose n'a pas d'annotation déplaçable prête à l'emploi (à vérifier). Il faudra donc un geste appui long, une projection écran → géo et une mise à jour de source, sans recréer les objets pendant le glisser (contrainte de performance déjà notée l.921-925). L'iOS s'appuie sur le glisser natif de MKMapView (`onSommetDeplace`, MapView.swift).
2. **LiveData → StateFlow.** Le VM mute la même `MutableList` puis la réassigne (TraceViewModel l.104-106, 112-114, 121-123, 131-134, 148-152, 157-162, 173-184, 191-202). LiveData notifie quand même ; un `StateFlow` n'émet pas une valeur égale (même référence), donc aucun écran ne se mettrait à jour. Il faut des listes immuables, copiées à chaque mutation.
3. **Auto-save synchrone, déclenché par l'affichage.**
   - `persisterBrouillon` réécrit tout le store à chaque mutation (TraceViewModel l.216-249).
   - `SaisieObservation.rafraichirListe()` appelle `synchroniserBatch()` (l.415) : chaque rafraîchissement de la liste écrit sur le disque.
   - En Compose, cela ne doit pas se produire pendant la composition : à déplacer dans le VM (sur événement), avec une écriture `suspend` protégée par un Mutex.
   - L'ordre entre auto-save et « Terminer » compte : Trace l.1071-1101 relit puis réécrit la sortie.
4. **Communication entre écrans par `savedStateHandle` et JSON Gson.** Caractérisation l.223-251, Dénombrement l.278-280, SaisieRapide l.650-700, SaisieObservation l.618-686 et 923-924, Trace l.166-167 et 386-409 : une quinzaine de clés de résultat et une trentaine de clés d'arguments. À remplacer par l'état du VM de session (graphe de navigation partagé) plutôt qu'en transposant la sérialisation.
5. **Crochet de sortie du flux de saisie** (MainActivity.kt:66-79, hors périmètre mais indispensable). Quand on quitte `DESTINATIONS_SAISIE`, l'appli appelle `sauvegarderBrouillon()` (le seul appel de cette méthode), arrête la trace et coupe le service. À refaire dans la navigation Compose commune.
6. **Formulaires dynamiques à base de Views.** La validation et la collecte lisent les vues : `AdditionalFieldsRenderer.champsObligatoiresVides(container)` (Dénombrement l.264-268, Caractérisation l.216, DetailsReleveDialog l.666) et `collecter(container)` (Dénombrement l.412-416, Caractérisation l.228-229/249, DetailsReleveDialog l.678/700, DetailsReleveFragment l.75). Il faut d'abord un modèle de formulaire commun (valeurs + règles de requis et de visibilité) ; les renderers sont hors périmètre.
7. **Restauration après mort du processus.** SaisieRapide l.192-211 et 1163-1178, SaisieObservation l.567-616, Dénombrement l.190-210 (y compris la photo en cours de prise) : spécifique à Android. À porter via `SavedStateHandle` KMP, ou à abandonner en connaissance de cause. Les surcharges « Détails du relevé » de la saisie mono ne vivent que là : elles ne sont jamais écrites dans le store.
8. **Écarts métier constatés dans le code** (à trancher, ne pas reproduire tels quels) :
   - **a. Saisie mono.** Caractérisation renvoie `naturalite` et `occExtraJson` (statut source, floutage, preuves : l.236, 250). SaisieRapide ne les lit jamais (l.652-699) et ne les pose pas sur l'Observation (l.886-931). Dans l'autre sens, SaisieRapide envoie et écoute `addReleveJson` vers et depuis Caractérisation (l.744, 664-668), qui ne le lit ni ne l'écrit.
   - **b. Détails communs.** Le dialogue renvoie toujours une date : l'heure d'ouverture par défaut (DetailsReleveDialog l.373-375, 481-490). Chaque nouveau relevé en hérite (SaisieObservation l.303-304), et cette date écrase une date a posteriori armée ensuite (l.285-288 puis 294-306). iOS ne pose la date que si elle a été modifiée, et documente l'écart (SessionSaisie.swift : « Android les écrase »).
   - **c. Passerelle mono ↔ multi.** Elle a été supprimée côté Android par décision produit (Trace l.316-322, garde DefautsPariteLot2Test.kt:189-190). Elle existe encore côté iOS (TraceView.swift:418-428 : appui long → saisie mono dans la sortie). Suivre Android.
   - **d. Total d'individus.** SaisieObservation l.405 compte tous les dénombrements ; Observations l.50, Sorties l.292 et SortieDetail l.127 ne comptent que le dénombrement n°0 (`nombre`).
   - **e. Code mort.** SaisieRapide l.398-406 ; SaisieObservation `obsInitialesIds` (l.150, 201-202) ; SaisieObservation l.656-661 (clés sexe, stadeVie, objDenbr, typDenbr : plus aucun producteur, ObservationDetailsFragment n'existe plus).
9. **Règles verrouillées par des tests qui lisent les sources.** DefautsPortageIosTest.kt:118, DefautsPariteLot2Test.kt:189-190, ProposeEgaleAccepteTest.kt:159, SuggestionPorteSonCdNomTest.kt:149-158, ChangementDeGroupeTest.kt:89-104 et ResolutionPurementLocaleTest.kt:112-113 lisent le texte de ces fragments ; ils ne survivront pas au portage. Les règles qu'ils protègent doivent devenir de vrais tests commonTest sur les VM :
   - ▶ continue une trace non vide ;
   - pas de passerelle mono/multi ;
   - les propositions périmées sont retirées avant recalcul ;
   - une suggestion choisie garde son cd_nom ;
   - le texte tapé est conservé au changement de groupe ;
   - la résolution est purement locale.
10. **Doublons à factoriser** (≈ 150 à 200 l. de code au total) :
    - valeurs initiales des Détails : Trace l.1116-1130, SaisieRapide l.567-586, SaisieObservation l.452-469 ;
    - `groupe2Inpn` : SaisieRapide l.713-726, SaisieObservation l.424-437 ;
    - `determinateurParDefaut` : SaisieRapide l.702-705, SaisieObservation l.878-881 ;
    - `refreshAutocompleteAdapter` : SaisieRapide l.484-531, SaisieObservation l.704-751 ;
    - hint de l'espèce : SaisieRapide l.533-548, SaisieObservation l.753-768 ;
    - arguments et retours des sous-écrans : SaisieRapide l.650-770, SaisieObservation l.501-557 et 618-686 ;
    - « Terminer » : Trace l.1034-1109, SaisieRapide l.1198-1252 ;
    - liste des espèces : Sorties l.288-302, SortieDetail l.123-137 ;
    - règle « envoyable » : Sorties l.178-181 et 397-398, SortieDetail l.114-116, Trace l.1046, SaisieRapide l.1208.
11. **API sans équivalent direct dans la stdlib.**
    - `java.text.Normalizer` (DetailsReleveDialog l.53-56) : le filtrage insensible aux accents demande une table de repli maison.
    - `Location.distanceBetween` (SaisieRapide l.1100) calcule sur l'ellipsoïde WGS84 ; un haversine sphérique donne de légers écarts. Le seuil de 30 m tolère ces écarts.
    - Mois abrégés localisés (`dd MMM yyyy`, Sorties l.337).
    - `String.format` avec un séparateur décimal qui dépend de la locale (Observations l.190).
12. **Deux flux de position.** L'overlay osmdroid « ma position » a son propre `GpsMyLocationProvider` (MapUtils.kt:229-230), indépendant du `LocationTracker`. En commun, le point bleu doit être alimenté par le tracker unique.
13. **Trace GPS longue.** L'ajout de points est incrémental aujourd'hui (Trace l.745-772, pour éviter un coût O(n²)). Avec MapLibre, la donnée d'une source GeoJSON se remplace en bloc (à vérifier selon l'API) : prévoir de découper la trace ou de limiter la fréquence de mise à jour.

## 5. Compatibilité des données

Condition préalable : pour relire les données sur place, GeoMys doit s'installer **en mise à jour** de l'appli existante, sans désinstallation. Il faut donc :
- le même applicationId **par flavor** : github = `fr.ariegenature.geomys`, play = `fr.ariegenature.public.geomys` ;
- la même clé de signature ;
- un versionCode > 196 (la version actuelle est 1.4.7).

**Formats persistés lus ou écrits par ce périmètre :**

1. **Sorties OccTax** (écrites par TraceViewModel l.216-249, Trace l.1090-1091, SaisieRapide l.1236-1246, Sorties l.72/212, et via `envoyerSortieVersGeoNature`) :
   - **Emplacement** : SharedPreferences `sorties_store`, clé `sorties_sauvegardees` : une seule chaîne JSON Gson contenant la liste des `Sortie`, la plus récente en tête (SortieStore.kt:34-36, 68). Quarantaine dans `filesDir/sorties_store.corrupt.json`.
   - **Clés et valeurs** : les clés JSON sont les noms des propriétés Kotlin (aucun `@SerializedName`, model/Models.kt:26-180). `Taxon` est écrit par son nom d'enum (`null` vaut OISEAU pour l'historique). Les dates sont des epoch ms (`Long`). `geometryType` vaut `Point`, `LineString` ou `Polygon`. `geometryCoordsJson` est une **chaîne** JSON imbriquée `[[lon,lat],…]`, nulle pour un point. `champsReleveExtra` et `champsOccExtra` sont des maps indexées par clé form_fields (`altitude_min`, `place_name`, `geo_object_nature`, `tech_collect`, `source_status`, `blurring`, `digital_proof`, `non_digital_proof`…).
   - **Lecture avec kotlinx.serialization** : `ignoreUnknownKeys = true`, `explicitNulls = false` (Gson omet les nulls), `coerceInputValues = true`.
   - **Champs non nuls sans défaut** (`espece`, `latitude`, `longitude`, `PointTrace.latitude/longitude`) : s'ils manquent ou valent null, le décodage lève une exception. Il faut une passe de normalisation tolérante sur `JsonElement`, comme `normaliserSortie/Observation` (SortieStore.kt:208-292), qui écarte les entrées invalides.
   - **`uuidOccurrence`** a une valeur par défaut aléatoire : elle doit être écrite dès la migration, faute de quoi l'anti-doublon d'envoi n'est pas stable (même comportement aujourd'hui, SortieStore.kt:270-272).
2. **Préférences `GeoMys_prefs`** :
   - `enregistrer_trace` (Boolean, défaut true ; lu Trace l.158-160, écrit par l'Accueil) ;
   - `saisie_dernier_taxon` (nom d'enum `Taxon`) et `saisie_nom_sci` (Boolean), via PreferencesSaisie ;
   - `fond_carte` : `OSM`, `OPENTOPO`, `TOPO`, `SCAN25`, `ORTHO`, `ESRI` ou `mbtiles:<fichier>` relatif au dossier de MbtilesStore (MapUtils.kt:50-75).

   Sur Android, multiplatform-settings doit envelopper `getSharedPreferences("GeoMys_prefs", MODE_PRIVATE)`. Les mêmes clés et formats existent déjà côté iOS dans `@AppStorage` (UserDefaults standard).
3. **Configuration `gn_config`** (GeoNatureConfig), en lecture seule ici : idDataset, nomDataset, observateurDefautId/Nom, idRoleUtilisateur, nomUtilisateur, login, additionalFieldsOcctaxJsonActif, formFieldsJson, urlServeur, taxaListeId, occtaxPeutCreer, datasetsCacheJson, datasetsCreablesOcctax, observateursCacheJson, et les dérivés heuresVisibles, dateFinVisible, estConfiguree, connexionConfiguree. Les noms exacts des clés n'ont pas été relevés (hors périmètre).
4. **File Monitoring** (SaisiesEnAttenteFragment) : `filesDir/monitoring_outbox.json`, avec `.tmp` et `.corrupt` (OutboxMonitoring.kt:129-185). L'écran met l'état à PENDING et efface `messageErreur` (l.654-656), supprime une entrée (l.662, 676) ou supprime en cascade (l.688).
5. **Médias et fichiers temporaires** :
   - Photos `filesDir/medias/photo_<ms>_<i>.jpg` et audio `<préfixe>_<ms>_<i>.<ext>`, référencés en **URI absolues** `file:/data/user/0/<applicationId>/files/medias/…` (`File.toURI()`, MediaImport.kt:44-62 et 119) dans `mediaUrisCounting0` et `Denombrement.mediaUris`. Ces chemins restent valides avec le même applicationId.
   - Fichier temporaire de capture `cacheDir/captures/capture_<ms>.jpg` (FileProvider `${applicationId}.provider`, cache-path « . »).
   - Export GPX `cacheDir/sortie_<8 caractères de l'id>.gpx`.
6. **États éphémères** (aucune compatibilité requise) :
   - clés rs_* (`rs_ds`, `rs_obs`, `rs_obsnom`, `rs_comment`, `rs_cdhab`, `rs_habnom`, `rs_datedeb`, `rs_datefin`, `rs_add`, `rs_extra`) ;
   - `captureUriStr`, `captureFichierPath`, `pickMediaTargetIndex` ;
   - clés de résultat de navigation et arguments.

   Une saisie en cours est de toute façon déjà dans `sorties_store` grâce à l'auto-save.

Pour information, hors question : les données iOS (`Application Support/sorties_sauvegardees.json`) ont un modèle **divergent**. iOS stocke `altMinReleve`, `placeNameReleve`, `sourceStatus`, `blurring`, `digitalProof`… en champs typés, là où Android utilise les maps `champsReleveExtra` et `champsOccExtra` (Models.swift). Une reprise des données iOS demanderait une table de correspondance.

## 6. Bilan chiffré

Lignes de code (non vides, hors commentaires), attribuées par plages après lecture du code :

| Fichier | Catégorie | Lignes fichier | Code | Logique extractible | Carte | Plateforme | Présentation | En-tête/imports |
|---|---|---|---|---|---|---|---|---|
| TraceViewModel.kt | COMMUN-ADAPTÉ | 301 | 168 | 159 | 0 | 0 | 0 | 9 |
| TraceFragment.kt | MIXTE | 1 226 | 898 | 253 | 374 | 24 | 212 | 35 |
| SaisieRapideFragment.kt | MIXTE | 1 285 | 999 | 437 | 186 | 77 | 243 | 56 |
| SaisieObservationFragment.kt | MIXTE | 1 053 | 774 | 483 | 0 | 62 | 196 | 33 |
| DenombrementFragment.kt | MIXTE | 449 | 335 | 109 | 0 | 94 | 95 | 37 |
| CaracterisationFragment.kt | MIXTE | 261 | 194 | 77 | 0 | 0 | 97 | 20 |
| DetailsReleveDialog.kt | MIXTE | 716 | 555 | 169 | 0 | 0 | 357 | 29 |
| DetailsReleveFragment.kt | UI | 113 | 72 | 27 | 0 | 0 | 30 | 15 |
| ObservationsFragment.kt | MIXTE | 222 | 175 | 54 | 0 | 0 | 101 | 20 |
| SaisiesEnAttenteFragment.kt | MIXTE | 888 | 632 | 260 | 0 | 0 | 352 | 20 |
| SortiesFragment.kt | MIXTE | 405 | 313 | 67 | 0 | 40 | 179 | 27 |
| SortieDetailFragment.kt | UI | 333 | 254 | 45 | 104 | 12 | 64 | 29 |
| EnvoiGroupeUi.kt | MIXTE | 128 | 93 | 28 | 0 | 0 | 54 | 11 |
| **Total** | | **7 380** | **5 462** | **2 168 (40 %)** | **664 (12 %)** | **309 (6 %)** | **1 980 (36 %)** | **341 (6 %)** |

Par catégorie de fichier :

| Catégorie | Fichiers | Lignes de fichier | Lignes de code |
|---|---|---|---|
| COMMUN (fichiers entiers) | 0 | 0 | 0 |
| COMMUN-ADAPTÉ | 1 | 301 | 168 |
| MIXTE | 10 | 6 633 | 4 968 |
| UI | 2 | 446 | 326 |
| PLATEFORME (fichiers entiers) | 0 | 0 | 0 |
| ABANDON (fichiers entiers) | 0 | 0 | 0 |

- **Code commun quasi tel quel** : ≈ 100 l. Ce sont `Releve`/`grouperEnReleves` (Observations l.43-65), `ResumeOutbox`/`resumeOutbox` (SaisiesEnAttente l.830-882), `DetailsReleveResult` (DetailsReleveDialog l.194-223), `PendingObs` (SaisieObservation l.72-109, hors UUID) et `Taxon.nomGroupe` (SaisieRapide l.1273-1284).
- **Logique extractible : 2 168 l.** Elle va vers des ViewModels ou services commonMain. Une partie est encore écrite contre des types Android (Bundle, GeoPoint, LiveData, Toast) : extractible, mais à adapter. Une fois les doublons factorisés (≈ 150 à 200 l., §4-10), on peut viser ≈ 1 950 à 2 000 l. communes.
- **À réécrire en Compose** : présentation (1 980) + carte (664) = 2 644 l. de code Android. En Compose, ce sera probablement plus court (fin des inflations, adapters et diffs d'overlays), sans chiffrage fiable possible.
- **Plateforme : 309 l.** Elles se répartissent entre médias (94), localisation, permissions et service, dictée, vibration, fichiers (partage et import) et restauration d'état, soit une douzaine d'interfaces (§1).
- **À abandonner** (petits morceaux) : code mort (SaisieRapide l.398-406 ; SaisieObservation l.150/201-202 et 656-661) ; `Configuration.getInstance().userAgentValue` d'osmdroid (Trace l.134, SaisieRapide l.181, SortieDetail l.59), à remplacer par la configuration HTTP de MapLibre ; et peut-être la restauration après mort du processus (§4-7), selon ce qui sera décidé.

**Notes de méthode :**
- L'attribution des lignes par plages a été faite à la lecture du code ; les totaux de code coïncident fichier par fichier avec la colonne `code` de `releve-automatique.tsv`.
- Lecture seule : aucun build, aucun fichier modifié dans GeoMys-Android ni GeoMys-IOS.
- Points non vérifiés : capacités exactes de maplibre-compose (marqueurs déplaçables, point de localisation, origine du mouvement de caméra, en-têtes HTTP, `mbtiles://`) ; BackHandler multiplateforme ; effet visible de `requireActivity().title` (SaisieObservation l.254-256) ; noms exacts des clés de `gn_config`.

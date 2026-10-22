# 6. Interface transversale, variantes de distribution, ressources et build

> Détail de l'inventaire GeoMys. La synthèse, les chiffres globaux et les décisions à prendre sont dans [`../INVENTAIRE.md`](../INVENTAIRE.md).
> Source analysée en lecture seule : GeoMys-Android `cdd5196` (v1.4.7, 18 octobre 2026). Les équivalents iOS ont été cherchés dans GeoMys-IOS `64d303b`.
> La colonne « Cible » donne une destination indicative ; la structure proposée pour GeoMys est décrite au § 9 de la synthèse.

## 1. Fichiers de code (GeoMys-Android @ cdd5196)

Conventions : « Lignes » = lignes physiques (lignes de code hors blancs/commentaires entre parenthèses, valeurs du relevé `releve-automatique.tsv`, recomptées). Les numéros `Lxx` renvoient au fichier source. Modules cibles proposés : `shared` (KMP + Compose MP : commonMain / androidMain / iosMain), `androidApp` (module application, porte les flavors), `iosApp` (Xcode). Chemins relatifs à `app/src/`.

| Fichier | Lignes | Rôle (1 phrase) | Catégorie | À remplacer / dépendances plateforme (précis) | Cible dans GeoMys | Remarques |
|---|---|---|---|---|---|---|
| `main/…/ui/MainActivity.kt` | 140 (80) | Activité unique : héberge le NavHostFragment, crée les 2 canaux de notification, coupe le GPS quand on quitte le flux de saisie, ouvre d'office Paramètres au 1er lancement. | MIXTE | `AppCompatActivity` + `setContentView(activity_main)` + `NavHostFragment` → `ComponentActivity.setContent { GeoMysApp() }` ; `enableEdgeToEdge` (garder, Android) ; osmdroid `Configuration.load/userAgentValue` + `MapTileCache.configurer` (L46-48, **doublon** de `GeoMysApplication` L59-61) → supprimé (MapLibre) ; `NotificationChannel` ×2 (`GeoMys_location` « Suivi GPS », `GeoMys_sync` « Synchronisation », L102-122) → androidApp ; `addOnDestinationChangedListener` + `DESTINATIONS_SAISIE` (7 `R.id`, L130-138) → `navController.currentBackStackEntryFlow` + `destination.hasRoute<…>()` sur routes typées ; `ViewModelProvider(this)[TraceViewModel]` → VM partagé à la racine (lifecycle-viewmodel MP) ; `locationTracker.arreterParcours()` + `LocationForegroundService.stop()` → interface commune `SuiviGps` (Android : FGS ; iOS : CLLocationManager) ; `savedInstanceState == null` + `armerRechargementSiRequis(ctx)` + `configurationComplete()` (L86-95) → logique de démarrage commune ; `onSupportNavigateUp` → supprimé. | `androidApp/…/MainActivity.kt` (≈25 l. : edge-to-edge, canaux, setContent) + `shared/commonMain/…/ui/GeoMysApp.kt` (NavHost, redirection initiale, règle « flux de saisie ») | iOS : `ContentView.swift` L64-69 (redirection) et L73-79 (clôture GPS, mais uniquement quand `.trace` quitte la pile — règle plus étroite qu'Android, qui inclut la saisie mono). Ne couper le GPS que sur une vraie transition saisie → hors-saisie (pas sur recréation, cf. `dansFluxSaisie`). |
| `main/…/ui/Navigation.kt` | 42 (15) | Extension `NavController.naviguerSur` : n'appelle `navigate(actionId)` que si l'action existe depuis la destination courante (anti double-tap → `IllegalArgumentException`). | ABANDON | `NavController`, ids d'action, `Bundle`, `NavOptions` ; `android.util.Log.d` | — (dans chaque écran : `dropUnlessResumed { nav.navigate(Route) }` de lifecycle-runtime-compose MP, ou `launchSingleTop`) | Navigation Compose n'a plus d'« actions » : le crash disparaît, mais le double empilement reste possible → besoin à couvrir autrement. 49 appels dans 18 fichiers. |
| `main/…/ui/AccueilFragment.kt` | 328 (224) | Écran d'accueil : tuiles multi/mono/Monitoring/OccHab, menu (Mes saisies, Mes visites, Mes stations, Explorer, Maps Manager), accès Paramètres, version/licence, interrupteur « Enregistrer la trace GPS », pastilles « en attente ». | UI (M) | ViewBinding ; `SharedPreferences("GeoMys_prefs")["enregistrer_trace"]` (L72-76) → multiplatform-settings (même clé que `@AppStorage("enregistrer_trace")` iOS) ; `PackageManager.getPackageInfo().versionName` (L288-292) → `InfosApp.version` (expect/actual ou BuildKonfig) ; `PopupMenu(menu_accueil)` → `DropdownMenu` ; `SpannableStringBuilder`/`ForegroundColorSpan` (L227-236) → `AnnotatedString` ; `ContextCompat.getColor` → thème ; `AlertDialog` licence → `AlertDialog` Compose ; `Intent.ACTION_VIEW` gnu.org → `LocalUriHandler.openUri` ; `LiveData estEnCours.observe` → `StateFlow.collectAsState` ; `activityViewModels` (TraceViewModel, OccHabViewModel) → VMs partagés ; `MiseAJourAccueil.*` (flavor) → interface injectée. | `shared/commonMain/…/ui/accueil/AccueilScreen.kt` + `AccueilViewModel.kt` | iOS : `UI/AccueilView.swift` (335). Code mort : `updateSortiesCount()` vide (L256-258). Texte de licence « GeoMys-Android v… » à rendre neutre. Comptages `SortieStore`/`OccHabStore` relus sur le thread UI à chaque `onResume`. Logique à extraire : §2. |
| `main/…/ui/ConfigGeoNatureFragment.kt` | 984 (673) | Paramètres : connexion GeoNature, test, lancement et suivi de la synchro, compteurs du cache, sélections JDD/liste/observateur, blocage de sortie tant que la config est incomplète. | MIXTE (UI XL) | Gson + `TypeToken` ×7 (L517, 625, 632, 639, 862, 866, 915) → kotlinx.serialization (`GeoNatureDataset/Liste/Observateur` `@Serializable`) ; `ArrayAdapter` + `MaterialAutoCompleteTextView` ×3 → `ExposedDropdownMenuBox` ou champ de recherche dédié ; 3 `TextWatcher` (4 champs) + `Job` de debounce → état du VM + `Flow.debounce(300)` ; `Toast` ×4 → Snackbar / zone de toasts commune ; `AlertDialog` à en-tête construit en code (L935-976 : `LinearLayout`, `TextView`, `Typeface`, `displayMetrics.density`) → `AlertDialog`/`ModalBottomSheet` + `LazyColumn` ; `onBackPressedDispatcher.addCallback` (L265) → `BackHandler` + neutraliser le geste retour iOS ; `registerForActivityResult(RequestPermission)` POST_NOTIFICATIONS + `SDK_INT ≥ 33` (L98, L790-797) → androidMain ; `SyncForegroundService.start` (L731) → interface `LanceurSynchro` ; `SyncRunner.etat` (LiveData) → StateFlow ; `lifecycleScope` → `viewModelScope` ; `MaterialColors.getColor`/`ContextCompat.getColor(holo_orange_dark)`/`0xFFE65100` → thème ; `GeoNatureConfig(requireContext())` → config injectée. | `ui/config/ConfigGeoNatureScreen.kt` + `ConfigGeoNatureViewModel.kt` ; `domain/config/EtatConfiguration.kt` (`configurationComplete`), `ReglesSelectionOcctax.kt`, `ConnexionServeur.kt` ; androidMain : permission notif + lanceur FGS | ≈330 l. de code de logique (§2). iOS : `UI/ConfigGeoNatureView.swift` (745) + `Store/ReglesSelectionOcctax.swift` (76, règles déjà extraites — modèle à suivre). Ids de layout jamais utilisés : `progress_datasets`, `tv_erreur_datasets`, `tv_erreur_observateurs`. 2 tests lisent ce fichier source (garde « pas de porte dérobée »). |
| `main/…/ui/TaxonsListeFragment.kt` | 190 (128) | Liste consultable des taxons d'une liste (Paramètres › Détails), recherche multi-mots insensible aux accents, compteur « n / total ». | UI (S) | `java.text.Normalizer` (NFD) + `Regex("\\p{Mn}+")` (L55, L63-66) → réutiliser `TaxRefCache.normaliser` (déjà Kotlin pur, et c'est ce que fait iOS) — sinon expect/actual ; `@Volatile` JVM sur un cache statique (L60-61) → `kotlin.concurrent.Volatile` ou cache tenu par le VM ; `RecyclerView`/`Adapter`/`notifyDataSetChanged` → `LazyColumn(key = cdNom)` ; `TextWatcher` → `TextField` ; arguments `Bundle` → route typée `TaxonsListe(idListe, nomListe)` ; `Dispatchers.Default` (déjà commun). | `ui/taxons/TaxonsListeScreen.kt` + `TaxonsListeViewModel.kt` (≈35 l. : construction triée + clés, filtre « tous les mots », cache de la dernière liste) | iOS : `UI/TaxonsListeView.swift` (160). Mode `taxonName` (groupe taxonomique, L87-113) **mort** : aucun appelant ne passe `taxonName` → ne pas porter. |
| `main/…/ui/FilAriane.kt` | 267 (156) | Fil d'Ariane cliquable des écrans Monitoring (🏠 › Monitoring › Protocole › objets…) et bandeau « 🏠 › écran » des autres écrans. | MIXTE | **Commun tel quel** : `FilSegment`, `FIL_TYPE_*`, `FIL_SEPARATEUR`, `filRacineSuivis`, `encoderFil`/`decoderFil` (L47-48, L82-121, ≈22 l. de code). **Navigation** `naviguerVersFil` (L236-266) → NavController Compose : `popBackStack<Route>(inclusive = false)` sinon `navigate(route) { popUpTo<SuiviDetail> }` ; `bundleOf` → routes typées. **UI** (L123-228) : `SpannableStringBuilder`/`ClickableSpan`/`ImageSpan(ALIGN_BOTTOM)`/`LinkMovementMethod`/`TextPaint`/`ContextCompat.getDrawable(ic_home).setTint` → `AnnotatedString` + `LinkAnnotation.Clickable` + `InlineTextContent(painterResource(Res.drawable.ic_home))`. `garderContenuSousFil`/`garderMargeSousFil` (L55-80, `OnLayoutChangeListener`) → supprimés (une `Column` suffit). | `ui/navigation/FilAriane.kt` (modèle + encodage + navigation) + `ui/composants/FilArianeBar.kt` | iOS : `UI/FilAriane.swift` (103). Test JVM pur `FilArianeTest` (73 l.) portable tel quel en commonTest. Fil appelé par 4 écrans, bandeau par 17. Encodage RS/US (`\u001E`/`\u001F`) dans un argument de route : à valider avec l'encodage d'URL de navigation-compose (sinon `List<FilSegment>` `@Serializable` + NavType). |
| `main/…/ui/Insets.kt` | 99 (61) | Helpers edge-to-edge : padding/marge = barres système + encoche (+ IME) sur des View. | ABANDON | `ViewCompat.setOnApplyWindowInsetsListener`, `WindowInsetsCompat`, `updatePadding`, `MarginLayoutParams` | — : `WindowInsets.safeDrawing/systemBars/ime` + `Modifier.windowInsetsPadding/safeDrawingPadding/imePadding` (Compose MP, Android et iOS), `contentPadding` de `Scaffold` | 57 appels dans 25 fichiers : remplacés écran par écran, aucun code à porter. |
| `main/…/ui/Couleurs.kt` | 80 (27) | Palette sémantique (texte secondaire, texte, erreur, succès, en cours, avertissement), cadre d'état arrondi, masquage du clavier. | UI (S) — thème | `MaterialColors.getColor(colorOnSurfaceVariant / colorOnSurface / colorError)` → `MaterialTheme.colorScheme.onSurfaceVariant / onSurface / error` ; constantes `0xFF2E7D32`, `0xFFFFB300`, `0xFFE65100` → `Color(…)` du thème commun ; `GradientDrawable` (`cadreColore` : rayon 8 dp, trait 2 dp, fond transparent) → `Modifier.border(2.dp, c, RoundedCornerShape(8.dp))` ; `InputMethodManager.hideSoftInputFromWindow` + `clearFocus` → `LocalSoftwareKeyboardController.hide()` + `LocalFocusManager.clearFocus()`. | `ui/theme/Couleurs.kt` + `ui/theme/GeoMysTheme.kt` + `ui/composants/Modifiers.kt` | iOS : `Theme/Couleurs.swift` (70). 8 helpers, 61 appels dans le code. Paramètres utilise 3 oranges différents pour « avertissement » (#E65100, `holo_orange_dark` #FF8800, `colorSecondary` nuit #FF6D00) → à unifier. |
| `main/…/ui/AlerteStockage.kt` | 47 (15) | Dialogue bloquant « Enregistrement impossible » quand un store n'a pas pu écrire (disque plein). | UI (S) | `AlertDialog.Builder(Context)` → état global commun (`StateFlow<String?>`) + `AlertDialog` affiché une seule fois à la racine de `GeoMysApp` | `ui/composants/AlerteStockage.kt` | C'est exactement le schéma iOS : `Store/AlerteStockage.swift` (30) + `.alert` de `ContentView`. 10 appels dans 6 fichiers ; textes communs. |
| `main/…/ui/MediaImport.kt` | 141 (95) | Copie locale d'un média choisi vers `filesDir/medias` ; images recompressées (2048 px côté long, JPEG 85, orientation EXIF appliquée), autres types copiés bruts. | PLATEFORME | `ContentResolver.getType/openInputStream` (URI `content://`), `BitmapFactory` (`inJustDecodeBounds`, `inSampleSize`), `Bitmap.createScaledBitmap`, `Bitmap.createBitmap` + `Matrix.postRotate`, `android.media.ExifInterface(InputStream)`, `Bitmap.compress(JPEG, 85)`, `File(filesDir, "medias")`, `File.toURI()`, `System.currentTimeMillis` (→ `Clock.System`), `Log.w` (→ Kermit). iOS : résultat PHPicker/UIImagePickerController → ImageIO `CGImageSourceCreateThumbnailAtIndex` (MaxPixelSize 2048, WithTransform) ou `UIImage` + `jpegData(0.85)`, écriture dans `Documents/medias`. | commonMain `media/ImportMedia.kt` (interface + `COTE_MAX`, `QUALITE_JPEG`, `prefixeEtExtension` L123-140, nommage `photo_<ms>_<i>.jpg`, calcul du sous-échantillonnage L78-81 : ≈25 l.) ; implémentations androidMain / iosMain | iOS : `UI/Saisie/MediaPickerView.swift` (L142-147, L248-251) + `Store/MediaLocal.swift`. **Contrat à changer** : Android renvoie une URI absolue `file://…`, iOS stocke une référence relative `medias/<nom>` (le chemin du conteneur iOS change à la réinstallation) → référence relative + résolveur tolérant en commun, et migration des saisies Android existantes. `MediaImportTest` = Robolectric. |
| `main/…/ui/PhotoCapture.kt` | 44 (13) | Crée le fichier cache cible et son URI FileProvider pour l'appareil photo système (contrat `TakePicture`). | PLATEFORME | `FileProvider.getUriForFile(ctx, "${packageName}.provider", …)`, `cacheDir/captures`, `System.currentTimeMillis` ; contrat `ActivityResultContracts.TakePicture` chez les appelants (DenombrementFragment, NouvelleVisiteFragment) | commonMain `media/CapturePhoto.kt` : `@Composable expect fun rememberCapturePhoto(onResultat: (MediaSource?) -> Unit)` ; androidMain (TakePicture + FileProvider) ; iosMain (`UIImagePickerController`, source `.camera`) | iOS : `MediaPickerView.swift` L207-240 (ni fichier temporaire ni URI). iOS exige `NSCameraUsageDescription` (Android : aucune permission CAMERA, passage par l'appli photo). Bibliothèques possibles (FileKit, Peekaboo) : non évaluées. |
| `github/…/network/MiseAJour.kt` | 126 (82) | MAJ intégrée : lit `releases/latest` de `ANA-CEN-Ariege/GeoMys-android`, compare les versions, télécharge l'APK (https uniquement) et lance l'installateur. | PLATEFORME (Android, flavor github seul) | `HttpURLConnection` → Ktor (facultatif) ; `org.json.JSONObject` → kotlinx.serialization ; `File(cacheDir, "geomys-update.apk")` ; `FileProvider` + `Intent.ACTION_VIEW` (`application/vnd.android.package-archive`, `FLAG_GRANT_READ_URI_PERMISSION`) : Android pur ; `comparerVersions` vient de `main/…/network/CompatibiliteServeur.kt` | `androidApp/src/github/…/MiseAJour.kt` | Aucun équivalent iOS (App Store / TestFlight). À garder pour Android : c'est le seul canal de MAJ des installations github existantes (§5). |
| `github/…/ui/MiseAJourAccueil.kt` | 70 (28) | Glue accueil ↔ MAJ : tap sur la version → écran MAJ ; vérification silencieuse → pastille « MAJ disponible ». | PLATEFORME (glue de flavor) | `findNavController().navigate(action_accueil_to_mise_a_jour)`, `viewLifecycleOwner.lifecycleScope`, accès aux membres `internal` d'AccueilFragment ; ancre `Class<MiseAJourFragment>` (contournement de compilation incrémentale) → inutile | interface commonMain `app/MiseAJourIntegree.kt` (`majDisponible: StateFlow<Boolean>`, `ouvrir()`, `reverifier()`) ; implémentation dans `androidApp/src/github` ; `null` en play et sur iOS | — |
| `github/…/ui/MiseAJourFragment.kt` | 196 (143) | Écran « Mise à jour » : vérifier, télécharger (progression), demander « sources inconnues », installer, puis supprimer l'APK. | UI (S, Android/github seul) | ViewBinding ; `BuildConfig.VERSION_NAME` (L89, L102) ; `registerForActivityResult(StartActivityForResult)` + `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` ; `packageManager.canRequestPackageInstalls()` ; `view.post` ; `onPause`/`onResume` (nettoyage) → `LifecycleResumeEffect` | `androidApp/src/github/…/MiseAJourScreen.kt` (Compose, route ajoutée au graphe par le flavor) | iOS : `UI/PlaceholderViews.swift:538` (placeholder ; route `.miseAJour` non atteignable depuis l'accueil) → à supprimer. Le KDoc « depuis la roue dentée » est faux : on y arrive par le numéro de version. |
| `play/…/ui/MiseAJourAccueil.kt` | 34 (6) | Implémentation vide (no-op) de la glue MAJ pour le `.aab` Play. | ABANDON | — | remplacé par `MiseAJourIntegree? = null` | — |

Aucun fichier du périmètre n'est COMMUN ou COMMUN-ADAPTÉ en entier : le code commun est enfoui dans les fichiers MIXTE ou UI.

## 2. Logique à extraire

### `ConfigGeoNatureFragment.kt` (MIXTE, 984 l.) — ≈330 lignes de code de logique sur 673 (≈450 lignes avec les commentaires de règles, à garder)

- **Validité de la configuration** — `configurationComplete(cfg)` L62-71, `MESSAGE_RECHARGEMENT_REQUIS` L75-76, messages de refus de sortie L252-258 et L269-274 → `domain/config/EtatConfiguration.kt` (pur ; lit `GeoNatureConfig` et `TaxRefCache`). Utilisé par MainActivity, par l'écran (×3) et par 2 tests. Sur iOS, c'est `config.configurationComplete`.
- **Règle http** L116-121 → `estHttpEnClair(url)` (même fonction côté iOS, `ConfigGeoNatureView.swift:258`). Le message dépend de la plateforme (Android : mot de passe en clair ; iOS : ATS refuse).
- **Visibilité des sections** : état initial L158-172, révélation après un test réussi L409-420, révélation après la vérification de version L289-291, « oubli » visuel dès qu'un champ de connexion est édité (`connexionEditeeNonEnregistree` + `refleterEditionConnexion` L313-335) → un état `SectionsVisibles` dérivé dans le VM (≈30 l.).
- **Enregistrement de la connexion** — `sauvegarderChamps()` L337-390 (≈22 l.). L'identité = URL ou login, **pas** le mot de passe. `invaliderCachesSession()` est appelé à tout changement ; `PictoCache.vider()` + `MonitoringCache.vider()` + `reinitialiserSelectionsOcctax()` seulement si l'identité change ; remise à zéro de `versionGeoNatureServeur`/`serveurCompatible` → `domain/config/ConnexionServeur.kt` (règle à haute valeur ; iOS : `enregistrerConnexion()` L639).
- **Test de connexion** `testerConnexion()` L392-422 et **vérification TaxRef au démarrage** L285-308 (libellé « TaxRef v$cached — maj v$version disponible ») → actions du VM (≈30 l.).
- **Sélections OCCTAX** → `ReglesSelectionOcctax` (≈80 l.), soit les 4 fonctions déjà portées côté iOS (`datasetValide`, `listesProposables`, `listeChoisie`, `observateurChoisi`) :
  - `peuplerSpinnerDatasets` L434-483 : filtre actif ∧ (module OCCTAX ou aucun module) ∧ (CRUVED C ou ensemble vide), purge de la sélection « fantôme », libellé « nom (id) ». Ce filtre est **dupliqué** dans `DetailsReleveDialog.datasetsPourDetailsReleve` L85-95, avec une variante OCCHAB L98-108.
  - `appliquerRestrictionListeDataset` L488-523 : liste imposée par `id_taxa_list`, repli synthétique « Liste <id> ».
  - `peuplerSpinnerListes` L525-565 : sélection automatique de l'unique liste, sauf juste après un reset.
  - `peuplerSpinnerObservateurs` L568-619 : par défaut, l'utilisateur connecté (`idRoleUtilisateur`), y compris après un reset.
  - À conserver : la résolution passe par la liste **immuable** capturée (L537-541, contre un IndexOutOfBounds observé).
- **Caches JSON** `restaurerCaches()` L622-644 et les 4 autres parsings Gson → accesseurs typés dans `GeoNatureConfig` (kotlinx.serialization) (≈25 l.).
- **Reset** `reinitialiserSelectionsOcctax()` L649-671 + drapeau `selectionsReinitialisees` (L90). Règle d'ordre à conserver : `restaurerCaches()` **avant** de désarmer le drapeau (L752-755).
- **Bandeau de blocage** `majBandeauRechargement()` L677-704 → `bandeauBlocage(requis, listesIncompletes): String?`. Décision produit « aucune porte dérobée vers les saisies » (L686-690), gardée par `ListeIncompleteTest`.
- **Synchro** — `chargerToutesLesDonnees()` L720-732 et `observerSyncEnArrierePlan()` L738-785 : le VM collecte `SyncRunner.etat` (StateFlow), réduit l'état (en cours / terminé / succès, libellé) et appelle `accuserReception()`. Le démarrage passe par `LanceurSynchro` (≈30 l. de logique).
- **Avertissement de liste** `updateAvertissementListe()` L804-828 → `avertissementListe(idListe, listesSynchronisees, nbTaxons)` renvoie `(message, estAvertissement)` (≈18 l.).
- **Compteurs** `updateCacheInfo()` L853-894 → `compteursCache()` sur `Dispatchers.Default` (≈20 l.).
- **« Taxons par listes »** `montrerTaxonsParListe()` : la partie données L903-930 (tri décroissant, nom de repli, marque « · protocole », union des cd_nom des listes de protocoles) → `lignesTaxonsParListe()` (≈22 l.). L935-976 = UI pure.
- **Reste UI/plateforme (≈340 l. de code)** : liaisons de vues, adaptateurs, visibilités, Toasts, dialogue construit en code, permission POST_NOTIFICATIONS (L96-99, L787-797, Android seul).

### `AccueilFragment.kt` (UI M, 328 l.) — ≈55 lignes de code de logique sur 224 (≈85 lignes avec les commentaires)

- **Préférence « Enregistrer la trace GPS »** L72-76 (fichier `GeoMys_prefs`, défaut `true`, relue par `TraceFragment` L159-160) → `PreferencesApp` (multiplatform-settings). Le même fichier sert aussi à MapUtils, PreferencesSaisie et MapTileCache.
- **Ouverture des flux** :
  - multi L78-84 : `reinitialiser()` puis Trace ;
  - mono L86-104 : `reinitialiser()` et `typeSaisieLabel`. L'écran « Détails du relevé » (`mono=true`) n'est intercalé que s'il existe un champ OCCTAX_RELEVE requis sans défaut (`AdditionalFieldsRenderer.aDesChampsReleveRequisSansDefaut`, logique pure logée dans un renderer UI) ;
  - OccHab L110-115 : `demarrerSession(detailsSessionParDefaut(cfg))`.
  - → `AccueilViewModel.ouvrirSaisieMulti/Mono/OccHab(): Route`.
- **Règles de visibilité et d'activation** : Monitoring et « Mes visites » si `countModulesEnCache() > 0` (L123-124, L243-244) ; OccHab et « Mes stations » si `occhabDisponible ∧ occhabPeutCreer` (L128-129, L247-248) ; boutons de saisie actifs si `saisieOcctaxValide` (L273-279) ; point vert ou pastille rouge sur Paramètres (L262-266).
- **Compteurs « en attente »** L197-208 (sorties non envoyées hors importées ; visites PENDING ou ERROR ; stations OccHab non envoyées) + pastille du menu L191-194 → `AccueilEtat`, calculé hors du thread UI.
- **État d'enregistrement GPS** L251-254 (LiveData → StateFlow).
- **Version et MAJ** : version affichée L288-292 → `InfosApp.version` ; MAJ L69, L186, L213-223 → `MiseAJourIntegree?`.
- **UI pure (≈120 l.)** : PopupMenu et titres à pastille « ● » (Spannable), dialogue de licence, grisage `alpha = 0.4`.

## 3. Carte des écrans

`nav_graph.xml` : 384 lignes, **25 destinations** `<fragment>`, **46 actions**, **20 déclarations d'argument**, départ `accueilFragment`. ★ = destination du « flux de saisie » (`MainActivity.DESTINATIONS_SAISIE`) : passer de ★ à une destination non ★ sauvegarde le brouillon et arrête le GPS et le service.

| # | Destination (label) | Fragment → layout(s) | Arguments déclarés | Clés réellement lues mais **non déclarées** | Actions sortantes (options) | Équivalent iOS (`Route.swift`) / notes |
|---|---|---|---|---|---|---|
| 1 | `accueilFragment` (« GeoMys »), **départ** | AccueilFragment → fragment_accueil | — | — | 12 : →trace, →sorties, →configGeoNature, →explorer, →saisieRapide, →detailsReleve (`mono=true`), →suivis, →saisiesEnAttente, →cacheManager, →miseAJour (github), →occhabCarte, →occhabStations | racine de la `NavigationStack` |
| 2 | `traceFragment` (« Sortie ») ★ | TraceFragment → fragment_trace | `sortieId` (string, "") | — | →observations, →saisieObservation, →detailsReleve | `.trace(sortieId:)` ; lit `demarrerSaisieSuivante` dans son `savedStateHandle` |
| 3 | `detailsReleveFragment` (« Détails du relevé ») ★ | DetailsReleveFragment → fragment_details_releve | — | `mono`, `addReleveJson`, `latitude`, `longitude`, `geometryType`, `geometryCoordsJson` | →saisieObservation, →saisieRapide (NavOptions `popUpTo(self, inclusive)` construites en code) | `.detailsReleveIntercalaire(…)` / `.detailsReleveIntercalaireMono` |
| 4 | `saisieObservationFragment` (« Nouveau relevé ») ★ | SaisieObservationFragment → fragment_saisie_observation (+item_pending_obs) | — | `releveId`, `obsId`, `latitude`, `longitude`, `geometryType`, `geometryCoordsJson`, `addReleveJson` | →caracterisation, →denombrement | `.saisieObservation(7 champs)` ; écrit `demarrerSaisieSuivante` dans l'entrée précédente |
| 5 | `caracterisationFragment` (« Caractérisation ») ★ | CaracterisationFragment → fragment_caracterisation | — | `taxon`, `groupe2Inpn`, `determinateur`, `determinateurDefaut`, `notes`, `comportementSeul`, `cdNom`, `addOccJson`, `occExtraJson` + clés `svKey` des nomenclatures | — (retour) | résultat via `previousBackStackEntry.savedStateHandle` ; iOS : `fullScreenCover` + Binding (pas de route) |
| 6 | `denombrementFragment` (« Dénombrement ») ★ | DenombrementFragment → fragment_denombrement (+item_denombrement, item_photo_denombrement) | — | `taxon`, `groupe2Inpn`, `espece`, `denombrementsJson`, `cdNom` | — (retour) | résultat `denombrementsJson` via `savedStateHandle` ; iOS : `fullScreenCover` |
| 7 | `saisieRapideFragment` (« Saisie rapide ») ★ | SaisieRapideFragment → fragment_saisie_rapide | — | `addReleveJson` | →caracterisation, →denombrement, →saisieObservation | `.saisieRapide(releveAddFieldsJson:, sortieId:)` — le `sortieId` iOS est la passerelle multi→mono **supprimée côté Android** (§6) |
| 8 | `observationsFragment` (« Observations ») ★ | ObservationsFragment → fragment_observations (+item_observation) | — | — | →saisieObservation | iOS : `fullScreenCover` depuis TraceView |
| 9 | `sortiesFragment` (« Mes saisies ») | SortiesFragment → fragment_sorties (+item_sortie) | — | — | →sortieDetail (`sortieId`), →trace (reprise, `sortieId`) | `.sorties` |
| 10 | `sortieDetailFragment` (« Sortie ») | SortieDetailFragment → fragment_sortie_detail (+infowindow_obs) | `sortieId` (string, requis) | — | — | `.sortieDetail(sortieId:)` ; export GPX par FileProvider |
| 11 | `configGeoNatureFragment` (« GeoNature ») | ConfigGeoNatureFragment → fragment_config_geonature | — | — | →taxonsListe (`idListe`, `nomListe`) | `.configGeoNature` ; ouvert d'office si la config est incomplète, sortie bloquée |
| 12 | `taxonsListeFragment` (« Taxons ») | TaxonsListeFragment → fragment_taxons_liste (+item_taxon_list) | `taxonName` (string, **requis, jamais fourni**) | `idListe` (int), `nomListe` | — | `.taxonsListe(idListe:, nomListe:)` |
| 13 | `miseAJourFragment` (« Mise à jour ») | MiseAJourFragment (**classe présente dans le seul flavor github**) → fragment_mise_a_jour | — | — | — | `.miseAJour` = placeholder non atteignable |
| 14 | `explorerFragment` (« Explorer ») | ExplorerFragment → fragment_explorer (+bottom_sheet_obs_explorer, item_obs_explorer) | — | — | — | `.explorer` ; `BottomSheetDialog` hors graphe |
| 15 | `cacheManagerFragment` (« Maps Manager ») | CacheManagerFragment → fragment_cache_manager | — | — | — | `.cacheManager` |
| 16 | `suivisFragment` (« Monitoring ») | SuivisFragment → fragment_suivis (+item_suivi_module) | — | — | →suiviDetail (`moduleCode`), →carteGeometrie (`objectType="module"`, `id`, `titre`, `fil`), →saisiesEnAttente | `.suivis` |
| 17 | `suiviDetailFragment` (« Détail protocole ») | SuiviDetailFragment → fragment_suivi_detail | `moduleCode` (requis) | — | →ficheObjet, →carteGeometrie, →nouvelleVisite | `.suiviDetail(moduleCode:, titre:)` ; unique dans la pile (ancre du fil d'Ariane) |
| 18 | `ficheObjetFragment` (« Fiche ») | FicheObjetFragment → fragment_fiche_objet | `moduleCode`, `objectType`, `id` (int), `fil` ("") | — | →ficheObjet, →carteGeometrie, →nouvelleVisite | `.ficheObjet(moduleCode:, objectType:, id:, titre:)` |
| 19 | `carteGeometrieFragment` (« Carte ») | CarteGeometrieFragment → fragment_carte_geometrie | `moduleCode`, `objectType`, `id`, `titre` (""), `fil` ("") | — | →ficheObjet, →nouvelleVisite | iOS sépare `.monitoringCarte(…)` et `.monitoringCarteProtocole(…)` |
| 20 | `nouvelleVisiteFragment` (« Nouvelle visite ») | NouvelleVisiteFragment → fragment_nouvelle_visite | `moduleCode`, `parentObjectType`, `parentId` (-1), `titreSite`, `editUuid`, `fil` (tous avec défaut) | `childObjectType`, `parentUuidLocal`, `modeCompletion`, `messageEphemere` (réécrit aussi ses propres arguments en édition, L233-237) | →ficheObjet (`popUpTo self, inclusive`), →nouvelleVisite « enchaîner » (`popUpTo self, inclusive`) | `.nouvelleVisite(7 champs)` |
| 21 | `saisiesEnAttenteFragment` (« Saisies en attente ») | SaisiesEnAttenteFragment → fragment_saisies_en_attente | — | — | →nouvelleVisite (édition) | `.saisiesEnAttente` |
| 22 | `occhabCarteFragment` (« Station OccHab ») | OccHabCarteFragment → fragment_occhab_carte | — | — | →occhabStation ; →occhabHabitat (**action morte** `action_occhab_carte_to_habitat`, plus citée depuis cdd5196) | `.occhabCarte(jeton: UUID)` (carte vierge à chaque tap) |
| 23 | `occhabStationFragment` (« Station OccHab ») | OccHabStationFragment → fragment_occhab_station | — | — | →occhabHabitat ; →occhabCarte (`popUpTo accueil`, non inclusif) | `.occhabStation` |
| 24 | `occhabHabitatFragment` (« Habitat OccHab ») | OccHabHabitatFragment → fragment_occhab_habitat | `habitatId` (string?, null) | — | →occhabStation (`popUpTo self, inclusive`, `launchSingleTop`) | `.occhabHabitat(habitatId:)` |
| 25 | `occhabStationsFragment` (« Mes stations ») | OccHabStationsFragment → fragment_occhab_stations (+item_occhab_station) | — | — | →occhabCarte | `.occhabStations` |

**Navigations hors graphe ou hors actions** (à reprendre explicitement dans le NavHost Compose) :
- `MainActivity` L93 : `navigate(action_accueil_to_config)` au démarrage si la config est incomplète.
- `FilAriane.kt` : navigations **directes vers des destinations**, sans action ni garde anti double-tap :
  - `popBackStack(accueilFragment)` sinon `navigate(accueilFragment)` (L218, L243) ;
  - idem pour `suivisFragment` (L246) ;
  - idem pour `suiviDetailFragment` avec `moduleCode` (L249-250) ;
  - `navigate(ficheObjetFragment, {moduleCode, objectType, id, fil}, popUpTo(suiviDetailFragment))` (L255-264).
- `popBackStack(R.id.accueilFragment, false)` : `OccHabCarteFragment` L896, `OccHabStationFragment` L72, `OccHabHabitatFragment` L114.
- NavOptions construites en code : `DetailsReleveFragment` L86-87 (`popUpTo detailsReleve, inclusive`).
- Flavor github : `MiseAJourAccueil` L52, `navigate(action_accueil_to_mise_a_jour)` sans `naviguerSur`.
- Résultats passés par `savedStateHandle` : Caractérisation et Dénombrement écrivent dans l'entrée précédente ; SaisieRapide (L652, `getLiveData`) et SaisieObservation (L619) les lisent dans leur propre entrée ; SaisieObservation → Trace (`demarrerSaisieSuivante`, L923-924, consommé par TraceFragment L166-167).
- État partagé implicite entre destinations : `TraceViewModel` et `OccHabViewModel` à portée Activity (`activityViewModels`).
- Dialogues hors graphe : `ouvrirDialogDetailsReleve`, `ouvrirDialogDetailsOccHab`, `BottomSheetDialog` de l'Explorer, dialogue « Taxons par listes » de Paramètres.

**Incohérences du graphe** (à corriger au passage aux routes `@Serializable`) :
- les écrans du flux de saisie reçoivent des clés non déclarées (`Bundle` libre) ;
- `taxonName` est déclaré requis mais jamais fourni ;
- `action_occhab_carte_to_habitat` est morte ;
- `miseAJourFragment` est déclaré dans le graphe de `main` alors que sa classe n'existe qu'en github (sans effet en play : jamais atteint) ;
- les `label` ne servent qu'au log de `naviguerSur`.

Le test `DefautsPariteLot2Test.aucune_passerelle_entre_saisie_mono_et_multi` lit `nav_graph.xml`, `TraceFragment.kt`, `SaisieRapideFragment.kt` et `AccueilFragment.kt` : décision produit à préserver.

## 4. Ressources

**Layouts** : 40 fichiers, 5 407 lignes → 0 % réutilisable (réécriture Compose).
- Répartition : 25 `fragment_*`, 10 `item_*`, `activity_main`, `view_bandeau_saisie` (inclus 17 fois), `champ_dropdown_releve`, `infowindow_obs`, `bottom_sheet_obs_explorer`.
- 7 layouts contiennent `org.osmdroid.views.MapView` et 4 un `CompassView` maison.
- Widgets : 139 TextView, 124 LinearLayout, 66 ImageButton, 78 Button/MaterialButton, 17 ProgressBar, 11 TextInputLayout (4 ExposedDropdownMenu), 6 RecyclerView, 3 TabLayout, 3 MaterialSwitch, 3 MaterialCardView, 3 ConstraintLayout.
- Mon périmètre : activity_main (9), fragment_accueil (255), fragment_config_geonature (532 : 3 cartes, 7 TextInputLayout dont 3 listes déroulantes à autocomplétion), fragment_taxons_liste (70), item_taxon_list (30), fragment_mise_a_jour (78), view_bandeau_saisie (21), soit 995 lignes.

**Drawables** (`drawable/`, 59 XML, tous référencés au moins une fois) :
- **40 vectoriels** → `composeResources/drawable/` tels quels pour 30 d'entre eux. **10 à normaliser** : 6 utilisent des attributs de thème `?attr/` (`ic_geom_line`, `ic_geom_polygon` : colorOnSurface ; `ic_eye`, `ic_info` : tint colorControlNormal ; `ic_mic` : colorControlNormal ; `ic_mic_active` : colorPrimary) et 4 uniquement `@android:color/white`, avec ou sans `android:tint` littéral (`ic_bird_large`, `ic_edit`, `ic_play` #4CAF50, `ic_stop` #F44336). Les `?attr/` ne seront pas résolus par Compose MP hors Android ; remplacer aussi `@android:color/*` par des couleurs littérales, par prudence (à vérifier), et teinter via `Icon(tint = …)`. Parmi ces vectoriels : les 10 icônes de groupes taxonomiques (amphibiens… reptiles2), dont le mapping Taxon → icône vit dans l'UI (ExplorerFragment, ObservationsFragment, TaxonPalette), pas dans le modèle.
- **15 shapes + 3 selectors + 1 inset** → à refaire en Compose (`Brush.linearGradient`, `CircleShape`, `RoundedCornerShape`, `border`, `Surface` + indication). `bg_accueil` = dégradé #14402E → #0D264C à 315°, soit `linearGradient` du haut-gauche vers le bas-droite (comme iOS). `ic_launcher_background` reste côté Android (icône adaptative).
- **Bitmaps** : `logo_geomys.png` × 5 densités (jusqu'à 480×480, 53 Ko) → composeResources (qualificatifs de densité acceptés) ; icônes de lanceur `mipmap-*` = 15 WebP + 2 `adaptive-icon` XML → androidApp seul (iOS : `AppIcon.appiconset`). L'iOS a recopié 22 imagesets (11 `ic_*`, 10 `taxon_*`, logo), redondants une fois composeResources en place.

**Couleurs** :
- `colors.xml` = 20 couleurs, toutes utilisées ; `color/` = 2 selectors (`occhab_valider_*`, dont les alphas 0,12 / 0,38 désactivés = valeurs par défaut M3).
- En dur : 56 littéraux ARGB `0x……` dans le Kotlin, 10 `#……` dans les layouts, 12 usages de `android.R.color.holo_*` / `darker_gray` → tout centraliser dans le thème.

**Chaînes** : `strings.xml` = **25** chaînes, toutes utilisées (+ `src/debug` : `app_name` = « GeoMys debug »). Une seule langue (fr), aucun `values-xx`.
- En dur dans les **layouts** : 187 attributs texte littéraux, contre 5 `@string`.
- En dur dans le **Kotlin** : ≈1 030 littéraux probablement visibles par l'utilisateur dans les 123 fichiers (heuristique : espace ou accent, hors logs et clés techniques ; ≈580 dans `ui/`, ≈76 dans le Kotlin de mon périmètre). Probablement sous-estimé : les libellés d'un seul mot ne sont pas comptés.
- Menu : 4 titres littéraux sur 5 ; 3 `android.R.string.ok/cancel`.
- Portage : `strings.xml` → `composeResources/values/strings.xml` (même format). `observations_count` (« Observations (%d) ») devra probablement passer en `%1$d` (format positionnel attendu par Compose MP, à vérifier).

**Thème** :
- `Theme.GeoMys` hérite de `Theme.Material3.DayNight.NoActionBar`, mais le mode nuit est **forcé** (`GeoMysApplication` L46 : `MODE_NIGHT_YES`). Seul `values-night/themes.xml` s'applique donc : primary #1B6CA8, onPrimary #FFFFFF, secondary **#FF6D00**, onSecondary #FFFFFF, `windowBackground` = dégradé `bg_accueil`. Le thème jour (secondary #43A047, `windowLightStatusBar=false`) est mort ; `colorPrimaryVariant` est un attribut M2, ignoré par M3.
- Styles maison : `Widget.GeoMys.Button.TextButton` (44 usages) et `Widget.GeoMys.Button.OutlinedButton` (10), texte et contour #FFE082 (`jaune_clair`, couleur des « liens »). Styles M3 utilisés : ExposedDropdownMenu ×4, OutlinedButton ×4, Button ×3, OutlinedBox ×1.
- Attributs de thème cités dans les layouts : `selectableItemBackgroundBorderless` (27), `colorControlNormal` (21), `colorOnSurfaceVariant` (12), `colorControlHighlight` (6), `colorSurfaceVariant` (5), `colorPrimary` (5), `textColorSecondary` (5)…
- Cible : `GeoMysTheme` = `darkColorScheme(primary, onPrimary, secondary, onSecondary)`, le reste aux valeurs M3 par défaut ; fond dégradé à la racine, `Scaffold` transparents ; boutons « lien » jaunes. iOS : `UIUserInterfaceStyle = Dark` dans l'Info.plist (l'actuel iOS fait `.preferredColorScheme(.dark)` + `.tint(jauneClair)`).

**Autres** :
- `ids.xml` (`tag_field_codes`, tags de View des renderers) → abandon.
- `menu_accueil.xml` (5 entrées) → `DropdownMenu`.
- `xml/` (4 fichiers) → Android seul (§5).
- **Asset** `nidification_oiseaux.json` : 48 641 octets, 285 espèces, **identique** octet pour octet à `GeoMys-IOS/Resources/nidification_oiseaux.json` → `composeResources/files/`, lu par `Res.readBytes()`. Ce n'est plus synchrone : le chargeur `NidificationOiseaux` (Gson) devra devenir `suspend` ou être préchargé.

## 5. Manifeste et build

**Permissions → iOS**

| Android | Usage réel trouvé | Équivalent iOS | Projet iOS actuel |
|---|---|---|---|
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | trace GPS, position des observations | `NSLocationWhenInUseUsageDescription` (+ `NSLocationAlwaysAndWhenInUseUsageDescription`) ; si l'utilisateur choisit « position approximative » : `NSLocationTemporaryUsageDescriptionDictionary` | 2 clés présentes (`INFOPLIST_KEY_*` du pbxproj) |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` | `LocationForegroundService` (type location) | `UIBackgroundModes = location` + `allowsBackgroundLocationUpdates` + `showsBackgroundLocationIndicator` | présent (Info.plist, `LocationTracker.swift` L123-133) |
| `FOREGROUND_SERVICE_DATA_SYNC` | `SyncForegroundService` (« Charger les données ») | **aucun équivalent** : `beginBackgroundTask` (≈30 s), `BGProcessingTask` (`UIBackgroundModes = processing` + `BGTaskSchedulerPermittedIdentifiers`), `URLSession` d'arrière-plan (téléchargements seulement) | non fait (FIXME dans `SyncRunner.swift` L8-11) |
| `POST_NOTIFICATIONS` | notifications des 2 services (demandée dans Paramètres) | `UNUserNotificationCenter.requestAuthorization` (sans clé Info.plist), inutile sans service | — |
| `INTERNET` | tout le réseau | implicite (ATS s'applique) | — |
| `ACCESS_NETWORK_STATE` | uniquement `SpeechToTextHelper` (reconnaissance vocale en ligne ou hors ligne) | rien (`NWPathMonitor` ne demande pas d'autorisation) | — |
| `RECORD_AUDIO` | dictée vocale (`SpeechRecognizer`) | `NSMicrophoneUsageDescription` + `NSSpeechRecognitionUsageDescription` | présentes |
| `VIBRATE` | `SaisieRapideFragment.vibrer()` L949-957 (Vibrator, 60 ms) | aucune permission (`UIImpactFeedbackGenerator`). En Compose : `LocalHapticFeedback`, qui sur Android se passe de VIBRATE mais respecte le réglage système | utilisé (TraceView, SaisieRapideView) |
| `WRITE_EXTERNAL_STORAGE` (maxSdk 28) | **aucun usage trouvé** ; présent depuis le commit initial (gabarit osmdroid probable, non vérifié) | — | à supprimer |
| `REQUEST_INSTALL_PACKAGES` (manifeste github seul) | MAJ intégrée | aucun (App Store / TestFlight) | — |
| (pas de `CAMERA`) | appareil photo système via `TakePicture` | `NSCameraUsageDescription` **obligatoire** | présente |
| (pas de permission médias) | sélecteur système (GetContent / picker) | PHPicker : aucune ; `NSPhotoLibraryUsageDescription` / `NSPhotoLibraryAddUsageDescription` si accès photothèque ou enregistrement | 2 clés présentes |

**`<application>`, composants**
- `allowBackup` + `fullBackupContent` + `dataExtractionRules` excluent `gn_secure*.xml`, `taxref`, `monitoring`, `mbtiles` et `medias` → androidApp. iOS : Documents et Application Support partent dans la sauvegarde iCloud par défaut ; poser `isExcludedFromBackup` (aujourd'hui fait pour les seuls MBTiles) ou utiliser Caches/ ; mot de passe dans le Keychain avec `…ThisDeviceOnly` pour reproduire l'exclusion.
- `largeHeap="true"` (ajouté en v1.1.2 pour les gros référentiels TaxRef) : **aucun équivalent iOS** ; le budget mémoire des caches TaxRef en mémoire sous Kotlin/Native sera à surveiller.
- `networkSecurityConfig` : `cleartextTrafficPermitted="true"` partout (http accepté, avertissement non bloquant dans Paramètres). iOS : ATS sans dérogation dans le projet (http refusé, avertissement dédié). **Décision à prendre** : https obligatoire sur iOS (statu quo), `NSAllowsLocalNetworking` (LAN), `NSExceptionDomains` (liste blanche) ou `NSAllowsArbitraryLoads` (justification à fournir à l'App Review).
- `MainActivity` : `configChanges` (orientation|screenSize|…) et `adjustResize` → à garder dans androidApp (`imePadding` en edge-to-edge). `supportsRtl`, icônes et label → androidApp.
- 2 services → androidMain/androidApp.
- `FileProvider` `${applicationId}.provider`, `cache-path "."`, utilisé par PhotoCapture (captures), `SortieDetailFragment.exporterGpx` (partage GPX) et MiseAJour (APK) → androidApp. iOS n'en a pas besoin (`UIActivityViewController` avec URL de fichier ; `UIImagePickerController`).

**Flavors** (dimension `distribution`)
- `github` : `applicationId = fr.ariegenature.geomys` ; 3 fichiers Kotlin (392 l.) + manifeste (REQUEST_INSTALL_PACKAGES).
- `play` : `fr.ariegenature.public.geomys` ; 1 no-op.
- Même `namespace` ; les deux applis peuvent cohabiter ; aucun passage github → play possible (identités différentes). Le build `debug` garde le même applicationId, seul le label change.
- Côté KMP, les flavors ne peuvent vivre que dans le module **application** (`androidApp/src/github|play`). Avec AGP 9, le plugin KMP ne s'applique plus à un module `com.android.application`, et `com.android.kotlin.multiplatform.library` n'a pas de variantes (à confirmer dans la doc AGP 9 / KMP). Le common expose `MiseAJourIntegree?` ; la route « Mise à jour » est ajoutée au graphe par le seul flavor github.

**Mise à jour intégrée** : **à garder pour Android/github uniquement, hors commonMain.**
- C'est l'unique voie de MAJ du parc github : l'appli 1.4.7 installée interroge `releases/latest` (non pre-release) de `ANA-CEN-Ariege/GeoMys-android` et prend le premier asset `.apk`.
- Pour faire passer ces utilisateurs à GeoMys, il faut, dans ce même dépôt : un tag > 1.4.7, le même applicationId, le même keystore et `versionCode ≥ 197`.
- Côté Play : même applicationId, même clé d'upload, `versionCode > 196`.
- iOS : rien (App Store / TestFlight) ; supprimer la route placeholder.

**BuildConfig** (grep sur tout `src/`) :
- `BuildConfig.DEBUG` : `SpeechToTextHelper.kt:93` et `MonitoringEnvoi.kt:212`, pour filtrer des logs → à remplacer par une sévérité minimale Kermit réglée au démarrage, ou un `expect val estDebug` (iOS : `Platform.isDebugBinary`, expérimental).
- `BuildConfig.VERSION_NAME` : `MiseAJourFragment.kt:89, 102` (github).
- La version de l'accueil passe, elle, par `PackageManager` (AccueilFragment L288-292).
- Ni `FLAVOR`, ni `APPLICATION_ID`, ni `buildConfigField`.
- → un objet `InfosApp` (version, build, debug, canal) via BuildKonfig ou expect/actual, avec une **source de version unique**. Aujourd'hui : Android 1.4.7 / 196 ; iOS `Version.swift` 0.11 (généré par `Scripts/bump-version.sh`), `MARKETING_VERSION` 1.0, bundle `fr.ariegenature.GeoMys-IOS`, cible de déploiement iOS 26.5.

**R8 / ProGuard** (release : `isMinifyEnabled` + `isShrinkResources`)
- `proguard-rules.pro` fait 69 lignes. Seule règle à garder : `-keepattributes SourceFile,LineNumberTable`.
- Tout le reste ne sert que Gson : `Signature`/`*Annotation*`, `TypeToken`, `@SerializedName`, `keep model.**`, 5 classes `network.*`, `SaisieEnAttente(+$*)`, `TaxRefEntry`, `NomValeur`, `TaxrefRestriction`, `NidificationOiseaux$*`, `OccHabDetailsSession`, `StationsServeurCache$*`, règle sur les enums. Avec kotlinx.serialization, ces règles **deviennent inutiles** : sérialiseurs générés à la compilation, règles R8 fournies par la bibliothèque, enums encodés par le nom de leur descripteur.
- Commentaire L22 obsolète (« isMinifyEnabled=false aujourd'hui »).
- `isShrinkResources` repose sur « aucun `getIdentifier` » : à revérifier avec Compose resources.
- iOS : sans objet.

**Outillage et dépendances**
- AGP 9.1.1, Gradle 9.3.1, Kotlin embarqué par AGP (aucun plugin Kotlin appliqué, cf. `build.gradle.kts` racine), compileSdk 37, targetSdk 36, minSdk 24, Java 11, viewBinding + buildConfig, configuration-cache.
- CI `tests.yml` : `testGithubDebugUnitTest`, `lintGithubDebug`, `assembleGithubRelease` + `bundlePlayRelease`. Les releases sont construites en local (`.aab` 175 à 196 à la racine).
- Correspondances :
  - core-ktx/appcompat/material → Compose Material3 MP ;
  - osmdroid → maplibre-compose ;
  - coroutines-android → coroutines-core ;
  - lifecycle viewmodel/runtime/livedata → lifecycle-viewmodel(-compose) MP + StateFlow ;
  - gson → kotlinx-serialization-json ;
  - navigation-fragment/ui → navigation-compose MP (routes `@Serializable`, comme `Route.swift`) ;
  - fragment-ktx/activity-ktx → activity-compose (androidApp seul).
- Tests : 99 fichiers, dont 65 sous Robolectric (`org.json` réel, MockWebServer) → kotlin.test en commonTest + Ktor MockEngine. **7 fichiers de tests « de garde » lisent du code source** (`File("src/main/…")`), dont 2 tests de `ListeIncompleteTest` sur ConfigGeoNatureFragment et 1 test de `DefautsPariteLot2Test` sur `nav_graph.xml` + AccueilFragment : à réécrire en tests de comportement, puisque chemins et noms d'actions disparaissent.

## 6. Points durs

1. **Décision produit divergente entre plateformes** : Android a supprimé la passerelle carte multi → saisie mono le 2026-10-17 (garde `aucune_passerelle_entre_saisie_mono_et_multi`), mais iOS (64d303b) la propose encore (appui long sur ➕, `TraceView.swift` L419-427, `Route.saisieRapide(sortieId:)`). À trancher avant de porter ; recommandation : suivre Android.
2. **Paramètres = machine d'états implicite** (visibilités modifiées depuis une quinzaine d'endroits). Il faut un `ConfigUiState` explicite, sans perdre des règles nées d'audits terrain : purge des sélections fantômes, identité ≠ mot de passe, liste imposée, sélection automatique, ordre reset/restauration, blocage sans porte dérobée.
3. **Blocage de sortie** : `BackHandler` sur Android (Compose MP : API multiplateforme expérimentale, à vérifier) et neutralisation du geste de retour iOS de la navigation Compose (iOS actuel : `navigationBarBackButtonHidden`).
4. **Synchro de fond sans équivalent iOS** (FGS dataSync) : le « Charger les données » doit tolérer la suspension sur iOS.
5. **GPS piloté par la navigation** (7 destinations ★, « vraie transition » seulement) : à reproduire sur le back stack Compose avec des routes typées. La règle iOS actuelle est plus étroite (`.trace` seulement).
6. **Contrats de navigation lâches** : clés non déclarées, résultats par `savedStateHandle`, VMs à portée Activity. Passer à des routes typées oblige à tout déclarer. Il faut aussi choisir la portée des VMs (racine ou graphe imbriqué « saisie »). iOS a préféré des `fullScreenCover` + Binding pour Caractérisation, Dénombrement, Observations et Détails.
7. **Médias** : URI absolue `file://` côté Android, référence relative côté iOS (migration de données) ; décodage, rotation et compression sans API commune (expect/actual ; sur iOS, réduire via ImageIO plutôt que décoder l'image entière).
8. **Chaînes** : ≈1 200 libellés codés en dur (layouts + Kotlin) contre 25 ressources : choisir une politique (externaliser ou laisser le français en dur dans le code commun).
9. **Ressources graphiques** : 10 vectoriels à normaliser, 19 shapes/selectors à recoder, couleurs en dur dispersées (56 + 10 + 12), thème sombre forcé à reproduire sur les deux plateformes.
10. **http vs ATS** : à trancher côté produit ; le message d'avertissement de Paramètres devient dépendant de la plateforme.
11. **Listes déroulantes longues** (observateurs, JDD) : `DropdownMenu`/`ExposedDropdownMenu` de Compose ne sont pas paresseux → composant de recherche dédié (iOS : `AutocompleteField.swift`, 249 l.). Le filtrage est à unifier : préfixe sensible aux accents (`ArrayAdapter`) dans Paramètres, « contient » insensible aux accents (`AdaptateurAutocomplete`) dans Détails du relevé.
12. **Structure Gradle** : flavors github/play + AGP 9 + KMP → module `androidApp` distinct ; destination « Mise à jour » propre au flavor.
13. **Migration en place** : applicationIds, signatures, `versionCode ≥ 197`, dépôt GitHub des releases. Les préférences existantes (`gn_config`, `GeoMys_prefs`, `gn_secure_v2`) et les stores JSON doivent être relus tels quels (multiplatform-settings sait encapsuler les mêmes SharedPreferences / NSUserDefaults). Bundle id iOS à conserver s'il a déjà été distribué (non vérifié).
14. **Pas de Toast en Compose MP** : il faut une zone de messages commune (iOS : `Toast.shared`, `Theme/ComposantsAndroid.swift`).
15. **Mémoire iOS** sans `largeHeap` pour les caches TaxRef.

## Bilan chiffré (périmètre : 15 fichiers Kotlin)

| Catégorie (fichier entier) | Fichiers | Lignes | Lignes de code |
|---|---|---|---|
| COMMUN | 0 | 0 | 0 |
| COMMUN-ADAPTÉ | 0 | 0 | 0 |
| MIXTE | 3 (MainActivity, ConfigGeoNatureFragment, FilAriane) | 1 391 | 909 |
| PLATEFORME | 4 (MediaImport, PhotoCapture, github MiseAJour, github MiseAJourAccueil) | 381 | 218 |
| UI | 5 (Accueil M, TaxonsListe S, Couleurs S, AlerteStockage S, github MiseAJourFragment S) | 841 | 537 |
| ABANDON | 3 (Navigation, Insets, play MiseAJourAccueil) | 175 | 82 |
| **Total** | **15** | **2 788** | **1 746** |

Ventilation par destination, en lignes de code (estimation ±20 %) :

| Destination | ≈ lignes de code | Principaux contributeurs |
|---|---|---|
| commonMain — logique (VM, domaine) | 515 | Config 330, Accueil 55, FilAriane 45, TaxonsListe 35, MainActivity 25, MediaImport 25 |
| commonMain — UI Compose à réécrire (en lignes View actuelles) | 575 | Config 290, Accueil 120, FilAriane 76, TaxonsListe 55, Couleurs 22, AlerteStockage 11 |
| androidMain / iosMain / androidApp — plateforme | 125 | MediaImport 62, MainActivity 27 (canaux de notification), Config 15, Accueil 12, PhotoCapture 8 |
| androidApp/src/github seul (MAJ intégrée) | 205 | MiseAJourFragment 120, MiseAJour 70, MiseAJourAccueil 15 |
| Abandonné (imports, boilerplate de binding, insets, garde double-tap, code mort, no-op play) | 325 | ≈160 lignes `package`/`import` (tous fichiers confondus) ; Insets 61, `garderSousFil` 20, Navigation 15 et play 6 (imports compris, donc en partie recoupés) ; le reste = binding et cycle de vie des fragments |

Ressources :

| Ressource | Quantité | Portage |
|---|---|---|
| Layouts | 40 (5 407 l.) | réécriture Compose |
| Vectoriels | 40 | 30 tels quels + 10 à normaliser |
| Shapes, selectors, inset | 19 | recodés en Compose (1 reste Android) |
| Bitmaps | 5 PNG (logo) + 15 WebP (lanceur) | logo → composeResources ; lanceur → androidApp |
| Chaînes en ressource | 25 (+1 debug) | → `composeResources/values` |
| Chaînes en dur | ≈190 (layouts) + ≈1 030 (Kotlin) | à externaliser ou à garder |
| Couleurs et thème | 20 couleurs + 2 selectors, 2 thèmes (seul le nuit sert), 2 styles | → `GeoMysTheme` |
| Navigation | 25 destinations, 46 actions (dont 1 morte), 20 arguments | → routes `@Serializable` + NavHost |
| `xml/` | 4 | Android seul |
| Assets | 1 JSON | → `composeResources/files` (identique à l'iOS) |

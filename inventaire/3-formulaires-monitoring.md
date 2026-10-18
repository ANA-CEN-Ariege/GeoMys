# 3. Moteur de formulaires, champs de saisie et écrans Monitoring (`monitoring/form/`, `ui/saisie/`)

> Détail de l'inventaire GeoMys-Common. La synthèse, les chiffres globaux et les décisions à prendre sont dans [`../INVENTAIRE.md`](../INVENTAIRE.md).
> Source analysée en lecture seule : GeoMys-Android `cdd5196` (v1.4.7, 18 octobre 2026). Les équivalents iOS ont été cherchés dans GeoMys-IOS `64d303b`.
> La colonne « Cible » donne une destination indicative ; la structure proposée pour GeoMys-Common est décrite au § 9 de la synthèse.

## 1. Inventaire fichier par fichier

Chemins relatifs à `app/src/main/java/fr/ariegenature/geomys/` (GeoMys-Android `cdd5196`). « Lignes » = colonne `loc` de `releve-automatique.tsv` ; « l. » = numéros de ligne ; iOS = GeoMys-IOS `64d303b`.

| Fichier | Lignes | Rôle (1 phrase) | Catégorie | À remplacer / dépendances plateforme (précis) | Cible dans GeoMys-Common | Remarques |
|---|---|---|---|---|---|---|
| `monitoring/form/ChangeRules.kt` | 254 | Parse le script JS `change` du schéma (`const`, `if`, ternaires, `patchValue`) en instructions ordonnées, puis les évalue en patchs de valeurs. | COMMUN | `RegexOption.DOT_MATCHES_ALL` (l.69) n'existe pas dans l'expect commun (seuls IGNORE_CASE et MULTILINE) → préfixe inline `(?s)` ou `[\s\S]`. Le reste (Regex, String, collections) est de la stdlib commune. | commonMain › `monitoring/form` | ChangeRulesTest (227 l., JUnit pur) → commonTest. iOS : `UI/Monitoring/ChangeRules.swift` (399 l.), redondant une fois le commun en place. |
| `monitoring/form/EditableField.kt` | 123 | Modèle du formulaire dynamique : `enum ViewType` (13 types), `PropertyValue` (value, label, cdNomenclature), `EditableField` (métadonnées de schéma et valeur). | COMMUN | Aucune (seuls les KDoc citent des widgets Android). | commonMain › `monitoring/form` | `var value: Any?` dans une data class : une mutation n'est pas observée par Compose. Ne garder `value` que comme valeur initiale ; la valeur courante vit dans l'état du formulaire. |
| `monitoring/form/ExpressionScanner.kt` | 93 | Découpe et recherche d'opérateurs « niveau 0 » (hors parenthèses, crochets, accolades, quotes), partagées par HiddenExpr et ChangeRules. | COMMUN | Aucune. | commonMain › `monitoring/form` (internal) | iOS : `enum ExpressionScanner` dans HiddenExpr.swift:305. |
| `monitoring/form/FormulaireRenderer.kt` | 1395 | Construit en code les Views du formulaire monitoring, stocke l'état dans ces vues (tags), applique hidden, required dynamique, change et min/max, puis relit des valeurs typées. | MIXTE (XL) | Context utilisé pour créer les vues, la densité (l.60), `getString(R.string.taxref_*)` (l.922-926, 1049-1053), les dialogues, l'ArrayAdapter et le Toast : aucun usage données/fichiers. Views : LinearLayout, EditText + InputType, Spinner + ArrayAdapter, RadioGroup + MaterialRadioButton, CheckBox/MaterialCheckBox, AutoCompleteTextView, MaterialButton. `DatePickerDialog`/`TimePickerDialog` (l.779, 822, 868-878), `AlertDialog` multi-choix (l.1148), Toast (l.1342), `MaterialColors` + attributs de thème (l.63-77), `R.drawable.bg_carte_contour` (shape XML, non pris en charge par Compose resources → `Modifier.border`), `android.util.Log` (l.1028) → Kermit, `SimpleDateFormat`/`Calendar`/`Locale` (l.745-788, 820-821, 838-880) → kotlinx-datetime, `"%02d:%02d".format` (l.812, 823) → `padStart(2, '0')`, `java.io.File(android.net.Uri.parse(uri).path).name` (l.1312) → `substringAfterLast('/')`. Les coroutines (`launch`, `delay`, `Dispatchers.Default`) passent en commun. Dépend de TaxRefCache, SyncRunner.actif et createAutocompleteAdapter. | état et règles → commonMain › `monitoring/form/FormulaireEtat` ; widgets → Compose commonMain › `ui/monitoring/formulaire` ; sélection de médias → interface plateforme | ≈ 485 l. de logique (état 193, règles 182, taxon 110) contre ≈ 840 l. de widgets (§2.1, §3). Le test Robolectric FormulaireRendererTest (383 l.) est à réécrire contre l'état commun. iOS : FormulaireRenderer.swift (`FormulaireEtat`:37, `FormulaireRendererView`:505, `WidgetTaxonView`:828). |
| `monitoring/form/HiddenExpr.kt` | 244 | Évalue les expressions `hidden`/`display`/`required` (lambdas JS normalisées en `${champ}`) : comparaisons, conjonctions et disjonctions, ternaires, `includes`, `meta.nomenclatures[…]`. | COMMUN-ADAPTÉ | `android.util.Log.w` (l.59) → Kermit ou Napier ; `RegexOption.DOT_MATCHES_ALL` (l.78, 82, 87) → `(?s)`. | commonMain › `monitoring/form` | `normaliser` recompile environ 8 Regex à chaque appel (l.76-110), et il est appelé pour chaque champ à chaque changement → précompiler (val) ou mémoriser l'expression normalisée ; performance sur Kotlin/Native à mesurer. HiddenExprTest : 328 l., JUnit pur. iOS : HiddenExpr.swift (390 l.). |
| `monitoring/form/ValidationExpr.kt` | 106 | Résout une borne min/max (littéral ou `value.X`) et qualifie la violation (TROP_PETIT, TROP_GRAND). | COMMUN-ADAPTÉ | `android.util.Log.w` (l.66) → Kermit. | commonMain › `monitoring/form` | ValidationExprTest : 135 l. iOS : ValidationExpr.swift (129 l.). |
| `monitoring/form/WidgetMapping.kt` | 196 | Fait correspondre `type_widget` et ViewType (taxon détecté par 4 signaux), exclut les champs techniques, ordonne selon display_properties et place les médias en fin (`construireFormulaire`). | COMMUN | Aucune. Dépend des data classes `MonitoringApi.MonitoringPropertySchema` et `MonitoringSchemaObjet` (pures, network/MonitoringApi.kt:53-200), à placer en commun dans un autre périmètre. | commonMain › `monitoring/form` | WidgetMappingTest : 124 l. iOS : MonitoringSchema.swift:703 (`mapperViewType`) et FormulaireRenderer.swift:162-200 (exclusions). |
| `ui/saisie/AdditionalFieldsRenderer.kt` | 340 | Rend et relit les champs additionnels Occtax (`gn_commons.t_additional_fields`), désérialise leur cache, applique les règles « requis » et les valeurs par défaut. | MIXTE (M) | Context utilisé pour les vues, `getColor(android.R.color.darker_gray)` (l.201, 332) et la densité (l.338) : aucun accès données. Gson + TypeToken (l.46-54) → kotlinx.serialization (`@Serializable AdditionalFieldDef`) ; `org.json.JSONArray` (l.168, 181) → `JsonArray` / `Json.parseToJsonElement` ; Spinner/ArrayAdapter, RadioButton, CheckBox, TextInputLayout/TextInputEditText, InputType, `R.id.tag_field_codes`. Dépend de DateHeureDefaut (util), AdditionalFieldDef et WidgetType (network). | logique → commonMain › `saisie/ChampsAdditionnels` ; rendu → Compose › `ui/saisie` | ≈ 110 l. de logique (§2.5). Utilisé par 8 écrans Occtax/OccHab hors périmètre. iOS : UI/Saisie/AdditionalFieldsRenderer.swift (316 l.). |
| `ui/saisie/ChampsTaxon.kt` | 57 | Calcule (groupes group2_inpn, regno) d'un groupe taxonomique pour filtrer les valeurs de nomenclature. | COMMUN | Aucune. Dépend de `NomenclatureCache` (store : `groupesBotaniquesConnus()`, `GROUPES_FONGE`, `regno()`) et de `model.Taxon`. | commonMain › `saisie` | ChampsTaxonTest : 56 l. iOS : `groupesEtRegnoPourObs` (CaracterisationView.swift:107). |
| `ui/saisie/OcctaxFieldsRenderer.kt` | 193 | Spinners des nomenclatures standard Occtax : valeurs filtrées par groupe et règne (repli sur le registre), dédoublonnage, tri français, défaut serveur, lecture seule, champs repliés sous « Plus de champs ». | MIXTE (S) | Context utilisé pour les vues, les couleurs `android.R.color.*` (l.78, 123) et la densité. `java.text.Collator.getInstance(Locale.FRENCH)` en force PRIMARY (l.179-180) n'a pas d'équivalent commun → expect/actual (`Collator` sur Android, `NSString.compare(_:options: [.caseInsensitive, .diacriticInsensitive], locale:)` sur iOS) ou tri sur clé normalisée. Spinner/ArrayAdapter, `R.id.tag_field_codes`. | logique → commonMain › `saisie/ChampsOcctax` ; rendu → Compose | ≈ 55 l. de logique : options (l.127-133), `dedupParLabel` (l.165-176), `trierAlphabetique` (l.184-189), défaut (l.154), séparation des champs repliés (l.72-73). iOS : code inline dans CaracterisationView.swift (repli l.189-193). |
| `ui/saisie/PreferencesSaisie.kt` | 52 | Mémorise le dernier groupe taxonomique choisi et l'état du switch « noms scientifiques ». | COMMUN-ADAPTÉ | Context utilisé uniquement pour `getSharedPreferences("GeoMys_prefs")` (données) → multiplatform-settings avec un `Settings` injecté : Android `SharedPreferencesSettings(getSharedPreferences("GeoMys_prefs", MODE_PRIVATE))`, iOS `NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)`. | commonMain › `saisie/PreferencesSaisie` | multiplatform-settings est préférable à DataStore ici : il relit le même fichier sans migration. Clés et compatibilité : §5. |
| `ui/saisie/SpeciesAutocomplete.kt` | 132 | Deux ArrayAdapters d'autocomplétion d'espèces (objets `SuggestionTaxon` ou chaînes) : préfixe puis « contient », insensibles aux accents et à la casse, avec une ligne secondaire en italique. | MIXTE (S) | Context utilisé pour l'ArrayAdapter ; `Filter` (filtrage sur un thread de fond), `R.layout.item_suggestion_taxon`, `android.R.layout.simple_dropdown_item_1line`. | filtre → commonMain › `saisie/Suggestions` (≈ 25 l., dupliqué 2 fois l.68-80 et 108-120) ; liste → Compose | En Compose, le filtrage (listes de 15 à 50 k noms) doit tourner sur `Dispatchers.Default` avec debounce et un plafond d'éléments affichés. iOS : UI/AutocompleteField.swift (249 l., même algorithme l.183-203). |
| `ui/saisie/SpeechToTextHelper.kt` | 168 | Dictée vocale fr-FR dans le champ espèce : résultats partiels poussés dans le champ, 5 hypothèses finales, bascule unique en ligne/hors-ligne sur erreur réseau. | PLATEFORME | Android : `SpeechRecognizer` + `RecognitionListener`, `RecognizerIntent` (EXTRA_LANGUAGE "fr-FR", MAX_RESULTS 5, PARTIAL_RESULTS, PREFER_OFFLINE, LANGUAGE_MODEL_FREE_FORM ou WEB_SEARCH), permission RECORD_AUDIO (`ContextCompat.checkSelfPermission` + `ActivityResultLauncher`), `ConnectivityManager`/`NetworkCapabilities`, Toast (l.61, 126), `TextInputLayout.setEndIconDrawable`, `BuildConfig.DEBUG` + Log (l.93-95). Context utilisé pour le service système, la permission et le Toast. Équivalent iOS : `SFSpeechRecognizer(locale: fr-FR)` + `AVAudioEngine` + `SFSpeechAudioBufferRecognitionRequest` (`shouldReportPartialResults`, `requiresOnDeviceRecognition`), autorisations `SFSpeechRecognizer.requestAuthorization` et `AVAudioApplication.requestRecordPermission`. | interface commune `DicteeVocale` (StateFlow : écoute, partiel, hypothèses, erreur) + implémentations androidMain/iosMain | Fichier iOS existant : **GeoMys-IOS/UI/Saisie/DicteeVocale.swift** (250 l. ; l.40-41, 77-92, 117, 151-160). Il applique la même politique de repli (`surLAppareil`, `retenterSurLAppareil`) et ajoute un vocabulaire contextuel (l.196). La politique de repli et les messages d'erreur (≈ 30 l.) peuvent remonter en commun. Utilisé seulement par SaisieRapide et SaisieObservation (Occtax), pas par le formulaire monitoring. |
| `ui/saisie/TaxRefLookup.kt` | 177 | `TaxRefLookupController` : recherche TaxRef debouncée à 500 ms, statut (trouvé, non trouvé, pas de données), choix figé, relance au changement de groupe. | MIXTE (S) | `LifecycleCoroutineScope` → `CoroutineScope` (viewModelScope) ; visibilités de `ProgressBar`/`TextView`, `ContextCompat.getColor(holo_green_dark, holo_orange_dark)`, `R.string.taxref_*` (Context utilisé pour les ressources UI). Dépend de TaxRefService.rechercher, TaxRefStatut et SyncRunner.actif. | machine d'états → commonMain › `saisie/RechercheTaxRef` (`StateFlow<TaxRefStatut?>` + `enCours`) ; statut → composable | ≈ 100 l. de logique (l.39-140, 173-176) / ≈ 33 l. d'UI (`updateUI`, l.142-171). Aucun équivalent iOS dédié trouvé par grep (logique probablement inline dans SaisieRapideView/LigneEspeceView, non vérifié). |
| `ui/saisie/TaxonPalette.kt` | 79 | Couleur et icône canoniques par groupe taxonomique ; masque les boutons des groupes sans taxon chargé. | UI (S) | `ContextCompat.getColor(R.color.*)` → `Color` du thème Compose ; `R.drawable.*` (10 vecteurs XML) → `Res.drawable.*` (Compose resources lit les vector drawables XML) ; `MaterialButton.visibility`. | Compose commonMain › `ui/saisie/TaxonPalette` ; filtre → commun | Logique : `groupesNonVides(idListe)` (≈ 10 l., l.68-76, via TaxRefCache.indexParTaxon). Le KDoc annonce « 9 taxons », mais l'enum a 10 valeurs (MOLLUSQUE et INVERTEBRES partagent la couleur). iOS : TaxonPalette.swift (`TaxonIcon`:110, `TaxonPaletteView`:134). |
| `ui/saisie/TaxonSelector.kt` | 71 | Rangée de boutons-icônes pour sélectionner le groupe taxonomique, avec surbrillance du choix. | UI (S) | `MaterialButton`, `ColorStateList`, `ContextCompat.getColorStateList` (Context utilisé pour les ressources). | Compose (Row de boutons ou de chips) ; `taxon` sélectionné dans le VM | iOS : `TaxonPaletteView` (TaxonPalette.swift:134). |
| `ui/SuivisFragment.kt` | 209 | Liste des protocoles monitoring (picto, description, boutons détail et carte) avec un bouton « Données en attente d'envoi (n) ». | UI (S) | Fragment + ViewBinding, `R.layout.item_suivi_module` gonflé pour chaque ligne, Navigation (`naviguerSur` + `bundleOf`, 3 actions), `lifecycleScope`, `BitmapFactory` + `inSampleSize` (l.183-188), `Dispatchers.IO`, Log (l.74). Context : `GeoNatureConfig(requireContext())` = préférences `gn_config` (données, l.55) ; LayoutInflater et densité (UI). | `SuivisScreen` (Compose) + `SuivisViewModel` commun ; image : Coil 3 ou `decodeToImageBitmap` | ≈ 40 l. de logique : chargement et messages d'erreur ou de liste vide (l.84-110), compteur de l'outbox (l.71-82), repli emoji (l.131). Dépend de PictoCache.fichierOuTelecharger, GeoNatureAuth.sessionEnCache et OutboxMonitoring. iOS : `SuivisView` (MonitoringViews.swift:23). |
| `ui/SuiviDetailFragment.kt` | 420 | Détail d'un protocole : types « macro » (sites, groupes de sites), lignes d'enfants avec sous-titre résolu, boutons fiche, carte et nouvelle saisie. | UI (M) | Fragment + ViewBinding ; vues construites en code (LinearLayout, TextView, ImageButton) ; attribut `selectableItemBackgroundBorderless` (l.257-259) ; `R.drawable.ic_info`/`ic_eye`/`ic_add` (ic_info et ic_eye référencent `?attr/colorControlNormal`, à retirer pour Compose resources ou à remplacer par des Material Icons) ; `R.color.jaune_clair` ; Navigation (3 actions + `encoderFil`) ; `lifecycleScope` + `async`. Context : GeoNatureConfig (données, l.115) ; le reste est de l'UI. | `SuiviDetailScreen` + VM commun | ≈ 140 l. de logique (§2.4). Code dupliqué avec FicheObjetFragment (`sousTitrePourEnfant`, `labelTypeParDefaut`). iOS : `SuiviDetailView` (MonitoringViews.swift:234). |
| `ui/FicheObjetFragment.kt` | 729 | Fiche générique d'un objet monitoring (propriétés, enfants serveur et saisies locales de l'outbox), avec drill-down récursif et boutons +, carte et détail. | UI (L) | Comme SuiviDetail, plus `SimpleDateFormat("dd/MM HH:mm")`/`java.util.Date` (l.621, 629, 657) → kotlinx-datetime ; `MaterialColors` colorSecondary (l.633-635), `cadreColore`/`couleurErreur` (ui/Couleurs.kt) ; Log (l.282, 580) ; rafraîchissement dans `onResume` par signature de l'outbox. Context : GeoNatureConfig (données, l.132) ; le reste est de l'UI. | `FicheObjetScreen` + `FicheObjetViewModel` commun | ≈ 325 l. de logique (§2.3). Ce fichier ne construit aucun payload et ne fait aucune validation. iOS : `FicheObjetView` (MonitoringViews.swift:427) et `MonitoringUtils` (:868). |
| `ui/NouvelleVisiteFragment.kt` | 1363 | Création et édition d'une saisie monitoring : choix du type, préparation des champs (datalists, défauts, liste taxonomique), rendu, enregistrement dans l'outbox, enchaînement des saisies, photos, garde de sortie. | MIXTE (XL) | `registerForActivityResult` : `GetMultipleContents` (l.106-125) et `TakePicture` + `FLAG_GRANT_WRITE_URI_PERMISSION` (l.135-181) ; `PhotoCapture` (cacheDir + FileProvider) ; `MediaImport` (filesDir/medias, recompression BitmapFactory/EXIF) ; `onBackPressedDispatcher` (l.294) ; `onSaveInstanceState`/Bundle (l.247-256, 1310-1332) ; `arguments` modifiés en place (l.233-237, 655) ; Navigation (2 actions + `navigateUp`) ; 6 `AlertDialog` et 6 Toast ; `org.json.JSONObject`/`JSONArray` (l.839-844, 905-907, 1136-1146, 1189-1196, 1324-1326) → `JsonObject`/`JsonArray` ; Gson + TypeToken (l.819-821) → kotlinx ; `java.util.UUID.randomUUID` (l.933, 1016) → `kotlin.uuid.Uuid.random()` (expérimental, @OptIn) ; `Dispatchers.IO` ; Log (l.1127). Context : GeoNatureConfig (préférences, l.524), applicationContext pour MediaImport (fichiers + ContentResolver, l.114, 152), PhotoCapture (cacheDir + FileProvider, l.198), dialogues et Toast. | `NouvelleSaisieViewModel` commun + écran Compose + interfaces plateforme `SelecteurPhotos`/`ImportMedia` | ≈ 865 l. de logique, ≈ 240 d'UI, ≈ 140 de plateforme et ≈ 35 de mode démo à abandonner (§2.2). iOS : UI/Monitoring/NouvelleVisiteView.swift (680 l.) et UI/Saisie/MediaPickerView.swift (PhotosPicker + `CameraPicker` sur UIImagePickerController, l.210). |
| `ui/PictoMonitoring.kt` | 78 | Distingue un picto image (URL ou chemin) d'un code FontAwesome et associe les codes FA à des emojis. | COMMUN | Aucune. | commonMain › `monitoring` | PictoMonitoringTest : 68 l. iOS : `enum PictoMonitoring` (Store/PictoCache.swift:18). |

Aucun usage dans le périmètre de : osmdroid/GeoPoint, HttpURLConnection/java.net, synchronized/@Volatile/Atomic*/ConcurrentHashMap, Base64, Math.*, LiveData, ViewModel androidx.

## 2. Logique à extraire (fichiers MIXTE ou UI de plus de 300 lignes)

### 2.1 FormulaireRenderer.kt (1395 l.) : découpe ligne à ligne

| Lignes | Bloc | Nature | Devenir |
|---|---|---|---|
| 1-41 | licence, imports | — | — |
| 42-59 | classe, constructeur (`Context`, `ViewGroup` parent, `CoroutineScope`) | widgets | `FormulaireEtat(champs, scope)` + composable |
| 60-77 | couleurs résolues depuis le thème (onSurface, colorError, onSurfaceVariant) | widgets | `MaterialTheme.colorScheme` |
| 78-96 | registres code → vue, wrapper, erreur, libellé, statut taxon ; `fieldsParCode` | widgets (+ état) | disparaissent : l'état devient une `Map<code, valeur>` |
| 97-112 | callbacks `onChangement` et `onChoixMedia` | I/O (glue) | Flow observé par l'écran ; sélection de médias via interface plateforme |
| 113-139 | règles parsées, garde `appliquantChange`, drapeau dirty (`valeursAuRendu`, `dernieresValeursAuto`, `champsDirtyInitiaux`) | état, règles | FormulaireEtat |
| 141-172 | `rendre()` : construction des lignes, photo « au rendu », passes initiales | widgets ≈ 20 / état ≈ 12 | initialisation de FormulaireEtat + composable |
| 174-211 | `setReglesChange`, `appliquerChangeRules` | règles | FormulaireEtat |
| 213-280 | `appliquerValeur` : écrit la valeur dans le widget selon le ViewType | widgets ≈ 48 / état ≈ 20 (coercitions booléen et listes) | simple écriture dans l'état |
| 282-298 | `setOnChangement`, `notifierChangement` (visibilité → change → validations → callback) | état (orchestration) | recalcul des dérivés |
| 300-332 | debounce de 220 ms des champs texte, `flushChangementsEnAttente` | état | FormulaireEtat (coroutines communes) |
| 334-354 | `champsObligatoiresManquants` (statique + `required` dynamique, champs masqués exclus) | règles | dérivé `manquants` |
| 356-374 | barres rouges du mode complétion | widgets | `ChampMonitoring` |
| 376-400 | `champsInvalides` (min/max, taxon non résolu) | règles | dérivé `invalides` |
| 402-430 | `appliquerValidations`, `formaterBorne` | règles ≈ 16 / widgets ≈ 13 | dérivé `violations` + texte du message |
| 432-458 | listeners CheckBox, Spinner, TextWatcher | widgets | `onValueChange` |
| 460-483 | visibilité `hidden`, astérisque du `required` dynamique | règles ≈ 16 / widgets ≈ 8 | dérivés `masques` et `requis` |
| 485-505 | `valeursPourExpressions` (clés enrichies `__dirty`, `__cd`) | règles | FormulaireEtat |
| 508-546 | `lireValeursPourEnvoi`, `champsObligatoiresManquantsOuRemplis` | règles (payload) | FormulaireEtat |
| 548-610 | `lireValeurs` : typage par ViewType (contrat des valeurs) | état ≈ 25 / widgets ≈ 38 (lecture des vues) | FormulaireEtat (la valeur y est déjà typée) |
| 612-710 | `creerLigne` : libellé, barre, éditeur, statut taxon, erreur, aide | widgets | `ChampMonitoring` |
| 712-739 | `creerEditText` + défaut « fantôme » des NUMBER | widgets ≈ 20 / état ≈ 8 | `OutlinedTextField` + `onFocusChanged` |
| 741-893 | DATE, TIME, DATETIME, `parseHeure` | widgets ≈ 110 / état ≈ 43 (formats, parse tolérant) | DatePicker/TimePicker + `FormatsDate` commun |
| 895-1108 | TAXON : statut, résolution restreinte à la liste, choix figé, index de suggestions sur `Dispatchers.Default`, refus au blur | règles et état ≈ 110 / widgets ≈ 104 / I/O (TaxRefCache, SyncRunner.actif, Log) | `ChampTaxonEtat` commun + composable d'autocomplétion |
| 1110-1192 | SELECT_MULTIPLE (AlertDialog multi-choix), SELECT (Spinner) | widgets | dialogue à cases à cocher + `ExposedDropdownMenuBox` |
| 1194-1267 | RADIO, CHECKBOX_MULTIPLE | widgets ≈ 68 / état ≈ 6 (disposition, valeurs par défaut) | `RadioButton`, `Checkbox` |
| 1269-1366 | MEDIA : liste de fichiers, ✕, « Ajouter des photos », Toast si le callback manque | widgets ≈ 85 / I/O ≈ 13 (callback du picker, nom de fichier via File/Uri) | composable + interface plateforme |
| 1368-1388 | CHECKBOX booléen | widgets ≈ 13 / état ≈ 8 | `Checkbox` |
| 1391-1394 | `ROUGE_OBLIGATOIRE` | widgets | constante de thème |

Bilan : ≈ 193 l. d'état, ≈ 182 l. de règles et ≈ 110 l. pour le champ taxon, soit **≈ 485 l. vers commonMain** ; **≈ 840 l. de widgets** à réécrire. Les entrées/sorties propres au fichier font environ 20 l., réparties dans les blocs ci-dessus : accès à TaxRefCache (l.912, 947, 962, 998-1004, 1019-1021, 1039), SyncRunner.actif (l.923, 1050), callbacks média (l.105-112, 1341-1353), Log (l.1028). Le fichier ne fait aucun accès direct aux fichiers ni au réseau.

L'état commun doit offrir ce qu'utilise NouvelleVisiteFragment :
- `marquerDirtyInitiaux` et `rendre` (deviennent l'initialisation) ;
- `setReglesChange` ;
- `lireValeurs`, `lireValeursPourEnvoi` ;
- `champsObligatoiresManquants`, `champsInvalides` ;
- `flushChangementsEnAttente` ;
- un flux observable, qui remplace `setOnChangement` ;
- `marquerObligatoiresManquants`, qui devient un booléen `modeCompletion` passé au composable.

### 2.2 NouvelleVisiteFragment.kt (1363 l.) : ≈ 865 l. vers un `NouvelleSaisieViewModel` commun

- **Navigation dans l'arborescence du protocole** :
  - choix du type à créer (`childObjectType` ou heuristique, l.538-546) ;
  - masquage du champ FK du parent (l.569-573) ;
  - type d'enfant « saisie » à enchaîner (l.639-641) ;
  - mode chaîne (l.350-359, 1088-1098) ;
  - enchaînement après création ou édition, retour à la fiche parente (l.969-1007, 1069-1083) ;
  - complétion de la visite parente « à compléter » (l.430-452) ;
  - contexte restitué depuis l'outbox en édition (l.226-237).

  Le VM émet des événements (Enchaîner, RetourFiche, Retour, ProposerComplétion) ; l'UI les traduit en routes.
- **Préparation du formulaire** :
  - `chargerSchemaEtRendre` (l.522-663) ;
  - `enrichirAvecOptions` (l.669-805) : datalists chargées en parallèle, repli hors-ligne sur les valeurs sauvées, option unique dataset/observers auto-sélectionnée et masquée, défauts scalaires ou objets (cd_nomenclature, label_default), date et heure du jour, utilisateur connecté ;
  - cascade de l'id_list_taxonomy (l.586-596, puis `idListeViaDataset`/`idListeViaDatasetLive` l.812-846).
- **Construction des payloads** :
  - valeurs pour l'envoi, extraction des médias, `valeursJson` via `valeurToJson` (l.1189-1197), champs texte libre, champs manquants (l.887-919) ;
  - lien vers le parent (l.908-910, 1017-1032) ;
  - uuid pré-généré (l.932-933, 1015-1016) ;
  - `nomsChampsSchema` (l.645).
- **Brouillons** :
  - ouverture en édition : `chargerValeursPourEdition`, `parseValeursJson`, `typerPourField` (l.1121-1183) ;
  - `mettreAJour` ou `ajouter`, avec contrôle de l'échec d'écriture (l.927-950, 1036-1039) ;
  - saisies « à compléter » (l.919, 944, 1031) ;
  - instantané `valeursApresRendu` pour détecter une saisie non enregistrée (l.650, 302-303, 403) ;
  - sérialisation de l'état pour la restauration (l.1314-1330) : la partie JSON est commune.
- **Validation** :
  - `majEtatBoutonSubmit` (l.478-484) ;
  - `enregistrementIncompletAutorise` et `estObjetDeNiveauVisite` (l.501-502, 1353-1357) ;
  - garde anti double-clic + flush (l.863-871) ;
  - `messageManquants`, `libellesManquants` (l.506-520) ;
  - décisions de sortie (l.298-341, 398-421).
- **Libellés accordés** (fonctions pures) : `titreEdition`, `titreNouvelle`, `typeMasculin`, `labelTypeCap`, `accordE`, `avecArticleType` (l.1221-1271).

Reste :
- UI, ≈ 240 l. : 6 dialogues, 6 Toast, fil d'Ariane, boutons, `ajouterDebug`.
- Plateforme, ≈ 140 l. : photos (l.102-210), restauration (l.242-256, 1306-1332).
- **Abandon**, ≈ 35 l. : le mode démo (`afficherValeursDemo`, `creerChampsDemo`, l.1273-1304, et ses branches l.384-387, 527-557, 874-879). Mieux vaut afficher une erreur explicite qu'un faux formulaire.

### 2.3 FicheObjetFragment.kt (729 l.) : ≈ 325 l. vers un `FicheObjetViewModel` commun

- **Chargement** :
  - objet et schéma chargés en parallèle, avec un `runCatching` par `async` (l.131-150), plus le résolveur de libellés ;
  - cache du dernier rendu (l.112-119) ;
  - rechargement au retour si la signature de l'outbox a changé (l.79-110).
- **Navigation dans l'arborescence** :
  - type de saisie créable par le bouton « + » pour chaque type d'enfant (l.314-324), neutralisé sur une saisie serveur déjà historisée ;
  - en-têtes « Type (n) + », y compris pour les types déclarés sans occurrence (3ᵉ passe, l.483-496) ;
  - cibles fiche, carte et nouvelle saisie avec le fil d'Ariane (l.377-444, 543-555, 650-661). Le parent est désigné par son id serveur ou par `parentUuidLocal`.
- **Brouillons** :
  - saisies locales non envoyées (état ≠ SENT) rattachées à l'objet (`saisiesLocalesAttacheesA`, l.564-585), fusionnées avec les enfants serveur ;
  - saisies locales orphelines groupées par type (l.456-472) ;
  - icône selon l'état et date `dd/MM HH:mm` (l.616-623).
- **Affichage dérivé du schéma** :
  - titre : nameField ou heuristique (l.164-169) ;
  - propriétés affichées : display_properties ou liste noire (l.190-209), avec résolution ID → libellé (l.228-230) ;
  - fonctions `trierChronologique` (l.245-266), `labelChamp` (l.670-686), `formatValeur` (l.689-694), `sousTitrePourEnfant` (l.698-715), `labelTypeParDefaut` (l.721-722).

Sortie : `StateFlow<FicheObjetUi>` (titre, type, propriétés, sections d'enfants avec lignes locales et serveur, et leurs actions). L'UI à réécrire fait ≈ 340 l. : LazyColumn, en-têtes, lignes avec IconButtons, cadre rouge des saisies locales.

### 2.4 SuiviDetailFragment.kt (420 l.) : ≈ 140 l. de logique

- `chargerEtAfficher` (l.110-173) : enfants et schéma en parallèle, résolveur, renommage par nameField, tri, comptes, résumé « Sites : n ».
- `typesAfficherEnListe` (l.180-195), `labelPour` (l.200-209), `sousTitrePourEnfant` (l.214-232), `formatValeurAffichee` (l.235-237), `labelTypeParDefaut` (l.243-244).
- Type de saisie enfant (l.285-292), présence d'une géométrie (l.347), cibles de navigation (l.332-406).

À mutualiser avec FicheObjet dans un `MonitoringAffichage` commun. L'UI fait ≈ 215 l. : `afficherListeSites` (l.246-414).

### 2.5 AdditionalFieldsRenderer.kt (340 l.) : ≈ 110 l. de logique

- `fromJson` (l.49-55), `aDesChampsReleveRequisSansDefaut` (l.61-65), `defautsChampsReleve` (l.71-79).
- Valeur effective au rendu (l.97-101) : saisie, sinon défaut serveur, sinon date ou heure du jour ; la chaîne "null" est traitée comme vide.
- `defautDateHeurePour` (l.109-114).
- Règle des champs obligatoires vides, CHECKBOX exclues (l.135-136).
- Encodage des valeurs (l.141-173) : code selon la position, tableau JSON des cases cochées, "true"/"false".
- `parseCodesCoches` (l.178-184).
- Alignement des codes et des libellés (l.217-218, 234, 261-262, 277).
- Radio dé-sélectionnable (l.236-246) ; checkbox multiple dès qu'il y a plus d'une valeur (l.271).

L'UI fait ≈ 180 l. : `buildWidget` (l.188-336) et les parcours de vues de `collecter`/`valeurChamp`.

## 3. Renderer Compose

**Architecture**
1. **`FormulaireEtat`** (commonMain, sans dépendance à Compose, donc testable en commonTest).
   - Entrées : `List<EditableField>`, règles `change`, champs dirty initiaux, résolveur taxon (interface sur TaxRefCache), `CoroutineScope`.
   - État : `Map<code, valeur>` (source de vérité, remplace les tags), textes saisis dans les champs TAXON, références du drapeau dirty.
   - Dérivés recalculés après chaque modification (debounce de 220 ms pendant la frappe) : `masques`, `requis`, `manquants`, `invalides`, `violations`, `statutsTaxon`.
   - Actions : `saisir(code, valeur)` (saisie utilisateur : dirty, une passe des règles change, recalcul), `flush()`, `lireValeurs()`, `lireValeursPourEnvoi()`.
   - Exposition : `StateFlow<FormulaireUi>`.
2. **`@Composable FormulaireMonitoring(ui, modeCompletion, onSaisie, onAjouterMedias)`** : une `Column(verticalScroll)` de `ChampMonitoring`. Une LazyColumn est possible, mais le focus des TextField recyclés pose problème.
3. **`ChampMonitoring`**, gabarit commun : libellé avec « * » si le champ est requis (statique ou dynamique) ; barre rouge de 3 dp à gauche de l'éditeur (`0xFFD32F2F`) en mode complétion si le champ manque ; éditeur ; ligne de statut taxon ; message min/max ; aide en italique. Le champ n'est pas composé s'il est masqué (`hidden` ou `masque`).

**Les 13 types de champs**

| # | ViewType (`type_widget` serveur) | Composant Compose MP | Valeur dans l'état (contrat de `lireValeurs`) | À reproduire (FormulaireRenderer.kt sauf mention) |
|---|---|---|---|---|
| 1 | TEXT (`text`, `string`, et repli de tout widget inconnu) | `OutlinedTextField` sur une ligne | `String` (`""` si vide) | recalcul des règles debouncé à 220 ms (l.300-320) |
| 2 | TEXTAREA (`textarea`, `observers-text`) | `OutlinedTextField(minLines = 3)` | `String` | l.635-639 |
| 3 | NUMBER (`number`, `integer`, `float`, `decimal`) | `OutlinedTextField` + `KeyboardOptions(Decimal)` si `decimal`, sinon `Number` ; `isError` + `supportingText` | `Int` ; `Double` seulement si `decimal`, sinon tronqué en `Int` ; virgule acceptée ; `null` si non numérique | l.563-564 ; défaut « fantôme » vidé au focus et restauré au blur (l.726-734, via `onFocusChanged`) ; message « Valeur ≥/≤ X attendue » (l.416-421, 429-430) ; signe « − » sur iOS (§4) |
| 4 | DATE (`date`) | champ en lecture seule cliquable + `DatePickerDialog`/`DatePicker` (M3) | `String` `yyyy-M-d` sans zéros (`""` si vide) | affichage `dd/MM/yyyy` ; pré-remplissage tolérant (heure retirée, `yyyy-MM-dd` accepté, l.761-773) ; date du jour par défaut si vide (NouvelleVisiteFragment.kt:777-786) ; conversion des millis UTC du `DatePickerState` |
| 5 | TIME (`time`) | `TimePicker(is24Hour = true)` dans un `AlertDialog` | `String` `HH:MM` | `parseHeure` tolérant (`HH:MM`, `HH:MM:SS`, `10h30`, l.886-893) ; heure courante par défaut |
| 6 | DATETIME (`datetime`, `date-time`, `timestamp`) | `DatePicker` puis `TimePicker` enchaînés | `String` `yyyy-MM-dd HH:mm:ss` (secondes à 0) | affichage `dd/MM/yyyy HH:mm` ; parse avec 'T' ou espace, secondes facultatives (l.852-862) |
| 7 | TAXON (`taxonomy`, `taxon`, `taxon-input`, `taxonomy-input`, `type_util=taxonomy`, nom `cd_nom`, API `allnamebylist/`) | `OutlinedTextField` + liste de suggestions (`ExposedDropdownMenuBox`, ou `Popup` + `LazyColumn`), seuil de 2 caractères, ligne secondaire en italique | `Int` cd_nom ou `null`, plus le texte saisi (pour l'invalidité) | résolution restreinte à la liste du protocole (l.946-947) ; choix figé (l.951, 1060-1081) ; statuts « ✓ nom sci • cd_nom N », « ✗ … », « pas de données » ou « chargement en cours » (l.910-939) ; erreur au blur (l.1099-1106) ; un cd_nom absent du cache s'affiche « cd_nom N » sans perte de valeur (l.966-973) ; jamais de défaut serveur (NouvelleVisiteFragment.kt:752) |
| 8 | SELECT (`select`, `datalist`, `nomenclature`, `observers`, `dataset` non multiples) | `ExposedDropdownMenuBox` en lecture seule, entrée « — choisir — » ; recherche conseillée pour les longues listes | `String` value (`""` si rien) | options `values` (avec `cdNomenclature` exposé en `X__cd`) ; option unique dataset/observers auto-sélectionnée et masquée (NouvelleVisiteFragment.kt:724-732) |
| 9 | RADIO (`radio`) | `RadioButton` + `Text` dans un `Row`/`FlowRow` si au plus 4 options de 15 caractères maximum (40 au total), sinon `Column` ; `Modifier.selectableGroup()` | `String` (`""` si rien) | heuristique l.1199-1201 ; pas de dé-sélection, contrairement aux champs additionnels |
| 10 | SELECT_MULTIPLE (`multiselect`, `multi_select`, `select_multiple`, `select-multiple`, ou select/radio/datalist `multiple`) | champ cliquable (libellés joints par « , » ou « Choisir… ») + `AlertDialog` avec une `LazyColumn` de `Checkbox`, boutons Valider et Annuler | `List<String>`, ou `null` si vide | désactivé avec « (aucune option disponible) » (l.1129-1133) |
| 11 | CHECKBOX (`bool_checkbox`, `bool`, `boolean`) | `Checkbox` (libellé au-dessus) | `true` ou `null` (jamais `false`) | coercition depuis Boolean, Number, `"true"` ou `"1"` (l.1371-1376) |
| 12 | CHECKBOX_MULTIPLE (`checkbox`) | `Column` de `Row(Checkbox, Text)` en ligne | `List<String>` ou `null` | l.1234-1267 |
| 13 | MEDIA (`medias`) | liste des fichiers (nom + `IconButton` ✕) et bouton « Ajouter des photos », qui propose Appareil photo ou Galerie (`ModalBottomSheet` ou `AlertDialog`) | `List<String>` (URI locales), retirée du payload | toujours en fin de formulaire (WidgetMapping.kt:190-194) ; sélection, capture et recompression côté plateforme (§4) ; jamais modifié par une règle `change` (l.252-254) |

**Comportements transverses à reproduire**
- Gabarit : astérisque dynamique (l.473-483), barre rouge (l.658-680), statut taxon (l.682), erreur min/max (l.685-696), aide (l.699-708).
- Champ masqué par `hidden` : sa valeur est conservée mais non envoyée, sauf s'il est requis ou posé par une règle `change` (l.508-535).
- Champ `masque` : jamais affiché, toujours envoyé (EditableField.kt:111-116).
- Widgets `html` ignorés, widgets inconnus rendus en TEXT (WidgetMapping.kt:82, 93) ; champs techniques exclus (WidgetMapping.kt:102-108, 138-146).
- Règles `change` appliquées au rendu, puis recalcul complet (l.176-190). En édition, les champs déjà remplis sont dirty d'emblée (l.127-139).
- Flush obligatoire avant toute décision (l.322-332).
- Bouton d'enregistrement actif si aucun champ n'est invalide et, hors objets de niveau visite, si aucun obligatoire ne manque (NouvelleVisiteFragment.kt:478-484).

**Composants et bibliothèques**
- Material3 de Compose MP : `OutlinedTextField`, `ExposedDropdownMenuBox`/`DropdownMenuItem`, `RadioButton`, `Checkbox`, `AlertDialog`, `DatePicker`/`DatePickerDialog`, `TimePicker` (dans un `AlertDialog`), `ModalBottomSheet`.
- Foundation : `FlowRow`, `LazyColumn`.
- Compose resources pour les chaînes `taxref_pas_de_donnees`, `taxref_chargement_en_cours`, `taxref_non_trouve` et les icônes vectorielles.
- kotlinx-datetime (avec un petit parseur tolérant), kotlinx.coroutines, Kermit.
- Médias : interfaces `SelecteurPhotos` (galerie multiple et appareil photo) et `ImportMedia` (recompression JPEG) dans androidMain/iosMain. Une bibliothèque tierce de sélection d'images pourrait couvrir la galerie (non évaluée ici).

Ordre de grandeur, estimé et non mesuré : ≈ 450 l. pour `FormulaireEtat` + `ChampTaxonEtat` (portage quasi direct des blocs état, règles et taxon), ≈ 500 à 700 l. de composables.

## 4. Points durs

1. **L'état vit dans les vues.**
   - Les valeurs sont portées par des `tag` (FormulaireRenderer.kt:249, 262, 769, 813, 858, 973, 1065, 1123, 1156, 1286) et relues par cast (l.553-610).
   - La visibilité est testée par `View.GONE` (l.346, 383, 532).
   - Il faut réifier un modèle d'état et garder **exactement** le contrat de types de `lireValeurs` : Int contre Double, `""` contre `null`, `true`/`null`, liste vide → `null`. L'envoi s'appuie dessus (network/MonitoringEnvoi.kt:45-52).
   - Les valeurs restent `Any?`, avec des casts non vérifiés (l.256, 586, 606, 1115, 1144) ; un type scellé serait plus sûr.
2. **Sémantique implicite des listeners.**
   - Mécanismes en jeu : garde anti-récursion `appliquantChange` (l.115-117, 195-211), debounce de 220 ms avec garde avant planification (l.300-320), flush (l.326-332), dirty approximé par comparaison de valeurs (l.119-139, 494-498).
   - Côté Android, un `setSelection` de Spinner notifie `onItemSelected` de façon différée (au passage de layout), donc hors garde. Une seconde passe des règles peut ainsi s'exécuter selon le widget modifié.
   - Le moteur commun doit fixer une règle explicite (une passe par saisie, patchs visibles des conditions suivantes, cf. ChangeRules.kt:164-186) et la verrouiller par des tests (FormulaireRendererTest l.112, 260).
3. **Champ TAXON (l.895-1108).**
   - Logique Android : index de 15 à 50 k noms construit hors du thread principal (l.985-1026), résolution restreinte à la liste du protocole (l.941-947), choix figé (l.951, 1060-1081), refus au blur (l.1099-1106).
   - En Compose : filtrage asynchrone avec debounce et plafond d'éléments. Un menu déroulant standard n'est pas conçu pour des milliers d'entrées (à mesurer). Désactiver l'autocorrection sur iOS.
4. **Dates.**
   - DATE est stockée en `yyyy-M-d` sans zéros (l.745-749) et lue par un `SimpleDateFormat` lenient : il accepte `yyyy-MM-dd` et fait rouler les dates invalides (2026-2-30 devient le 2 mars).
   - DATETIME accepte 'T' ou espace, avec ou sans secondes (l.852-856).
   - kotlinx-datetime est strict : il faut un petit parseur tolérant commun, avec des tests.
   - Le `DatePickerState` M3 travaille en millis UTC, d'où un risque de décalage d'un jour.
5. **JSON des valeurs** (NouvelleVisiteFragment.kt:905-907, 1135-1147, 1189-1197) : reproduire avec `JsonPrimitive` la distinction chaîne/nombre/booléen/null (`isString`) et la relecture des tableaux en `List<String>`. Particularités d'org.json : §5.
6. **Photos** (NouvelleVisiteFragment.kt:102-210, ui/MediaImport.kt, ui/PhotoCapture.kt).
   - Android : galerie en multi-sélection ; appareil photo via FileProvider + `FLAG_GRANT_WRITE_URI_PERMISSION` (l.135-139) ; récupération de la photo après la mort du process (l.153-166, 242-256) ; recompression JPEG/EXIF.
   - iOS : PhotosPicker + UIImagePickerController (MediaPickerView.swift:25, 210).
   - Les chemins sont stockés en URI absolues `file:/…/files/medias/…` (MediaImport.kt:49, 62, 119). Ce n'est pas portable sur iOS, où le chemin du conteneur varie : stocker des chemins relatifs à une racine fournie par la plateforme.
7. **Garde de sortie** (NouvelleVisiteFragment.kt:294-345) : dialogue à 3 issues sur le retour système. En CMP, un BackHandler commun existe, mais il est expérimental selon les versions (à vérifier). Sur iOS, le retour par balayage doit aussi être intercepté.
8. **Restauration d'état** (l.242-256, 1306-1332) et `arguments` modifiés en place (l.233-237, 655).
   - Cible : `SavedStateHandle` du ViewModel multiplateforme. Il n'accepte que des types sérialisables, et sa disponibilité en KMP dépend de la version de lifecycle (à vérifier).
   - iOS n'a pas d'équivalent au Bundle restauré après la mort du process : envisager un brouillon sauvegardé automatiquement dans le code commun.
9. **Navigation.**
   - 11 actions `action_*` dans le périmètre, dont `action_nouvelle_visite_enchainer` avec `popUpTo` inclusif (res/navigation/nav_graph.xml:195-198, utilisée l.460, 977, 1072).
   - Le `fil` est encodé avec U+001E et U+001F (ui/FilAriane.kt:104-123).
   - Cible : routes typées `@Serializable` (navigation-compose MP), en passant les segments sérialisés plutôt que des caractères de contrôle.
10. **Clavier numérique signé et décimal sur iOS** (FormulaireRenderer.kt:640-646, AdditionalFieldsRenderer.kt:314-315).
    - Les claviers iOS decimalPad et numberPad n'ont pas de signe « − ».
    - L'app iOS actuelle utilise `.numberPad` (FormulaireRenderer.swift:593) : aujourd'hui, ni décimale ni nombre négatif ne peut y être saisi.
    - Vérifier la correspondance `KeyboardType` → `UIKeyboardType` de la version CMP retenue ; prévoir un bouton ± ou un clavier numbersAndPunctuation.
11. **Collation française** (OcctaxFieldsRenderer.kt:179-188) : expect/actual, ou tri sur clé normalisée (l'ordre peut légèrement différer).
12. **Dictée** (SpeechToTextHelper.kt).
    - API, permissions et cycle de vie (`destroy`) sont entièrement différents. Sur iOS : 2 autorisations, et les clés NSSpeechRecognitionUsageDescription et NSMicrophoneUsageDescription (présentes dans le project.pbxproj de GeoMys-IOS).
    - Les hypothèses partielles sont injectées directement dans le champ (l.132-138). En commun, elles doivent passer par l'état, pas directement par le TextField.
13. **Performance du moteur d'expressions.**
    - Regex recompilées à chaque appel (HiddenExpr.kt:76-110 ; ChangeRules.kt:84, 155, 228-234, 248) et recalcul complet à chaque changement (FormulaireRenderer.kt:291-298).
    - Précompiler, puis mesurer sur Kotlin/Native, dont le moteur Regex diffère de java.util.regex.
14. **Décodage d'images** (SuivisFragment.kt:179-188) : BitmapFactory + inSampleSize → Coil 3 (qui gère le sous-échantillonnage) ou expect/actual.
15. **Divergences Android/iOS à arbitrer avant d'unifier.**
    - iOS pré-sélectionne le dataset Occtax s'il figure dans les options et pose `id_digitiser` dès l'initialisation (FormulaireRenderer.swift:114-128).
    - Android se limite à l'auto-sélection d'une option unique (NouvelleVisiteFragment.kt:724-732) et pose `id_digitiser` à l'envoi (network/MonitoringEnvoi.kt:146-153).
    - Il faut expliciter quelle version sert de référence de parité.
16. **Tests.**
    - FormulaireRendererTest (Robolectric, 383 l.) est à réécrire contre `FormulaireEtat`.
    - Les 6 tests JUnit purs (HiddenExpr 328, ChangeRules 227, ValidationExpr 135, WidgetMapping 124, PictoMonitoring 68, ChampsTaxon 56, soit 938 l.) passent en commonTest (kotlin.test). Exécutés aussi sur iOS, ils vérifieront la parité JVM/Native du moteur.

Précisions sur la grille de correspondances :
- SharedPreferences → **multiplatform-settings** plutôt que DataStore : relecture directe de `GeoMys_prefs`, sans migration.
- SimpleDateFormat → kotlinx-datetime pour les calculs, mais **parseur maison tolérant** pour reproduire le mode lenient.
- `java.text.Collator` et `RegexOption.DOT_MATCHES_ALL` (absents de la grille) → expect/actual et `(?s)`.
- LiveData → StateFlow : le périmètre ne contient ni LiveData ni ViewModel ; tout l'état est dans les Fragments et les Views. Les ViewModels sont à créer, pas à convertir.

## 5. Compatibilité des données

Cas étudié : GeoMys-Common, avec le même applicationId, relit les données de l'appli Android existante.

| Donnée | Lue ou écrite dans le périmètre | Format actuel | Pour GeoMys-Common |
|---|---|---|---|
| Préférences de saisie : SharedPreferences `GeoMys_prefs`, clés `saisie_dernier_taxon` (String) et `saisie_nom_sci` (Boolean) | PreferencesSaisie.kt:28-50 (lecture et écriture) | XML SharedPreferences. Le taxon est stocké par son nom d'enum (`OISEAU`, `MAMMIFERE`, `REPTILE`, `BATRACIEN`, `POISSON`, `INSECTE`, `FONGE`, `MOLLUSQUE`, `INVERTEBRES`, `PLANTE`) et relu par `Taxon.valueOf` | multiplatform-settings branché sur le même fichier : relecture directe. Ne pas renommer les constantes de l'enum. L'app iOS actuelle utilise les mêmes clés dans `UserDefaults.standard` (@AppStorage, SaisieRapideView.swift:63-64, SaisieObservationView.swift:61-62), avec des rawValues identiques (Model/Models.swift:13-22) : compatible avec `NSUserDefaultsSettings(standardUserDefaults)`. |
| Outbox `filesDir/monitoring_outbox.json` (liste de `SaisieEnAttente`, Gson) : saisies non envoyées, y compris celles « à compléter » | écrite par NouvelleVisiteFragment.kt:927-946 (`mettreAJour`) et 1017-1036 (`ajouter`) ; lue par NouvelleVisiteFragment.kt:227-229, 431-434 et FicheObjetFragment.kt:102-110, 564-585 | Gson par réflexion : clés = noms des propriétés Kotlin (aucun `@SerializedName`), nulls omis, `etat` = nom d'enum (PENDING, SENDING, SENT, ERROR), `dateLocale` en millisecondes epoch, `uuid` = UUID v4 textuel. Gson échappe `< > & = '` en `\uXXXX`. | **Données irremplaçables, à relire impérativement.** `@Serializable SaisieEnAttente` avec les mêmes noms, des valeurs par défaut partout, `ignoreUnknownKeys = true` et `coerceInputValues = true` (Gson pouvait laisser `null` dans des champs non-null, cf. la normalisation de store/OutboxMonitoring.kt:201). Champs écrits ici : uuid, moduleCode, objectType, parentObjectType, parentIdServeur, parentUuidLocal, parentIdField, nomsChampsSchema, champsTexteLibre, valeursJson, uuidPayload, uuidFieldName, mediaPathsLocal, mediaPathLocal (legacy, remis à null en édition, l.938), mediaSchemaDotTable, champsManquants, etat, messageErreur. `Uuid.random().toString()` produit le même format. L'écriture atomique (tmp + rename) est à conserver (OutboxMonitoring.kt:129-171, hors périmètre). |
| `valeursJson` (chaîne JSON dans chaque saisie) | construit NouvelleVisiteFragment.kt:905-907 (avec `valeurToJson`, l.1189-1197) ; relu l.1135-1147 | objet `{code: valeur}` produit par org.json, avec des clés MEDIA retirées et `/` échappé en `\/`. Types des valeurs :<br>• `null` → JSON `null` ;<br>• Boolean ;<br>• Int, ou Double (l'org.json d'Android écrit un Double entier sans « .0 ») ;<br>• List → tableau ;<br>• tout le reste → chaîne : ids de nomenclature, dataset et observateurs (`"123"`), DATE `yyyy-M-d`, TIME `HH:MM`, DATETIME `yyyy-MM-dd HH:mm:ss`, SELECT `""` si vide ;<br>• TAXON → Int (cd_nom) ;<br>• CHECKBOX → `true` ou `null`. | Lisible tel quel par kotlinx (`\/` est un échappement JSON valide). À la relecture, garder `isString` et convertir les tableaux en `List<String>` comme l.1142. À l'écriture, kotlinx écrit `12.0` là où org.json écrivait `12`, sans effet pour les NUMBER entiers, déjà typés Int. |
| Médias joints | MediaImport.kt:47-62, 119 (appelé par NouvelleVisiteFragment.kt:117, 161, 170) ; nom affiché par FormulaireRenderer.kt:1312 | fichiers `filesDir/medias/photo_<horodatage>_<i>.jpg`, référencés dans `mediaPathsLocal` par une URI absolue `file:/…/files/medias/…` (`File.toURI()`) | Avec le même applicationId, les chemins et les fichiers restent accessibles. Accepter ces URI legacy ; pour les nouveaux médias, stocker un chemin relatif (obligatoire côté iOS). |
| Caches de `gn_config` lus ici : `gn_cache_add_fields_occtax` (`List<AdditionalFieldDef>`), `gn_cache_datasets` (`List<GeoNatureDataset>` : `moduleCodes`, `idTaxaList`), `gn_id_role_utilisateur` (Int) | AdditionalFieldsRenderer.kt:49-55 ; NouvelleVisiteFragment.kt:789, 817-822 ; clés définies dans store/GeoNatureConfig.kt:96-99, 197-199, 231-233 | Gson : clés camelCase, `WidgetType` par nom, `nomenclatureOptions: List<Pair>` → `[{"first":…,"second":…}]` | Ce sont des caches re-synchronisables : leur migration n'est pas indispensable. Pour les relire, le sérialiseur `Pair` de kotlinx.serialization utilise lui aussi `first`/`second` ; reprendre les mêmes options (`ignoreUnknownKeys`, valeurs par défaut). `gn_id_role_utilisateur` provient du login. |
| Valeurs des champs additionnels (stockées dans les sessions Occtax, hors périmètre) | produites par AdditionalFieldsRenderer.kt:141-173 ; relues l.178-184 | `Map<String,String>` : checkbox multiple = `["1","3"]`, case simple = `"true"`/`"false"`, SELECT et RADIO = code ou `""` | Garder ces encodages : `JsonArray.toString()` de kotlinx produit la même forme compacte. |
| Données transitoires, sans migration : Bundle `onSaveInstanceState` (`captureUriStr`, `captureFichierPath`, `valeursEnCours`, `mediaPathsEnCours`, NouvelleVisiteFragment.kt:1312-1330), arguments de navigation dont `fil` (ui/FilAriane.kt:104-123), fichiers `cacheDir/captures/` (PhotoCapture.kt:38-40) | — | — | Rien à reprendre. |

Non analysé ici : la reprise des données de l'app iOS existante (même bundle id), hormis les préférences ci-dessus. Elle a son propre `valeursJson` (NouvelleVisiteView.swift:338, 507, 530).

## 6. Bilan chiffré

**Par catégorie de fichier**

| Catégorie | Fichiers | Lignes (`loc`) | Lignes de code (colonne `code` de `releve-automatique.tsv`) |
|---|---|---|---|
| COMMUN | 6 : ChangeRules, EditableField, ExpressionScanner, WidgetMapping, ChampsTaxon, PictoMonitoring | 801 | 419 |
| COMMUN-ADAPTÉ | 3 : HiddenExpr, ValidationExpr, PreferencesSaisie | 402 | 213 |
| MIXTE | 6 : FormulaireRenderer, NouvelleVisiteFragment, AdditionalFieldsRenderer, OcctaxFieldsRenderer, SpeciesAutocomplete, TaxRefLookup | 3600 | 2459 |
| PLATEFORME | 1 : SpeechToTextHelper | 168 | 119 |
| UI | 5 : TaxonPalette, TaxonSelector, SuivisFragment, SuiviDetailFragment, FicheObjetFragment | 1508 | 1115 |
| ABANDON | aucun fichier entier (≈ 35 l. de mode démo dans NouvelleVisiteFragment) | — | — |
| **Total** | **21** | **6479** | **4325** |

**Après découpe des fichiers MIXTE et UI (approximations)**

| Destination | ≈ lignes | Détail |
|---|---|---|
| commonMain, logique (moteur, états, ViewModels) | ≈ 3370 | 1203 l. en fichiers entiers (COMMUN + COMMUN-ADAPTÉ) ; ≈ 1640 l. extraites des MIXTE (NouvelleVisite 865, FormulaireRenderer 485, AdditionalFields 110, TaxRefLookup 100, Occtax 54, SpeciesAutocomplete 26) ; ≈ 525 l. extraites des écrans UI (FicheObjet 327, SuiviDetail 140, Suivis 41, TaxonPalette et TaxonSelector ≈ 16) |
| UI Compose à réécrire (en commonMain également) | ≈ 2180 lignes Android à remplacer | FormulaireRenderer 840, FicheObjet 339, NouvelleVisite 240, SuiviDetail 217, AdditionalFields 179, Suivis 103, Occtax 88, SpeciesAutocomplete 67, TaxonPalette ≈ 40, TaxonSelector ≈ 37, TaxRefLookup 33 |
| androidMain + iosMain (plateforme) | ≈ 330 | SpeechToTextHelper 168 (dont ≈ 30 l. de politique de repli et de messages remontables en commun), NouvelleVisite ≈ 140 (photos, restauration), Suivis ≈ 20 (décodage d'image), FormulaireRenderer ≈ 5 |
| Abandon | ≈ 35 | mode démo de NouvelleVisite (l.1273-1304 et branches) |
| En-têtes, imports et blancs des fichiers découpés | ≈ 560 | — |

Environ 52 % des lignes du périmètre deviennent de la logique commune. Environ 34 % sont de l'UI à réécrire en Compose, elle aussi partagée en commonMain. Environ 5 % relèvent du code plateforme. Les lignes de logique incluent beaucoup de KDoc : sur 6479 lignes, 4325 sont du code selon la colonne `code` de `releve-automatique.tsv`.

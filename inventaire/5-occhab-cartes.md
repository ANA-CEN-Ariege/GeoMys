# 5. OccHab, édition géométrique et cartes

> Détail de l'inventaire GeoMys-Common. La synthèse, les chiffres globaux et les décisions à prendre sont dans [`../INVENTAIRE.md`](../INVENTAIRE.md).
> Source analysée en lecture seule : GeoMys-Android `cdd5196` (v1.4.7, 18 octobre 2026). Les équivalents iOS ont été cherchés dans GeoMys-IOS `64d303b`.
> La colonne « Cible » donne une destination indicative ; la structure proposée pour GeoMys-Common est décrite au § 9 de la synthèse.

## 1. Inventaire fichier par fichier

Périmètre : `GeoMys-Android/app/src/main/java/fr/ariegenature/geomys/ui/`, commit cdd5196. Le préfixe `ui/` est omis dans les références « fichier:ligne ».
- **Lignes** = lignes brutes, puis entre parenthèses les lignes de code, sans commentaires ni lignes vides. Les commentaires (≈ 31 %) documentent des décisions terrain datées : il faut les garder avec la logique.
- **Cibles** = proposition, puisque GeoMys-Common est vide pour l'instant. Les chemins sont relatifs à `shared/src/commonMain/kotlin/fr/ariegenature/geomys/`, sauf mention androidMain ou iosMain.

| Fichier | Lignes | Rôle (1 phrase) | Catégorie | À remplacer / dépendances plateforme (précis) | Cible dans GeoMys-Common | Remarques |
|---|---|---|---|---|---|---|
| OccHabCarteFragment.kt | 965 (601) | Écran carte OccHab : vue d'ensemble des stations (locales en rouge, serveur en violet), création ou réédition d'une géométrie Point/Polygone (avec trous), aimantage, annulation, validation avec persistance au fil de l'eau, altitudes MNT, « Terminer » (+ envoi). | UI (XL) | **osmdroid** : `MapView`, `MapEventsOverlay` (277), `MyLocationNewOverlay` (284-297), `Projection.toPixels` (515-521), `controller.setZoom/setCenter/animateTo/zoomIn/zoomOut`, `setMapOrientation` (270), `Configuration.userAgentValue` (151), `mapCenter`/`zoomLevelDouble` (947-948). **Android** : Fragment, ViewBinding, Navigation, `activityViewModels`, `AlertDialog` ×4 (459, 883, 917, 924), `Toast` ×4, `alerterEchecEcritureStore` ×3, `ProgressBar` construit en code (906-916). **Context** sert à trois choses : stockage (`OccHabStore` 95, 848 ; `GeoNatureConfig` 199, 395, 838, 870), ressources (`displayMetrics.density` 517 et 906, `R.color` 633), UI (Toast et dialogues). `Math.hypot` et `Math.round` (522, 665, 797) → `kotlin.math`. `lifecycleScope`, `viewModelScope` + `Dispatchers.IO` (837-853). | `occhab/carte/OccHabCarteViewModel` (état consultation/création, règles des gestes, valider, annuler, persister, altitudes) + `occhab/ui/OccHabCarteScreen` (Compose) ; aimantage dans `geo/Aimantage` | ≈ 290 l. de code de logique extractible (§2.1). Équivalent iOS : `UI/OccHab/OccHabCarteView.swift` (718) + `OccHabMapView.swift` (686). KDoc 717-719 obsolète (parle encore de « propagation topologique »). |
| OccHabDetailsDialog.kt | 587 (424) | Dialogue « Informations obligatoires » (démarrage) / « Détails de la station », plus deux fabriques partagées : `construireSpinnerNomenclature` (410-428) et `construireSectionAnaEval` (445-587). | UI (L) | **Widgets** : `AlertDialog`, `EditText`, `Spinner` + `ArrayAdapter`, `MaterialAutoCompleteTextView`/`TextInputLayout`, `SwitchCompat`, `DatePickerDialog` (117-122). **Dates** : `SimpleDateFormat("dd/MM/yyyy", FRANCE)` (67) et `Calendar` (116-123, 219-220) → kotlinx-datetime `LocalDate` + format maison. **Tri** : `java.text.Collator(FRENCH, PRIMARY)` (413-416) → clé de tri maison (minuscules + désaccentuation) ou expect/actual. **Gson** (395) → kotlinx.serialization. `Toast` (315). Context = UI uniquement (+ `theme.resolveAttribute`). | `occhab/ui/DetailsStationDialog` (Compose) + `occhab/DetailsStationFormulaire` (brouillon, validation, application atomique) ; composants `ui/ChampNomenclature` et `occhab/ui/SectionAnaEval` ; fusion ANA dans `util/AnaEval` (commun) | ≈ 150 l. de logique (§2.2). Dépend de helpers hors périmètre (`DetailsReleveDialog.kt` : `datasetsPourOccHab`, `observateursPourDetailsReleve`, `construireSelecteurMultiObservateurs`, `AdaptateurAutocomplete`). Équivalent iOS : `OccHabInfosStationSheet.swift` (463) + `AnaEvalSectionView.swift` (192). |
| OccHabHabitatFragment.kt | 395 (277) | Création ou édition d'un habitat : autocomplétion HABREF débouncée, nomenclatures pilotées par `formConfig`, champs requis conditionnels, section ANA-EVAL, puis enregistrement de la station. | UI (M) | **Widgets** : `MaterialAutoCompleteTextView` + `ArrayAdapter` à `Filter` neutre (169-177) + `TextWatcher` (186-205), `Spinner` + `OnItemSelectedListener` (262-266), `EditText`, `Toast`. Navigation. **Context** = stockage (`GeoNatureConfig` 106, `OccHabStore` 369) + UI. | `occhab/HabitatFormViewModel` + `occhab/ui/HabitatScreen` | ≈ 100 l. de logique (§2.3). Équivalent iOS : `OccHabHabitatView.swift` (297). |
| OccHabStationFragment.kt | 241 (161) | Écran station : résumé géométrie et JDD, liste des habitats (éditer, supprimer), « Détails », « Ajouter un habitat », « Terminer ». | UI (S) | ViewBinding, `LinearLayout` et `ImageButton` construits en code, `MaterialColors`. `"%.5f".format` (166) → formatage maison. Context = stockage + UI. | `occhab/ui/StationScreen` (état porté par `OccHabViewModel`) | ≈ 50 l. de logique : `majTitre` 118-123, résumé 153-172, `sauvegarderAuFilDeLEau` 131-136, « Terminer » 91-110, garde « process mort » 68-74. Équivalent iOS : `OccHabStationView.swift` (237). |
| OccHabStationsFragment.kt | 283 (218) | « Mes stations » : onglets « À envoyer » / « Envoyées », liste des saisies, éditer / supprimer / envoyer, « Tout envoyer ». | UI (M) | `TabLayout`, `RecyclerView` + Adapter (222-283), `EnvoiGroupeUi` (hors périmètre), `AlertDialog`/`Toast`. `SimpleDateFormat("dd/MM/yyyy HH:mm")` (161) et `("dd MMM yyyy HH:mm", Locale.getDefault())` (230) → kotlinx-datetime + noms de mois français. | `occhab/MesStationsViewModel` + `occhab/ui/MesStationsScreen` | ≈ 65 l. de logique : filtres, tri et compteurs 124-153, `saisiesEnvoyables` 143-145, envoi 175-212, visibilité des boutons 273-278. Équivalent iOS : `OccHabStationsView.swift` (268). |
| OccHabViewModel.kt | 296 (152) | État de session OccHab partagé (détails par station, défauts de session, station courante, `saisieId`, dernier cadrage) + `detailsSessionParDefaut` (défauts serveur ou relevé précédent). | COMMUN-ADAPTÉ | `androidx.lifecycle.ViewModel` (99) → lifecycle-viewmodel KMP. `java.util.UUID` (111, 129) → `kotlin.uuid.Uuid`. `Calendar` (67-70) → kotlinx-datetime (début du jour dans le fuseau système). Gson (76) → kotlinx.serialization (`ignoreUnknownKeys`). L'état est en `var` non observables : passer en `StateFlow`. Aucun `GeoPoint` (le hit du relevé est un faux positif). | `occhab/OccHabViewModel` + `occhab/OccHabDetailsSession` (@Serializable) | Toutes les fonctions sont portables après ces remplacements (§2.9). Test portable : `OccHabViewModelTest`. Équivalent iOS : `OccHabSessionModel.swift` (298). |
| EditeurGeometrie.kt | 373 (235) | Géométrie en cours d'édition (mode, point, sommets, trous) et son rendu bleu osmdroid : pin et sommets déplaçables, poignées « + », polygone à trous. | MIXTE | **osmdroid** : `Marker` (`isDraggable`, `OnMarkerDragListener`), `Polygon` (`holes`, `fillPaint`/`outlinePaint`, `setOnClickListener`), `Overlay`, `MapView.overlays`/`invalidate`. `Toast` (276). `ContextCompat.getDrawable(ic_pin_drop)` (355). Context = ressources et Toast seulement. | État + opérations → `occhab/carte/EditeurGeometrieEtat` (commonMain, sur le modèle de `OccHabEditeurGeometrie.swift`) ; rendu → `occhab/ui/CoucheEdition` (couches MapLibre + poignées Compose) | ≈ 55 l. de logique / ≈ 150 l. de rendu (§2.4, §2.9). Plus aucune propagation topologique depuis cdd5196 (« un geste ne modifie plus que sa station »). |
| PileAnnulationGeometrie.kt | 72 (28) | Pile bornée (30) d'instantanés de la géométrie en cours, pour « Annuler ». | COMMUN | `GeoPoint` (21, 43-45, 61-63) → `LatLon`. `ArrayDeque` et `removeLastOrNull` viennent de kotlin.collections (OK). | `occhab/carte/PileAnnulationGeometrie` | Les copies (61-63) ne servent qu'à cause de la mutabilité de `GeoPoint` : inutiles avec un `LatLon` immuable. Aucun test Android direct. Équivalent iOS : `Instantane` et `pile` dans `OccHabEditeurGeometrie.swift`. |
| StationsServeurOverlay.kt | 392 (249) | Stations du serveur : chargement (réseau, sinon repli sur le cache hors-ligne), classification (importable / déjà locale / en édition), rendu violet, import, bascule vers la saisie qui détient la copie. | MIXTE | **osmdroid** : `Polygon` (holes), `Marker`, `DashPathEffect` (281). `Toast` (123, 147, 179) et `AlertDialog` (312, 344, 364). `SimpleDateFormat`/`Date`/`Locale` (172, 310) → kotlinx-datetime. `AtomicInteger` (135) n'est qu'un compteur passé en paramètre de sortie → valeur de retour. Context = stockage (`GeoNatureConfig` 137, `OccHabStore` 229, 329, 357) **et** UI. | `occhab/carte/StationsServeurEtat` (+ événements UI) ; rendu → couche GeoJSON violette | ≈ 125 l. de logique (§2.5, §2.9). Code mort : variable `orange` (240), branche pointillée (`pointille` toujours `false`, 254), KDoc 215-223 obsolète. Équivalent iOS : `OccHabCarteView.swift` (`stationsServeurVisibles` 104, `importer` 647). |
| StationsSessionOverlay.kt | 160 (105) | Rendu rouge des stations locales non envoyées (toutes saisies) ; un clic ouvre l'édition ; fournit les cibles d'aimantage. | MIXTE (surtout du rendu) | **osmdroid** : `Polygon` (holes), `Marker`. `ContextCompat.getDrawable` (134). Context = ressources seulement. | Sélection → `occhab/carte/StationsLocalesEtat` ; rendu → couche GeoJSON rouge + cercles de sommets | ≈ 25 l. de logique (§2.9). Le paramètre `toutesLesSaisies=false` n'est jamais utilisé. La mémoïsation par empreinte (82-86) devient superflue avec une source GeoJSON alimentée par l'état. |
| IconesEditionCarte.kt | 80 (42) | Fabriques des icônes de sommet (disque à liseré blanc) et de poignée « + ». | UI (S) | `GradientDrawable`, `Bitmap`/`Canvas`, `BitmapDrawable`, `R.drawable.ic_add`, densité. | `carte/ui/IconesCarte` (Painter ou ImageVector Compose, ou simple `CircleLayer`) | La rastérisation (54-59, 64-78) contourne une limite d'osmdroid : sans objet avec MapLibre. |
| CarteGeometrieFragment.kt | 537 (397) | Carte Monitoring d'un objet (et de ses enfants) ou d'un protocole : récupération parallèle des géométries, rendu GeoJSON (Point, MultiPoint, LineString, Polygon, MultiPolygon), menu d'actions (fiche / nouvelle saisie). | UI (M) | **osmdroid** : `Marker`, `Polyline`, `Polygon`, `MyLocationNewOverlay`, `zoomerSur`. **org.json** (`JSONObject`/`JSONArray`, 288-358) → kotlinx.serialization `JsonElement`. Bundle, Navigation, `AlertDialog`/`Toast` (451-458). Context = stockage (`GeoNatureConfig` 132) + UI. | `monitoring/CarteGeometrieViewModel` + `monitoring/ui/CarteGeometrieScreen` ; parsing dans `geo/GeometrieGeoJson` (commun) | ≈ 165 l. de logique (§2.6). Les trous sont dessinés comme des polygones pleins superposés (312-317), alors qu'iOS les rend en creux (`CarteGeometrieView.swift`:759). Équivalent iOS : `UI/Monitoring/CarteGeometrieView.swift` (823). |
| MapUtils.kt | 281 (188) | Utilitaires partagés des cartes : catalogue des fonds (OSM, OpenTopoMap, IGN Plan / SCAN 25 / Ortho, Esri) et MBTiles, persistance du choix, application au `MapView`, overlay « ma position », aire géodésique, cadrage. | MIXTE | **osmdroid** : `MapTileProviderBasic`, `OfflineTileProvider`, `SimpleRegisterReceiver`, `XYTileSource`, `OnlineTileSourceBase`, `TileSourcePolicy`, `MapTileIndex`, `BoundingBox`, `GpsMyLocationProvider`, `MyLocationNewOverlay`. SharedPreferences `GeoMys_prefs` (56, 71) → multiplatform-settings. `java.io.File` (47, 59) → nom de fichier + racine fournie par la plateforme (okio). `AlertDialog` (168). `Math.toRadians/sin/abs` (253-257) → `kotlin.math`. Context = prefs, `filesDir` (via `MbtilesStore.dossier`), providers osmdroid, ressources. | `carte/FondsCarte` (enum, gabarits d'URL, bornes de zoom, attributions), `carte/StyleCarte` (style JSON MapLibre), `carte/PreferencesCarte`, `geo/AireGeodesique`, `geo/Emprise` ; `carte/ui/ChoixFondDialog` | ≈ 105 l. passent en commun (§2.9). Sert aussi à `TraceFragment`, `SaisieRapideFragment` et `SortieDetailFragment` (hors périmètre). Équivalent iOS : `UI/Map/MapTileSource.swift` (336), `Util/Geo/AireGeodesique.swift`. |
| MapCompassController.kt | 115 (76) | Lit le cap (rotation-vector, sinon accéléromètre + magnétomètre) et fait tourner la carte et l'aiguille quand le mode boussole est actif. | PLATEFORME | `SensorManager`/`Sensor`/`SensorEventListener` (54-114), `System.arraycopy` (65, 70), `MapView.setMapOrientation` (83). | Interface commune `CapAppareil` (`Flow<Double>`) ; androidMain : SensorManager ; iosMain : `CLLocationManager.startUpdatingHeading`. La rotation (bearing) est appliquée par la couche carte commune. | Équivalent iOS : `Location/LocationTracker.swift` (cap 104-111, 193) + `UI/Map/MapView.swift` 369-383. Android donne le nord magnétique (`getOrientation`), iOS `trueHeading` quand il est disponible : divergence à trancher. |
| CompassView.kt | 113 (78) | Vue boussole dessinée (aiguille N/S, anneau bleu quand elle est active). | UI (S) | `android.view.View` + `Canvas`/`Paint`/`Path`. | `carte/ui/Boussole` (Compose `Canvas`) | Équivalent iOS : `MapView.swift` 762-823 (`BoussoleBouton`, `AiguilleBoussole`). |
| ExplorerFragment.kt | 555 (454) | Explorer : observations Synthèse récentes sur l'emprise visible (rechargement débouncé, garde à 100 km), regroupement à 0,001°, filtre par groupe, liste en bottom sheet, position GPS en direct, aiguille de boussole. | UI (L) | **osmdroid** : `MapListener` (125-128), `boundingBox` (360, 379), `Marker` (+ `isFlat`). **Plateforme** : `LocationManager` GPS + NETWORK (163-213), permissions `RequestMultiplePermissions` (84-88, 190-199), `SensorManager` (308-325). **UI** : `Bitmap`/`Canvas` (235-248, 437-480), `BottomSheetDialog` + `RecyclerView` (482-515), 10 `MaterialButton` de filtre. `"%.3f".format` (416) → arrondi ; `Math.cos/toRadians` (362) → `kotlin.math`. | `explorer/ExplorerViewModel` (debounce, garde, regroupement, filtre) + `explorer/ui/ExplorerScreen` ; expect/actual `PositionAppareil` et `CapAppareil` | ≈ 75 l. de logique (§2.7). Équivalent iOS : `UI/ExplorerView.swift` (414), même algorithme (290-300). Le libellé « 12 mois » est codé en dur (392, 490) alors que la profondeur vient de `config.dureeObservationJours`. |
| CacheManagerFragment.kt | 753 (588) | « Maps Manager » : estimation et téléchargement des tuiles de l'emprise visible (du zoom courant à 17, ≤ 200 km²), taille et vidage du cache, import et suppression de MBTiles, cadrage sur les sites d'un protocole. | UI (L) | **osmdroid** : `CacheManager.downloadAreaAsync` (325-370), `BoundingBox`, `MapListener` (164-171), overlays (520-565). `MapTileCache` (`SqlTileWriter` d'osmdroid ; 207-216, 618). SAF `OpenDocument`/`Uri` (76-79, 279-297). **org.json** (484, 500-574, 586-601) → `JsonElement`. `String.format` (189-191, 210). `Math.toRadians` et `Math.PI` (636, 658-660) → `kotlin.math`. `java.io.File`. `LinearProgressIndicator`. | `cartes/horsligne/HorsLigneViewModel` + `geo/TuileMath` (estimations) + expect/actual `TelechargeurZone` (paquets hors-ligne MapLibre) et `SelecteurFichier` ; UI Compose `cartes/horsligne/ui` | ≈ 175 l. de logique pure (§2.8). Équivalent iOS : `UI/Map/CacheManagerView.swift` (621), `CartesMbtilesView.swift` (132), `Store/MapTileCache.swift` (`TuileMath` 153-197). |

## 2. Logique à extraire (fichiers MIXTE/UI de plus de 300 lignes)

Les chiffres sont des lignes de code (hors commentaires et lignes vides), estimés à ±10 %.

### 2.1 OccHabCarteFragment.kt — ≈ 290 l. de logique sur 601 (≈ 480 lignes brutes avec les commentaires)

**État d'écran à remonter dans le ViewModel.** Il vit aujourd'hui dans le fragment : `enCreation` (70), `geometrieChargee` (90), `cadrageInitialFait` (143), `carteSuitBoussole` (147).

**Décisions à l'ouverture (≈ 20 l.).**
- 227-251 : on reprend en création si `station.geometrieDefinie()`. Sinon, vue d'ensemble : cache serveur si le switch est actif, plus les stations de toutes les saisies.
- 299-326 : cadrage sur la géométrie courante, sinon sur le cadrage mémorisé, sinon sur l'Ariège (42.93, 1.40, z11).

**Fonctions à extraire.**
- `preremplirDepuisViewModel` 351-386 (26) → `EditeurGeometrieEtat.charger(station)`, comme le `charger` d'iOS.
- `demarrerCreationStation` 392-416 (≈ 8 de logique) : enchaînement après le formulaire (`enCreation`, recharge serveur avec `conserverLesAutresJdd`).
- `majPanneauBas` 419-430 et `majBoutons` 752-775 (≈ 18) : visibilités et activations dérivées (`geomOk`, `geometrieEngagee`, Annuler actif) → état dérivé, comme `peutValider`, `peutAnnuler` et `geometrieEngagee` d'iOS.
- `editerStationExistante` 437-506 (≈ 32 de logique sur 46) :
  - une station envoyée n'est rééditable que si son id serveur est connu ;
  - invariant « une seule copie locale non envoyée » (`copieLocaleNonEnvoyee`) ;
  - rattachement à la saisie propriétaire, `reprendreStation`, purge de la pile et de l'éditeur.
  - Le dialogue et le toast deviennent des événements UI.
- `snapVersSommet` 512-526 (≈ 10) : cible la plus proche à moins de 44 dp à l'écran. Portable si la projection est injectée (`(LatLon) -> Offset`) ; cf. `plusProche` d'iOS (`OccHabEditeurGeometrie.swift`:239-249).
- Cadrage (≈ 8) : `restaurerCadrageMemorise` 530-536, règles de `cadrerSur` et `centrerSurPosition` 542-559, mémorisation du cadrage en `onPause` 946-950.
- `singleTapConfirmedHelper` 561-584 (15) : gardes consultation/édition, mémorisation, aimantage, ajout du point ou du sommet → `tapCarte` (iOS 113-124).
- Textes du bandeau 591-618 (23) → fonctions pures (`texteBandeau` d'iOS).
- `changerMode` 621-628 (7).
- `trousDeLaStationEditee` + `persisterSelectionApresDrag` 646-673 (23) : géométrie vers le VM, surface nette, altitudes, upsert.
- `abandonnerCreation` / `deselectionnerStation` 678-703 (23).
- `memoriserAvantOperation` / `annulerDerniereOperation` 708-746 (21).
- `valider` 778-821 (25) : géométrie, surface (`airePolygoneM2`), altitudes, upsert, vidage de la pile, navigation.
- `lancerRemplissageAltitudes` 830-855 (23) : appel réseau, garde « même station », report dans le store en IO.
- `terminerSaisie` 862-892 et `envoyerPuisQuitter` 901-933 (≈ 15 de logique) : `peutEnvoyer`, choix des options, envoi.

**Reste UI ou plateforme (≈ 310 l.).**
- Câblage de `onViewCreated` (≈ 70) et insets ; mise en évidence des boutons de mode (632-641).
- Quatre `AlertDialog` et une progression construite en code (459-474, 883-891, 906-928), toasts.
- Overlay de position et boussole (264-297), cycle de vie de la carte (935-964).
- Colle des trois `Hote` (98-140, ≈ 35 l. qui disparaissent).

### 2.2 OccHabDetailsDialog.kt — ≈ 150 l. de logique sur 424

- **Brouillon local** (125-136, 219-224) : JDD, dates et observateurs « en attente » ; la date affichée est celle qui sera enregistrée.
- **Règles de visibilité** (`occhabChampVisible` 207-208, 237, 266-275) et mode `jddSeul`.
- **Validation** 316-350 (32) : JDD, au moins un observateur, dates, nature de l'objet géographique si elle est visible, altitude min ≤ max, validateurs ANA-EVAL.
- **Application atomique** 351-388 (27) : `majDetails(demarrage)`, `dateMax = max(dateMax, dateMin)`, préservation des champs masqués, altitudes et surface par station, `anaEvalJson`.
- **Mémorisation du relevé précédent** 394-396.
- **Nomenclatures** : tri 413-416 et lecteur d'id 424-427.
- **ANA-EVAL** : validateurs d'entiers bornés 511-522, textes en lecture seule `determination`/`corresp` 550-571, fusion non destructive 573-586.

Reste UI (≈ 250) : construction programmatique des widgets (51-124, 137-187, 189-294, 445-541), `DatePickerDialog`, `AlertDialog`.

### 2.3 OccHabHabitatFragment.kt — ≈ 100 l. de logique sur 277

- **Règle « Autre, préciser »** : `techniqueEstAutre` / `precisionRequise` (90-95) testent cd_nomenclature « 10 ».
- **Bouton Valider** : `majEtatBoutonValider` (149-158) applique les requis HABREF, technique et précision conditionnelle, et met à jour le libellé avec astérisque.
- **Recherche HABREF** (186-205, 384-388) : seuil de 2 caractères, debounce de 300 ms, 20 résultats via `HabitatService.rechercher` ; la sélection renseigne `cdHabChoisi`.
- **Défauts** de nomenclature pour un nouvel habitat (`idInit` 222-223).
- **`valider`** 311-379 (≈ 40) :
  - le nom cité est conservé si l'habitat HABREF n'a pas changé ;
  - un champ visible laissé vide est effacé, un champ masqué est conservé ;
  - le recouvrement accepte la virgule décimale ;
  - puis `ajouterOuMajHabitat`, `stationAEnregistrer` et upsert.
- **Garde « process mort »** 110-116.

Reste UI (≈ 150) : formulaire construit en code (160-309), autocomplétion et `Filter` neutre.

### 2.4 EditeurGeometrie.kt — ≈ 55 l. de logique, ≈ 150 l. de rendu

La liste des lignes portables est au §2.9. Le rendu, entièrement lié à osmdroid :
- `redessiner` 95-188, `dessinerSommetsTrous` 198-248, `retirerOverlays` 296-308, `redessinerForme` 321-349 ;
- `markerDraggable` 353-359, `poigneeAjout` 363-364, `liberer` 369-372 ;
- `repereDeTrou` 252-257 : la correspondance marker → (anneau, index) devient inutile si chaque poignée porte ses indices.

Le modèle iOS `OccHabEditeurGeometrie.swift` (état + gestes + pile, sans rendu) est la bonne forme pour commonMain.

### 2.5 StationsServeurOverlay.kt — ≈ 125 l. de logique, ≈ 85 l. de rendu et de dialogues

- **État** 73-91 (sauf `overlays`) et `afficherToutLeCache` 108-112.
- **`charger`** 118-199 (≈ 42 de logique) :
  - garde sur le JDD ;
  - `OccHabApi.chargerStations`, filtre `idDataset`, écriture immédiate `remplacerJdd` ;
  - repli sur `StationsServeurCache.lire`, fusion avec les autres JDD ;
  - message (nombre, mention hors-ligne datée, stations ignorées) → événement UI.
- **`resynchroniserAuJdd`** 205-213.
- **Classification** dans `afficher` 224-259 : une station avec copie locale en attente ou en édition n'est pas dessinée ; compteur `nbReprisesAilleurs`.
- **Extraction des sommets** dans `dessiner` (cibles d'aimantage et cadrage).
- **`basculerVersSaisie`** 328-335, gardes de `proposerImport` (343-362 : MultiPolygon partiel, copie existante) et **`importer`** 383-391 (dates de session, empreinte d'origine).

Le rendu osmdroid (272-303) et les dialogues (309-323, 344-374) restent côté UI.

### 2.6 CarteGeometrieFragment.kt — ≈ 165 l. de logique sur 397

- **Chargement** (≈ 80), `chargerObjet` 139-206 et `chargerProtocole` 211-270 :
  - schéma et objet chargés en parallèle ;
  - récupération individuelle des enfants sans géométrie (best-effort) ;
  - choix des types « macro » d'après le schéma, avec repli structurel (224-237) ;
  - messages d'erreur.
  - Tout cela va dans `CarteGeometrieViewModel` (états chargement / erreur / géométries).
- **Parsing GeoJSON** 288-358 (≈ 45) : `rendre` (aiguillage par type), `lonLatToGeoPoint`, `extrairePoints`, `extraireAnneaux` → `geo/GeometrieGeoJson`, un modèle typé (points, lignes, polygones avec trous), puis rendu par couches.
- **`montrerActions`** 418-459 (≈ 15) : quelles actions proposer (« fiche » si l'objet n'est pas l'objet courant, « nouvelle saisie » si `childrenTypes` contient un type de saisie).
- **`filPourItem`** 469-480 (11) : fil d'Ariane cible.
- **Recadrage** sur tous les points (272-281, 517-522).

Reste UI : overlays 360-412, navigation 484-515, dialogue et toast.

### 2.7 ExplorerFragment.kt — ≈ 75 l. de logique : clustering et requête sur l'emprise sont **portables**

**Requête sur l'emprise visible.**
- Elle est déclenchée par les événements de scroll ou de zoom (125-128), avec un debounce de 800 ms (350-356).
- Garde de largeur (358-377) : rien n'est chargé au-delà de 100 km, largeur calculée comme Δlon × 111 km × cos(latitude moyenne).
- Le job précédent est annulé (380), puis `GeoNatureBrowse.recupererObsExplorer(minLon, minLat, maxLon, maxLat)` est appelé (384-388), avec un compteur (392).
- Tout cela va dans un `ExplorerViewModel` qui consomme un `Flow` d'emprises. Seules l'emprise visible et les événements de caméra viennent de la couche carte.

**Regroupement (416).**
- Clé = coordonnées formatées à 3 décimales (≈ 110 m), indépendante du zoom.
- Le représentant est la première observation ; l'icône prend la couleur de son groupe et affiche l'effectif.
- C'est du pur : remplacer `String.format` par `round(x*1000)`, comme iOS (`ExplorerView.swift`:290-300).

**Autres logiques.**
- Filtre par groupe (286-306), tri par date décroissante et libellés de la liste (483-511).
- Position initiale : dernière position connue, sinon centre de la France à z6 (141-158) ; recentrage au premier fix (172-176).

Reste UI : barre de filtres, icônes bitmap (437-480), bottom sheet. Plateforme (≈ 90) : `LocationManager` (163-213), permissions (84-88, 185-199), capteur de boussole (308-325).

### 2.8 CacheManagerFragment.kt — ≈ 175 l. de logique pure

- **Maths de tuiles et estimation** 634-680 (37) → `geo/TuileMath` (le `TuileMath` d'iOS est équivalent) :
  - `surfaceKm2` (approximation équirectangulaire) ;
  - `nombreDeTuiles` / `lonLatToTile` (WebMercator, du zoom min à 17) ;
  - `poidsTuileKbMoyen` (25 ou 50 Ko) et `nomFond`.
- **`majInfos`** 177-217 (≈ 22) :
  - zoom min = zoom courant tronqué, au moins 1 ; plafond de 200 km² ; état du bouton ;
  - un fond MBTiles n'a rien à télécharger ;
  - pourcentage du cache, avec seuil d'alerte à 90 %.
- **`BboxAccumulator`** 579-607 (26) : emprise récursive de coordonnées GeoJSON imbriquées → `geo/Emprise`.
- **Protocoles** : `chargerSitesProtocole` 459-487 (28), `cadrerSurProtocole` 421-450 (≈ 10), liste 394-415 (≈ 8).
- **Parsing des géométries de sites** 499-574 (≈ 15) : aiguillage par type, `extrairePoints`.
- **Règles MBTiles** : repli sur OSM si l'on supprime le fond affiché (304-312), textes de la liste (252-260).
- **Constantes** 749-752.

Plateforme (≈ 60) : téléchargement osmdroid (321-371), taille et vidage du cache osmdroid (207-208, 618-619), import SAF (76-79, 279-297). UI (≈ 300) : dialogue MBTiles construit en code (223-277), `DialogueProgression` (703-747), overlays (520-565).

### 2.9 Géométrie : ce qui est portable tel quel en remplaçant `GeoPoint` par `LatLon`

**PileAnnulationGeometrie** — tout le fichier (39-71) : `Instantane` 41-46, `empiler` 53-67, `depiler` 69, `vider` 71.

**EditeurGeometrie**
- Portable :
  - `ModeGeometrie` (32) et l'état `mode` / `pointChoisi` / `sommets` / `trous` (64-74) ;
  - `insererSommetTrou` 261-270 (cœur 262-266) et `insererSommetSurArete` 285-293 (cœur 286-289) ;
  - le prédicat de `avertirSiSommetTrouHorsPolygone` (275) et `effacer` (315-317).
- Logique noyée dans les listeners osmdroid, à extraire en fonctions :
  - fin de glisser du point avec aimantage (110-112) ;
  - suivi en direct d'un sommet (132-133) et fin de glisser avec aimantage (148-153) ;
  - suivi et fin de glisser d'un sommet de trou (208-210, 213-216) ;
  - positions des poignées « + » (166-171, 230-235) et règle « `geometrieChargee` et au moins 3 sommets » (166, 229, 231) ;
  - règle « le corps du polygone avale le tap en édition » (343).
- Non portable : 76-93, 95-188 (création des `Marker`/`Polygon`), 198-248, 252-257, 296-308, 321-372.

**StationsSessionOverlay**
- Portable : sélection 74-77, empreinte 82-87, extraction des points 94-98 et 129-131, cibles 148-150.
- Non portable : rendu 89-146, `liberer` 156-159.

**StationsServeurOverlay**
- Portable :
  - 73-91 (sauf `overlays` 74), 108-112, 205-213 ;
  - 118-199, sauf les toasts (123, 147, 179-183) ; `SimpleDateFormat` (172) et `AtomicInteger` (135) sont à remplacer ;
  - classification 228-256 (sauf 226-227 et 257) ;
  - points 268-271, 287, 289-291, 302 ;
  - 328-335, 343, 357-362, 383-391.

**OccHabViewModel** — tout le fichier, après les quatre remplacements (Calendar 67-70, Gson 76, UUID 111/129, ViewModel 99) :
- types : `OccHabDetailsSession` 34-58, `detailsSessionParDefaut` 63-90 ;
- fonctions : `demarrerSession` 124-130, `nouvelleStation` 140-143, `definirGeometrie` 149-156, `definirSurface` 160, `definirAltitudes` 164-166, habitats 168-177, `majDetails` 182-186, `reprendreSaisie` 191-211, `detailsDe` 215-228, `rattacherASaisie` 234-236, `reprendreStation` 243-253, dérivées 258-267, `figerEmpreinteOrigine` 274-276, `stationAEnregistrer` 281-295.
- Il ne manipule aucun `GeoPoint`.

**MapUtils** — `airePolygoneM2` 246-264 (avec `kotlin.math`) et le calcul de boîte de `zoomerSur` 273-276.

**Hors inventaire, à porter avec le reste** :
- `util/TopologiePolygone.kt` : `milieu` et `pointDansAnneau`. `memePoint` n'a plus d'appelant et la KDoc 44-45 est orpheline.
- `util/GeoJsonCoords.kt` : org.json → `JsonArray`.

**Topologie partagée : il n'y a plus rien à porter.** Le commit de référence cdd5196 a supprimé la propagation entre polygones mitoyens. Il ne reste que l'aimantage par copie de coordonnées (OccHabCarteFragment:512-526, EditeurGeometrie:110 et 149).

**Tests.** `OccHabSommetAimanteIndependantTest` et `OccHabEmpriseStableTest` vérifient le **texte** du code source Android. Ils seront sans objet après le portage : à réécrire en tests de comportement dans commonTest, sur le modèle de `GeoMys-IOSTests/OccHabEditeurTests.swift` et `HitTestGeometrieTests.swift`. `OccHabPolygoneTrouTest` (aire nette, trous) et `OccHabViewModelTest` sont portables.

## 3. Capacités de carte (cahier des charges de la couche carte commune)

Degrés de certitude :
- **sûr** : j'en suis confiant ;
- **probable** : je pense que oui, à vérifier ;
- **incertain** : je ne sais pas, ou plutôt non → prévoir un expect/actual.

« Natif » désigne MapLibre Native Android/iOS ; « compose » désigne maplibre-compose, dont l'API est encore en 0.x et évolue.

| Capacité | Où (fichier:ligne) | Aujourd'hui (osmdroid) | MapLibre / maplibre-compose (estimation) |
|---|---|---|---|
| Fonds raster XYZ en ligne : OSM (a/b/c, z0-19), OpenTopoMap (a/b/c, z0-17), Esri World Imagery (ordre **z/y/x**, z0-19) | MapUtils:112-134, 179-205 ; appliqués dans OccHabCarte:171-184, CarteGeometrie:100-121, Explorer:99/134/327-333, CacheManager:107-151 | `XYTileSource` / `OnlineTileSourceBase`, tuiles de 256 px | Natif : source `raster` avec gabarit `{z}/{x}/{y}` ou `{z}/{y}/{x}` — **sûr**. Compose : style JSON généré en commun (`BaseStyle.Json`) — **sûr** ; source raster déclarée en composable — **probable**. |
| IGN Géoplateforme WMTS (GetTile en KVP, `TILEMATRIXSET=PM`) : PLANIGNV2 en png, SCAN 25 en jpeg via `data.geopf.fr/private/wmts?apikey=ign_scan_ws`, ORTHOPHOTOS en jpeg | MapUtils:207-221 | URL construite en code, zoom 2-19 pour les trois couches | Gabarit avec `{z}`, `{y}`, `{x}` dans la query string — **sûr** (iOS utilise ce gabarit, `MapTileSource.swift`:50-66). Fixer le vrai zoom max du serveur (iOS : 18 pour Plan et SCAN 25, 19 pour Ortho ; Android déclare 19 partout) pour que MapLibre sur-zoome correctement. |
| Sur-zoom au-delà du zoom serveur | implicite dans osmdroid | approximation par les tuiles parentes | `maxzoom` de la source — **sûr**. Le sur-échantillonnage écrit à la main sur iOS (`MapTileSource.swift`:181-300) devient inutile. |
| Changement de fond à chaud, menu de choix | MapUtils:77-104, 162-176 | changement de provider ou de source | Changer de style ou de source — **sûr**. Menu en dialogue Compose. |
| Attribution | déclarée MapUtils:132, 187, 196, mais jamais affichée (aucun `CopyrightOverlay`) | — | Attribution native des sources (ornement) — **sûr** en natif, **probable** dans compose. Gain de conformité. |
| Fond MBTiles hors-ligne | MapUtils:88-101 ; import et suppression CacheManager:223-319 ; `store/MbtilesStore` | `OfflineTileProvider` + `XYTileSource("mbtiles", min, max…)`, `setIgnoreTileSource(true)` | URL `mbtiles:///chemin/absolu` de MapLibre Native — **probable mais à valider sur Android ET iOS** (spike). Métadonnées lues via androidx.sqlite KMP. Si ça échoue : serveur de tuiles local (Ktor), plus lourd. |
| Cache des tuiles en ligne (1 Go, purge LRU à 800 Mo) | `store/MapTileCache` ; taille affichée CacheManager:207-216 ; vidage 609-629 | base `cache.db` de `SqlTileWriter` dans `cacheDir/MapTiles` | Natif : cache « ambient » à taille configurable, avec `clearAmbientCache` / `resetDatabase` — **sûr**. Exposition dans compose — **incertain** (expect/actual sur `OfflineManager` / `MLNOfflineStorage`). Taille occupée : pas d'API connue — **incertain**. |
| Téléchargement d'une zone : emprise visible, du zoom courant à 17, ≤ 200 km², fond actif seulement | CacheManager:321-371 (+ estimation 177-217) | `CacheManager.downloadAreaAsync` | Natif : paquets hors-ligne « tile pyramid » (bornes, zooms, style) — **sûr**. Dans compose — **incertain** (je ne sais pas si une API hors-ligne y est exposée), expect/actual probable. Alternative maîtrisée : téléchargeur Ktor commun qui écrit un MBTiles par zone et par fond, affiché comme source (dépend de la ligne MBTiles). |
| Marqueurs à icône : goutte `ic_pin_drop` ancrée en bas, disques de sommet, poignées « + », `ic_location_pin` teinté, pastille de groupe avec effectif | EditeurGeometrie:353-359, 172-181, 236-243 ; StationsSession:115-126, 132-144 ; StationsServeur:292-299 ; CarteGeometrie:360-378 ; CacheManager:520-535 ; Explorer:420-429, 437-480 | `Marker` + Drawable | `SymbolLayer` (images, `iconAnchor`) ou `CircleLayer` — **sûr** en natif ; images issues d'un `Painter` dans compose — **probable**. |
| **Marqueurs déplaçables** (appui long + glisser, suivi en direct, aimantage au relâcher) | EditeurGeometrie:100-119 (point), 126-163 (sommets), 201-223 (sommets de trous) | `Marker.isDraggable` + `OnMarkerDragListener` | **Absent** de maplibre-compose (**sûr** qu'il n'existe pas d'équivalent direct ; le plugin d'annotations Android n'est pas multiplateforme). À construire : poignées Compose superposées et positionnées par la projection, ou hit-test au long-press + capture du geste + blocage du pan (cf. iOS `OccHabMapView.swift`:463-502). Point dur n°1. |
| Clic sur marqueur, polygone ou polyligne, consommé ou non | StationsSession:107-110, 122, 138-141 ; StationsServeur:283, 298 ; EditeurGeometrie:104, 178, 242, 343 ; CarteGeometrie:373-375, 388, 408 ; Explorer:226, 425-428 | un listener par overlay ; priorité = ordre d'empilement | `onClick` par couche ou `queryRenderedFeatures`, avec `ClickResult.Consume/Pass` — **probable**. L'ordre entre les couches et `onMapClick` est à vérifier. |
| Tap sur la carte → coordonnée | OccHabCarte:277, 561-584 | `MapEventsOverlay` placé sous tous les overlays | `onMapClick(position, offset)` — **sûr**. |
| Appui long sur la carte | OccHabCarte:586 (renvoie `false`, inutilisé) | — | `onMapLongClick` — **sûr** ; pas nécessaire aujourd'hui. |
| Polylignes (couleur, épaisseur, extrémités rondes) | CarteGeometrie:380-392 ; CacheManager:537-549 | `Polyline` | `LineLayer` — **sûr**. |
| Polygones remplis avec contour | EditeurGeometrie:325-344 ; StationsSession:99-111 ; StationsServeur:272-284 ; CarteGeometrie:394-412 ; CacheManager:551-565 | `Polygon` | `FillLayer` + `LineLayer` — **sûr**. |
| Polygones à trous | EditeurGeometrie:328-329 ; StationsServeur:276-277 ; StationsSession:102-103 ; non gérés dans CarteGeometrie:312-317 et CacheManager:553 | `Polygon.holes` | Anneaux intérieurs GeoJSON — **sûr** ; hit-test d'une `FillLayer` qui exclut le trou — **probable**. |
| Contour pointillé | StationsServeur:281 (branche morte) | `DashPathEffect` | `lineDasharray` — **sûr**. |
| Regroupement (clustering) | Explorer:416, 418-431 | grille fixe de 0,001° (≈ 110 m), indépendante du zoom, côté client | Clustering natif d'une source GeoJSON, dépendant du zoom — **sûr** en natif, **probable** dans compose. Pour garder la parité avec iOS (`ExplorerView.swift`:290-300), garder la grille en commun. |
| Info-bulles | désactivées partout (`setInfoWindow(null)` / `infoWindow = null` : EditeurGeometrie:342, 358 ; StationsServeur:282, 297 ; StationsSession:121, 137 ; Explorer:225), sauf le titre des sites (CacheManager:523-532) ; remplacées par un `AlertDialog` (CarteGeometrie:454-458) ou une bottom sheet (Explorer:482-515) | `InfoWindow` | Pas d'info-bulle dans maplibre-compose — **sûr**. Popup Compose ancrée par la projection, ou feuille / dialogue. |
| Boussole (aiguille + bascule « cap suivi ») | CompassView ; OccHabCarte:264-274 ; Explorer:308-325 (aiguille seule, la carte ne tourne pas) | vue maison + capteurs | La boussole native existe mais sa sémantique diffère (retour au nord au tap, masquée à 0°) — **sûr**. Garder une boussole Compose maison (cf. iOS `MapView.swift`:762-823). |
| Rotation de la carte selon le cap de l'appareil | MapCompassController:81-84 ; remise à 0 OccHabCarte:269-273 | `setMapOrientation(-azimuth)` à chaque événement capteur | Bearing de la caméra — **sûr**. Le cap de l'appareil n'est pas fourni par maplibre-compose (à ma connaissance) : expect/actual `CapAppareil`. Lisser ou seuiller (cf. `CapCarte.changementNotable` iOS). |
| Gestes de rotation et d'inclinaison | non activés (pas de `RotationGestureOverlay`) ; iOS `isRotateEnabled=false` (`MapView.swift`:104) | — | Désactivables par les options de gestes — **probable** (nom exact de l'API à vérifier). |
| Pinch-zoom, pan, boutons +/- | OccHabCarte:173-177 ; CarteGeometrie:102-107 ; Explorer:135-140 ; CacheManager:109-114 | `setMultiTouchControls(true)`, `controller.zoomIn/zoomOut` | Gestes par défaut + `animateTo(zoom ± 1)` — **sûr**. |
| Bornes de zoom | CacheManager:115-116 (2 à 17) | `minZoomLevel` / `maxZoomLevel` | Plage de zoom de la carte — **sûr**. |
| Position de l'utilisateur | `creerLocationOverlayBleu` MapUtils:229-238 (OccHabCarte:284-297, CarteGeometrie:112-113, CacheManager:128-142 ; `runOnFirstFix`, `myLocation`) ; Explorer : `LocationManager` + `Marker` plat (163-232) | `MyLocationNewOverlay` + `GpsMyLocationProvider`, icône `ic_gps_blue_dot` | Composant de localisation natif — **sûr**. Module de localisation (« puck ») dans maplibre-compose — **probable mais récent**, à vérifier. Solution sûre : flux de position commun (expect/actual) dessiné en `CircleLayer` ou `SymbolLayer` (`iconRotationAlignment = map` pour l'effet « plat »). |
| Projection écran ↔ coordonnées | aimantage OccHabCarte:515-523 (seuil de 44 dp × densité) | `Projection.toPixels` | `cameraState.projection`, conversions écran ↔ position en dp — **probable / assez sûr**. |
| Emprise visible | Explorer:360, 379 ; CacheManager:183, 322 | `map.boundingBox` | Bbox visible via la projection — **probable**. |
| Événements de caméra (scroll, zoom) | Explorer:125-128 ; CacheManager:164-171 | `MapListener` | Observation de l'état de la caméra (`snapshotFlow`) — **sûr**. |
| Cadrage sur une emprise (boîte de ± offset pour un point seul, garde contre une boîte dégénérée, marge) | MapUtils:271-281 (OccHabCarte:547 : 0,004 / ×1,8 ; CarteGeometrie:521 : 0,002 / ×1,3) ; CacheManager:446 (`zoomToBoundingBox(bbox, true, 60)`) | à faire après mesure de la vue (`post`) | `animateTo(boundingBox, padding)` — **probable / assez sûr**. Calcul de la boîte en commun. |
| Centre et zoom programmatiques, animation, mémorisation du cadrage | OccHabCarte:259, 291-292, 324-325, 530-536, 557-558, 946-950 ; Explorer:150-157, 174-175, 346 ; CacheManager:119-120, 136-137, 386-387 | `controller.*`, `mapCenter`, `zoomLevelDouble` | `CameraPosition` — **sûr**. |
| Vues superposées à la carte (boutons, bandeaux, boussole, panneaux, compteurs, voile de chargement) | `fragment_occhab_carte.xml` (MapView + CompassView + 8 ImageButton + 3 Button), `fragment_explorer.xml` (10 MaterialButton de filtre + CompassView), `fragment_cache_manager.xml`, `fragment_carte_geometrie.xml` ; insets `applyStatusBarMargin` / `applyNavBarMargin` | FrameLayout | `Box` Compose + `WindowInsets` — **sûr**. Sur iOS la carte est une vue UIKit sous Compose : l'interaction des touchers est à valider. |
| Ordre d'empilement | OccHabCarte:277 (événements tout en dessous) ; EditeurGeometrie:123 (forme sous les sommets) ; Explorer:432-433 (position au-dessus) | ordre des overlays | Ordre des couches — **sûr**. |
| User-Agent des requêtes de tuiles | OccHabCarte:151 ; CarteGeometrie:70 ; Explorer:91 ; CacheManager:90 (+ GeoMysApplication) | `Configuration.userAgentValue = packageName` | Configurable en natif (client HTTP Android, configuration réseau iOS) — **sûr**. Non exposé par maplibre-compose à ma connaissance → expect/actual à l'initialisation. |
| Politique de téléchargement | MapUtils:106-110 (2 connexions, téléchargement massif autorisé) | `TileSourcePolicy` | Sans équivalent (planification native). Contrainte juridique : voir points durs. |
| Cycle de vie de la carte | `map.onResume/onPause` dans chaque écran | — | Géré par maplibre-compose — **sûr**. |

### 3.b Comment GeoMys-IOS a résolu la carte (commit 64d303b)

**MapKit, pas MapLibre.**
- La carte est un `MKMapView` dans un `UIViewRepresentable` (`UI/Map/MapView.swift`, 996 l. ; `UI/OccHab/OccHabMapView.swift`, 686 l.).
- Les fonds sont des sous-classes de `MKTileOverlay` :
  - `MapKitOverlayMercator` (`MapTileSource.swift`:181-300) : cache disque maison, sur-échantillonnage sur 3 niveaux, User-Agent via `TuileReseau` ;
  - `MbtilesOverlay` (`MbtilesOverlay.swift`) : SQLite3 en direct, inversion TMS.
- Cache maison par fichiers `MapTiles/<source>/<z>/<x>/<y>.tile` (`Store/MapTileCache.swift`, plafonds 1 Gio / 800 Mio).
- Téléchargeur de zone maison avec `TaskGroup` (`CacheManagerView.swift`:386-460).

**Polygones, gestes et hit-test.**
- Polygones à trous via `MKPolygon(interiorPolygons:)` (`OccHabMapView.swift`:212-231, `CarteGeometrieView.swift`:759).
- Glisser de sommet : un `UILongPressGestureRecognizer` posé sur chaque vue d'annotation, avec `isScrollEnabled=false` pendant le geste (`OccHabMapView.swift`:463-502, 591+).
- Aimantage à 44 pt via `map.convert` (512-519).
- Hit-test maison (`HitTestGeometrie.swift`, 99 l.) : lancer de rayon pair-impair, trous exclus, marge de 22 pt en consultation et de 0 pendant le dessin.
- Arbitrage explicite (`OccHabMapView.swift`:407-441) : pendant le dessin, l'aimantage passe avant la sélection. Android fait l'inverse : la surface d'une station gagne toujours.
- Cap suivi : `camera.heading` (`MapView.swift`:369-383), cap appareil fourni par `LocationTracker.swift`.

**Références directes pour commonMain** (logique déjà pure, sans dépendance à la carte) :
- `OccHabEditeurGeometrie.swift` (341 l. : état, gestes, poignées, pile, textes du bandeau) ;
- `HitTestGeometrie.swift`, `Util/Geo/TopologiePolygone.swift`, `AireGeodesique.swift` ;
- `GeometrieGeoJSON.swift` (lecture et écriture GeoJSON avec trous et MultiPolygon partiel) ;
- `TuileMath` (`MapTileCache.swift`:153-197) et le regroupement de l'Explorer (`ExplorerView.swift`:290-300) ;
- `FondChoisi`, sérialisé exactement comme sur Android (`MapTileSource.swift`:99-125).

Les tests iOS (`OccHabEditeurTests`, `HitTestGeometrieTests`, `MapTileCacheTests`, `OccHabStationsServeurTests`) sont de bons gabarits pour commonTest.

**Inutile si l'on retient MapLibre** : les overlays MapKit (sur-échantillonnage, cache, MBTiles), que MapLibre fournit nativement, sous réserve des lignes MBTiles, cache et téléchargement du tableau.

## 4. Points durs

1. **Glisser des sommets et du point** (EditeurGeometrie:100-163, 201-223).
   - Il n'y a pas d'équivalent dans maplibre-compose. Il faut un mécanisme commun : soit des poignées Compose positionnées par la projection à chaque frame de caméra, soit un hit-test à l'appui long avec capture du geste et blocage du pan.
   - Le polygone doit suivre le doigt en direct (aujourd'hui `redessinerForme` 321-349 à chaque mouvement) et s'aimanter au relâcher.
   - Sur iOS, la carte MapLibre est une `UIView` sous Compose : le partage des touchers entre poignées Compose et carte, appui long compris, est à prototyper tôt.
   - Nombre de poignées : sommets extérieurs + sommets des trous + une « + » par arête.

2. **Arbitrage des gestes, aujourd'hui implicite** (ordre des overlays osmdroid).
   - `MapEventsOverlay` est à l'index 0 (OccHabCarte:277).
   - Les polygones rouges consomment toujours le tap (StationsSession:107-110). Pendant le dessin, un tap dans une station voisine la sélectionne donc, et abandonne le tracé, au lieu d'aimanter.
   - Le corps du polygone édité n'avale les taps qu'en réédition (EditeurGeometrie:343) ; les cercles de sommets laissent passer le tap (StationsSession:122) ; le pin du point le consomme sans effet (EditeurGeometrie:104).
   - iOS a fixé des règles différentes (`OccHabMapView.swift`:407-441). Avec MapLibre, ces règles doivent être écrites en commun (hit-test commun, portage de `HitTestGeometrie.swift`), et l'écart Android/iOS doit être tranché.

3. **Aimantage en pixels écran** (OccHabCarte:512-526).
   - Il faut projeter toutes les cibles à chaque tap et à chaque relâcher : sommets locaux + sommets serveur de tous les JDD affichés, soit potentiellement des milliers.
   - Avec maplibre-compose, chaque conversion traverse la frontière native. Préfiltrer en degrés (44 dp convertis en mètres).

4. **Hors-ligne.**
   - **Cache osmdroid** (`cacheDir/MapTiles/cache.db`, schéma propre à osmdroid) : illisible par MapLibre. Les zones téléchargées par les utilisateurs seront perdues à la migration, sauf conversion maison vers MBTiles (faisable en principe, schéma à vérifier).
   - **Téléchargement de zone** (CacheManager:321-371) : passe aux paquets hors-ligne MapLibre. Leur API diffère entre Android et iOS, leur exposition par maplibre-compose est incertaine, et ils sont liés à un style : il faut un style par fond, généré en commun.
   - **Taille du cache et seuil de 90 %** (CacheManager:207-216) : pas d'équivalent direct connu.
   - **Politique d'usage** : MapUtils:106-110 contourne la politique osmdroid pour autoriser le téléchargement massif, que la politique d'usage des tuiles openstreetmap.org interdit. À arbitrer : exclure OSM du téléchargement, ou changer de fournisseur.

5. **MBTiles.**
   - Le rendu via `mbtiles://` est à valider sur les deux OS.
   - L'import par sélecteur système (SAF `OpenDocument`, CacheManager:76-79 et 279-297 ↔ `UIDocumentPicker` / `fileImporter`) demande un expect/actual.
   - Validation SQLite et métadonnées (MbtilesStore) → androidx.sqlite KMP (driver bundled).
   - Le chemin absolu doit être fourni par la plateforme.

6. **Cap de l'appareil et « carte qui suit la boussole »** (MapCompassController:54-89).
   - La fusion des capteurs Android reste en androidMain ; iOS passe par `CLHeading`.
   - Écart de référence : nord magnétique sur Android (`getOrientation`), nord géographique sur iOS (`trueHeading`).
   - Le bearing change à chaque événement (SENSOR_DELAY_UI) : lisser ou seuiller pour éviter des rafales de recompositions et d'animations de caméra.

7. **Position.**
   - L'Explorer utilise volontairement `LocationManager` GPS + NETWORK (163-213), pour les GPS externes RTK en position fictive (71-73). Ne pas passer à un fournisseur « fusionné » sur Android sans test.
   - OccHab, CarteGeometrie et CacheManager ne demandent jamais la permission ; seuls Explorer, Trace et SaisieRapide le font. Centraliser la permission dans Common.

8. **État OccHab éclaté**, à réunir dans un état unique observable (`StateFlow`) pour Compose. Il est aujourd'hui réparti entre :
   - le fragment : `enCreation`, `geometrieChargee`, `cadrageInitialFait` (OccHabCarte:70, 90, 143) ;
   - l'éditeur : état public mutable (EditeurGeometrie:64-74), écrit directement par le fragment via des propriétés déléguées (77-84) ;
   - deux overlays reliés par leurs `Hote` (98-140) ;
   - le ViewModel, non observable (OccHabViewModel:101-136).

   Environ 20 dialogues et toasts déclenchés au fil du code (OccHabCarte, StationsServeur) deviennent des événements UI.

9. **Asynchronisme lié à la vue.**
   - Altitudes MNT (OccHabCarte:830-855) : lancées dans `viewModelScope` pour survivre à la navigation, appliquées seulement si la station courante n'a pas changé, puis reportées dans le store sur `Dispatchers.IO`.
   - Chargement serveur gardé par `vueVivante` (StationsServeur:128-198) ; callbacks gardés par `isAdded` (CacheManager:334-368).
   - Écritures `commit()` synchrones du store sur le thread UI à chaque relâcher de sommet (`persisterSelectionApresDrag` 652-673 → `OccHabStore.upsertStation`) : à passer en IO sans perdre l'ordre des écritures.

10. **Formatage dépendant de la locale de l'appareil.**
    - `String.format` : CacheManager:189-191 (le `replace(',', ' ')` ne sert qu'en locale anglaise) et 210 ; OccHabStation:166 ; Explorer:416.
    - `SimpleDateFormat` avec `Locale.getDefault()` (OccHabStations:161, 230 ; StationsServeur:310) ou `Locale.FRANCE` (StationsServeur:172 ; OccHabDetails:67).
    - `Collator` français (OccHabDetails:413-416).
    - Pas d'équivalent commun direct : formatage et clé de tri maison, figés en français.

11. **Empreinte de contenu persistée** (`empreinteOrigine`, figée par OccHabViewModel:274-276).
    - Elle est construite par concaténation de `Double.toString` et `List.toString` (model/OccHab.kt:201-234).
    - Pour que les stations importées déjà stockées restent « intactes » après migration, l'implémentation commune doit produire exactement la même chaîne sur Android et sur iOS.
    - Je ne suis pas sûr que `Double.toString` de Kotlin/Native soit identique à celui de la JVM dans tous les cas : prévoir un formateur déterministe et des vecteurs de test issus d'Android.

12. **Rendu GeoJSON hétérogène.**
    - Trous dessinés pleins ou ignorés : CarteGeometrie:312-317, CacheManager:551-565.
    - MultiPolygon OccHab tronqué à sa première partie (`geometryPartielle`, StationsServeur:343-353).
    - Écrire un parseur commun unique (cf. `GeometrieGeoJSON.swift`) avant de réécrire les écrans.

13. **Code mort et documentation obsolète à ne pas porter tels quels.**
    - StationsServeur : variable `orange` (240), pointillé (254), KDoc 215-223.
    - Le texte du bandeau parle encore de stations « orange » (OccHabCarte:611-612), alors qu'aucune n'est plus dessinée en orange.
    - `toutesLesSaisies=false` (StationsSession:73).
    - `TopologiePolygone.memePoint` et sa KDoc orpheline (TopologiePolygone.kt:44-45).
    - KDoc sur la « propagation » (OccHabCarte:717-719).

14. **Couche carte partagée au-delà du périmètre.** `MapUtils`, `MapCompassController` et `CompassView` servent aussi Trace, SaisieRapide et SortieDetail. La façade carte commune doit couvrir tous ces écrans. Comme maplibre-compose est en 0.x, mieux vaut l'isoler derrière cette façade.

**Ajustements proposés aux correspondances de référence**
- **`GeoPoint` → `LatLon` immuable** : d'accord. Concentrer les conversions vers `Position(longitude, latitude)` de maplibre-compose dans la façade carte : l'ordre lon/lat y est inverse de celui de `GeoPoint`.
- **osmdroid → MapLibre** : d'accord, derrière une façade carte commune. Faire d'abord un spike sur quatre sujets : glisser, MBTiles, hors-ligne, User-Agent et cap.
- **SQLite pour MBTiles** : androidx.sqlite (driver bundled) plutôt que SQLDelight, car il s'agit de fichiers arbitraires interrogés par requêtes brutes.
- **`AtomicInteger`** (StationsServeur:135) : c'est un paramètre de sortie ; changer la signature plutôt qu'utiliser atomicfu.
- **Correspondances à ajouter** :
  - `java.text.Collator` → clé de tri maison ;
  - noms de mois français pour `"dd MMM yyyy"` → `MonthNames` personnalisés de kotlinx-datetime.
- **LiveData** est absent du périmètre : ce qu'il faut traiter, c'est l'absence d'observabilité des `var` du ViewModel.

## 5. Compatibilité des données

Les deux applicationId Android sont `fr.ariegenature.geomys` et `fr.ariegenature.public.geomys`, une par flavor (app/build.gradle.kts:46, 50). La compatibilité vaut flavor par flavor.

| Donnée | Emplacement Android | Format | Lu / écrit dans le périmètre | Relecture par GeoMys-Common |
|---|---|---|---|---|
| Fond de carte choisi | SharedPreferences `GeoMys_prefs`, clé `fond_carte` | Chaîne : `OSM`, `OPENTOPO`, `TOPO`, `SCAN25`, `ORTHO`, `ESRI` ou `mbtiles:<nom de fichier>`. Absente ou inconnue → OSM. | MapUtils:50-72 (tous les écrans carte) | Directe : multiplatform-settings sur le même fichier (`SharedPreferencesSettings(getSharedPreferences("GeoMys_prefs", …))`). iOS utilise la même clé et les mêmes valeurs (`@AppStorage("fond_carte")`, `MapTileSource.swift`:99-125). |
| Date de dernière purge des tuiles | `GeoMys_prefs`, `derniere_purge_tuiles` (Long, ms) | — | MapTileCache (hors périmètre) | Sans objet avec MapLibre (abandon). |
| Cartes MBTiles importées | `filesDir/mbtiles/*.mbtiles` (+ `*.mbtiles.tmp` transitoire) | MBTiles standard (SQLite : `metadata(name, value)`, `tiles(zoom_level, tile_column, tile_row en TMS, tile_data)`) | MapUtils:59, 88-101 ; CacheManager:223-319 | Réutilisables tels quels (même `filesDir`). Le chemin se reconstruit à partir du nom stocké dans `fond_carte` et de la racine fournie par la plateforme. |
| Cache de tuiles | `cacheDir/MapTiles/` : `cache.db` (SQLite de `SqlTileWriter`) + éventuels fichiers ; préférences `osmdroid` (GeoMysApplication:59) | Propre à osmdroid | CacheManager:207-216, 609-629 (via MapTileCache) | **Non lisible** par MapLibre : les zones téléchargées sont perdues, sauf outil de conversion (non évalué). Purger à la migration et prévenir l'utilisateur. Préférences `osmdroid` : abandon. |
| Saisies OccHab (« Mes stations ») | SharedPreferences `occhab_store`, clé `saisies_sauvegardees` ; quarantaine `filesDir/occhab_store.corrupt.json` | JSON Gson de `List<OccHabSaisie>`. Clés = noms des propriétés Kotlin (aucun `@SerializedName`), nulls omis. `date`, `dateMin`, `dateMax` en epoch ms ; ids UUID en texte ; `geometryCoordsJson` = chaîne `"[[lon,lat],…]"` sans point de fermeture ; `geometryTrousJson` = chaîne `"[[[lon,lat],…],…]"` ou absent ; `empreinteOrigine` = chaîne d'empreinte. | OccHabCarte:209, 484-486, 669, 809, 849-851, 864 ; StationsServeur:229-237, 331, 357 ; StationsSession:75-76 ; OccHabStation:99-100, 120, 132 ; OccHabStations:125, 168 ; OccHabHabitat:369-370 | Relisible : `@Serializable` avec les mêmes noms et défauts, `Json { ignoreUnknownKeys = true; coerceInputValues = true }` (Gson a pu laisser des `null` dans des champs non nullables, que `OccHabStore.normaliserSaisie` corrigeait). Migration ponctuelle en androidMain depuis la clé SharedPreferences, puis stockage fichier conseillé. Garder la normalisation (UUID SINP manquant, etc.). |
| Stations du serveur pour le hors-ligne | `filesDir/stations_serveur_occhab_v1.json` | JSON Gson `{dateChargement, stations: [OccHabStation], versionFormat}`. Format 2 attendu ; toute autre version est ignorée. | StationsServeur:110, 141, 145, 150, 158 | Relisible avec kotlinx.serialization. Garder le contrôle `versionFormat == 2`, sinon cache vide. |
| Informations obligatoires du relevé précédent | SharedPreferences `gn_config`, clé `gn_occhab_details_prec` | JSON Gson d'`OccHabDetailsSession` : `idDataset`, `nomDataset`, `observateursIds`, `observateursNoms`, `observateursTxt`, `dateMin`, `dateMax`, `idNomCalculSurface`, `idNomObjetGeographique`, `comment` (forcé à null), `masquerStationsServeur`. L'ancien champ `chargerStationsServeur` peut subsister. | Écrit OccHabDetails:394-396 ; lu OccHabViewModel:74-80 | Relisible (`ignoreUnknownKeys`). Garder le booléen « en négatif » : absent ⇒ stations du serveur affichées. |
| Autres clés `gn_config` lues | `gn_occhab_formconfig`, `gn_occhab_id_list_habitat`, défauts de nomenclature, URL, identifiants… | — | OccHabDetails:207-275 ; OccHabHabitat:224, 386 ; StationsServeur:137 | Hors périmètre (GeoNatureConfig). À noter : migration depuis l'EncryptedSharedPreferences `gn_secure` (GeoNatureConfig:40). |
| Empreinte `empreinteOrigine` | dans les saisies OccHab ci-dessus | chaîne produite par `OccHabStation.empreinteContenu()` | figée par OccHabViewModel:274-276 | Compatible seulement si l'algorithme est reproduit à l'octet près (point dur n°11). |
| Non persisté | — | état du ViewModel (station courante, détails, `saisieId`, `dernierCadrageCarte`), pile d'annulation | — | Rien à migrer. Reproduire le comportement « process mort » (OccHabHabitat:110-116, OccHabStation:68-74). |

## 6. Bilan chiffré

**Classement par fichier**

| Catégorie | Fichiers | Lignes brutes | Lignes de code |
|---|---|---|---|
| COMMUN | PileAnnulationGeometrie | 72 | 28 |
| COMMUN-ADAPTÉ | OccHabViewModel | 296 | 152 |
| MIXTE | EditeurGeometrie, StationsServeurOverlay, StationsSessionOverlay, MapUtils | 1 206 | 777 |
| PLATEFORME | MapCompassController | 115 | 76 |
| UI | OccHabCarteFragment (XL), OccHabDetailsDialog (L), ExplorerFragment (L), CacheManagerFragment (L), CarteGeometrieFragment (M), OccHabHabitatFragment (M), OccHabStationsFragment (M), OccHabStationFragment (S), CompassView (S), IconesEditionCarte (S) | 4 509 | 3 240 |
| ABANDON | aucun fichier entier (seulement des sous-parties, colonne « Abandon » ci-dessous) | 0 | 0 |
| **Total (17 fichiers)** | | **6 198** | **4 273** (dont 1 925 lignes de commentaires et lignes vides) |

**Ventilation par nature** (lignes de code, ±10 %)

| Fichier | Logique extractible (commonMain) | Présentation (Compose / couches carte) | Plateforme (expect/actual) | Abandon (imports, binding, colle osmdroid, cycle de vie) |
|---|---|---|---|---|
| OccHabCarteFragment | 290 | 200 | 25 | 85 |
| OccHabDetailsDialog | 153 | 247 | — | 23 |
| OccHabHabitatFragment | 100 | 147 | — | 30 |
| OccHabStationFragment | 52 | 84 | — | 24 |
| OccHabStationsFragment | 66 | 126 | — | 26 |
| OccHabViewModel | 147 | — | — | 5 |
| EditeurGeometrie | 55 | 150 | — | 30 |
| PileAnnulationGeometrie | 26 | — | — | 2 |
| StationsServeurOverlay | 123 | 87 | — | 38 |
| StationsSessionOverlay | 25 | 52 | — | 27 |
| IconesEditionCarte | — | 32 | — | 10 |
| CarteGeometrieFragment | 167 | 199 | — | 31 |
| MapUtils | 105 | 34 | — | 49 |
| MapCompassController | 6 | — | 63 | 7 |
| CompassView | — | 73 | — | 5 |
| ExplorerFragment | 74 | 219 | 91 | 70 |
| CacheManagerFragment | 177 | 297 | 61 | 52 |
| **Total** | **≈ 1 565 (37 %)** | **≈ 1 950 (46 %)** | **≈ 240 (6 %)** | **≈ 515 (12 %)** |

**Lecture du bilan**
- **Logique de géométrie et de carte pure : ≈ 250 lignes** sur les ≈ 1 565 lignes de logique : aire géodésique, emprises, maths de tuiles, parsing GeoJSON, opérations de l'éditeur, aimantage, pile d'annulation, regroupement de l'Explorer. Le reste est de la logique métier OccHab et Monitoring (règles de saisie, persistance, import/réédition, chargements réseau).
- **Couche carte commune à construire en plus** (aujourd'hui fournie par osmdroid) : ≈ 400 à 600 lignes selon les fonctionnalités de maplibre-compose réellement disponibles. C'est une estimation, sans base dans le code existant : façade, style, poignées déplaçables, position, cap, hors-ligne.
- **Présentation à réécrire en Compose : ≈ 1 950 lignes de vues.** L'écran carte OccHab (XL) et la couche d'édition dépendent du point dur n°1.

---
Notes de méthode :
- Lecture seule de GeoMys-Android et de GeoMys-IOS. Aucune commande gradle ni git autre que `git log`.
- Les comptages par plage de lignes ont été faits à la lecture du code.
- Les disponibilités MapLibre / maplibre-compose sont des estimations de mémoire, sans vérification en ligne : le spike carte (voir la synthèse) doit les confirmer.

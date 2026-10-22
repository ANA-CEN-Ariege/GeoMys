# 7. Tests unitaires

> Détail de l'inventaire GeoMys. La synthèse, les chiffres globaux et les décisions à prendre sont dans [`../INVENTAIRE.md`](../INVENTAIRE.md).
> Source analysée en lecture seule : GeoMys-Android `cdd5196` (v1.4.7, 18 octobre 2026). Les équivalents iOS ont été cherchés dans GeoMys-IOS `64d303b`.
> La colonne « Cible » donne une destination indicative ; la structure proposée pour GeoMys est décrite au § 9 de la synthèse.


*Source : GeoMys-Android `cdd5196`, dossier `app/src/test/java/fr/ariegenature/geomys/` (99 fichiers, 15 189 lignes, 718 `@Test`). Classement établi par analyse automatique des imports et du corps des tests, puis vérifié à la main sur les cas ambigus.*

## 7.1 Synthèse

Tous les tests sont des tests JVM (JUnit 4). Il n'y a aucun test instrumenté (`androidTest`). Dans GeoMys, la cible naturelle est `commonTest` (kotlin.test, kotlinx-coroutines-test) : les tests y tournent sur la JVM et sur le simulateur iOS.

| Cat. | Nature | Fichiers | Lignes | @Test |
|---|---|---|---|---|
| T1 | Code de test portable tel quel | 20 | 2566 | 199 |
| T2 | Fixtures JSON à convertir | 9 | 812 | 60 |
| T3 | Robolectric à retirer (Context, SharedPreferences, fichiers, GeoPoint) | 38 | 5386 | 241 |
| T4 | Réseau : MockWebServer → Ktor MockEngine | 19 | 4404 | 151 |
| T5 | Plateforme ou vues Android | 6 | 1059 | 35 |
| T6 | Surtout des gardes textuelles | 6 | 735 | 29 |
| T7 | Format de sérialisation | 1 | 227 | 3 |
| | **Total** | **99** | **15189** | **718** |

Lecture :

- **T1 à T4 (86 fichiers, 13 168 lignes, 651 tests) passent en `commonTest`.** T1 demande seulement de passer de `org.junit` à `kotlin.test`. T2 demande de réécrire les fixtures `JSONObject`/Gson en kotlinx.serialization. T3 demande que le stockage soit abstrait (système de fichiers en mémoire, comme `okio.fakefilesystem.FakeFileSystem`, et réglages en mémoire, comme `MapSettings` de multiplatform-settings) et que `GeoPoint` soit remplacé par un type commun. T4 demande de remplacer MockWebServer par le `MockEngine` de Ktor : les scénarios et les assertions se reprennent, seul le harnais change.
- **Attention : un test portable n'implique pas un code testé portable.** Par exemple, `GpxUtilsTest` (T1) teste `GpxUtils`, qui dépend de `javax.xml`. Le test se reprend tel quel, mais seulement une fois le code porté.
- **T5 (6 fichiers)** porte sur du code propre à Android : `LocationTracker`, `FormulaireRenderer` (vues), `MediaImport`. Ces tests restent en `androidUnitTest` ou sont réécrits contre les nouvelles interfaces.
- **T6 (6 fichiers)** et une partie de T3 et T5 contiennent des **gardes textuelles** : des tests qui lisent le code source Android (`File("src/main/java/…").readText()`) et vérifient la présence ou l'absence d'un motif. Ils protègent des comportements logés dans des Fragments, impossibles à tester autrement. Ils ne se portent pas tels quels. Une fois la logique sortie dans des ViewModels communs, chacun devient un vrai test de comportement (liste au § 7.3).
- **T7 (`GsonRoundTripTest`)** verrouille le contrat Gson et les règles R8. Il disparaît avec Gson. Il doit être remplacé par un test d'aller-retour kotlinx.serialization et, si GeoMys doit relire les données de l'appli existante, par des tests de lecture de fichiers produits par la version Android actuelle (voir la compatibilité des données dans la synthèse).

Légende de la colonne « Ce qui le lie à Android » : `robolectric` = lancé par RobolectricTestRunner ; `context` = `Context`/`ApplicationProvider` ; `prefs` = SharedPreferences ; `fichiers` = `java.io.File`/dossiers temporaires ; `mockwebserver` = serveur HTTP simulé OkHttp ; `gson`/`orgjson` = fixtures JSON ; `osmdroid` = `GeoPoint` ; `coroutines` = `runBlocking`/`runTest` (portable) ; `concurrence` = `java.util.concurrent` (latch, pools) ; `vues` = widgets ou Fragments Android ; `livedata` ; `location` = `android.location` ; `jvm_misc` = API JVM diverses (`String.format`, `::class.java`, `java.util.UUID`, `java.time`…).

## 7.2 Détail par catégorie

#### T1 — Code de test portable tel quel

| Test | Lignes | @Test | Ce qui le lie à Android |
|---|---|---|---|
| `AdditionalFieldVisibiliteTest` | 93 | 7 | — |
| `AnaEvalTest` | 496 | 45 | — |
| `BuildOccurrenceTest` | 125 | 9 | — |
| `ChangeRulesTest` | 228 | 15 | — |
| `CollisionCdNomTest` | 110 | 9 | — |
| `ConstruireGeometrieTest` | 82 | 6 | — |
| `DateHeureDefautTest` | 61 | 4 | — |
| `EstTypeSaisieTest` | 81 | 5 | — |
| `ExtraireNomHeuristiqueTest` | 63 | 6 | — |
| `FilArianeTest` | 74 | 5 | — |
| `FondCarteTest` | 42 | 2 | — |
| `GpxUtilsTest` | 92 | 5 | — |
| `HiddenExprTest` | 329 | 30 | — |
| `HumaniserErreurReseauTest` | 61 | 5 | — |
| `IndexParGroupeTest` | 90 | 3 | — |
| `OcctaxFieldsConfigTest` | 79 | 6 | — |
| `PictoMonitoringTest` | 69 | 6 | — |
| `ResumeOutboxTest` | 130 | 5 | — |
| `ValidationExprTest` | 136 | 17 | — |
| `WidgetMappingTest` | 125 | 9 | — |

#### T2 — Fixtures JSON à convertir

| Test | Lignes | @Test | Ce qui le lie à Android |
|---|---|---|---|
| `AdditionalFieldsParsingTest` | 69 | 3 | gson |
| `AdditionalFieldsServerParsingTest` | 113 | 8 | orgjson |
| `AplatirProprietesTest` | 64 | 4 | orgjson |
| `ExtraireDefautTest` | 76 | 7 | orgjson |
| `FormatGeometrieTest` | 78 | 7 | orgjson |
| `JsonDepuisMapTest` | 60 | 4 | orgjson |
| `MonitoringApiParsingTest` | 127 | 13 | orgjson |
| `SchemaFusionTest` | 136 | 9 | orgjson |
| `SubstituerVariablesModuleTest` | 89 | 5 | orgjson |

#### T3 — Robolectric à retirer (Context, SharedPreferences, fichiers, GeoPoint)

| Test | Lignes | @Test | Ce qui le lie à Android |
|---|---|---|---|
| `CacheCorruptionTest` | 164 | 7 | robolectric,context,prefs,gson,fichiers |
| `ChampsTaxonTest` | 57 | 3 | prefs |
| `EcritureIndexEnEchecTest` | 187 | 9 | robolectric,context,fichiers |
| `EmpreinteExhaustiviteTest` | 118 | 2 | robolectric,jvm_misc |
| `EnvoiOccHabTest` | 166 | 7 | robolectric,context,prefs,coroutines |
| `FiabiliteEnvoiTest` | 101 | 4 | robolectric,context,prefs |
| `FondCartePersistanceTest` | 69 | 4 | robolectric,context,prefs |
| `GeoJsonCoordsTest` | 75 | 4 | robolectric,osmdroid |
| `GeoNatureConfigTest` | 160 | 11 | robolectric,context,prefs |
| `GroupesEtRegnoPlanteTest` | 62 | 2 | robolectric,context |
| `InvalidationServeurTest` | 86 | 3 | robolectric,context |
| `LotCAuditTest` | 89 | 4 | robolectric |
| `MbtilesStoreTest` | 111 | 3 | robolectric,context,fichiers |
| `MonitoringCacheLabelTest` | 90 | 6 | robolectric,context |
| `NomenclatureCacheTest` | 106 | 7 | robolectric,context |
| `OccHabParserTest` | 400 | 11 | robolectric,orgjson,concurrence,jvm_misc |
| `OccHabPolygoneTrouTest` | 276 | 16 | robolectric,osmdroid |
| `OccHabRechercheHabitatTest` | 102 | 5 | robolectric,context |
| `OccHabSaisieStoreTest` | 270 | 15 | robolectric,context,prefs |
| `OccHabStoreRoundTripTest` | 176 | 3 | robolectric,context,prefs,jvm_misc |
| `OccHabViewModelTest` | 184 | 9 | robolectric,gson,jvm_misc |
| `OutboxMonitoringTest` | 143 | 9 | robolectric,context,gson |
| `OutboxRoundTripTest` | 132 | 3 | robolectric,context,jvm_misc |
| `PurgeRechargementApresMajTest` | 141 | 3 | robolectric,context,fichiers |
| `RechargementRequisTest` | 105 | 4 | robolectric,context |
| `ResolutionDansListeTest` | 246 | 15 | robolectric,context |
| `ResolutionParGroupeTest` | 142 | 6 | robolectric,context |
| `SortieStoreNormalisationTest` | 114 | 3 | robolectric,context,prefs,gson,fichiers |
| `SortieStoreTest` | 112 | 7 | robolectric,context,prefs,gson |
| `StationServeurCacheApresEnvoiTest` | 180 | 6 | robolectric,context,prefs,coroutines |
| `StationsServeurCacheTest` | 236 | 13 | robolectric,context,gson,fichiers,jvm_misc |
| `StoreConcurrenceTest` | 98 | 2 | robolectric,context,prefs,concurrence |
| `TaxRefCacheTest` | 199 | 15 | robolectric,context,concurrence |
| `TaxRefServiceVoixTest` | 81 | 3 | robolectric,context,coroutines |
| `TaxonSansCleTest` | 150 | 8 | robolectric,context,fichiers |
| `TopologiePolygoneTest` | 79 | 4 | robolectric,osmdroid |
| `TraceViewModelReleveTest` | 75 | 2 | robolectric,context |
| `VersionApresEcritureGroupesTest` | 104 | 3 | robolectric,context |

#### T4 — Réseau : MockWebServer → Ktor MockEngine

| Test | Lignes | @Test | Ce qui le lie à Android |
|---|---|---|---|
| `AdditionalFieldsChargerTest` | 115 | 3 | robolectric,context,mockwebserver,coroutines |
| `ChargerModulesTest` | 114 | 4 | robolectric,context,mockwebserver,coroutines |
| `CompatibiliteServeurTest` | 200 | 8 | robolectric,context,mockwebserver,coroutines |
| `DatalistHorsLigneTest` | 103 | 2 | robolectric,context,mockwebserver,coroutines,vues |
| `EnvoiReleveAntiDoublonTest` | 230 | 5 | robolectric,context,mockwebserver,coroutines,concurrence |
| `EnvoiSortieConcurrenceTest` | 205 | 3 | robolectric,context,mockwebserver,coroutines,concurrence |
| `EnvoiSortiePreMarquageTest` | 181 | 2 | robolectric,context,mockwebserver,coroutines |
| `GeoNatureAuthTest` | 93 | 4 | mockwebserver |
| `GeoNatureBrowseTest` | 453 | 18 | context,prefs,mockwebserver,gson,fichiers,coroutines |
| `GeoNatureUploadEnvoyerTest` | 379 | 13 | robolectric,context,mockwebserver,coroutines,concurrence |
| `GeoNatureUploadReseauDegradeTest` | 248 | 9 | robolectric,context,mockwebserver,coroutines,concurrence |
| `MediasMonitoringReconciliationTest` | 192 | 4 | robolectric,context,mockwebserver,fichiers,coroutines |
| `MonitoringDatalistDatasetTest` | 130 | 2 | robolectric,context,mockwebserver,coroutines |
| `MonitoringEnvoiTest` | 164 | 3 | robolectric,context,mockwebserver,orgjson,coroutines |
| `OccHabAntiDoublonTest` | 259 | 10 | robolectric,context,mockwebserver,orgjson,coroutines |
| `OutboxEnvoiTest` | 549 | 25 | robolectric,context,mockwebserver,gson,fichiers,coroutines,concurrence,jvm_misc |
| `PertesEtDoublonsEnvoiTest` | 366 | 11 | robolectric,context,mockwebserver,coroutines |
| `PictoCacheTest` | 275 | 21 | mockwebserver,fichiers,jvm_misc |
| `TaxRefSyncPaginationTest` | 148 | 4 | robolectric,context,mockwebserver,coroutines |

#### T5 — Plateforme ou vues Android

| Test | Lignes | @Test | Ce qui le lie à Android |
|---|---|---|---|
| `ChangementDeGroupeTest` | 114 | 3 | robolectric,context,fichiers,vues |
| `DefautsPariteLot2Test` | 213 | 7 | robolectric,context,fichiers,vues |
| `DefautsPortageIosTest` | 158 | 5 | robolectric,context,fichiers,vues,livedata |
| `FormulaireRendererTest` | 384 | 15 | robolectric,context,coroutines,vues |
| `LocationTrackerTest` | 101 | 2 | robolectric,context,location,jvm_misc |
| `MediaImportTest` | 89 | 3 | robolectric,context,fichiers |

#### T6 — Surtout des gardes textuelles

| Test | Lignes | @Test | Ce qui le lie à Android |
|---|---|---|---|
| `ListeIncompleteTest` | 161 | 6 | robolectric,context,fichiers,vues |
| `OccHabEmpriseStableTest` | 63 | 2 | fichiers,vues |
| `OccHabSommetAimanteIndependantTest` | 55 | 2 | fichiers,vues |
| `ProposeEgaleAccepteTest` | 168 | 7 | robolectric,context,fichiers,coroutines,vues |
| `ResolutionPurementLocaleTest` | 122 | 4 | robolectric,context,orgjson,fichiers,coroutines,vues,jvm_misc |
| `SuggestionPorteSonCdNomTest` | 166 | 8 | robolectric,context,fichiers,vues |

#### T7 — Format de sérialisation

| Test | Lignes | @Test | Ce qui le lie à Android |
|---|---|---|---|
| `GsonRoundTripTest` | 227 | 3 | gson |

## 7.3 Gardes textuelles à réécrire en tests de comportement

Chacun de ces tests lit un fichier source Android et vérifie un motif de texte. Dans GeoMys, le comportement protégé doit devenir un test sur le ViewModel ou le service commun qui le porte.

| Test | Fichier(s) Android inspecté(s) | Comportement protégé (d'après le nom du test) |
|---|---|---|
| `ChangementDeGroupeTest.les_deux_ecrans_conservent_le_texte_au_changement_de_groupe` | `ui/SaisieObservationFragment.kt`, `ui/SaisieRapideFragment.kt` | le texte tapé survit au changement de groupe taxonomique |
| `ChangementDeGroupeTest.le_switch_noms_scientifiques_conserve_aussi_le_texte` | idem | le texte tapé survit au changement de mode d'affichage |
| `DefautsPariteLot2Test.l_envoi_occhab_ne_conclut_rien_sur_une_exception_non_typee` | `network/EnvoiOccHab.kt` | une exception non typée ne marque pas la station comme envoyée |
| `DefautsPariteLot2Test.aucune_passerelle_entre_saisie_mono_et_multi` | `res/navigation/nav_graph.xml`, `ui/AccueilFragment.kt`, `ui/TraceFragment.kt`, `ui/SaisieRapideFragment.kt` | les flux mono-taxon et multi-taxons restent cloisonnés |
| `DefautsPortageIosTest.le_bouton_de_trace_ne_redemarre_de_zero_que_sur_une_trace_vide` | `ui/TraceFragment.kt` | la trace GPS n'est remise à zéro que si elle est vide |
| `DefautsPortageIosTest.seul_un_changement_d_identite_reinitialise_les_selections` | `ui/ConfigGeoNatureFragment.kt` | seul un changement d'URL ou d'identifiant efface les sélections |
| `EcritureIndexEnEchecTest.la_synchro_controle_chaque_ecriture_dindex_et_ressort_en_echec` | `network/GeoNatureSync.kt` | chaque écriture d'index TaxRef est vérifiée ; un échec fait échouer la synchro |
| `ListeIncompleteTest.la_synchro_persiste_le_verdict_et_l_ecran_le_prend_en_compte` | `network/GeoNatureSync.kt`, `ui/ConfigGeoNatureFragment.kt` | une liste de taxons incomplète est mémorisée et signalée |
| `ListeIncompleteTest.parametres_n_ouvre_aucune_porte_derobee_vers_les_saisies` | `ui/ConfigGeoNatureFragment.kt`, `res/layout/fragment_config_geonature.xml` | l'écran Paramètres ne donne pas accès aux saisies |
| `OccHabEmpriseStableTest.le_chargement_des_stations_serveur_ne_touche_jamais_a_l_emprise` | `ui/StationsServeurOverlay.kt` | charger les stations serveur ne recadre jamais la carte |
| `OccHabEmpriseStableTest.le_cadrage_ne_se_replie_jamais_sur_les_stations_de_session` | `ui/OccHabCarteFragment.kt` | sans géométrie en cours, la carte garde l'emprise mémorisée |
| `OccHabSommetAimanteIndependantTest.le_drag_d_un_sommet_ne_propage_rien_aux_stations_voisines` | `ui/EditeurGeometrie.kt`, `ui/OccHabCarteFragment.kt` | déplacer un sommet ne modifie que la station éditée |
| `OccHabSommetAimanteIndependantTest.aucun_utilitaire_de_topologie_partagee_ne_subsiste` | `util/TopologiePolygone.kt` | la topologie partagée a été retirée (comportement changé le 2026-10-18) |
| `ProposeEgaleAccepteTest.les_ecrans_retirent_les_propositions_perimees_avant_de_recalculer` | `ui/SaisieObservationFragment.kt`, `ui/SaisieRapideFragment.kt` | les propositions périmées sont retirées avant chaque recalcul |
| `PurgeRechargementApresMajTest.syncrunner_appelle_la_purge_restreinte_et_jamais_la_purge_totale` | `network/SyncRunner.kt` | après une mise à jour, la synchro ne purge que les caches concernés |
| `ResolutionPurementLocaleTest.le_service_taxref_ne_contient_plus_aucun_appel_reseau` | `network/TaxRefService.kt` | la résolution des taxons est purement locale |
| `ResolutionPurementLocaleTest.aucun_chemin_de_saisie_n_ecrit_dans_le_cache_taxref` | `monitoring/form/FormulaireRenderer.kt`, `network/TaxRefService.kt`, les deux Fragments de saisie | aucun écran de saisie n'écrit dans le cache TaxRef |
| `SuggestionPorteSonCdNomTest.les_ecrans_de_saisie_consomment_la_suggestion_et_ne_re_resolvent_pas_son_texte` | `ui/SaisieObservationFragment.kt`, `ui/SaisieRapideFragment.kt` | la suggestion choisie fournit directement son cd_nom |
| `TaxonSansCleTest.la_synchro_persiste_l_index_des_noms_scientifiques` | `network/GeoNatureSync.kt` | la synchro enregistre l'index des noms scientifiques |

Ce relevé automatique en compte 19 avec certitude. Deux ou trois tests voisins de ces fichiers appellent la même fonction utilitaire de lecture sans être des gardes ; ils sont classés avec leur fichier.

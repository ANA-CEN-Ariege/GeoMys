# Lot 5 : socle des écrans, Accueil et Paramètres

*18 octobre 2026. Portage depuis GeoMys-Android `cdd5196`, inchangée depuis ; allure iOS reprise de GeoMys-IOS `64d303b`. Code dans `ui`, sauf la logique de Paramètres, passée dans `core`.*

Le lot 5 pose le socle de l'interface (`INVENTAIRE.md` § 10) :
- le thème sombre et la navigation typée ;
- les composants : boutons, champs, menus de choix, cartes, dialogues, messages brefs, bandeau de navigation ;
- l'Accueil et les Paramètres, avec la liste des taxons d'une liste (Paramètres › Détails).

**Décision 15, précisée au début du lot.** Sous Android, les écrans reproduisent GeoMys-Android : layouts XML, styles Material Components, couleurs, dimensions. Sous iOS, ils prennent les styles de GeoMys-IOS : police système, symboles SF, contrôles UIKit, cartes translucides. Les éléments, leur ordre, les textes et les comportements restent ceux de GeoMys-Android sur les deux plateformes, sauf ce qui dépend vraiment de la plateforme (§ 6).

**État.** Tout le contenu du lot est porté : 467 tests sur la JVM, 460 sur le simulateur iOS (441 et 435 au lot 2).

**Critère de sortie** (configuration et synchro depuis l'interface) : **atteint le 18 octobre** sur le Galaxy A12 et l'iPhone 16, contre le serveur GeoNature 2.17.5 des lots 1 et 2 (§ 8).

## 1. Ce qui est porté

| GeoMys-Android (`cdd5196`) | GeoMys-IOS (`64d303b`) | GeoMys Common |
|---|---|---|
| `AccueilFragment.kt`, `fragment_accueil.xml`, `menu_accueil.xml` | `UI/AccueilView.swift` | `ui/…/accueil/AccueilScreen.kt` |
| `ConfigGeoNatureFragment.kt`, `fragment_config_geonature.xml` | `UI/ConfigGeoNatureView.swift` | `ui/…/parametres/ParametresScreen.kt` et `ParametresViewModel.kt` ; règles dans `core/…/store/ReglesSelectionOcctax.kt` et `ParametresGeoNature.kt` |
| `TaxonsListeFragment.kt`, `fragment_taxons_liste.xml`, `item_taxon_list.xml` | `UI/TaxonsListeView.swift` | `ui/…/parametres/TaxonsListeScreen.kt` ; liste et recherche dans `core/…/store/ListeTaxons.kt` |
| Dialogue « Taxons par listes » (`montrerTaxonsParListe`) | `TaxonsParListesVue` | `DialogueListe` ; contenu par `taxonsParListes()` (core) |
| `FilAriane.kt` (`appliquerBandeauNavigation`), `view_bandeau_saisie.xml` | `UI/FilAriane.swift` | `ui/…/composants/FilAriane.kt` |
| `Couleurs.kt`, `colors.xml`, `styles.xml`, `themes.xml` | `Theme/Couleurs.swift`, `ButtonStyles.swift`, `ComposantsAndroid.swift` | `ui/…/theme/` et `ui/…/composants/` |
| `MainActivity` : redirection vers Paramètres | `ContentView.swift` | `ui/…/navigation/NavigationGeoMys.kt` |
| `nav_graph.xml` | `Navigation/Route.swift` | routes `@Serializable` de `NavigationGeoMys.kt` |
| préférence `GeoMys_prefs` / `enregistrer_trace` | `@AppStorage("enregistrer_trace")` | `core/…/store/PreferencesGeoMys.kt` |

Les écrans des lots suivants (saisies, Suivis, OccHab, Explorer, Maps Manager) ouvrent un écran d'attente qui nomme le lot qui les apportera (`EcranAVenir`, le `PlaceholderEcran` de GeoMys-IOS). Le prototype de connexion des lots 0 et 1 disparaît, remplacé par Paramètres. Les prototypes carte et mesure TaxRef ne s'ouvrent plus que par paramètre de lancement.

## 2. Deux allures, un seul code d'écran

`Apparence` (`ANDROID` ou `IOS`) est posée par `GeoMysTheme` selon la plateforme. Les écrans sont communs. Chaque composant de `ui/…/composants` dessine l'une ou l'autre allure, à partir de la même API. Quand la mise en page diffère vraiment (espacements des piles SwiftUI contre marges XML), l'écran branche sur `estIos` ou sur `Modifier.margesAndroid`.

Ce que chaque plateforme dessine elle-même (`Natifs.kt`, `expect`/`actual`) :

| Élément | Android | iOS |
|---|---|---|
| Interrupteur | `Switch` Material 3 de Compose, même spécification que `MaterialSwitch` | `UISwitch` de UIKit, teinté du bleu primaire, intégré par `UIKitView` |
| Indicateur d'activité | arc de la ProgressBar Material (4/48 de la boîte, orange `colorControlActivated`, extrémités carrées) | `UIActivityIndicatorView` « medium », réduit d'un cinquième pour `.controlSize(.small)` |
| Icônes | drawables vectoriels de GeoMys-Android et de Material Components 1.12 | symboles SF, rendus par iOS (`UIImage(systemName:)`) |
| Alertes | dialogue Compose aux mesures de l'AlertDialog d'AppCompat | `UIAlertController`, boutons teintés en jaune |
| Messages brefs | `Toast` d'Android | capsule de GeoMys-IOS (noir à 85 %, 64 pt au-dessus du bas), dessinée par Compose |
| Permission de notification | demandée au premier « Charger les données » (Android 13 et suivants) | sans objet |

**Police.** Sous Android, Roboto du système, comme GeoMys-Android, avec la marge haute et basse des TextView (`includeFontPadding`). Sous iOS, la police système (SF Pro), à taille fixe comme les `.font(.system(size:))` de GeoMys-IOS : la taille du texte réglée dans iOS ne s'applique pas. L'idée d'embarquer Roboto sous iOS est abandonnée.

**Interrupteur iOS et fond.** Une vue UIKit intégrée se place sous le canevas Compose, dans une découpe transparente. Autour des coins arrondis de l'`UISwitch`, on verrait donc ce qui est derrière Compose : du noir. La vue Compose est rendue non opaque (`opaque = false`), et la vue SwiftUI hôte (`iosApp/ContentView.swift`) peint le même dégradé dessous. L'indicateur d'activité, posé sur des boutons colorés, est au contraire placé au-dessus du canevas (`placedAsOverlay`).

**Coins continus.** Les `RoundedRectangle(…, style: .continuous)` de SwiftUI (logo, menu, dialogue) sont reproduits par `FormeContinue`, qui suit le tracé d'`UIBezierPath`.

## 3. Composants

| Composant | Android (Material Components 1.12) | iOS (GeoMys-IOS) |
|---|---|---|
| `BoutonContour` | `Widget.GeoMys.Button.OutlinedButton` : contour jaune de 1 dp, marges 24 × 6 dp, retrait de 4 dp, 48 dp de haut au moins | `GeoMysBoutonOutlineStyle` : pilule pleine largeur, 14 pt medium |
| `BoutonPlein` | `Widget.Material3.Button`, grisé quand il est désactivé | `GeoMysBoutonBleuStyle`, estompé à 40 %, appui long de 0,6 s |
| `BoutonTexte` | `Widget.GeoMys.Button.TextButton` | bouton SwiftUI, 15 pt medium |
| `ChampTexte`, `ChampMotDePasse` | `TextInputLayout` OutlinedBox (étiquette flottante, texte d'aide, œil, croix d'effacement) | `ChampTexteSaisie` : libellé de 13 pt au-dessus, champ sur fond blanc 8 % |
| `ChampSelection` | `ExposedDropdownMenu` : filtre de l'`ArrayAdapter`, liste entière au toucher, liste d'`AutoCompleteTextView` (§ 3 bis) | `AutocompleteRow` : pastille à croix, suggestions dessous |
| `CarteSection` | `MaterialCardView` M3 Outlined, coins de 12 dp, élévation 2 dp | carte blanc 7 %, coins de 12 pt, éléments espacés de 12 pt |
| `BandeauAvertissement` | `cadreColore` | `bandeauOrange` |
| `DialogueAlerte` | AlertDialog d'AppCompat : carte gris 800 de 95 % de la largeur moins 32 dp, coins de 2 dp, voile à 32 % | alerte système |
| `DialogueListe` | AlertDialog à en-tête personnalisé et `setItems` | `DialogueMaterial`, monté du bas comme un `fullScreenCover` |
| `BandeauNavigation` | `view_bandeau_saisie` | `FilAriane` |

**§ 3 bis. La liste déroulante d'Android est écrite à la main.** L'`ExposedDropdownMenu` de Material 3 (androidx 1.5.0-alpha22) faisait planter l'appli sur le Galaxy A12 quand on touchait un choix des Paramètres :
- l'ouverture du clavier réduit la fenêtre, et la position du menu est recalculée avec l'ancienne hauteur de liste ;
- son dernier repli appelle alors `coerceIn` sur un intervalle vide : `IllegalArgumentException: Cannot coerce value to an empty range: maximum -73 is less than minimum 90` ;
- le menu de l'accueil n'est pas concerné : son calcul de position prévoit ce cas.

`ListeDeroulanteAndroid` reprend la règle du `ListPopupWindow` d'un `AutoCompleteTextView` :
- largeur du champ ;
- dessous si la liste y tient (décalage de −8 dp, qui chevauche le bas du champ), sinon dessus, à 8 dp du champ (`PopupWindow.findDropDownPosition`) ;
- hauteur limitée à l'espace visible du plus grand côté, clavier déduit (`getMaxAvailableHeight`) ;
- Retour ferme la liste avant le clavier (`onKeyPreIme`).

Elle ne peut pas lever d'exception. Elle est plus fidèle, aussi : pas de marge intérieure, lignes `simple_dropdown_item_1line`.

## 4. Navigation

- **Routes typées** : `RouteAccueil`, `RouteParametres`, `RouteTaxonsListe(idListe, nomListe)`, `RouteAVenir(titre, lot)`.
- **Transitions.**
  - Android : aucune, le `nav_graph.xml` de GeoMys-Android n'en déclare pas.
  - iOS : celles d'une NavigationStack (glissement latéral et voile), fournies par navigation-compose.
- **Retour.**
  - Android : le bouton Retour garde son comportement. Dans Paramètres, il est refusé tant que la configuration est incomplète, avec le message d'Android.
  - iOS : GeoMys-IOS masque le bouton retour de ses écrans, ce qui désactive aussi le balayage depuis le bord. On en sort par la coche, « Retour » ou la maison du bandeau. Le balayage est donc avalé (`BalayageRetourIosDesactive`).
- **Lancement.**
  1. Une mise à jour qui exige un rechargement arme son drapeau (`armerRechargementSiRequis`).
  2. Si la configuration est incomplète, Paramètres s'ouvre d'office, sans animation.
  3. Le paramètre caché `ecran=accueil` saute la redirection (essais d'affichage) ; `ecran=parametres` l'impose.

## 5. La logique de Paramètres passe au socle

Sous Android, les règles vivaient dans le fragment et dans les adaptateurs de ses menus. Elles deviennent des fonctions du socle, testées sans écran :
- `ReglesSelectionOcctax` : jeux de données proposés, liste imposée par le jeu de données, liste unique choisie d'office, observateur par défaut, purge des sélections fantômes ;
- `enregistrerConnexion` (`sauvegarderChamps`) : invalidation de la session, purge au changement d'identité, réinitialisation des sélections ;
- `texteBandeauRechargement`, `messageListe`, `compteursCache`, `taxonsParListes` ;
- `ListeTaxons` : la liste d'une liste de taxons et sa recherche mot à mot.

`ParametresViewModel` reprend l'état que le fragment posait sur ses vues : visibilité des trois boîtes, textes, couleurs, oubli visuel de la connexion à la frappe, suivi de `SyncRunner.etat`. « Charger les données » appelle `SyncRunner.lancer()` : service au premier plan sous Android, tâche de fond sous iOS (lot 2).

## 6. Écarts assumés

**Allure iOS de GeoMys-IOS, là où elle diffère d'Android :**
- choix du jeu de données, de la liste et de l'observateur : pastille à croix et recherche « contient », 20 suggestions au plus, rien tant que la recherche est vide ;
- liste imposée : son nom et la mention verte, plutôt qu'un champ grisé ; « Aucun proposé (droits CRUVED) » quand aucun jeu de données n'est proposé ;
- indicateur dans le bouton (« Test en cours… », « Synchronisation… ») plutôt qu'une ProgressBar dessous ;
- boutons de saisie de l'accueil désactivés à 16 % : GeoMys-IOS estompe deux fois à 40 % ;
- point vert de configuration valide en `#43A047` (`#4CAF50` sous Android) ;
- logo plein cadre aux coins continus ;
- statut de Paramètres avec symbole SF plutôt que les glyphes ✓ et ⚠ ;
- menu de l'accueil : le flou `.regularMaterial` est rendu par sa teinte sombre, Compose ne sachant pas flouter ce qui est dessous.

**Textes de GeoMys-Android sur les deux plateformes.** « Saisie mono-taxons », « Connexion non testée — cliquez sur… », « Rechargement complet forcé (taxons) », les deux textes du bandeau, « cd_nom: », le sous-titre du dialogue avec les taxons « via protocoles ».

**Dépend de la plateforme.** Sous iOS, une adresse en `http://` affiche le texte de GeoMys-IOS : iOS refuse le trafic en clair (sauf réseau local, que `Info.plist` autorise pour les essais).

**Pas repris :**
- de GeoMys-IOS, ce qu'Android n'a pas : « Utilisateur connecté : … » ;
- d'Android, ce qui ne s'affiche jamais : le champ manuel `id_liste` et deux textes d'erreur, toujours masqués.

**Défauts d'Android non reproduits :**
- une liste imposée absente de `/biblistes` était purgée par le second peuplement des menus (`restaurerCaches` appelle deux fois `peuplerSpinnerListes`) : elle reste choisie ;
- la liste de taxons gardée en mémoire n'était jamais oubliée : elle l'est après une synchro ou un « Vider le cache ».

**Pas encore là :**
- les envois en attente (pastilles du menu) restent à zéro jusqu'aux stores de saisie (lots 3, 6 à 8) ;
- la vérification des mises à jour sur l'accueil (variante GitHub de GeoMys-Android) attend le lot 10.

## 7. Tests

- `ReglesSelectionOcctaxTest` (11 cas) et `ParametresGeoNatureTest` (14 cas), sur la JVM et le simulateur iOS.
- `GardeParametresTest` (JVM) : la garde textuelle de GeoMys-Android `parametres_n_ouvre_aucune_porte_derobee_vers_les_saisies`, reportée du lot 2. Elle lit maintenant l'écran Compose et son ViewModel. Son volet « bandeau » est un vrai test dans `ParametresGeoNatureTest`.
- Les deux cas d'`EstTypeSaisieTest` sur `estObjetDeNiveauVisite` attendent les écrans Suivis (lot 7).
- Liste déroulante d'Android, sur l'émulateur (Pixel 10), dans un écran d'essai temporaire au contexte des Paramètres, avec 531 jeux de données : ouverture avec le clavier, filtrage, choix, clavier refermé liste ouverte, rotation liste ouverte. Aucun plantage.

## 8. Critère de sortie

L'utilisateur a fait la recette sur les deux téléphones, avec la connexion enregistrée aux lots 1 et 2 :
1. au lancement, les Paramètres s'ouvrent d'office : aucun jeu de données, liste ni observateur n'est encore choisi ;
2. choix du jeu de données, de la liste et de l'observateur ;
3. « Charger les données » : la synchro se lance depuis l'écran ;
4. la coche valide : retour à l'accueil, boutons de saisie actifs.

**Plantage corrigé en cours de recette.** Sur l'A12, les choix plantaient l'appli quand le clavier s'ouvrait (§ 3 bis). Cinq occurrences sont relevées dans le journal du téléphone, de 22 h 35 à 22 h 44. Aucune depuis la correction.

L'allure des deux plateformes a été validée par l'utilisateur : celle de GeoMys-Android sur l'A12, celle de GeoMys-IOS sur l'iPhone.

## 9. Reste à faire

- **Lot 3** : envois, et avec eux les pastilles « en attente » de l'accueil.
- **Écrans des lots 6 à 9** : chacun remplace son écran d'attente, avec les mêmes composants.
- **Alerte d'échec d'écriture** (`AlerteStockage`) : avec les stores de saisie qui la déclenchent (lot 3).

## 10. Outils et versions

- navigation-compose 2.10.0-rc01, lifecycle-viewmodel-compose et lifecycle-runtime-compose 2.11.0 (JetBrains), plugin kotlinx-serialization dans `ui`.
- androidx.activity-compose dans `ui` (Android) pour la demande de permission.
- Ressources : drawables vectoriels de GeoMys-Android (`ic_menu`, `ic_settings`, `ic_walk`, `ic_home`, `coche`, `coche_orange`) et de Material Components 1.12 (`mdc_*`, Apache 2.0) ; logo de GeoMys-IOS réduit à 360 px (`logo_geomys_ios.png`).
- Paramètres de lancement : `ecran=accueil`, `ecran=parametres`.

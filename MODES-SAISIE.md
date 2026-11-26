# Mode pro et mode simplifié : les différences

État au 26 novembre 2026 (version 2.2.2, build 228).

**À tenir à jour.** Toute modification qui fait diverger ou converger les deux modes se reporte ici, et dans la page destinée aux utilisateurs [`MODES-SAISIE.html`](MODES-SAISIE.html), dans le même commit. Les divergences se retrouvent dans le code par `ModeSaisie`, `simplifie`, `occtaxSeul` et `donneesSimplifiees` ; le détail des décisions est dans [`LOT-9.md`](LOT-9.md) § 43 à 51, 55 à 62, 64 et 65.

## En bref

| | Mode pro | Mode simplifié |
|---|---|---|
| Public | Naturalistes habitués à GeoNature | Utilisateurs amateurs |
| Modules | Saisie multi-taxons, saisie mono-taxon, Monitoring, OccHab | Saisie multi-taxons seulement |
| Champs de l'observation | Tous ceux que le serveur montre | Nombre (et nombre max), comportement, sexe, stade de vie, type d'observation, état biologique, commentaire, photo, audio |
| Données chargées | Tout | Seulement ce qui sert à la saisie multi-taxons |

Depuis le 20 novembre 2026, une nouvelle installation démarre en mode simplifié. Une appli déjà utilisée (un serveur configuré, y compris par GeoMys-Android) garde à la mise à jour le mode pro qu'elle avait par défaut ; un mode choisi reste. Le mode est enregistré au premier démarrage, puis ne change que par les Paramètres.

## Le choix du mode

- Il se fait en tête des Paramètres, dans la carte « Mode de saisie » (Pro ou Simplifié).
- Il est enregistré aussitôt, dans les préférences de l'appli (`PreferencesGeoMys.modeSaisie`) et non dans la configuration du serveur : ni un changement de serveur ou de compte, ni « Vider le cache » n'y touchent.
- Le mode est lu à l'ouverture d'un relevé : un relevé ouvert garde le mode qu'il avait.

## Accueil

| | Mode pro | Mode simplifié |
|---|---|---|
| Boutons de saisie | « Saisie multi-taxons » et « Saisie mono-taxon » | Un seul, « Démarrer une saisie » (ouvre la saisie multi-taxons), plus haut, avec un « + » |
| Configuration incomplète | Boutons estompés | Bouton estompé et, dessous, « Pour commencer, renseignez la connexion dans les Paramètres », qui ouvre les Paramètres |
| « Monitoring » | Si des protocoles sont chargés | Jamais |
| « OccHab » | Si le serveur l'a et que l'utilisateur peut créer | Jamais |
| Menu : « Mes saisies », « Explorer », « Maps Manager », « Aide » | Oui | Oui |
| Menu : « Mes visites », « Mes stations » | Si le module est là, ou s'il reste quelque chose à envoyer | Seulement s'il reste quelque chose à envoyer |

## Saisie multi-taxons

La carte, la trace GPS, le placement des observations, « Mes saisies » et l'envoi sont **identiques** dans les deux modes. Diffèrent les détails du relevé et la saisie d'une espèce.

### Les détails du relevé

Ce sont les détails d'un relevé (bouton « Détails ») et les détails communs de la saisie (bouton « i » de la carte).

| | Mode pro | Mode simplifié |
|---|---|---|
| Observateurs | Liste pour en ajouter, croix pour en retirer | Montrés sans pouvoir en ajouter ni en retirer : l'observateur par défaut des Paramètres |
| Habitat | Recherche d'un habitat, si le serveur montre le champ | Absent ; un habitat déjà porté par le relevé est gardé |
| Jeu de données | Tous ceux où l'utilisateur peut saisir. Pas de croix pour vider le choix : il se change en touchant le champ (depuis le 20 novembre 2026) | Seulement ses jeux de données personnels, s'il en a ; sinon tous. Pas de croix non plus |
| Autres champs (dates, heures, commentaire, etc.) | Oui | Oui, identiques |
| Validation | Bouton rond ✓ en bas au milieu de l'écran, sous la zone qui défile (depuis le 17 novembre 2026 ; avant, coche verte en haut à droite) | Identique |

Un jeu de données personnel est un jeu dont l'utilisateur connecté est acteur en personne (`cor_dataset_actor.id_role`) : GeoNature en crée un à l'inscription d'un compte, si le serveur l'a prévu. Un compte sans jeu personnel garde la liste complète, pour ne pas bloquer la saisie. Les acteurs sont lus au chargement des données : un téléphone chargé avant cette règle garde la liste complète jusqu'au prochain rechargement. Un jeu déjà porté par un relevé est gardé.

Deux cas gardent le champ des observateurs du mode pro, pour que le relevé reste validable : un relevé sans aucun observateur, et un serveur qui demande les observateurs en texte libre.

### Après l'ajout d'une espèce

| | Mode pro | Mode simplifié |
|---|---|---|
| Ce qui s'ouvre | La « Fiche de l'observation », à chaque espèce | Identique |
| Champs additionnels obligatoires vides | Rien de plus : ils sont sur la fiche | Identique |
| Oiseau en période de nidification | Rien de plus : le comportement est en tête de la fiche (depuis le 19 novembre 2026 ; avant, la section « Avancé », qui le portait, s'ouvrait dépliée) | Rien de plus : le comportement et « Chant » sont sur la fiche |

### La ligne d'une espèce dans le relevé

| | Mode pro | Mode simplifié |
|---|---|---|
| Boutons | Un crayon, qui ouvre la fiche ; toucher le nom l'ouvre aussi | Identique |
| Champ obligatoire vide | Le crayon est rouge | Identique |
| Corbeille | Oui, avec confirmation | Identique |
| « Terminer le relevé » (✓ de l'écran des espèces) | Bouton rond ✓ en bas au milieu de l'écran, sous la zone qui défile (depuis le 17 novembre 2026 ; avant, coche verte en haut à droite) | Identique |

Depuis le 16 novembre 2026, la ligne d'espèce est la même dans les deux modes : en mode pro, le crayon remplace « × N » (Dénombrement) et « i » (Caractérisation). Le nombre n'est plus lisible dans la liste.

### La fiche de l'observation

Dans les deux modes, un seul écran, « Fiche de l'observation », porte tous les champs de l'espèce ; son ✓ les contrôle et les écrit, et le retour demande avant d'abandonner une fiche modifiée. Depuis le 17 novembre 2026, ce ✓ est un bouton rond en bas au milieu de l'écran, sous les champs, dans les deux modes (avant, une coche en haut à droite). Les champs diffèrent.

Depuis le 20 novembre 2026, son titre est « Observation : » suivi, sur la même ligne, du nom français de l'espèce seul : celui qui a été choisi, ou, pour une espèce choisie par son nom latin, son nom usuel dans TaxRef. Une espèce sans nom français garde son nom latin. Le nom latin n'est plus sous le titre (il reste sous l'espèce dans la liste du relevé), et le bandeau finit toujours par « Fiche de l'observation ».

**Mode pro**, les champs du web de GeoNature (un seul écran depuis le 16 novembre 2026 ; avant, deux écrans, Dénombrement et Caractérisation, que la saisie mono-taxon a gardés jusqu'au 24 novembre 2026). Depuis le 19 novembre 2026, l'ordre s'écarte du web à la demande de l'utilisateur, et les champs vont par deux sur deux colonnes :

- **En tête** : comportement et statut biologique (que le web range sous « Plus de champs »), puis technique d'observation et état biologique.
- **Dénombrement** : objet et type du dénombrement, nombre min et nombre max, stade de vie et sexe, médias, champs additionnels ; plusieurs dénombrements par espèce.
- **« Plus de détails »** (la section « Avancé » du web, repliée) : déterminateur, méthode de détermination, statut d'observation, naturalité, statut de la source, floutage, preuve d'existence, preuves numérique et non numérique, commentaire ; puis les champs additionnels.

En saisie mono-taxon, depuis le 24 novembre 2026, la même fiche règle les valeurs de la série, sans les médias ; depuis le 25 novembre 2026, le « i » de la carte la rouvre pour la suite de la série (voir « Ce qui ne dépend pas du mode »).

**Mode simplifié**, un choix réduit de champs, dans cet ordre (le comportement et « Chant » juste sous les nombres depuis le 18 novembre 2026 ; sur la fiche elle-même depuis le 16 novembre 2026 ; avant, des boutons « Détails », « Comportement » et « Commentaire » ouvraient chacun un écran) :

| Champ | Ce qu'il porte | Présent si |
|---|---|---|
| Nombre, Nombre max | Les effectifs du premier dénombrement. Un seul champ « Nombre » d'abord, sur la moitié de la largeur ; « ＋ Nombre max » déplie le max (« Nombre » devient « Nombre min »), déjà déplié s'il diffère du min. Replié, le max suit le min (depuis le 18 novembre 2026) | Le serveur montre `count_min`, `count_max` |
| Comportement | Le comportement | Le serveur montre `behaviour` et l'espèce est un animal |
| Chant (sous « Comportement ») | D'un toucher, choisit un comportement de chant dans le champ, ou l'en retire : l'indice « Mâle chanteur en période de reproduction » si l'espèce est en période de nidification et que le serveur a les codes atlas, sinon « Chant » | L'espèce est un oiseau et le serveur a la valeur qui convient |
| Sexe, Stade de vie | Les listes du serveur pour l'espèce | Le serveur montre le champ ; le sexe pour un animal seulement |
| Type d'observation | La « Technique d'observation » de GeoNature (`METH_OBS`), sous un autre libellé ; valeurs, défaut du serveur et envoi inchangés (depuis le 18 novembre 2026) | Le serveur montre `obs_tech` |
| État biologique | La liste du serveur pour l'espèce | Le serveur montre le champ |
| Champs additionnels obligatoires | Ceux de l'occurrence et du dénombrement | Le serveur en a pour le jeu de données |
| Commentaire | Le commentaire de l'observation | Le serveur montre `comment_occ` |
| Photo | Appareil photo ou galerie | Le serveur active les médias |
| Audio | Choix d'un fichier son | Le serveur active les médias |

Depuis le 20 novembre 2026, sexe et stade de vie, type d'observation et état biologique vont par deux sur deux colonnes, comme sur la fiche du mode pro. Un champ absent décale les suivants : pour une plante, sans sexe, le stade de vie partage sa ligne avec le type d'observation, et l'état biologique prend toute la largeur.

Dans les deux modes, et donc en saisie mono-taxon, les listes de nomenclature de la fiche n'ont plus de croix rouge pour vider le choix (depuis le 19 novembre 2026) : un choix se change, il ne se vide plus. Les champs additionnels gardent la leur, ainsi que les détails du relevé, sauf le jeu de données.

En mode simplifié, une photo ou un son est écrit aussitôt, sans attendre le ✓, et une fiche validée sans rien changer n'écrit rien. En mode pro, les médias s'écrivent au ✓ avec le reste, et le ✓ écrit toujours les valeurs affichées, défauts du serveur compris.

### Règles propres au mode simplifié

- **Un seul dénombrement.** La fiche ne touche que le premier ; les autres, saisis en mode pro, sont conservés tels quels.
- **Champs selon le groupe taxonomique.** Le sexe et le comportement ne sont proposés que pour un animal. Une nomenclature sans aucune valeur pour le taxon est masquée. En mode pro, tous les champs restent, comme sur le web.
- **Champs non montrés.** Ils gardent leur valeur ; jamais saisis, ils restent vides et le serveur y met sa valeur par défaut (statut d'observation, objet du dénombrement, etc.).
- **Champs additionnels facultatifs.** Ils ne sont pas proposés ; seuls les obligatoires le sont, sur la fiche.
- **Observation déjà envoyée.** « Photo » et « Audio » sont grisés, avec une phrase d'explication.

## Paramètres et chargement des données

| | Mode pro | Mode simplifié |
|---|---|---|
| « Charger les données » | Tout : OccTax, Suivis, OccHab | OccTax seulement |
| Suivis (modules, schémas, objets, droits, listes de taxons des protocoles) | Chargés | Non chargés |
| OccHab (jeux, habitats, configuration, défauts, stations) | Chargé | Non chargé |
| TaxRef | Listes d'OccTax et des protocoles | Listes d'OccTax seulement |
| Résumé du chargement | Compteurs (taxons, protocoles, nomenclatures, listes, observateurs, stations OccHab) et bouton « Détails » | Ni compteurs ni « Détails » ; restent la version du référentiel et « Vider le cache » |

Chargés dans les deux modes : jeux de données, listes, observateurs, champs additionnels et configuration d'OccTax, habitats d'OccTax, nomenclatures, détection des modules.

| Jeu de données par défaut | Tous ceux où l'utilisateur peut saisir, aucun choisi d'office | Seulement ses jeux personnels, s'il en a ; un jeu personnel unique est choisi d'office si rien ne l'est |
| Liste de taxons par défaut | Toutes les listes du serveur, aucune choisie d'office | Seulement la liste de la saisie d'OccTax (« Saisie Occtax »), choisie d'office si rien ne l'est ; introuvable, toutes |
| Observateur par défaut | Tous les observateurs du serveur | Seulement l'utilisateur connecté |

La liste de la saisie d'OccTax est celle de la configuration du module sur le serveur (`OCCTAX.id_taxon_list`), sinon la liste nommée « Saisie Occtax ». Dans les deux modes, un jeu de données qui impose sa liste l'emporte.

Le reste des Paramètres (connexion, liste des serveurs) est identique.

## Passer d'un mode à l'autre

- **Pro vers simplifié.** Rien n'est effacé : les caches des Suivis et d'OccHab restent sur le téléphone, et les visites ou stations en attente restent accessibles par le menu.
- **Simplifié vers pro.** Si les données ont été chargées en mode simplifié, Monitoring et OccHab restent masqués à l'accueil, et le bandeau des Paramètres invite à recharger : « Données chargées pour la saisie simplifiée : rechargez-les pour retrouver Monitoring et OccHab. La saisie multi-taxons reste utilisable d'ici là. » Un rechargement en mode pro les rend.
- **Jeu de données, liste de taxons et observateur par défaut.** Ceux qui sont choisis le restent d'un mode à l'autre, même hors de ce que le mode simplifié propose : seuls les menus changent.
- **Saisies faites dans un mode, rouvertes dans l'autre.** Elles s'ouvrent sans perte : le mode simplifié ne modifie que les champs qu'il montre.

## Ce qui ne dépend pas du mode

Saisie mono-taxon (le ✓ de ses détails est en bas au milieu depuis le 17 novembre 2026. Depuis le 24 novembre 2026 : l'espèce choisie dans ses réglages ouvre sa fiche, comme en multi-taxons, celle du mode, pro ou simplifié, sans photo ni son ; ses valeurs valent pour chaque « + » et restent avec la saisie, et son ✓ ouvre la carte ; plus de boutons « Caractérisation » et « Dénombrement ». Une observation touchée sur la carte ouvre un popup « Éditer », qui mène directement à sa fiche, ou « Supprimer » ; plusieurs au même point, on choisit d'abord laquelle. « Continuer la saisie » la rouvre directement sur sa carte, avec les valeurs de sa série et, depuis le 25 novembre 2026, les détails du relevé de son dernier « + » (observateurs, date et heures, commentaire, habitat). Depuis le 25 novembre 2026, un « i » sur la carte, au même endroit et de même allure que sur la carte multi-taxons, rouvre la fiche de la série, dont les valeurs valent pour les « + » suivants ; plus de « Modifier » vers les réglages, donc plus de changement d'espèce ni de détails depuis la carte, ni de codes du sexe et du stade sous l'espèce, ni d'avertissement « Donnée exceptionnelle » sur la carte : il reste dans « Mes saisies » et le détail d'une saisie ; plus de ligne de confirmation après chaque « + », ni de « Modifier » vers l'écran du relevé de la dernière observation : la consigne en haut de la carte dit qu'une observation touchée s'édite ; le compteur compte toutes les observations de la saisie, reprises comprises, et le panneau du bas tient sur une ligne), Monitoring et OccHab (absents du mode simplifié, inchangés en mode pro), Explorer, Maps Manager, carte et trace GPS, envoi des saisies, connexion au serveur.

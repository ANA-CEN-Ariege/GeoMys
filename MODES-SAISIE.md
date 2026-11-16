# Mode pro et mode simplifié : les différences

État au 16 novembre 2026 (version 2.2.0-beta5, build 209).

**À tenir à jour.** Toute modification qui fait diverger ou converger les deux modes se reporte ici, et dans la page destinée aux utilisateurs [`MODES-SAISIE.html`](MODES-SAISIE.html), dans le même commit. Les divergences se retrouvent dans le code par `ModeSaisie`, `simplifie`, `occtaxSeul` et `donneesSimplifiees` ; le détail des décisions est dans [`LOT-9.md`](LOT-9.md) § 43 à 51 et 55 à 58.

## En bref

| | Mode pro | Mode simplifié |
|---|---|---|
| Public | Naturalistes habitués à GeoNature | Utilisateurs amateurs |
| Modules | Saisie multi-taxons, saisie mono-taxon, Monitoring, OccHab | Saisie multi-taxons seulement |
| Champs de l'observation | Tous ceux que le serveur montre | Nombre, stade de vie, sexe, état biologique, comportement, commentaire, photo, audio |
| Données chargées | Tout | Seulement ce qui sert à la saisie multi-taxons |

Le mode pro est le mode par défaut : sans choix, et après une mise à jour, rien ne change.

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
| Menu : « Mes saisies », « Explorer », « Maps Manager » | Oui | Oui |
| Menu : « Mes visites », « Mes stations » | Si le module est là, ou s'il reste quelque chose à envoyer | Seulement s'il reste quelque chose à envoyer |

## Saisie multi-taxons

La carte, la trace GPS, le placement des observations, « Mes saisies » et l'envoi sont **identiques** dans les deux modes. Diffèrent les détails du relevé et la saisie d'une espèce.

### Les détails du relevé

Ce sont les détails d'un relevé (bouton « Détails ») et les détails communs de la saisie (bouton « i » de la carte).

| | Mode pro | Mode simplifié |
|---|---|---|
| Observateurs | Liste pour en ajouter, croix pour en retirer | Montrés sans pouvoir en ajouter ni en retirer : l'observateur par défaut des Paramètres |
| Habitat | Recherche d'un habitat, si le serveur montre le champ | Absent ; un habitat déjà porté par le relevé est gardé |
| Jeu de données | Tous ceux où l'utilisateur peut saisir | Seulement ses jeux de données personnels, s'il en a ; sinon tous |
| Autres champs (dates, heures, commentaire, etc.) | Oui | Oui, identiques |

Un jeu de données personnel est un jeu dont l'utilisateur connecté est acteur en personne (`cor_dataset_actor.id_role`) : GeoNature en crée un à l'inscription d'un compte, si le serveur l'a prévu. Un compte sans jeu personnel garde la liste complète, pour ne pas bloquer la saisie. Les acteurs sont lus au chargement des données : un téléphone chargé avant cette règle garde la liste complète jusqu'au prochain rechargement. Un jeu déjà porté par un relevé est gardé.

Deux cas gardent le champ des observateurs du mode pro, pour que le relevé reste validable : un relevé sans aucun observateur, et un serveur qui demande les observateurs en texte libre.

### Après l'ajout d'une espèce

| | Mode pro | Mode simplifié |
|---|---|---|
| Ce qui s'ouvre | Rien, sauf cas ci-dessous : on reste sur le relevé | La « Fiche de l'observation », à chaque espèce |
| Champs additionnels obligatoires vides | La Caractérisation ou le Dénombrement s'ouvre d'office | « Détails » s'ouvre par-dessus la fiche |
| Oiseau en période de nidification | La Caractérisation s'ouvre, réduite au comportement | « Comportement » s'ouvre par-dessus la fiche |

### La ligne d'une espèce dans le relevé

| | Mode pro | Mode simplifié |
|---|---|---|
| Boutons | « × N » (Dénombrement) et « i » (Caractérisation) | Un crayon, qui ouvre la fiche ; toucher le nom l'ouvre aussi |
| Champ obligatoire vide | Le bouton de l'écran concerné est rouge | Le crayon est rouge |
| Corbeille | Oui, avec confirmation | Oui, avec confirmation |

### Les écrans de l'espèce

**Mode pro**, comme le web de GeoNature :

- **Dénombrement** : objet et type du dénombrement, nombre min et nombre max, stade de vie, sexe, médias, champs additionnels ; plusieurs dénombrements par espèce.
- **Caractérisation** : technique d'observation, état biologique ; sous « Plus de champs » : méthode de détermination, statut d'observation, naturalité, statut biologique, comportement, statut de la source, floutage, preuve d'existence, preuves numérique et non numérique, déterminateur, commentaire ; champs additionnels.

**Mode simplifié**, la « Fiche de l'observation » et ses boutons :

| Bouton | Ce qu'il porte | Présent si |
|---|---|---|
| Détails | Nombre, stade de vie, sexe, état biologique, plus les champs additionnels obligatoires | Toujours |
| Comportement | Le comportement | Le serveur montre `behaviour` et l'espèce est un animal |
| Chant (à droite de « Comportement ») | D'un toucher, pose un comportement de chant, ou l'enlève : l'indice « Mâle chanteur en période de reproduction » si l'espèce est en période de nidification et que le serveur a les codes atlas, sinon « Chant » | L'espèce est un oiseau et le serveur a la valeur qui convient |
| Commentaire | Le commentaire de l'observation | Le serveur montre `comment_occ` |
| Photo | Appareil photo ou galerie | Le serveur active les médias |
| Audio | Choix d'un fichier son | Le serveur active les médias |

La fiche n'a rien à valider : chaque volet validé et chaque média sont écrits aussitôt dans le relevé.

### Règles propres au mode simplifié

- **Un seul nombre.** Il vaut pour le minimum et le maximum du premier dénombrement. Un maximum saisi en mode pro reste tant que le nombre ne change pas.
- **Un seul dénombrement.** La fiche ne touche que le premier ; les autres, saisis en mode pro, sont conservés tels quels.
- **Champs selon le groupe taxonomique.** Le sexe et le comportement ne sont proposés que pour un animal. Une nomenclature sans aucune valeur pour le taxon est masquée. En mode pro, tous les champs restent, comme sur le web.
- **Champs non montrés.** Ils gardent leur valeur ; jamais saisis, ils restent vides et le serveur y met sa valeur par défaut (technique d'observation, objet du dénombrement, etc.).
- **Champs additionnels facultatifs.** Ils ne sont pas proposés ; seuls les obligatoires le sont, dans « Détails ».
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

Saisie mono-taxon, Monitoring et OccHab (absents du mode simplifié, inchangés en mode pro), Explorer, Maps Manager, carte et trace GPS, envoi des saisies, connexion au serveur.

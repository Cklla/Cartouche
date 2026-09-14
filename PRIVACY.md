# Politique de confidentialité

Cartouche est une application personnelle, non distribuée publiquement (pas de présence sur le
Play Store) : ce document décrit les données traitées pour toute personne qui l'installe malgré
tout à partir des sources de ce dépôt.

## Données collectées

- **Compte Google** (via Firebase Auth / Google Sign-In) : identifiant de compte, adresse e-mail,
  nom et photo de profil éventuels. Sert uniquement à identifier l'utilisateur et à cloisonner ses
  données dans Firestore — aucun autre usage.
- **Backlog de jeux** : titres, plateformes, genres, statuts, notes personnelles, temps de jeu et
  notes libres saisis par l'utilisateur, ainsi que les métadonnées récupérées automatiquement
  (jaquette, temps de jeu estimé) via les API RAWG et IGDB.
- **Requêtes de recherche** : les termes tapés dans l'écran Recherche sont envoyés à l'API RAWG
  pour renvoyer des résultats. Ils ne sont pas stockés au-delà de la session de recherche.

Aucune autre donnée n'est demandée : pas de géolocalisation, pas de contacts, pas d'identifiant
publicitaire.

## Stockage et accès

- Le backlog est stocké localement sur l'appareil (base Room) et synchronisé sur Firebase
  Firestore, dans un espace propre à chaque compte (`users/{uid}/...`). Les règles de sécurité
  Firestore (`firestore.rules`) empêchent un utilisateur d'accéder aux données d'un autre.
- Firebase App Check garantit que seule l'application elle-même peut appeler Firestore et Auth
  (voir le README, section Sécurité).
- Aucune donnée locale n'est incluse dans les sauvegardes automatiques Android
  (`allowBackup="false"`).

## Partage avec des tiers

- **RAWG** et **IGDB** reçoivent les requêtes nécessaires à la recherche de jeux et à l'estimation
  du temps de jeu (titre recherché, identifiant du jeu) — aucune donnée de compte n'est transmise.
- **Google** (Firebase) héberge l'authentification et les données du backlog.
- Aucune vente, aucun partage à des fins publicitaires, aucun tracking tiers.

## Conservation et suppression

Les données sont conservées tant que le compte est utilisé. Pour une suppression complète
(compte Firebase et données Firestore associées), contacter l'adresse ci-dessous.

## Contact

Pour toute question sur cette politique ou pour demander la suppression de vos données :
contact@cklla.fr

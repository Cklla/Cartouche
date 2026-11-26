# Politique de confidentialité

Cartouche est une application personnelle, jamais publiée sur le Play Store : elle n'est partagée
qu'avec quelques proches, en test interne. Ce document décrit les données traitées pour toute
personne qui l'utilise ou l'installe à partir des sources de ce dépôt.

## Données collectées

- **Compte Google** (via Supabase Auth / Google Sign-In) : identifiant de compte, adresse e-mail,
  nom et photo de profil éventuels. Sert uniquement à identifier l'utilisateur et à cloisonner ses
  données dans Supabase — aucun autre usage.
- **Backlog de jeux** : titres, plateformes, genres, statuts, notes personnelles, temps de jeu et
  notes libres saisis par l'utilisateur, ainsi que les métadonnées récupérées automatiquement
  (jaquette, plateformes, année de sortie, temps de jeu estimé) via l'API IGDB.
- **Descriptions des jeux** : pour un jeu relié à IGDB, la description publique du jeu (texte de
  présentation en anglais) et sa traduction française sont enregistrées avec le jeu, pour ne pas être
  redemandées.
- **Requêtes de recherche** : les termes tapés dans l'écran Recherche sont envoyés à l'API IGDB
  pour renvoyer des résultats. Ils ne sont pas stockés au-delà de la session de recherche.

Aucune autre donnée n'est demandée : pas de géolocalisation, pas de contacts, pas d'identifiant
publicitaire.

## Stockage et accès

- Le backlog est stocké localement sur l'appareil (base Room) et synchronisé sur Supabase
  (base Postgres hébergée en Union européenne), dans une table liée à l'identifiant de chaque
  compte. Des règles de sécurité au niveau des lignes (RLS) empêchent un utilisateur d'accéder aux
  données d'un autre, et seules les adresses autorisées peuvent créer un compte.
- Les descriptions sont stockées dans une table distincte, liée au jeu : elles sont supprimées avec le
  jeu, et copiées dans la base locale pour rester lisibles hors ligne.
- Aucune donnée locale n'est incluse dans les sauvegardes automatiques Android
  (`allowBackup="false"`).

## Partage avec des tiers

- **IGDB** (service de Twitch) reçoit les requêtes nécessaires à la recherche de jeux et à l'enrichissement
  de leur fiche (titre recherché, identifiant du jeu) — aucune donnée de compte n'est transmise. Les
  jaquettes sont chargées depuis `images.igdb.com`. Données : IGDB.com.
- **Anthropic** (API Claude) traduit en français la description d'un jeu quand on déroule la description sur sa fiche. La
  demande passe par une fonction Supabase qui lit elle-même le texte public dans IGDB : seul ce texte
  part vers Anthropic, depuis les serveurs de Supabase. Ni le compte, ni l'adresse e-mail, ni l'adresse
  IP, ni le backlog, ni aucun identifiant de compte ou de jeu ne lui sont envoyés. Selon la
  documentation d'Anthropic, les textes envoyés à son API sont supprimés au plus tard dans les 30
  jours, sauf exceptions prévues par sa politique (abus signalé, obligation légale), et ne servent pas
  à entraîner ses modèles. La traduction n'est demandée, en principe, qu'une fois par jeu, jamais pour
  toute la bibliothèque d'un coup. La fiche l'indique (« Traduit automatiquement ») et permet de lire
  le texte original.
- **Supabase** héberge l'authentification et les données du backlog ; **Google** fournit la
  connexion (Google Sign-In).
- Aucune vente, aucun partage à des fins publicitaires, aucun tracking tiers.

## Conservation et suppression

Les données sont conservées tant que le compte est utilisé. Pour une suppression complète
(compte et données du backlog associées), contacter l'adresse ci-dessous.

## Contact

Pour toute question sur cette politique ou pour demander la suppression de vos données :
contact@cklla.fr

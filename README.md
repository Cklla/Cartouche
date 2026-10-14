# Cartouche

Application Android personnelle de gestion de backlog de jeux vidéo : suivez les jeux à faire, en
cours, terminés ou abandonnés, recherchez-en de nouveaux via l'API RAWG, et retrouvez votre backlog
synchronisé automatiquement entre tous vos appareils grâce à Supabase.

## Fonctionnalités

- **Bibliothèque** : liste du backlog avec statut visuel (À faire / En cours / Terminé /
  Abandonné), tri et filtrage.
- **Recherche** : recherche de jeux via l'[API RAWG](https://rawg.io/apidocs), ajout en un tap au
  backlog.
- **Fiche détail** : statut, note personnelle, temps de jeu personnel, notes libres, temps de jeu
  estimé via [IGDB](https://www.igdb.com/api), et plateformes « Joué sur » pour un jeu
  multi-plateforme.
- **Plateforme fiabilisée via IGDB** : distingue Switch et Switch 2, regroupe Windows/Mac/Linux sous
  « PC ».
- **Année de fin ou d'abandon** : depuis la fiche d'un jeu Terminé ou Abandonné, on choisit l'année,
  y compris pour un jeu qui n'en avait aucune.
- **Statistiques** : progression du backlog par statut, temps de jeu cumulé, répartition par
  plateforme, filtre par année pour les jeux terminés et abandonnés.
- **Récap annuel** : bilan de l'année (jeux terminés, heures de jeu) du 25 décembre au 31 janvier,
  avec une notification locale une fois par an.
- **Récap en images** : suite de slides façon *Wrapped* (total, coups de cœur, plus longues parties,
  plateformes, faits de l'année, mosaïque des jaquettes).
- **Connexion Google et synchronisation** : backlog synchronisé entre appareils via Supabase,
  utilisable hors ligne.

## Captures d'écran

| Bibliothèque | Recherche | Détail |
|:---:|:---:|:---:|
| <img src="screenshots/bibliotheque.png" width="220" alt="Bibliothèque"> | <img src="screenshots/recherche.png" width="220" alt="Recherche"> | <img src="screenshots/detail.png" width="220" alt="Détail"> |

| Statistiques | Récap annuel |
|:---:|:---:|
| <img src="screenshots/stats.png" width="220" alt="Statistiques"> | <img src="screenshots/recap.png" width="220" alt="Récap annuel"> |

| Récap en images | Les jeux les plus longs | Mosaïque |
|:---:|:---:|:---:|
| <img src="screenshots/recap-images.png" width="220" alt="Récap en images"> | <img src="screenshots/recap-faits.png" width="220" alt="Faits de l'année"> | <img src="screenshots/recap-mosaique.png" width="220" alt="Mosaïque"> |

## Stack technique

| Domaine | Choix |
|---|---|
| Langage / UI | Kotlin 2.2, Jetpack Compose, Navigation Compose |
| Persistance locale | Room |
| Réseau | Retrofit 3 + Moshi (RAWG, IGDB) |
| Injection de dépendances | Hilt |
| Arrière-plan | WorkManager (notification du récap annuel) |
| Cloud | Supabase (Postgres + Auth par jeton Google + Realtime), via supabase-kt |
| Images | Coil |
| Tests | JUnit4 + kotlinx-coroutines-test, avec des fakes (ni mock ni Robolectric) |

## Architecture

MVVM, avec le Repository comme source de vérité unique :

```
UI (Compose) ↔ ViewModel ↔ Repository ─┬─ Room (cache local, hors ligne)
                                       ├─ Supabase (synchro distante)
                                       └─ Retrofit (recherche RAWG, temps de jeu IGDB)
```

- Les ViewModels passent toujours par une interface de repository (`domain/repository/`), injectée
  par Hilt.
- **Room est la seule source lue par l'UI**, même en ligne : affichage instantané et usage hors
  ligne complet.
- Les écritures vont d'abord dans Room avec une file d'envoi (outbox), rejouée dès que le réseau
  revient ; Supabase fait autorité sur le contenu dès qu'un compte est connecté et est mirroré dans
  Room.
- Les erreurs réseau et Supabase remontent sous forme de `Resource` structuré, traduit en message
  côté UI.

## Installation

**Prérequis** : Android Studio avec JDK 17+, un appareil ou émulateur en API 24 ou plus, une clé API
[RAWG](https://rawg.io/apidocs), une application [Twitch Developer](https://dev.twitch.tv/console/apps)
(authentification IGDB), un projet [Supabase](https://supabase.com/) avec le
fournisseur Google activé.

1. **Cloner** : `git clone https://github.com/Cklla/Cartouche`
2. **Clés API** : dans `local.properties` (ignoré par git) :
   ```properties
   sdk.dir=/chemin/vers/le/sdk/android
   RAWG_API_KEY=votre_clé_rawg
   IGDB_CLIENT_ID=votre_client_id_twitch
   IGDB_CLIENT_SECRET=votre_client_secret_twitch
   SUPABASE_URL=https://votre-projet.supabase.co
   SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
   GOOGLE_WEB_CLIENT_ID=identifiant_client_web_google
   ```
3. **Supabase** : appliquer `supabase/migrations/` dans l'éditeur SQL du tableau de bord. La migration
   crée le schéma `cartouche` et la table `game` (isolation par compte via RLS, contraintes sur
   chaque champ, Realtime). Ajouter `cartouche` aux schémas exposés de la Data API. Le projet peut
   être partagé avec une autre application : les tables de Cartouche vivent dans leur propre schéma.
4. **Google Cloud** : un client OAuth *Web* (celui déclaré dans le fournisseur Google de Supabase,
   et dont l'identifiant va dans `GOOGLE_WEB_CLIENT_ID`) et un client *Android* avec le SHA-1 du
   keystore (`./gradlew signingReport`).
5. **Lancer** : `./gradlew assembleDebug`, ou Run ▶ dans Android Studio.

## Tests

```bash
./gradlew testDebugUnitTest
```

Les tests couvrent les ViewModels, les repositories (synchro Supabase ↔ Room, file d'envoi, mapping, erreurs) et
la logique pure (récaps, années), avec des fakes pour rester rapides et déterministes.

## Structure du projet

```
app/src/main/java/fr/cklla/cartouche/
├── data/           # Room, clients distants (RAWG, IGDB, Supabase), repositories
├── di/             # Modules Hilt
├── domain/         # Modèles, interfaces de repository, logique pure
├── notification/   # Notification locale du récap annuel
└── ui/             # Écrans Compose, navigation, thème
```

## Choix techniques notables

- **RAWG pour la recherche, IGDB en complément** : RAWG est simple (clé API, pas d'OAuth) ; IGDB est
  plus fiable pour le temps de jeu estimé et la plateforme.
- **UUID pour `Game.id`** : le même identifiant côté Room et Supabase, sans table de correspondance.
- **Connexion Google obligatoire** : un utilisateur = un espace de données, sans mot de passe dédié.
- **Notification locale, sans FCM** : un job WorkManager décide d'après la date de l'appareil.
- **Statistiques et récaps calculés en direct** à partir des données déjà stockées, par des
  fonctions pures : rien de plus à synchroniser.

## Confidentialité

Voir [PRIVACY.md](PRIVACY.md).

## Licence

Projet personnel — usage privé.

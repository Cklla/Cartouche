package fr.cklla.cartouche.data.remote.description

import fr.cklla.cartouche.domain.description.DescriptionSource

/**
 * Descriptions de jeux côté serveur (table `game_description` et fonction de traduction). Mêmes
 * opérations que la règle de la fiche ([DescriptionSource]) ; l'interface existe pour brancher un
 * faux serveur dans les tests du repository, comme `RemoteGameDataSource` pour les jeux.
 */
interface RemoteDescriptionDataSource : DescriptionSource

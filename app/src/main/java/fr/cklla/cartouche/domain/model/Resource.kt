package fr.cklla.cartouche.domain.model

/**
 * Enveloppe de résultat renvoyée par le Repository pour toute opération
 * pouvant échouer (accès Room, appel réseau RAWG, synchro Firebase...).
 *
 * Le Repository ne traduit jamais l'erreur en texte destiné à l'utilisateur :
 * il fournit un [message] technique/contextuel, et c'est la couche UI qui
 * décide comment l'afficher (voir convention "erreurs structurées" du projet).
 */
sealed interface Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>
    data class Error(val message: String, val cause: Throwable? = null) : Resource<Nothing>
}

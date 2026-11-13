package fr.cklla.cartouche.ui.recherche

import fr.cklla.cartouche.domain.model.GameSearchResult
import java.util.Locale

/**
 * Logique de déduplication "déjà ajouté au backlog" pour l'écran Recherche,
 * en fonctions pures pour rester testable hors ViewModel (voir maquette :
 * "bouton d'ajout... pilule verte désactivée si déjà dans le backlog,
 * déduplication par id IGDB, puis par titre").
 */

fun normalizeTitle(title: String): String = title.trim().lowercase(Locale.ROOT)

fun isAlreadyAdded(title: String, backlogGameIdsByTitle: Map<String, String>): Boolean =
    normalizeTitle(title) in backlogGameIdsByTitle

/** Id du jeu déjà dans le backlog pour ce titre, ou `null` s'il n'y est pas. */
fun backlogGameId(title: String, backlogGameIdsByTitle: Map<String, String>): String? =
    backlogGameIdsByTitle[normalizeTitle(title)]

/**
 * Id du jeu du backlog qui correspond à ce résultat : d'abord par **id IGDB** (fiable : deux jeux
 * peuvent porter le même titre, et un titre a pu être modifié), puis, pour les jeux ajoutés sans id
 * IGDB, par titre normalisé.
 */
fun backlogGameId(
    result: GameSearchResult,
    backlogGameIdsByIgdbId: Map<Long, String>,
    backlogGameIdsByTitle: Map<String, String>,
): String? = backlogGameIdsByIgdbId[result.igdbId] ?: backlogGameId(result.title, backlogGameIdsByTitle)

package fr.cklla.cartouche.data.sync

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.cklla.cartouche.domain.repository.IgdbUpgradeStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ensemble d'identifiants de jeux (UUID) dans de simples `SharedPreferences`, comme
 * [InitialUploadPrefs]. Un identifiant qui survit à la suppression de son jeu ne coûte que quelques
 * octets et ne désigne plus rien.
 */
@Singleton
class IgdbUpgradePrefs @Inject constructor(@ApplicationContext context: Context) : IgdbUpgradeStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isDone(gameId: String): Boolean = gameId in doneIds()

    override fun markDone(gameId: String) {
        // `getStringSet` renvoie un ensemble qu'il ne faut pas modifier : on en écrit une copie.
        prefs.edit { putStringSet(KEY_DONE_IDS, doneIds() + gameId) }
    }

    private fun doneIds(): Set<String> = prefs.getStringSet(KEY_DONE_IDS, emptySet()).orEmpty()

    private companion object {
        const val PREFS_NAME = "igdb_upgrade"
        const val KEY_DONE_IDS = "done_game_ids"
    }
}

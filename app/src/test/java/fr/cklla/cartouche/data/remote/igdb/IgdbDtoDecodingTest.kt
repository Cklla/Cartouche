package fr.cklla.cartouche.data.remote.igdb

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import fr.cklla.cartouche.data.remote.igdb.dto.IgdbGameDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Décodage de réponses IGDB incomplètes, avec le même Moshi que l'app : un champ absent ne casse rien. */
class IgdbDtoDecodingTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter<List<IgdbGameDto>>(Types.newParameterizedType(List::class.java, IgdbGameDto::class.java))

    // Forme relevée sur IGDB, avec un jeu sans nom, une plateforme sans nom et un genre sans nom.
    private val json = """
        [
          {"id": 2364, "name": "Xenoblade Chronicles", "first_release_date": 1276128000,
           "cover": {"id": 570625, "image_id": "coc8ap"},
           "platforms": [{"id": 5, "name": "Wii"}, {"id": 41}, {"id": 99, "name": "Wii U"}],
           "genres": [{"id": 12}, {"id": 13, "name": "Role-playing (RPG)"}],
           "game_type": {"id": 0, "type": "Main Game"}, "total_rating_count": 152},
          {"id": 555, "first_release_date": 1500000000, "platforms": [{"id": 6, "name": "PC (Microsoft Windows)"}]},
          {"id": 191411, "name": "Xenoblade Chronicles 3", "platforms": [{"id": 130, "name": "Nintendo Switch"}]}
        ]
    """.trimIndent()

    @Test
    fun `un nom absent ne fait pas echouer le decodage de toute la reponse`() {
        val games = adapter.fromJson(json)!!

        assertEquals(3, games.size)
        assertNull(games[1].name)
        assertNull(games[0].platforms!![1].name)
        assertNull(games[0].genres!![0].name)
    }

    @Test
    fun `un genre ou une plateforme sans nom est ignore, le reste est garde`() {
        val first = adapter.fromJson(json)!!.first()

        assertEquals("Wii/Wii U", formatIgdbPlatforms(first.platforms))
        assertEquals("Role-playing (RPG)", firstGenreName(first))
    }

    @Test
    fun `un jeu sans nom est ecarte par la passerelle sans perdre les autres`() = runTest {
        val api = FakeIgdbApi(catalog = adapter.fromJson(json)!!, searchResults = listOf(2364, 555, 191411))

        val results = IgdbGateway(api, FakeIgdbTokenSource()).search("xenoblade")

        assertEquals(listOf(2364L, 191411L).sorted(), results.map { it.game.id }.sorted())
    }

    @Test
    fun `une recherche par titre ignore aussi un candidat sans nom`() = runTest {
        val api = FakeIgdbApi(catalog = adapter.fromJson(json)!!, searchResults = listOf(555, 2364))

        val candidates = IgdbGateway(api, FakeIgdbTokenSource()).searchByTitle("Xenoblade Chronicles")

        assertEquals(listOf(2364L), candidates.map { it.id })
        assertEquals(2364L, findBestMatch("Xenoblade Chronicles", 2010, candidates)?.id)
    }
}

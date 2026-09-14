package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.remote.RawgApi
import fr.cklla.cartouche.data.remote.dto.RawgGameDto
import fr.cklla.cartouche.data.remote.dto.RawgSearchResponseDto
import fr.cklla.cartouche.data.remote.dto.RawgStoreLinkDto
import fr.cklla.cartouche.data.remote.dto.RawgStoreLinksResponseDto

/** Faux client RAWG en mémoire, pour tester [GameSearchRepositoryImpl] sans appel réseau réel. */
class FakeRawgApi : RawgApi {

    var results: List<RawgGameDto> = emptyList()
    var storeLinks: List<RawgStoreLinkDto> = emptyList()
    var shouldThrow = false

    override suspend fun searchGames(apiKey: String, query: String, pageSize: Int): RawgSearchResponseDto {
        if (shouldThrow) error("Échec réseau simulé")
        return RawgSearchResponseDto(results = results)
    }

    override suspend fun getStoreLinks(rawgId: Long, apiKey: String): RawgStoreLinksResponseDto {
        if (shouldThrow) error("Échec réseau simulé")
        return RawgStoreLinksResponseDto(results = storeLinks)
    }
}

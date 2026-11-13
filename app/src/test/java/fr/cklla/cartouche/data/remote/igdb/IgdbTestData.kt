package fr.cklla.cartouche.data.remote.igdb

import fr.cklla.cartouche.data.remote.igdb.dto.IgdbCoverDto
import fr.cklla.cartouche.data.remote.igdb.dto.IgdbGameDto
import fr.cklla.cartouche.data.remote.igdb.dto.IgdbGameTypeDto
import fr.cklla.cartouche.data.remote.igdb.dto.IgdbGenreDto
import fr.cklla.cartouche.data.remote.igdb.dto.IgdbPlatformDto
import fr.cklla.cartouche.data.remote.igdb.dto.IgdbRefDto

/** Fabrique de jeux IGDB pour les tests : `type = null` simule un `game_type` absent. */
fun igdbGame(
    id: Long,
    name: String,
    type: Long? = 0,
    versionParent: Long? = null,
    parentGame: Long? = null,
    platforms: List<String>? = emptyList(),
    ratings: Int? = null,
    cover: String? = null,
    genre: String? = null,
    releaseDate: Long? = null,
) = IgdbGameDto(
    id = id,
    name = name,
    firstReleaseDate = releaseDate,
    platforms = platforms?.map { IgdbPlatformDto(it) },
    cover = cover?.let { IgdbCoverDto(it) },
    genres = genre?.let { listOf(IgdbGenreDto(it)) },
    gameType = type?.let { IgdbGameTypeDto(it) },
    versionParent = versionParent?.let { IgdbRefDto(it) },
    parentGame = parentGame?.let { IgdbRefDto(it) },
    totalRatingCount = ratings,
)

/**
 * Jeux réels (ids et liens relevés sur IGDB lors de l'exploration), sauf l'édition Switch 2 de
 * *Xenoblade*, dont l'id est fictif : seule sa structure compte (édition d'une édition).
 */
object IgdbSamples {
    const val XENOBLADE = 2364L
    const val XENOBLADE_DE = 122238L
    const val XENOBLADE_SWITCH2_EDITION = 900_001L
    const val OCARINA = 1029L
    const val OCARINA_3D = 1039L
    const val OCARINA_SWITCH2 = 405460L

    val xenoblade = igdbGame(
        XENOBLADE, "Xenoblade Chronicles", platforms = listOf("Wii", "Wii U"), ratings = 152, cover = "coc8ap",
        genre = "Role-playing (RPG)", releaseDate = 1_276_128_000L,
    )
    val xenobladeDefinitive = igdbGame(
        XENOBLADE_DE, "Xenoblade Chronicles: Definitive Edition", type = 10, parentGame = XENOBLADE,
        platforms = listOf("Nintendo Switch"),
    )
    val xenobladeSwitch2 = igdbGame(
        XENOBLADE_SWITCH2_EDITION, "Xenoblade Chronicles: Definitive Edition - Nintendo Switch 2 Edition",
        type = 0, versionParent = XENOBLADE_DE, platforms = listOf("Nintendo Switch 2"),
    )
    val xenoblade3d = igdbGame(
        9553, "Xenoblade Chronicles 3D", type = 11, parentGame = XENOBLADE,
        platforms = listOf("Nintendo 3DS", "New Nintendo 3DS"),
    )
    val xenobladeCollector = igdbGame(
        50591, "Xenoblade Chronicles: Collector's Edition", type = 0, versionParent = XENOBLADE, platforms = null,
    )
    val xenobladeDlc = igdbGame(
        777, "Xenoblade Chronicles: Costume Pack", type = 1, parentGame = XENOBLADE, platforms = listOf("PlayStation 5"),
    )
    val xenoblade3 = igdbGame(
        191411, "Xenoblade Chronicles 3", platforms = listOf("Nintendo Switch"), ratings = 70, cover = "co4ocq",
    )

    val ocarina = igdbGame(
        OCARINA, "The Legend of Zelda: Ocarina of Time", platforms = listOf("Wii", "Nintendo 64", "64DD", "Wii U"),
        ratings = 400,
    )
    val ocarina3d = igdbGame(
        OCARINA_3D, "The Legend of Zelda: Ocarina of Time 3D", type = 8, parentGame = OCARINA,
        platforms = listOf("Nintendo 3DS"), ratings = 300,
    )
    val ocarina3dFirstEdition = igdbGame(
        89904, "The Legend of Zelda Ocarina of Time 3D: First Edition", type = 0, versionParent = OCARINA_3D,
        platforms = listOf("Nintendo 3DS"),
    )
    val ocarinaSwitch2 = igdbGame(
        OCARINA_SWITCH2, "The Legend of Zelda: Ocarina of Time", type = 8, parentGame = OCARINA,
        platforms = listOf("Nintendo Switch 2"), ratings = 20,
    )
    val masterQuest = igdbGame(
        45142, "The Legend of Zelda: Ocarina of Time - Master Quest", type = 4, parentGame = OCARINA,
        platforms = listOf("Nintendo GameCube", "64DD"), ratings = 50,
    )
}

package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.local.entity.GameEntity
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus

/**
 * Conversions entre le modèle métier [Game] et l'entité Room [GameEntity].
 *
 * Isolées dans ce fichier plutôt que dans le Repository pour rester testables
 * indépendamment, sans avoir besoin d'une base Room en mémoire.
 */

fun GameEntity.toDomain(): Game = Game(
    id = id,
    title = title,
    platform = platform,
    genre = genre,
    status = GameStatus.valueOf(status),
    userPlaytimeHours = userPlaytimeHours,
    estimatedPlaytimeHastilyHours = estimatedPlaytimeHastilyHours,
    estimatedPlaytimeNormallyHours = estimatedPlaytimeNormallyHours,
    estimatedPlaytimeCompletelyHours = estimatedPlaytimeCompletelyHours,
    rating = rating,
    notes = notes,
    coverUrl = coverUrl,
)

fun Game.toEntity(): GameEntity = GameEntity(
    id = id,
    title = title,
    platform = platform,
    genre = genre,
    status = status.name,
    userPlaytimeHours = userPlaytimeHours,
    estimatedPlaytimeHastilyHours = estimatedPlaytimeHastilyHours,
    estimatedPlaytimeNormallyHours = estimatedPlaytimeNormallyHours,
    estimatedPlaytimeCompletelyHours = estimatedPlaytimeCompletelyHours,
    rating = rating,
    notes = notes,
    coverUrl = coverUrl,
)

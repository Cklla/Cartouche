package fr.cklla.cartouche.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Représentation d'un jeu telle que stockée dans Room.
 *
 * [status] est stocké en texte (nom de l'enum `GameStatus`) plutôt qu'en
 * entier : c'est un peu plus verbeux en base, mais ça reste lisible en cas
 * d'inspection manuelle de la base et ça évite de casser les données existantes
 * si l'ordre des valeurs de l'enum change un jour.
 */
@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val platform: String,
    val genre: String,
    val status: String,
    val userPlaytimeHours: Int,
    val estimatedPlaytimeHours: Int?,
    val rating: Int?,
    val notes: String,
    val coverUrl: String?,
)

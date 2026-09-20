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
 *
 * [id] est un UUID (généré côté Repository, pas par Room) plutôt qu'un entier auto-incrémenté :
 * avec la synchro Firestore, cet id doit être stable et unique sur tous les appareils, ce qu'un
 * compteur local ne garantit pas (deux téléphones généreraient tous les deux un jeu n°1).
 */
@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val id: String,
    val title: String,
    val platform: String,
    val genre: String,
    val status: String,
    val rawgId: Long?,
    val releaseYear: Int?,
    val userPlaytimeHours: Int,
    val estimatedPlaytimeHastilyHours: Int?,
    val estimatedPlaytimeNormallyHours: Int?,
    val estimatedPlaytimeCompletelyHours: Int?,
    val rating: Int?,
    val notes: String,
    val coverUrl: String?,
    val completedAt: Long?,
)

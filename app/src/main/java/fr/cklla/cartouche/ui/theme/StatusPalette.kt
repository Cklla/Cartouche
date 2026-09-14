package fr.cklla.cartouche.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import fr.cklla.cartouche.R
import fr.cklla.cartouche.domain.model.GameStatus

/** Couleur de statut + fond/bordure du badge associé (voir tokens de la maquette). */
data class StatusPalette(
    val color: Color,
    val badgeBackground: Color,
    val badgeBorder: Color,
)

@StringRes
fun GameStatus.labelRes(): Int = when (this) {
    GameStatus.A_FAIRE -> R.string.filter_a_faire
    GameStatus.EN_COURS -> R.string.filter_en_cours
    GameStatus.TERMINE -> R.string.filter_termine
    GameStatus.ABANDONNE -> R.string.filter_abandonne
}

fun GameStatus.palette(): StatusPalette = when (this) {
    GameStatus.A_FAIRE -> StatusPalette(
        color = TextMuted,
        badgeBackground = TextMuted.copy(alpha = 0.12f),
        badgeBorder = TextMuted.copy(alpha = 0.4f),
    )
    GameStatus.EN_COURS -> StatusPalette(
        color = AccentPurpleLight,
        badgeBackground = AccentPurple.copy(alpha = 0.16f),
        badgeBorder = AccentPurple.copy(alpha = 0.45f),
    )
    GameStatus.TERMINE -> StatusPalette(
        color = SuccessGreen,
        badgeBackground = SuccessGreen.copy(alpha = 0.14f),
        badgeBorder = SuccessGreen.copy(alpha = 0.4f),
    )
    GameStatus.ABANDONNE -> StatusPalette(
        color = ErrorCoral,
        badgeBackground = ErrorCoral.copy(alpha = 0.14f),
        badgeBorder = ErrorCoral.copy(alpha = 0.4f),
    )
}

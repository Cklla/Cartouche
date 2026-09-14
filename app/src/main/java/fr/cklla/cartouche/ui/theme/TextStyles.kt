package fr.cklla.cartouche.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Styles de texte "one-off" de la maquette, en dehors de l'échelle Material3
 * classique (qui ne correspond pas au design). Les couleurs qui dépendent d'un
 * état (actif/inactif, couleur de statut) restent en dehors de ces styles et
 * sont passées au paramètre `color` de `Text` au cas par cas.
 */
object CartoucheTextStyles {

    val kicker = TextStyle(
        fontFamily = FrauncesItalic,
        fontWeight = FontWeight.Medium,
        fontStyle = FontStyle.Italic,
        fontSize = 12.sp,
    )

    val screenTitle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
    )

    val chipLabel = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    )

    val cardTitle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.5.sp,
    )

    val cardSubtitle = TextStyle(
        fontFamily = InterFamily,
        fontSize = 12.sp,
    )

    val badgeLabel = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        letterSpacing = 0.5.sp,
    )

    val emptyTitle = TextStyle(
        fontFamily = FrauncesItalic,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = 17.sp,
    )

    val emptyMessage = TextStyle(
        fontFamily = InterFamily,
        fontSize = 13.sp,
    )

    val navLabel = TextStyle(
        fontFamily = InterFamily,
        fontSize = 11.sp,
    )

    val coverLetter = TextStyle(
        fontFamily = FrauncesItalic,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = 28.sp,
    )
}

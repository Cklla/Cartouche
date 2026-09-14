package fr.cklla.cartouche.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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

    // --- Écran Détail ---

    val coverLetterLarge = TextStyle(
        fontFamily = FrauncesItalic,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = 96.sp,
    )

    // Pas de police monospace dédiée embarquée pour une simple étiquette
    // décorative : la monospace système suffit (voir maquette, "jaquette").
    val coverLabel = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
    )

    val backLabel = TextStyle(
        fontFamily = InterFamily,
        fontSize = 15.sp,
    )

    val detailTitle = TextStyle(
        fontFamily = FrauncesItalic,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = 25.sp,
    )

    val detailSubtitle = TextStyle(
        fontFamily = InterFamily,
        fontSize = 13.sp,
    )

    val sectionLabel = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.9.sp,
    )

    val statusPillLabel = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    )

    val hoursValue = TextStyle(
        fontFamily = FrauncesItalic,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = 20.sp,
    )

    val notesText = TextStyle(
        fontFamily = InterFamily,
        fontSize = 14.sp,
    )

    val linkLabel = TextStyle(
        fontFamily = InterFamily,
        fontSize = 13.sp,
    )

    // --- Écran Stats ---

    val statValueLarge = TextStyle(
        fontFamily = FrauncesItalic,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = 28.sp,
    )

    val statValueMedium = TextStyle(
        fontFamily = FrauncesItalic,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = 22.sp,
    )

    val donutPercent = TextStyle(
        fontFamily = FrauncesItalic,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = 26.sp,
    )

    val donutLabel = TextStyle(
        fontFamily = InterFamily,
        fontSize = 10.5.sp,
        letterSpacing = 0.42.sp,
    )

    val legendLabel = TextStyle(
        fontFamily = InterFamily,
        fontSize = 13.sp,
    )

    val legendCount = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
    )
}

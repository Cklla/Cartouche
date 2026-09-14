package fr.cklla.cartouche.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// La maquette ne définit qu'un thème sombre ("Fidelity: high-fidelity, couleurs
// définitives") : pas de variante claire ni de couleur dynamique (Material You),
// qui casseraient l'identité visuelle voulue.
private val CartoucheColorScheme = darkColorScheme(
    primary = AccentPurple,
    secondary = AccentPurpleLight,
    tertiary = SuccessGreen,
    background = BackgroundDark,
    surface = SurfaceCard,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = ErrorCoral,
)

@Composable
fun CartoucheTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CartoucheColorScheme,
        typography = Typography,
        content = content,
    )
}

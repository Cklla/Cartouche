package fr.cklla.cartouche.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.cklla.cartouche.ui.theme.CartoucheTextStyles
import fr.cklla.cartouche.ui.theme.CoverGradients

/**
 * Jaquette placeholder utilisée tant que l'API RAWG n'est pas branchée : un
 * dégradé (choisi selon le titre, pour une couleur stable par jeu) avec la
 * première lettre du titre en filigrane, comme dans la maquette.
 */
@Composable
fun GameCoverPlaceholder(
    title: String,
    modifier: Modifier = Modifier,
    width: Dp,
    height: Dp,
) {
    val gradient = remember(title) {
        CoverGradients[(title.hashCode().mod(CoverGradients.size))]
    }
    Box(
        modifier = modifier
            .size(width, height)
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.linearGradient(listOf(gradient.first, gradient.second))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.firstOrNull()?.uppercase() ?: "?",
            style = CartoucheTextStyles.coverLetter,
            color = Color.White.copy(alpha = 0.35f),
        )
    }
}

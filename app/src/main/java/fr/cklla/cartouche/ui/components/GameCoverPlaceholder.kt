package fr.cklla.cartouche.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.cklla.cartouche.R
import fr.cklla.cartouche.ui.theme.CartoucheTextStyles
import fr.cklla.cartouche.ui.theme.CoverGradients
import fr.cklla.cartouche.ui.theme.TextMuted
import fr.cklla.cartouche.ui.theme.TextPrimary

/**
 * Jaquette d'un jeu : la vraie image RAWG ([coverUrl]) si elle est connue,
 * sinon un dégradé placeholder (choisi selon le titre, pour une couleur stable
 * par jeu) avec la première lettre du titre en filigrane, comme dans la
 * maquette d'origine (avant branchement de l'API).
 *
 * [width]/[height] fixent une taille précise (carte liste) ; laissés à `null`,
 * la taille suit le [modifier] fourni par l'appelant (jaquette pleine largeur
 * de l'écran Détail, via `fillMaxWidth().aspectRatio(3f / 4f)`).
 */
@Composable
fun GameCoverPlaceholder(
    title: String,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp? = null,
    coverUrl: String? = null,
    letterStyle: TextStyle = CartoucheTextStyles.coverLetter,
    letterAlpha: Float = 0.16f,
    showLabel: Boolean = false,
) {
    val shapedModifier = modifier
        .let { if (width != null && height != null) it.size(width, height) else it }
        .clip(RoundedCornerShape(6.dp))

    if (coverUrl != null) {
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = shapedModifier,
        )
        return
    }

    val gradient = remember(title) {
        CoverGradients[(title.hashCode().mod(CoverGradients.size))]
    }
    Box(
        modifier = shapedModifier.background(Brush.linearGradient(listOf(gradient.first, gradient.second))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.firstOrNull()?.uppercase() ?: "?",
            style = letterStyle,
            color = TextPrimary.copy(alpha = letterAlpha),
        )
        if (showLabel) {
            Text(
                text = stringResource(R.string.cover_placeholder_label),
                style = CartoucheTextStyles.coverLabel,
                color = TextMuted,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            )
        }
    }
}

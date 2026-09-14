package fr.cklla.cartouche.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.ui.theme.CartoucheTextStyles
import fr.cklla.cartouche.ui.theme.labelRes
import fr.cklla.cartouche.ui.theme.palette
import java.util.Locale

/** Pilule affichant le statut d'un jeu, colorée selon [GameStatus]. */
@Composable
fun StatusBadge(status: GameStatus, modifier: Modifier = Modifier) {
    val palette = status.palette()
    Text(
        text = stringResource(status.labelRes()).uppercase(Locale.FRENCH),
        style = CartoucheTextStyles.badgeLabel,
        color = palette.color,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(palette.badgeBackground)
            .border(BorderStroke(0.5.dp, palette.badgeBorder), RoundedCornerShape(5.dp))
            .padding(PaddingValues(horizontal = 8.dp, vertical = 3.dp)),
    )
}

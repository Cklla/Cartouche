package fr.cklla.cartouche.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.cklla.cartouche.R
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.ui.theme.BorderHairline
import fr.cklla.cartouche.ui.theme.CartoucheTextStyles
import fr.cklla.cartouche.ui.theme.SurfaceCard
import fr.cklla.cartouche.ui.theme.TextMuted
import fr.cklla.cartouche.ui.theme.TextPrimary

/**
 * Carte compacte d'un jeu (jaquette + titre + plateforme/genre + badge de statut), utilisée dans
 * toute liste de jeux de l'app — Bibliothèque à l'origine, réutilisée telle quelle par les listes
 * de la carte "Récap" (`RecapGamesScreen`).
 */
@Composable
fun GameCard(game: Game, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GameCoverPlaceholder(
            title = game.title,
            width = 56.dp,
            height = 76.dp,
            coverUrl = game.coverUrl,
            letterStyle = CartoucheTextStyles.coverLetterListCard,
        )
        Column {
            Text(
                text = game.title,
                style = CartoucheTextStyles.cardTitle,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.game_card_platform_genre, game.platform, game.genre),
                style = CartoucheTextStyles.cardSubtitle,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(6.dp))
            StatusBadge(status = game.status)
        }
    }
}

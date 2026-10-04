package fr.cklla.cartouche.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cklla.cartouche.R
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.ui.theme.AccentPurple
import fr.cklla.cartouche.ui.theme.BackgroundDark
import fr.cklla.cartouche.ui.theme.BorderHairline
import fr.cklla.cartouche.ui.theme.CartoucheTextStyles
import fr.cklla.cartouche.ui.theme.CartoucheTheme
import fr.cklla.cartouche.ui.theme.TextMuted
import fr.cklla.cartouche.ui.theme.TextPrimary
import fr.cklla.cartouche.ui.theme.palette
import java.time.ZoneId

/**
 * Récap en images d'une année : un pager horizontal de slides plein écran (voir
 * [RecapStoryViewModel]). Les grilles et listes défilent à la verticale à l'intérieur de leur
 * slide, jamais en horizontal, pour ne pas se disputer le balayage avec le pager. Un tap sur une
 * jaquette ouvre la fiche Détail.
 */
@Composable
fun RecapStoryScreen(
    onCloseClick: () -> Unit,
    onGameClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecapStoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecapStoryContent(
        year = viewModel.year,
        state = state,
        onCloseClick = onCloseClick,
        onGameClick = onGameClick,
        modifier = modifier,
    )
}

@Composable
private fun RecapStoryContent(
    year: Int,
    state: RecapStoryUiState,
    onCloseClick: () -> Unit,
    onGameClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark),
    ) {
        when {
            state.isLoading -> StoryTopBar(slideCount = 0, currentPage = 0, onCloseClick = onCloseClick)
            state.slides.isEmpty() -> {
                StoryTopBar(slideCount = 0, currentPage = 0, onCloseClick = onCloseClick)
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.recap_story_empty, year),
                        style = CartoucheTextStyles.emptyMessage,
                        color = TextMuted,
                    )
                }
            }
            else -> StoryPager(year = year, slides = state.slides, onCloseClick = onCloseClick, onGameClick = onGameClick)
        }
    }
}

/**
 * Le pager n'est créé qu'une fois la première liste réelle de slides connue (voir
 * [RecapStoryUiState.isLoading]) : son état (page courante) est conservé par `rememberPagerState` à
 * travers une rotation et un aller-retour vers une fiche.
 */
@Composable
private fun ColumnScope.StoryPager(
    year: Int,
    slides: List<RecapSlide>,
    onCloseClick: () -> Unit,
    onGameClick: (String) -> Unit,
) {
    val pagerState = rememberPagerState { slides.size }
    StoryTopBar(slideCount = slides.size, currentPage = pagerState.currentPage, onCloseClick = onCloseClick)
    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        key = { index -> slides[index].key },
    ) { index ->
        val slide = slides[index]
        Box(modifier = Modifier.fillMaxSize().background(slideBackground(slide))) {
            RecapSlideContent(slide = slide, year = year, onGameClick = onGameClick)
        }
    }
}

/**
 * Voile de couleur en haut de la slide, qui se fond dans le fond sombre : la couleur du statut sur
 * la slide Total (vert si des jeux sont terminés, corail s'il n'y a que des abandons),
 * [AccentPurple] ailleurs.
 */
private fun slideBackground(slide: RecapSlide): Brush {
    val tint = if (slide is RecapSlide.Total) {
        val status = if (slide.completedCount > 0) GameStatus.TERMINE else GameStatus.ABANDONNE
        status.palette().color
    } else {
        AccentPurple
    }
    return Brush.verticalGradient(listOf(tint.copy(alpha = 0.2f), BackgroundDark))
}

/** Barre de progression segmentée (un segment par slide) et bouton fermer. */
@Composable
private fun StoryTopBar(slideCount: Int, currentPage: Int, onCloseClick: () -> Unit) {
    val progress = stringResource(R.string.recap_story_progress, currentPage + 1, slideCount)
    Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp)) {
        if (slideCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp)
                    .semantics { contentDescription = progress },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(slideCount) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (index <= currentPage) AccentPurple else BorderHairline),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            // IconButton : zone tactile de 48 dp par défaut.
            IconButton(onClick = onCloseClick) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.recap_story_close),
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun RecapStoryContentPreview() {
    val completedAt = 1_790_000_000_000L
    val games = listOf(
        Game(id = "1", title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.TERMINE, rating = 5, userPlaytimeHours = 60, releaseYear = 2020, completedAt = completedAt),
        Game(id = "2", title = "Celeste", platform = "Switch", genre = "Plateforme", status = GameStatus.TERMINE, rating = 4, userPlaytimeHours = 12, releaseYear = 2018, completedAt = completedAt + 86_400_000L),
        Game(id = "3", title = "Elden Ring", platform = "PS5", genre = "Action-RPG", status = GameStatus.TERMINE, rating = 5, userPlaytimeHours = 120, releaseYear = 2022, completedAt = completedAt + 172_800_000L),
        Game(id = "4", title = "Hollow Knight", platform = "PC", genre = "Metroidvania", status = GameStatus.ABANDONNE, abandonedAt = completedAt),
    )
    CartoucheTheme {
        RecapStoryContent(
            year = 2026,
            state = RecapStoryUiState(isLoading = false, slides = buildRecapSlides(games, 2026, ZoneId.systemDefault())),
            onCloseClick = {},
            onGameClick = {},
        )
    }
}

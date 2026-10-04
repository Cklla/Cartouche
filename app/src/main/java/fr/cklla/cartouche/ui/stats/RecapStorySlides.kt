package fr.cklla.cartouche.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import fr.cklla.cartouche.R
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.ui.components.GameCoverPlaceholder
import fr.cklla.cartouche.ui.theme.AccentPurple
import fr.cklla.cartouche.ui.theme.AccentPurpleLight
import fr.cklla.cartouche.ui.theme.AccentPurpleMuted
import fr.cklla.cartouche.ui.theme.BorderHairline
import fr.cklla.cartouche.ui.theme.CartoucheTextStyles
import fr.cklla.cartouche.ui.theme.SurfaceCard
import fr.cklla.cartouche.ui.theme.TextPrimary
import fr.cklla.cartouche.ui.theme.TextSecondary
import fr.cklla.cartouche.ui.theme.TextTertiary
import fr.cklla.cartouche.ui.theme.labelRes
import fr.cklla.cartouche.ui.theme.palette
import java.util.Locale

/** Ratio portrait des jaquettes (largeur / hauteur), le même que la fiche Détail. */
private const val COVER_ASPECT_RATIO = 3f / 4f

/** Contenu d'une slide du récap en images ; les valeurs viennent telles quelles de [RecapSlide], sans calcul. */
@Composable
internal fun RecapSlideContent(slide: RecapSlide, year: Int, onGameClick: (String) -> Unit) {
    when (slide) {
        is RecapSlide.Total -> TotalSlide(slide)
        is RecapSlide.Favorites -> FavoritesSlide(slide, onGameClick)
        is RecapSlide.Longest -> LongestSlide(slide, onGameClick)
        is RecapSlide.Platforms -> PlatformsSlide(slide)
        is RecapSlide.Facts -> FactsSlide(slide, onGameClick)
        is RecapSlide.Mosaic -> MosaicSlide(slide, year, onGameClick)
    }
}

// --- Total ---

@Composable
private fun TotalSlide(slide: RecapSlide.Total) {
    val completedColor = GameStatus.TERMINE.palette().color
    val abandonedColor = GameStatus.ABANDONNE.palette().color
    // Rien de terminé (année avec seulement des abandons) : le gros chiffre prend la couleur du statut abandonné.
    val heroColor = if (slide.completedCount > 0) completedColor else abandonedColor
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.recap_story_total_kicker),
            style = CartoucheTextStyles.kicker,
            color = AccentPurpleLight,
        )
        Text(text = slide.completedCount.toString(), style = CartoucheTextStyles.recapHero, color = heroColor)
        Text(
            text = pluralStringResource(R.plurals.recap_story_total_title, slide.completedCount, slide.year),
            style = CartoucheTextStyles.detailTitle,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        if (slide.totalHours > 0) {
            Text(
                text = stringResource(R.string.recap_story_total_hours, slide.totalHours),
                style = CartoucheTextStyles.hoursValue,
                color = AccentPurpleLight,
            )
        }
        if (slide.abandonedCount > 0) {
            Text(
                text = pluralStringResource(R.plurals.recap_story_total_abandoned, slide.abandonedCount, slide.abandonedCount),
                style = CartoucheTextStyles.legendLabel,
                color = abandonedColor,
            )
        }
        // La répartition n'a de sens que s'il y a les deux statuts.
        if (slide.completedCount > 0 && slide.abandonedCount > 0) {
            Spacer(modifier = Modifier.height(32.dp))
            StatusSplit(slide = slide)
        }
    }
}

/** Barre proportionnelle Terminé / Abandonné et sa légende, aux couleurs de [GameStatus.palette]. */
@Composable
private fun StatusSplit(slide: RecapSlide.Total) {
    val segments = listOf(
        GameStatus.TERMINE to slide.completedCount,
        GameStatus.ABANDONNE to slide.abandonedCount,
    )
    // Décorative : la légende juste dessous porte les mêmes chiffres en texte.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        segments.forEach { (status, count) ->
            Box(
                modifier = Modifier
                    .weight(count.toFloat())
                    .fillMaxSize()
                    .background(status.palette().color),
            )
        }
    }
    Spacer(modifier = Modifier.height(20.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        segments.forEach { (status, count) -> StatusLegendRow(status = status, count = count) }
    }
}

@Composable
private fun StatusLegendRow(status: GameStatus, count: Int) {
    val color = status.palette().color
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(
            text = stringResource(status.labelRes()),
            style = CartoucheTextStyles.legendLabel,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(text = count.toString(), style = CartoucheTextStyles.legendCount, color = color)
    }
}

// --- Titre commun aux slides à contenu défilant ---

@Composable
private fun SlideHeader(kicker: String?, title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)) {
        if (kicker != null) {
            Text(text = kicker, style = CartoucheTextStyles.kicker, color = AccentPurpleLight)
        }
        Text(text = title, style = CartoucheTextStyles.detailTitle, color = TextPrimary)
        if (subtitle != null) {
            Text(text = subtitle, style = CartoucheTextStyles.detailSubtitle, color = TextTertiary)
        }
    }
}

// --- Coups de cœur ---

@Composable
private fun FavoritesSlide(slide: RecapSlide.Favorites, onGameClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        SlideHeader(
            kicker = stringResource(R.string.recap_story_favorites_kicker),
            title = stringResource(R.string.recap_story_favorites_title),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(slide.columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items = slide.games, key = { it.id }) { game ->
                FavoriteCell(game = game, onClick = { onGameClick(game.id) })
            }
        }
    }
}

/**
 * Jaquette, étoiles et titre. La jaquette est décorative pour TalkBack : le titre juste dessous
 * la décrit, la cellule entière étant un seul bouton « Ouvrir la fiche ».
 */
@Composable
private fun FavoriteCell(game: Game, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = stringResource(R.string.recap_story_open_game), onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GameCoverPlaceholder(
            title = game.title,
            coverUrl = game.coverUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_ASPECT_RATIO),
        )
        game.rating?.let { RatingStars(rating = it) }
        Text(
            text = game.title,
            style = CartoucheTextStyles.cardSubtitle,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RatingStars(rating: Int) {
    val description = stringResource(R.string.recap_story_rating_content_description, rating)
    Row(modifier = Modifier.semantics { contentDescription = description }) {
        for (star in 1..5) {
            val filled = star <= rating
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = null,
                tint = if (filled) AccentPurpleLight else AccentPurpleMuted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// --- Plus longs ---

@Composable
private fun LongestSlide(slide: RecapSlide.Longest, onGameClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        SlideHeader(
            kicker = stringResource(R.string.recap_story_longest_kicker),
            title = stringResource(R.string.recap_story_longest_title),
        )
        // Au plus MAX_LONGEST lignes : une colonne défilante à la verticale suffit, pour les petits écrans.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            slide.games.forEachIndexed { index, game ->
                LongestRow(rank = index + 1, game = game, onClick = { onGameClick(game.id) })
            }
        }
    }
}

@Composable
private fun LongestRow(rank: Int, game: Game, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceCard)
            .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.5f)), shape)
            .clickable(onClickLabel = stringResource(R.string.recap_story_open_game), onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.recap_story_longest_rank, rank),
            style = CartoucheTextStyles.statValueMedium,
            color = AccentPurpleLight,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp),
        )
        GameCoverPlaceholder(title = game.title, coverUrl = game.coverUrl, width = 56.dp, height = 76.dp)
        Text(
            text = game.title,
            style = CartoucheTextStyles.cardTitle,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.recap_story_longest_duration, game.userPlaytimeHours),
            style = CartoucheTextStyles.hoursValue,
            color = TextPrimary,
        )
    }
}

// --- Plateformes ---

@Composable
private fun PlatformsSlide(slide: RecapSlide.Platforms) {
    val dotColor = GameStatus.TERMINE.palette().color
    Column(modifier = Modifier.fillMaxSize()) {
        SlideHeader(
            kicker = stringResource(R.string.recap_story_platforms_kicker),
            title = stringResource(R.string.recap_story_platforms_title),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            slide.counts.forEach { entry ->
                PlatformBarRow(entry = entry, maxCount = slide.maxCount, color = dotColor)
            }
        }
    }
}

/** Une plateforme, son nombre de jeux et une barre proportionnelle au maximum de la slide. */
@Composable
private fun PlatformBarRow(entry: PlatformCount, maxCount: Int, color: Color) {
    val fraction = if (maxCount > 0) entry.count.toFloat() / maxCount else 0f
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(color))
            Text(
                text = entry.platform,
                style = CartoucheTextStyles.legendLabel,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = pluralStringResource(R.plurals.recap_story_platforms_count, entry.count, entry.count),
                style = CartoucheTextStyles.legendCount,
                color = TextPrimary,
            )
        }
        // Décorative : le nombre de jeux est déjà lu juste au-dessus.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceCard)
                .clearAndSetSemantics {},
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
    }
}

// --- Faits ---

@Composable
private fun FactsSlide(slide: RecapSlide.Facts, onGameClick: (String) -> Unit) {
    val facts = slide.facts
    val locale = rememberRecapLocale()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(R.string.recap_story_facts_title), style = CartoucheTextStyles.detailTitle, color = TextPrimary)
        facts.first?.let { DatedGameFactCard(label = R.string.recap_story_fact_first, fact = it, locale = locale, onGameClick = onGameClick) }
        facts.last?.let { DatedGameFactCard(label = R.string.recap_story_fact_last, fact = it, locale = locale, onGameClick = onGameClick) }
        facts.busiestMonth?.let { BusiestMonthCard(busiest = it, locale = locale) }
        facts.oldest?.let { OldestFactCard(game = it, onGameClick = onGameClick) }
    }
}

@Composable
private fun DatedGameFactCard(label: Int, fact: DatedGame, locale: Locale, onGameClick: (String) -> Unit) {
    FactCard(
        label = stringResource(label),
        headline = fact.game.title,
        detail = formatRecapDate(fact.date, locale),
        game = fact.game,
        onClick = { onGameClick(fact.game.id) },
    )
}

@Composable
private fun OldestFactCard(game: Game, onGameClick: (String) -> Unit) {
    FactCard(
        label = stringResource(R.string.recap_story_fact_oldest),
        headline = game.title,
        detail = game.releaseYear?.let { stringResource(R.string.recap_story_fact_oldest_released, it) }.orEmpty(),
        game = game,
        onClick = { onGameClick(game.id) },
    )
}

@Composable
private fun BusiestMonthCard(busiest: BusiestMonth, locale: Locale) {
    FactCard(
        label = stringResource(R.string.recap_story_fact_busiest_month),
        headline = monthName(busiest.month, locale).replaceFirstChar { it.titlecase(locale) },
        detail = pluralStringResource(R.plurals.recap_story_fact_month_count, busiest.count, busiest.count),
        game = null,
        onClick = null,
    )
}

@Composable
private fun FactCard(label: String, headline: String, detail: String, game: Game?, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(12.dp)
    val openLabel = stringResource(R.string.recap_story_open_game)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceCard)
            .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.5f)), shape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = openLabel, onClick = onClick) else Modifier)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (game != null) {
            GameCoverPlaceholder(title = game.title, coverUrl = game.coverUrl, width = 60.dp, height = 80.dp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = label.uppercase(), style = CartoucheTextStyles.sectionLabel, color = TextTertiary)
            Text(
                text = headline,
                style = if (game == null) CartoucheTextStyles.statValueLarge else CartoucheTextStyles.cardTitle,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotEmpty()) {
                Text(text = detail, style = CartoucheTextStyles.detailSubtitle, color = AccentPurpleLight)
            }
        }
    }
}

// --- Mosaïque ---

@Composable
private fun MosaicSlide(slide: RecapSlide.Mosaic, year: Int, onGameClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        SlideHeader(
            kicker = null,
            title = stringResource(R.string.recap_story_mosaic_title),
            subtitle = pluralStringResource(
                R.plurals.recap_story_mosaic_subtitle,
                slide.games.size,
                slide.games.size,
                year,
            ),
        )
        // LazyVerticalGrid : seules les tuiles visibles sont composées et chargées, même avec une centaine de jeux.
        LazyVerticalGrid(
            columns = GridCells.Fixed(MOSAIC_COLUMNS),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items = slide.games, key = { it.id }) { game ->
                MosaicTile(game = game, onClick = { onGameClick(game.id) })
            }
        }
    }
}

/** Petite jaquette ; sans jaquette, le titre lisible sur fond [SurfaceCard]. */
@Composable
private fun MosaicTile(game: Game, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    val tileModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(COVER_ASPECT_RATIO)
        .clip(shape)
        .clickable(onClickLabel = stringResource(R.string.recap_story_open_game), onClick = onClick)
    if (game.coverUrl != null) {
        AsyncImage(
            model = game.coverUrl,
            contentDescription = game.title,
            contentScale = ContentScale.Crop,
            modifier = tileModifier,
        )
    } else {
        Box(
            modifier = tileModifier
                .background(SurfaceCard)
                .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.5f)), shape)
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = game.title,
                style = CartoucheTextStyles.cardSubtitle.copy(fontSize = 9.sp, lineHeight = 11.sp),
                color = TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// --- Aides ---

/** Langue des noms de mois, lue dans les ressources plutôt que figée dans le code. */
@Composable
private fun rememberRecapLocale(): Locale {
    val tag = stringResource(R.string.recap_date_locale)
    return remember(tag) { Locale.forLanguageTag(tag) }
}

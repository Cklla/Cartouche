package fr.cklla.cartouche.ui.recherche

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cklla.cartouche.R
import fr.cklla.cartouche.domain.model.GameSearchResult
import fr.cklla.cartouche.ui.components.GameCoverPlaceholder
import fr.cklla.cartouche.ui.theme.AccentPurple
import fr.cklla.cartouche.ui.theme.AccentPurpleLight
import fr.cklla.cartouche.ui.theme.AccentPurpleMuted
import fr.cklla.cartouche.ui.theme.BackgroundDark
import fr.cklla.cartouche.ui.theme.BorderHairline
import fr.cklla.cartouche.ui.theme.CartoucheTextStyles
import fr.cklla.cartouche.ui.theme.CartoucheTheme
import fr.cklla.cartouche.ui.theme.SuccessGreen
import fr.cklla.cartouche.ui.theme.SurfaceCard
import fr.cklla.cartouche.ui.theme.TextMuted
import fr.cklla.cartouche.ui.theme.TextPrimary
import fr.cklla.cartouche.ui.theme.TextSecondary

@Composable
fun RechercheScreen(
    modifier: Modifier = Modifier,
    viewModel: RechercheViewModel = hiltViewModel(),
    onResultClick: (GameSearchResult, backlogGameId: String?) -> Unit = { _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RechercheContent(
        uiState = uiState,
        onQueryChanged = viewModel::onQueryChanged,
        onSuggestionSelected = viewModel::onSuggestionSelected,
        onAddGame = viewModel::onAddGame,
        onResultClick = onResultClick,
        modifier = modifier,
    )
}

@Composable
private fun RechercheContent(
    uiState: RechercheUiState,
    onQueryChanged: (String) -> Unit,
    onSuggestionSelected: (String) -> Unit,
    onAddGame: (GameSearchResult) -> Unit,
    onResultClick: (GameSearchResult, backlogGameId: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark),
    ) {
        Header()
        SearchBar(query = uiState.query, onQueryChanged = onQueryChanged, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(modifier = Modifier.height(16.dp))
        when {
            uiState.query.isBlank() -> SuggestionsSection(onSuggestionSelected = onSuggestionSelected)
            uiState.isSearching -> LoadingState()
            uiState.errorMessage != null -> ErrorState(message = uiState.errorMessage)
            uiState.results.isEmpty() -> NoResultsState(query = uiState.query)
            else -> ResultsList(
                results = uiState.results,
                backlogGameIdsByTitle = uiState.backlogGameIdsByTitle,
                onAddGame = onAddGame,
                onResultClick = onResultClick,
            )
        }
    }
}

@Composable
private fun Header() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
    ) {
        Text(text = stringResource(R.string.recherche_kicker), style = CartoucheTextStyles.kicker, color = AccentPurpleLight)
        Text(text = stringResource(R.string.recherche_title), style = CartoucheTextStyles.screenTitle, color = TextPrimary)
    }
}

@Composable
private fun SearchBar(query: String, onQueryChanged: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.6f)), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(imageVector = Icons.Outlined.Search, contentDescription = null, tint = TextMuted)
        Box(modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                singleLine = true,
                textStyle = CartoucheTextStyles.chipLabel.copy(color = TextPrimary),
                cursorBrush = SolidColor(AccentPurple),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(text = stringResource(R.string.recherche_search_placeholder), style = CartoucheTextStyles.chipLabel, color = AccentPurpleMuted)
                    }
                    innerTextField()
                },
            )
        }
    }
}

@Composable
private fun SuggestionsSection(onSuggestionSelected: (String) -> Unit) {
    val suggestions = stringArrayResource(R.array.recherche_suggestions)
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = stringResource(R.string.recherche_suggestions_label).uppercase(),
            style = CartoucheTextStyles.sectionLabel,
            color = TextMuted,
        )
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { suggestion ->
                SuggestionChip(label = suggestion, onClick = { onSuggestionSelected(suggestion) })
            }
        }
    }
}

@Composable
private fun SuggestionChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.7f)), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = CartoucheTextStyles.chipLabel, color = TextSecondary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AccentPurple)
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Text(text = message, style = CartoucheTextStyles.emptyMessage, color = TextMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NoResultsState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .border(BorderStroke(1.5.dp, AccentPurpleMuted), CircleShape),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.recherche_empty_title), style = CartoucheTextStyles.emptyTitle, color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.recherche_empty_message, query),
            style = CartoucheTextStyles.emptyMessage,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResultsList(
    results: List<GameSearchResult>,
    backlogGameIdsByTitle: Map<String, String>,
    onAddGame: (GameSearchResult) -> Unit,
    onResultClick: (GameSearchResult, backlogGameId: String?) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = results, key = { it.rawgId }) { result ->
            val backlogGameId = backlogGameId(result.title, backlogGameIdsByTitle)
            ResultRow(
                result = result,
                backlogGameId = backlogGameId,
                onAddGame = onAddGame,
                onClick = { onResultClick(result, backlogGameId) },
            )
        }
    }
}

@Composable
private fun ResultRow(
    result: GameSearchResult,
    backlogGameId: String?,
    onAddGame: (GameSearchResult) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GameCoverPlaceholder(title = result.title, width = 48.dp, height = 64.dp, coverUrl = result.coverUrl)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = result.title, style = CartoucheTextStyles.searchResultTitle, color = TextPrimary, maxLines = 2)
            Text(
                text = stringResource(R.string.recherche_result_platform_year, result.platform, result.year),
                style = CartoucheTextStyles.cardSubtitle,
                color = TextMuted,
            )
        }
        if (backlogGameId != null) {
            AddedPill()
        } else {
            AddButton(title = result.title, onClick = { onAddGame(result) })
        }
    }
}

@Composable
private fun AddedPill() {
    Box(
        modifier = Modifier
            .heightIn(min = 30.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SuccessGreen.copy(alpha = 0.12f))
            .border(BorderStroke(0.5.dp, SuccessGreen.copy(alpha = 0.4f)), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.recherche_added_label),
            style = CartoucheTextStyles.addedPillLabel,
            color = SuccessGreen,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun AddButton(title: String, onClick: () -> Unit) {
    Box(
        // Zone de clic 48dp (accessibilité) autour du bouton visuel 30dp de la maquette.
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(AccentPurple),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.recherche_add_content_description, title),
                tint = TextPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun RechercheSuggestionsPreview() {
    CartoucheTheme {
        RechercheContent(
            uiState = RechercheUiState(),
            onQueryChanged = {},
            onSuggestionSelected = {},
            onAddGame = {},
            onResultClick = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun RechercheResultsPreview() {
    val results = listOf(
        GameSearchResult(rawgId = 1, title = "The Legend of Zelda: Tears of the Kingdom", platform = "Switch", genre = "Aventure", year = "2023", coverUrl = null),
        GameSearchResult(rawgId = 2, title = "Hades", platform = "PC", genre = "Roguelike", year = "2020", coverUrl = null),
    )
    CartoucheTheme {
        RechercheContent(
            uiState = RechercheUiState(query = "ze", results = results),
            onQueryChanged = {},
            onSuggestionSelected = {},
            onAddGame = {},
            onResultClick = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun RechercheEmptyPreview() {
    CartoucheTheme {
        RechercheContent(
            uiState = RechercheUiState(query = "zzzzz"),
            onQueryChanged = {},
            onSuggestionSelected = {},
            onAddGame = {},
            onResultClick = { _, _ -> },
        )
    }
}

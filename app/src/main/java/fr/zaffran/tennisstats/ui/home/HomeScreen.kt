package fr.zaffran.tennisstats.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.zaffran.tennisstats.R
import fr.zaffran.tennisstats.domain.model.Match
import fr.zaffran.tennisstats.domain.model.MatchResult
import fr.zaffran.tennisstats.domain.model.MatchStatus
import fr.zaffran.tennisstats.domain.model.MatchSummary
import fr.zaffran.tennisstats.navigation.MatchTab
import fr.zaffran.tennisstats.ui.components.MatchStatusBadge
import fr.zaffran.tennisstats.ui.components.ThemePreviews
import fr.zaffran.tennisstats.ui.components.formatDate
import fr.zaffran.tennisstats.ui.components.opponentLevelLabel
import fr.zaffran.tennisstats.ui.components.shotCountText
import fr.zaffran.tennisstats.ui.theme.TennisStatsTheme
import java.time.Instant

@Composable
fun HomeScreen(
    onNewMatch: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenMatch: (matchId: String, tab: MatchTab) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(uiState = uiState, onNewMatch = onNewMatch, onOpenStats = onOpenStats, onOpenMatch = onOpenMatch)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onNewMatch: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenMatch: (matchId: String, tab: MatchTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onOpenStats) {
                        Icon(painterResource(R.drawable.ic_bar_chart), stringResource(R.string.home_stats))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (uiState.canCreateMatch) {
                // Content-slot overload: the icon/text overload hides the label from accessibility services.
                ExtendedFloatingActionButton(onClick = onNewMatch) {
                    Icon(painterResource(R.drawable.ic_sports_tennis), contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.home_new_match))
                }
            }
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                // Leaves room for the FAB above the navigation bar.
                bottom = innerPadding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            uiState.activeMatch?.let { active ->
                item(key = "active") {
                    ActiveMatchCard(active, onClick = { onOpenMatch(active.match.id, MatchTab.INPUT) })
                }
                item(key = "info") {
                    Text(
                        text = stringResource(R.string.home_finish_current_match),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item(key = "historyHeader") {
                Text(
                    text = stringResource(R.string.home_recent_matches),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { heading() },
                )
            }
            if (uiState.completedMatches.isEmpty()) {
                item(key = "empty") { EmptyHistory() }
            } else {
                items(uiState.completedMatches, key = { it.match.id }) { summary ->
                    HistoryRow(summary, onClick = { onOpenMatch(summary.match.id, MatchTab.STATS) })
                }
            }
        }
    }
}

@Composable
private fun ActiveMatchCard(summary: MatchSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val green = TennisStatsTheme.semanticColors.green
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = green.container),
        border = BorderStroke(2.dp, green.content),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MatchStatusBadge(MatchStatus.IN_PROGRESS, null)
            Text(
                text = stringResource(R.string.opponent_vs, summary.match.opponentName),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            summary.match.opponentLevel?.let { level ->
                Text(
                    text = stringResource(R.string.opponent_level, opponentLevelLabel(level)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            MatchMeta(summary)
            Text(
                text = stringResource(R.string.home_resume_match),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = green.content,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun HistoryRow(summary: MatchSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.opponent_vs, summary.match.opponentName),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MatchMeta(summary)
            }
            MatchStatusBadge(summary.match.status, summary.match.result)
        }
    }
}

/** Date and number of shots. */
@Composable
private fun MatchMeta(summary: MatchSummary) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconText(
            R.drawable.ic_schedule,
            stringResource(R.string.home_date_description),
            formatDate(summary.match.playedAt),
        )
        IconText(
            R.drawable.ic_monitoring,
            stringResource(R.string.home_shot_count_description),
            shotCountText(summary.shotCount),
        )
    }
}

@Composable
private fun IconText(icon: Int, iconDescription: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = iconDescription,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_sports_tennis),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.home_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun previewMatch(id: String, name: String, status: MatchStatus, result: MatchResult?, level: String? = null) =
    Match(
        id = id,
        opponentName = name,
        opponentLevel = level,
        result = result,
        status = status,
        playedAt = Instant.parse("2026-10-26T10:00:00Z"),
        createdAt = Instant.parse("2026-10-26T10:00:00Z"),
        updatedAt = Instant.parse("2026-10-26T10:00:00Z"),
    )

@ThemePreviews
@Composable
private fun HomeWithActiveMatchPreview() {
    TennisStatsTheme {
        HomeContent(
            uiState = HomeUiState(
                isLoading = false,
                activeMatch = MatchSummary(previewMatch("1", "Jean Dupont", MatchStatus.IN_PROGRESS, null, "15/4"), 1),
                completedMatches = listOf(
                    MatchSummary(previewMatch("2", "Paul Martin", MatchStatus.COMPLETED, MatchResult.WIN), 42),
                    MatchSummary(previewMatch("3", "Luc Bernard", MatchStatus.COMPLETED, MatchResult.LOSS), 0),
                ),
            ),
            onNewMatch = {},
            onOpenStats = {},
            onOpenMatch = { _, _ -> },
        )
    }
}

@ThemePreviews
@Composable
private fun HomeEmptyPreview() {
    TennisStatsTheme {
        HomeContent(uiState = HomeUiState(isLoading = false), onNewMatch = {
        }, onOpenStats = {}, onOpenMatch = { _, _ -> })
    }
}

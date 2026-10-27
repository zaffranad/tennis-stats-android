package fr.zaffran.tennisstats.ui.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.zaffran.tennisstats.R
import fr.zaffran.tennisstats.domain.model.Match
import fr.zaffran.tennisstats.domain.model.MatchResult
import fr.zaffran.tennisstats.domain.model.MatchStatus
import fr.zaffran.tennisstats.domain.model.Shot
import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotType
import fr.zaffran.tennisstats.domain.stats.HistoryCalculator
import fr.zaffran.tennisstats.domain.stats.MatchTally
import fr.zaffran.tennisstats.domain.stats.MetricCalculator
import fr.zaffran.tennisstats.domain.stats.ShotTally
import fr.zaffran.tennisstats.domain.stats.StatMetric
import fr.zaffran.tennisstats.domain.stats.StatsCalculator
import fr.zaffran.tennisstats.domain.stats.StatsPeriod
import fr.zaffran.tennisstats.domain.stats.TrendCalculator
import fr.zaffran.tennisstats.navigation.MatchTab
import fr.zaffran.tennisstats.ui.components.ThemePreviews
import fr.zaffran.tennisstats.ui.theme.TennisStatsTheme
import java.time.Instant

@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onOpenMatch: (matchId: String, tab: MatchTab) -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StatsContent(
        uiState = uiState,
        onBack = onBack,
        onTabSelected = viewModel::onTabSelected,
        onPeriodSelected = viewModel::onPeriodSelected,
        onMetricSelected = viewModel::onMetricSelected,
        onOpenMatch = onOpenMatch,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsContent(
    uiState: StatsUiState,
    onBack: () -> Unit,
    onTabSelected: (StatsTab) -> Unit,
    onPeriodSelected: (StatsPeriod) -> Unit,
    onMetricSelected: (StatMetric) -> Unit,
    onOpenMatch: (matchId: String, tab: MatchTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        // The bottom inset is handled by the lists so that they can scroll behind the navigation bar.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
        ),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PrimaryTabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                StatsTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        text = { Text(stringResource(tab.label)) },
                    )
                }
            }
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            val contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            )
            when (uiState.selectedTab) {
                StatsTab.MATCH -> FocusMatchTab(uiState.focus, contentPadding, onOpenMatch)

                StatsTab.HISTORY -> HistoryTab(uiState, contentPadding, onPeriodSelected)

                StatsTab.EVOLUTION -> EvolutionTab(
                    uiState = uiState,
                    contentPadding = contentPadding,
                    onPeriodSelected = onPeriodSelected,
                    onMetricSelected = onMetricSelected,
                    onOpenMatch = { onOpenMatch(it, MatchTab.STATS) },
                )
            }
        }
    }
}

private val StatsTab.label: Int
    get() = when (this) {
        StatsTab.MATCH -> R.string.stats_tab_match
        StatsTab.HISTORY -> R.string.stats_tab_history
        StatsTab.EVOLUTION -> R.string.stats_tab_evolution
    }

/** « Tous » / « 10 derniers » / « 5 derniers », shared by the « Historique » and « Évolution » tabs. */
@Composable
fun PeriodSelector(selected: StatsPeriod, onSelected: (StatsPeriod) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        StatsPeriod.entries.forEachIndexed { index, period ->
            SegmentedButton(
                selected = period == selected,
                onClick = { onSelected(period) },
                shape = SegmentedButtonDefaults.itemShape(index, StatsPeriod.entries.size),
                label = { Text(periodLabel(period)) },
            )
        }
    }
}

/** Centred icon, message and explanation. */
@Composable
fun StatsEmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bar_chart),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(12.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Six completed matches and one in progress, for the previews. */
private fun previewUiState(tab: StatsTab): StatsUiState {
    val base = Instant.parse("2026-09-01T10:00:00Z")
    fun shots(matchId: String, firstIn: Int, forehandIn: Int, doubleFaults: Int) = List(10) { i ->
        Shot(
            "$matchId-s$i",
            matchId,
            ShotType.SERVE,
            if (i <
                firstIn
            ) {
                ShotOutcome.IN
            } else {
                ShotOutcome.FAULT
            },
            false,
            base,
        )
    } +
        List(doubleFaults) { i -> Shot("$matchId-d$i", matchId, ShotType.SERVE, ShotOutcome.NET, true, base) } +
        List(10) { i ->
            Shot(
                "$matchId-f$i",
                matchId,
                ShotType.FOREHAND,
                if (i <
                    forehandIn
                ) {
                    ShotOutcome.WINNER
                } else {
                    ShotOutcome.NET
                },
                false,
                base,
            )
        }
    val completed = listOf(4 to 5, 5 to 6, 6 to 5, 6 to 7, 7 to 6, 8 to 8).mapIndexed { i, (firstIn, forehandIn) ->
        val match = Match(
            id = "m$i",
            opponentName = "Adversaire ${i + 1}",
            opponentLevel = null,
            result = if (i % 3 == 1) MatchResult.LOSS else MatchResult.WIN,
            status = MatchStatus.COMPLETED,
            playedAt = base.plusSeconds(i * 7 * 86_400L),
            createdAt = base,
            updatedAt = base,
        )
        MatchTally(match, ShotTally.of(shots(match.id, firstIn, forehandIn, doubleFaults = 3 - i / 2)))
    }.reversed()
    val focus = completed.first()
    val others = completed.drop(1).map { it.tally }
    return StatsUiState(
        isLoading = false,
        selectedTab = tab,
        focus = FocusMatch(
            match = focus.match,
            shotCount = focus.tally.total,
            stats = StatsCalculator.computeStats(focus.tally),
            comparison = MetricCalculator.compare(focus.tally, others),
            comparedMatchCount = others.size,
        ),
        completedMatchCount = completed.size,
        periodMatchCount = completed.size,
        history = HistoryCalculator.computeHistory(completed),
        trend = TrendCalculator.computeTrend(StatMetric.FIRST_SERVE, completed),
    )
}

@Composable
private fun StatsPreview(tab: StatsTab) {
    TennisStatsTheme {
        StatsContent(
            uiState = previewUiState(tab),
            onBack = {},
            onTabSelected = {},
            onPeriodSelected = {},
            onMetricSelected = {},
            onOpenMatch = { _, _ -> },
        )
    }
}

@ThemePreviews
@Composable
private fun StatsMatchTabPreview() = StatsPreview(StatsTab.MATCH)

@ThemePreviews
@Composable
private fun StatsHistoryTabPreview() = StatsPreview(StatsTab.HISTORY)

@ThemePreviews
@Composable
private fun StatsEvolutionTabPreview() = StatsPreview(StatsTab.EVOLUTION)

@ThemePreviews
@Composable
private fun StatsEmptyPreview() {
    TennisStatsTheme {
        StatsContent(
            uiState = StatsUiState(isLoading = false),
            onBack = {},
            onTabSelected = {},
            onPeriodSelected = {},
            onMetricSelected = {},
            onOpenMatch = { _, _ -> },
        )
    }
}

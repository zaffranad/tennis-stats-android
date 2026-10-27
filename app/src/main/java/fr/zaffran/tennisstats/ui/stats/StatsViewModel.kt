package fr.zaffran.tennisstats.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.zaffran.tennisstats.data.repository.MatchRepository
import fr.zaffran.tennisstats.domain.model.MatchSummary
import fr.zaffran.tennisstats.domain.model.Shot
import fr.zaffran.tennisstats.domain.stats.HistoryCalculator
import fr.zaffran.tennisstats.domain.stats.MatchTally
import fr.zaffran.tennisstats.domain.stats.MetricCalculator
import fr.zaffran.tennisstats.domain.stats.ShotTally
import fr.zaffran.tennisstats.domain.stats.StatMetric
import fr.zaffran.tennisstats.domain.stats.StatsCalculator
import fr.zaffran.tennisstats.domain.stats.StatsPeriod
import fr.zaffran.tennisstats.domain.stats.TrendCalculator
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Statistics screen (statistics spec): current match, history and evolution, all observed live. */
@HiltViewModel
class StatsViewModel @Inject constructor(repository: MatchRepository, private val savedStateHandle: SavedStateHandle) :
    ViewModel() {

    private val selection = MutableStateFlow(
        Selection(
            tab = savedStateHandle.get<String>(KEY_TAB)?.let(StatsTab::valueOf) ?: StatsTab.MATCH,
            period = savedStateHandle.get<String>(KEY_PERIOD)?.let(StatsPeriod::valueOf) ?: StatsPeriod.ALL,
            metric = savedStateHandle.get<String>(KEY_METRIC)?.let(StatMetric::valueOf) ?: StatMetric.FIRST_SERVE,
        ),
    )

    private val activeMatch = repository.observeActiveMatch()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val activeShots = activeMatch.map { it?.match?.id }.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeShots(id)
    }

    val uiState: StateFlow<StatsUiState> = combine(
        activeMatch,
        activeShots,
        repository.observeCompletedMatchTallies(),
        selection,
    ) { active, shots, completed, s ->
        val periodMatches = s.period.select(completed)
        StatsUiState(
            isLoading = false,
            selectedTab = s.tab,
            period = s.period,
            metric = s.metric,
            focus = focusMatch(active, shots, completed),
            completedMatchCount = completed.size,
            periodMatchCount = periodMatches.size,
            history = HistoryCalculator.computeHistory(periodMatches),
            trend = TrendCalculator.computeTrend(s.metric, periodMatches),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StatsUiState())

    fun onTabSelected(tab: StatsTab) {
        savedStateHandle[KEY_TAB] = tab.name
        selection.update { it.copy(tab = tab) }
    }

    fun onPeriodSelected(period: StatsPeriod) {
        savedStateHandle[KEY_PERIOD] = period.name
        selection.update { it.copy(period = period) }
    }

    fun onMetricSelected(metric: StatMetric) {
        savedStateHandle[KEY_METRIC] = metric.name
        selection.update { it.copy(metric = metric) }
    }

    /** The in-progress match (live), otherwise the last completed one, compared with the other completed matches. */
    private fun focusMatch(active: MatchSummary?, activeShots: List<Shot>, completed: List<MatchTally>): FocusMatch? {
        val (match, tally) = when {
            // The shots may briefly belong to the previous active match: only use those of this one.
            active != null -> active.match to ShotTally.of(activeShots.filter { it.matchId == active.match.id })

            else -> completed.firstOrNull()?.let { it.match to it.tally } ?: return null
        }
        val others = completed.filter { it.match.id != match.id }.map { it.tally }
        return FocusMatch(
            match = match,
            shotCount = tally.total,
            stats = StatsCalculator.computeStats(tally),
            comparison = MetricCalculator.compare(tally, others),
            comparedMatchCount = others.size,
        )
    }

    private data class Selection(val tab: StatsTab, val period: StatsPeriod, val metric: StatMetric)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val KEY_TAB = "stats.tab"
        const val KEY_PERIOD = "stats.period"
        const val KEY_METRIC = "stats.metric"
    }
}

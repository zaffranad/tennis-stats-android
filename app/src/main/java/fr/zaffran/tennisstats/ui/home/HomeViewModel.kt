package fr.zaffran.tennisstats.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.zaffran.tennisstats.data.repository.MatchRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(repository: MatchRepository) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeActiveMatch(),
        repository.observeCompletedMatches(),
    ) { active, completed ->
        HomeUiState(isLoading = false, activeMatch = active, completedMatches = completed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

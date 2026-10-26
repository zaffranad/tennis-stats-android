package fr.zaffran.tennisstats.data.repository

/** Business rule violations reported by [MatchRepository]. */
sealed interface MatchError {
    /** RG-01: a match is already in progress. */
    data object MatchAlreadyInProgress : MatchError

    /** RG-02: the opponent name is blank. */
    data object OpponentNameRequired : MatchError

    /** RG-03, RG-04, RG-06: the operation requires an in-progress match. */
    data object MatchNotInProgress : MatchError

    data object MatchNotFound : MatchError
}

sealed interface RepositoryResult<out T> {
    data class Success<out T>(val value: T) : RepositoryResult<T>

    data class Failure(val error: MatchError) : RepositoryResult<Nothing>
}

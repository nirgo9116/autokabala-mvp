package com.autokabala.listener

sealed class MatchResult {
    data class SingleMatch(val client: ClientEntity, val isStrong: Boolean) : MatchResult()
    data class MultipleMatches(val clients: List<ClientEntity>) : MatchResult()
    object NoMatch : MatchResult()
}

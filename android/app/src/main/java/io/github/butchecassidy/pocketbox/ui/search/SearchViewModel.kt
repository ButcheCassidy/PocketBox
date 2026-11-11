// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.butchecassidy.pocketbox.data.repo.SearchResults
import io.github.butchecassidy.pocketbox.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which result groups to show. */
enum class SearchFilter { ALL, SONGS, ALBUMS, ARTISTS }

/** Results update on every keystroke (60 ms debounce); a newer query cancels the older one. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    val filter = MutableStateFlow(SearchFilter.ALL)

    val results: StateFlow<SearchResults> = query
        .debounce(DEBOUNCE_MS)
        .distinctUntilChanged()
        .flatMapLatest { text -> flow { emit(if (text.isBlank()) SearchResults(text) else container.search.search(text)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SearchResults())

    val recentSearches: StateFlow<List<String>> = container.history.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun onQueryChanged(text: String) {
        query.value = text
    }

    /** Remembers a search once the user acts on its results (stored only on this phone). */
    fun remember(text: String) {
        viewModelScope.launch { container.history.addRecentSearch(text) }
    }

    fun clearRecent() {
        viewModelScope.launch { container.history.clearRecentSearches() }
    }

    private companion object {
        const val DEBOUNCE_MS = 60L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

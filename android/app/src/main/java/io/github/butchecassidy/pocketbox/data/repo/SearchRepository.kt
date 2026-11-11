// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.repo

import io.github.butchecassidy.pocketbox.data.db.PocketBoxDatabase
import io.github.butchecassidy.pocketbox.domain.model.Album
import io.github.butchecassidy.pocketbox.domain.model.Artist
import io.github.butchecassidy.pocketbox.domain.model.Track
import io.github.butchecassidy.pocketbox.domain.search.Fuzzy
import io.github.butchecassidy.pocketbox.domain.search.MatchTier
import io.github.butchecassidy.pocketbox.domain.search.SearchText
import io.github.butchecassidy.pocketbox.domain.sort.SortKeys
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A song result with its rank and the title letters to show in bold. */
data class RankedTrack(val track: Track, val tier: MatchTier, val highlights: List<IntRange>)

/** The best single answer shown first. */
sealed interface TopResult {
    data class Song(val result: RankedTrack) : TopResult
    data class Release(val album: Album) : TopResult
    data class Person(val artist: Artist) : TopResult
}

data class SearchResults(
    val query: String = "",
    val tokens: List<String> = emptyList(),
    val top: TopResult? = null,
    val songs: List<RankedTrack> = emptyList(),
    val albums: List<Album> = emptyList(),
    val singles: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val closeMatches: List<Track> = emptyList(),
) {
    val isEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty() && singles.isEmpty() && artists.isEmpty() && closeMatches.isEmpty()
}

/**
 * Instant search: a Room FTS4 prefix query finds candidates, then an in-memory pass ranks them
 * (title starts with → a title word starts with → title contains → other fields; ties A–Z), groups
 * them, and adds typo-tolerant "close matches" from a cached list of names. No network, no ML.
 */
class SearchRepository(
    db: PocketBoxDatabase,
    private val library: LibraryRepository,
    private val cpu: CoroutineDispatcher,
) {
    private val dao = db.libraryDao()
    private val namesLock = Mutex()
    private var names: List<String> = emptyList()
    private var namesForCount = -1

    suspend fun search(query: String): SearchResults = withContext(cpu) {
        val tokens = SearchText.tokens(query)
        val fts = SearchText.ftsQuery(tokens) ?: return@withContext SearchResults(query)
        val tracks = dao.search(fts, CANDIDATE_LIMIT).map { it.toTrack() }

        val ranked = tracks
            .map { RankedTrack(it, SearchText.tier(it.title, tokens), SearchText.highlights(it.title, tokens)) }
            .sortedWith(compareBy<RankedTrack> { it.tier }.thenComparator { a, b -> SortKeys.comparator.compare(a.track.title, b.track.title) })

        val releaseKeys = rankedKeys(tracks, tokens, { it.albumKey }, { it.album })
        val releases = library.albumsByKeys(releaseKeys.map { it.first }).associateBy { it.key }
        val orderedReleases = releaseKeys.mapNotNull { releases[it.first] }
        val (singles, albums) = orderedReleases.partition { it.releaseType.isSingleOrEp }

        val artistKeys = rankedKeys(tracks, tokens, { it.artistKey }, { it.albumArtist })
        val artistsByKey = library.artistsByKeys(artistKeys.map { it.first }).associateBy { it.key }
        val artists = artistKeys.mapNotNull { artistsByKey[it.first] }

        SearchResults(
            query = query,
            tokens = tokens,
            top = topResult(ranked.firstOrNull(), orderedReleases.firstOrNull(), releaseKeys.firstOrNull()?.second, artists.firstOrNull(), artistKeys.firstOrNull()?.second),
            songs = ranked,
            albums = albums,
            singles = singles,
            artists = artists,
            closeMatches = closeMatches(query, tracks.mapTo(HashSet()) { it.id }),
        )
    }

    /** Distinct albums or artists whose own name matches, best tier first, then A–Z. */
    private fun rankedKeys(
        tracks: List<Track>,
        tokens: List<String>,
        key: (Track) -> String,
        name: (Track) -> String,
    ): List<Pair<String, MatchTier>> = tracks
        .distinctBy(key)
        .map { Triple(key(it), name(it), SearchText.tier(name(it), tokens)) }
        .filter { it.third != MatchTier.OTHER_FIELD }
        .sortedWith(compareBy<Triple<String, String, MatchTier>> { it.third }.thenComparator { a, b -> SortKeys.comparator.compare(a.second, b.second) })
        .map { it.first to it.third }

    /** Prefers the closest match; on a tie an artist beats an album, and an album beats a song. */
    private fun topResult(song: RankedTrack?, album: Album?, albumTier: MatchTier?, artist: Artist?, artistTier: MatchTier?): TopResult? {
        val candidates = listOfNotNull(
            artist?.let { Triple(TopResult.Person(it), artistTier ?: MatchTier.OTHER_FIELD, 0) },
            album?.let { Triple(TopResult.Release(it), albumTier ?: MatchTier.OTHER_FIELD, 1) },
            song?.let { Triple(TopResult.Song(it), it.tier, 2) },
        )
        return candidates.minWithOrNull(compareBy<Triple<TopResult, MatchTier, Int>> { it.second }.thenBy { it.third })?.first
    }

    private suspend fun closeMatches(query: String, exclude: Set<Long>): List<Track> {
        val folded = SearchText.tokens(query).joinToString(" ")
        if (Fuzzy.allowedDistance(folded.length) == 0) return emptyList()
        val matches = currentNames().filter { Fuzzy.isCloseMatch(folded, it) }.take(MAX_CLOSE_NAMES)
        if (matches.isEmpty()) return emptyList()
        return dao.tracksMatchingNames(matches, MAX_CLOSE_TRACKS).map { it.toTrack() }.filter { it.id !in exclude }
    }

    /** Distinct titles, artists and albums, reloaded only when the library changes size. */
    private suspend fun currentNames(): List<String> = namesLock.withLock {
        val count = library.songCount.first()
        if (count != namesForCount) {
            names = dao.distinctNames()
            namesForCount = count
        }
        names
    }

    private companion object {
        const val CANDIDATE_LIMIT = 1_500
        const val MAX_CLOSE_NAMES = 20
        const val MAX_CLOSE_TRACKS = 50
    }
}

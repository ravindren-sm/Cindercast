package io.github.ravindren_sm.openstream.data.tmdb

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ravindren_sm.openstream.profiles.db.CatalogTitleEntity
import io.github.ravindren_sm.openstream.profiles.db.TitleTagEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/** Where the tagger reads the next titles and keeps its results (the database in the app, a fake in tests). */
interface TagStorage {
    suspend fun nextUntagged(limit: Int): List<CatalogTitleEntity>
    suspend fun save(tag: TitleTagEntity)
    suspend fun counts(): TaggerCounts
}

data class TaggerCounts(val checked: Int, val matched: Int, val graded: Int)

/** What Settings shows. [message] is set when the crawl stopped for a reason the viewer should know. */
class TaggerStatus(val hasKey: Boolean, val running: Boolean, val counts: TaggerCounts, val message: String? = null) {
    companion object { val NONE = TaggerStatus(false, false, TaggerCounts(0, 0, 0)) }
}

/**
 * Looks the portal's titles up in the online tag service, one title at a time, and keeps the age certification, genres and keywords
 * it finds. Opt-in (a key and Start), gentle (a pause between requests, nothing while a video plays), resumable (a title with a row
 * is never asked about again) and cautious (only a confident match is kept; the rest are marked so they are not retried). Titles the
 * portal gave no age label are done first. The crawl stops at once when the key is refused.
 */
class TitleTagger(
    private val storage: TagStorage,
    private val api: () -> TmdbApi?,
    private val enabled: () -> Boolean,
    private val setEnabled: (Boolean) -> Unit,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val pauseMillis: Long = 120,
) {
    var status: TaggerStatus by mutableStateOf(TaggerStatus.NONE)
        private set

    suspend fun refreshStatus(hasKey: Boolean, message: String? = null) { status = TaggerStatus(hasKey, enabled(), storage.counts(), message) }

    suspend fun run(canWork: () -> Boolean) {
        var failures = 0
        while (enabled()) {
            if (!canWork()) { delay(2_000); continue }
            val client = api()
            if (client == null) { setEnabled(false); refreshStatus(false, "No key saved."); return }
            val batch = storage.nextUntagged(BATCH)
            if (batch.isEmpty()) { setEnabled(false); refreshStatus(true, "Every title has been checked."); return }
            for (group in batch.chunked(WORKERS)) {
                if (!enabled()) return
                while (!canWork()) delay(2_000)
                // A few titles at a time: each lookup is two slow requests, and the service allows many more per second than one worker makes.
                val outcomes = coroutineScope { group.map { t -> async { tagOne(client, t) } }.awaitAll() }
                if (outcomes.any { it == Outcome.KeyRefused }) { setEnabled(false); refreshStatus(true, "The service refused the key. Check it and start again."); return }
                val wait = outcomes.filterIsInstance<Outcome.Wait>().maxOfOrNull { it.seconds }
                val failed = outcomes.count { it == Outcome.Failed }
                if (failed == 0 && wait == null) failures = 0
                if (wait != null) delay(wait * 1_000)
                if (failed > 0) { if (++failures >= MAX_FAILURES) { setEnabled(false); refreshStatus(true, "Stopped after repeated network errors."); return }; delay(5_000L * failures) }
                delay(pauseMillis)
            }
            refreshStatus(true)
        }
        refreshStatus(api() != null)
    }

    private sealed interface Outcome {
        data object Done : Outcome
        data object KeyRefused : Outcome
        data object Failed : Outcome
        class Wait(val seconds: Long) : Outcome
    }

    private suspend fun tagOne(client: TmdbApi, title: CatalogTitleEntity): Outcome {
        val series = title.kind == "SHOW"
        val (query, trailing) = TmdbMatcher.clean(title.name)
        if (query.length < 2) { storage.save(none(title)); return Outcome.Done }
        val candidates = when (val r = client.search(query, title.year ?: trailing, series)) {
            is TmdbResult.Ok -> r.value
            TmdbResult.KeyRefused -> return Outcome.KeyRefused
            is TmdbResult.Slow -> return Outcome.Wait(r.seconds)
            is TmdbResult.Failed -> return Outcome.Failed
        }
        val match = TmdbMatcher.pick(title.name, title.year, candidates)
        if (match == null) { storage.save(none(title)); return Outcome.Done }
        val details = when (val r = client.details(match.id, series)) {
            is TmdbResult.Ok -> r.value
            TmdbResult.KeyRefused -> return Outcome.KeyRefused
            is TmdbResult.Slow -> return Outcome.Wait(r.seconds)
            is TmdbResult.Failed -> return Outcome.Failed
        }
        if (details == null) { storage.save(none(title)); return Outcome.Done }
        val cert = Certification.pick(details.certifications)
        storage.save(TitleTagEntity(
            kind = title.kind, id = title.id, tmdbId = details.id, certLabel = cert?.label, certAge = cert?.level,
            genres = details.genres.joinToString(", ").ifEmpty { null }, language = details.language, rating = details.rating,
            keywords = details.keywords.take(30).joinToString(", ").ifEmpty { null }, flagged = TagFlags.flagged(details.genres, details.keywords), taggedAt = nowMillis(),
        ))
        return Outcome.Done
    }

    private fun none(t: CatalogTitleEntity) = TitleTagEntity(t.kind, t.id, 0, null, null, null, null, null, null, false, nowMillis())

    private companion object { const val BATCH = 40; const val WORKERS = 5; const val MAX_FAILURES = 6 }
}

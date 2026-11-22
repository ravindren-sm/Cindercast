package io.github.ravindren_sm.openstream.data

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ravindren_sm.openstream.profiles.CategoryRules
import io.github.ravindren_sm.openstream.profiles.db.CatalogIndexDao
import io.github.ravindren_sm.openstream.profiles.db.CatalogTitleEntity
import io.github.ravindren_sm.openstream.profiles.db.IndexProgressEntity
import io.github.ravindren_sm.openstream.provider.VodItem
import kotlinx.coroutines.delay

/** What the Search screen shows about the local title index. */
class IndexStatus(val enabled: Boolean, val titles: Int, val percent: Int, val complete: Boolean, val message: String? = null) {
    companion object { val OFF = IndexStatus(false, 0, 0, false) }
}

/**
 * Builds a lean local copy of the portal's title names, one catalog page at a time, so search can suggest instantly.
 * Opt-in: nothing is downloaded until [enable] is called. Gentle by design: one request at a time through the shared portal
 * session, a pause between requests, no work while the app is playing or the portal is not connected, adult categories
 * skipped, and resumable (progress is kept per category, so a restart carries on). When finished it refreshes itself after
 * [REFRESH_MILLIS]: the lists are re-read and rows replaced in place.
 */
class TitleIndexer(
    private val catalog: VodCatalog,
    private val dao: CatalogIndexDao,
    private val prefs: SharedPreferences,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val pauseBetweenMillis: Long = 300,
) {
    var status: IndexStatus by mutableStateOf(IndexStatus.OFF)
        private set

    val enabled: Boolean get() = prefs.getBoolean(KEY_ENABLED, false)

    fun enable() { prefs.edit().putBoolean(KEY_ENABLED, true).apply() }
    fun pause() { prefs.edit().putBoolean(KEY_ENABLED, false).apply() }

    /** Forgets everything (the next [enable] starts over). */
    suspend fun clear() {
        pause(); dao.clearTitles(); dao.clearProgress(); prefs.edit().remove(KEY_COMPLETED_AT).apply(); status = IndexStatus.OFF
    }

    /** Reads the stored counts into [status] (cheap; call when the screen opens). */
    suspend fun refreshStatus(message: String? = null) {
        val progress = dao.progress()
        val complete = prefs.getLong(KEY_COMPLETED_AT, 0) > 0
        status = IndexStatus(enabled, dao.titleCount(), percentOf(progress, knownCategories), complete, message)
    }

    private var knownCategories: List<VodCategory> = emptyList()

    /**
     * Runs until cancelled, finished or disabled. [canWork] says whether a request may be made right now (for example
     * not while a player is open). Blocking portal calls happen on the caller's dispatcher: use Dispatchers.IO.
     */
    suspend fun run(canWork: () -> Boolean) {
        var failures = 0
        while (enabled) {
            if (!canWork()) { delay(2_000); continue }
            if (knownCategories.isEmpty()) {
                when (val r = catalog.allCategories()) {
                    is CatalogResult.Ok -> knownCategories = r.value.filterNot { CategoryRules.isAdult(it.title) }
                    is CatalogResult.Failed -> { if (++failures >= MAX_FAILURES) { refreshStatus("Indexing stopped: ${r.message}"); pause(); return }; delay(5_000); continue }
                }
            }
            var progress = dao.progress().associateBy { it.categoryId }
            val finishedAt = prefs.getLong(KEY_COMPLETED_AT, 0)
            if (finishedAt > 0) {
                if (nowMillis() - finishedAt < REFRESH_MILLIS) { refreshStatus(); delay(60_000); continue }
                dao.clearProgress(); prefs.edit().remove(KEY_COMPLETED_AT).apply(); progress = emptyMap()   // time for a refresh pass
            }
            val next = knownCategories.firstOrNull { progress[it.id]?.done != true }
            if (next == null) {
                prefs.edit().putLong(KEY_COMPLETED_AT, nowMillis()).apply(); refreshStatus(); continue
            }
            val page = progress[next.id]?.nextPage ?: 1
            when (val r = catalog.titles(next.id, page)) {
                is CatalogResult.Ok -> {
                    failures = 0
                    dao.putTitles(r.value.items.map { entityOf(it, next.id) })
                    val pages = pageCount(r.value.totalItems, r.value.pageSize)
                    val done = r.value.items.isEmpty() || page >= pages
                    dao.putProgress(IndexProgressEntity(next.id, page + 1, r.value.totalItems, done))
                    refreshStatus()
                }
                is CatalogResult.Failed -> {
                    if (++failures >= MAX_FAILURES) { refreshStatus("Indexing stopped: ${r.message}"); pause(); return }
                    delay(5_000); continue
                }
            }
            delay(pauseBetweenMillis)
        }
        refreshStatus()
    }

    companion object {
        const val KEY_ENABLED = "index_enabled"
        const val KEY_COMPLETED_AT = "index_completed_at"
        const val REFRESH_MILLIS = 7L * 24 * 3600 * 1000
        const val MAX_FAILURES = 5

        fun pageCount(totalItems: Int, pageSize: Int): Int = if (totalItems <= 0 || pageSize <= 0) 0 else (totalItems + pageSize - 1) / pageSize

        /** Share of the catalog read, 0 to 100: each category counts equally, by the pages it has had. Unstarted categories count 0. */
        fun percentOf(progress: List<IndexProgressEntity>, categories: List<VodCategory>): Int {
            val byId = progress.associateBy { it.categoryId }
            val ids = if (categories.isEmpty()) byId.keys.toList() else categories.map { it.id }
            if (ids.isEmpty()) return 0
            var sum = 0.0
            for (id in ids) {
                val p = byId[id] ?: continue
                sum += if (p.done) 1.0 else {
                    // The portal's page size is not stored: 14 is what this portal uses, and the estimate only drives a progress label.
                    val pages = pageCount(p.totalItems, 14)
                    if (pages == 0) 0.0 else ((p.nextPage - 1).toDouble() / pages).coerceIn(0.0, 1.0)
                }
            }
            return (sum * 100 / ids.size).toInt().coerceIn(0, 100)
        }

        fun entityOf(item: VodItem, categoryId: String) = CatalogTitleEntity(
            kind = if (item.isSeries) "SHOW" else "MOVIE", id = item.id, name = PortalText0.clean(item.name), nameKey = SearchRanking.normalize(item.name),
            year = item.year, posterUri = item.posterUri, cmd = item.cmd ?: item.fileCmds.firstOrNull(), age = item.age, categoryId = categoryId,
        )
    }
}

/** Name text kept as the portal sent it, minus the HTML entities. */
private object PortalText0 { fun clean(s: String) = io.github.ravindren_sm.openstream.provider.PortalText.decodeEntities(s).trim() }

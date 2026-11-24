package io.github.ravindren_sm.openstream.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ravindren_sm.openstream.profiles.db.FavoriteEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The current profile's favorites, held in memory so every screen can show a heart and toggle it at once. The
 * database is the source of truth: each change is written there and the list re-read. Writes run on [scope].
 */
class FavoritesState(
    private val repo: ProfileRepository,
    private val profileId: () -> Long,
    private val scope: CoroutineScope,
) {
    var all: List<FavoriteEntity> by mutableStateOf(emptyList())
        private set
    private var keys: Set<String> by mutableStateOf(emptySet())

    fun isFavorite(kind: String, refId: String) = "$kind/$refId" in keys
    fun channels(): List<FavoriteEntity> = FavoriteItems.channelsInOrder(all)
    fun ofKind(kind: String): List<FavoriteEntity> = all.filter { it.kind == kind }

    /** Re-reads from the database (blocking off the main thread is the caller's business: this suspends). */
    suspend fun reload() {
        val id = profileId()
        val rows = if (id > 0) repo.allFavorites(id) else emptyList()
        all = rows
        keys = rows.mapTo(HashSet()) { "${it.kind}/${it.refId}" }
    }

    /** Adds the item if it is not a favorite, removes it if it is. [details] is what is needed to open it again. */
    fun toggle(kind: String, refId: String, name: String, posterUri: String?, details: FavoriteEntity) {
        val id = profileId()
        if (id <= 0) return
        val add = !isFavorite(kind, refId)
        // Update the heart at once; the database write follows.
        keys = if (add) keys + "$kind/$refId" else keys - "$kind/$refId"
        scope.launch {
            repo.setFavorite(id, kind, refId, name, posterUri, add, details)
            reload()
        }
    }

    fun toggleChannel(c: io.github.ravindren_sm.openstream.provider.LiveChannel) = toggle(Kinds.CHANNEL, c.id, c.name, null, FavoriteItems.ofChannel(c))
    fun toggleVod(v: io.github.ravindren_sm.openstream.provider.VodItem) {
        val d = FavoriteItems.ofVod(v)
        toggle(d.kind, v.id, v.name, v.posterUri, d)
    }
    fun isChannel(c: io.github.ravindren_sm.openstream.provider.LiveChannel) = isFavorite(Kinds.CHANNEL, c.id)
    fun isVod(v: io.github.ravindren_sm.openstream.provider.VodItem) = isFavorite(FavoriteItems.kindOf(v), v.id)
}

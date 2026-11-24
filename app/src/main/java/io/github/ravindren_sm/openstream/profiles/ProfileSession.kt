package io.github.ravindren_sm.openstream.profiles

import io.github.ravindren_sm.openstream.data.HistoryRecorder
import io.github.ravindren_sm.openstream.data.PlayTarget
import io.github.ravindren_sm.openstream.data.ResumeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Writes watch history for the current profile. Work runs on [scope] (Room is off the main thread). */
class RoomHistoryRecorder(
    private val repo: ProfileRepository,
    private val profileId: () -> Long,
    private val scope: CoroutineScope,
) : HistoryRecorder {
    override fun record(target: PlayTarget, positionMs: Long, durationMs: Long) {
        val profile = profileId()
        if (profile <= 0L) return                                  // no profile yet: nothing to attach it to
        val cmd = target.file.playableCmd
        scope.launch {
            repo.recordProgress(
                profile, target.resumeKey, target.kind, target.titleId, target.historyTitle, target.subtitle,
                target.posterUri, positionMs, durationMs, playCmd = cmd,
                watch = WatchInfo(
                    io.github.ravindren_sm.openstream.data.WatchTags.titleKey(target.historyTitle), target.historyTitle,
                    io.github.ravindren_sm.openstream.data.WatchTags.genresOf(target.genres),
                    io.github.ravindren_sm.openstream.data.WatchTags.languagesOf("${target.historyTitle} ${target.languageText.orEmpty()}"),
                ),
            )
        }
    }
}

/**
 * Resume positions kept apart per profile: the same movie remembers one place for each person. The profile can
 * change while the app runs, so it is read at each call.
 */
class ProfileResumeStore(private val base: ResumeStore, private val profileId: () -> Long) : ResumeStore {
    private fun key(id: String) = "p${profileId()}:$id"
    override fun get(id: String): Long? = base.get(key(id))
    override fun put(id: String, positionMillis: Long) = base.put(key(id), positionMillis)
    override fun clear(id: String) = base.clear(key(id))
}

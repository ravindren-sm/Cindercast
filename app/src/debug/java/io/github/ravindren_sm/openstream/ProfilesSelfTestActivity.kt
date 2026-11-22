package io.github.ravindren_sm.openstream

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import io.github.ravindren_sm.openstream.profiles.Kinds
import io.github.ravindren_sm.openstream.profiles.PinResult
import io.github.ravindren_sm.openstream.profiles.ProfileRepository
import io.github.ravindren_sm.openstream.profiles.db.OpenStreamDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Debug-only: exercises the Room layer on the real device (a unit test cannot, Room needs Android). It uses throwaway
 * databases, touches no portal and no real data, and logs one line per check under the tag OpenStream.selftest.
 *   adb shell am start -n <package>/io.github.ravindren_sm.openstream.ProfilesSelfTestActivity
 */
class ProfilesSelfTestActivity : ComponentActivity() {
    private val lines = mutableStateOf(listOf<String>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val shown by remember { lines }
            Column(Modifier.fillMaxSize().background(Color(0xFF0B0E14)).padding(48.dp)) {
                shown.forEach { BasicText(it, style = TextStyle(color = if ("FAIL" in it) Color(0xFFF87171) else Color(0xFFF3F6FA), fontSize = 22.sp)) }
            }
        }
        lifecycleScope.launch { withContext(Dispatchers.IO) { run() } }
    }

    private fun report(line: String) { Log.i(TAG, line); lines.value = lines.value + line }

    private suspend fun run() {
        var failures = 0
        suspend fun check(name: String, block: suspend () -> Boolean) {
            val ok = try { block() } catch (t: Throwable) { Log.e(TAG, "exception in $name", t); false }
            if (!ok) failures++
            report("${if (ok) "PASS" else "FAIL"}  $name")
        }

        val mem = OpenStreamDb.inMemory(this)
        val repo = ProfileRepository(mem)
        lateinit var dad: io.github.ravindren_sm.openstream.profiles.ProfileInfo
        lateinit var maya: io.github.ravindren_sm.openstream.profiles.ProfileInfo

        check("create an adult and a kids profile") {
            dad = repo.create("Dad", 0, isKids = false, showAdultCategories = true)
            maya = repo.create("Maya", 1, isKids = true, showAdultCategories = true)   // asked for, must be refused
            repo.count() == 2 && !repo.profile(maya.id)!!.showAdultCategories && repo.profile(dad.id)!!.showAdultCategories
        }
        check("household PIN: set, verify, wrong, lock, persisted in the database") {
            repo.pins.set("2468")
            val ok = repo.pins.verify("2468") is PinResult.Ok
            val again = ProfileRepository(mem).pins                     // a fresh service reads the stored hash
            val wrong = again.verify("0000") is PinResult.Wrong
            repeat(4) { again.verify("0000") }
            ok && wrong && again.verify("2468") is PinResult.Locked
        }
        check("favorites are per profile and removable") {
            repo.setFavorite(dad.id, Kinds.CHANNEL, "c1", "Channel One", null, true)
            repo.setFavorite(dad.id, Kinds.MOVIE, "m1", "Movie One", "/p/1.jpg", true)
            repo.setFavorite(maya.id, Kinds.MOVIE, "m2", "Cartoon", null, true)
            val before = repo.favorites(dad.id, Kinds.MOVIE).size == 1 && repo.favorites(maya.id, Kinds.MOVIE).single().refId == "m2"
            repo.setFavorite(dad.id, Kinds.MOVIE, "m1", "Movie One", null, false)
            before && !repo.isFavorite(dad.id, Kinds.MOVIE, "m1") && repo.isFavorite(dad.id, Kinds.CHANNEL, "c1")
        }
        check("history: skips a barely started title, keeps newest first, marks finished") {
            var t = 1_000L
            val r2 = ProfileRepository(mem) { t++ }
            r2.recordProgress(dad.id, "a", Kinds.MOVIE, "a", "Barely", null, null, 5_000, 3_600_000)           // under 15 s: not recorded
            r2.recordProgress(dad.id, "b", Kinds.MOVIE, "b", "Film B", null, null, 600_000, 3_600_000)
            r2.recordProgress(dad.id, "c/1/2", Kinds.EPISODE, "c", "Show C", "S01 E02", null, 300_000, 1_500_000)
            r2.recordProgress(dad.id, "b", Kinds.MOVIE, "b", "Film B", null, null, 700_000, 3_600_000)            // updates in place
            val unfinished = r2.continueWatching(dad.id).map { it.resumeKey }
            r2.recordProgress(dad.id, "c/1/2", Kinds.EPISODE, "c", "Show C", "S01 E02", null, 1_480_000, 1_500_000)  // watched to the end
            unfinished == listOf("b", "c/1/2") && r2.continueWatching(dad.id).map { it.resumeKey } == listOf("b") &&
                r2.recentlyWatched(dad.id).size == 2 && r2.continueWatching(maya.id).isEmpty()
        }
        check("pinned categories keep their order") {
            repo.pinCategory(dad.id, Kinds.MOVIES, "10", "ENGLISH MOVIES 4K"); repo.pinCategory(dad.id, Kinds.MOVIES, "12", "KIDS MOVIES")
            repo.pinCategory(dad.id, Kinds.MOVIES, "11", "HINDI MOVIES"); repo.unpinCategory(dad.id, Kinds.MOVIES, "12")
            repo.pinnedCategories(dad.id, Kinds.MOVIES).map { it.categoryId } == listOf("10", "11")
        }
        check("kids allow-list: suggestions apply only to kid-friendly names and never to adult ones") {
            val added = repo.seedKidsAllowed(maya.id, listOf("12" to "KIDS MOVIES", "40" to "ENGLISH MOVIES 4K", "50" to "ADULTS", "13" to "Cartoons"))
            repo.allowKidsCategory(maya.id, "51", "Adults 18+")
            added == 2 && repo.kidsAllowed(maya.id) == setOf("12", "13") && repo.seedKidsAllowed(maya.id, listOf("12" to "KIDS MOVIES")) == 0
        }
        check("deleting a profile deletes its favorites, history, pins and allow-list, and only its own") {
            repo.delete(maya.id)
            repo.profile(maya.id) == null && repo.favorites(maya.id, Kinds.MOVIE).isEmpty() && repo.kidsAllowed(maya.id).isEmpty() &&
                repo.favorites(dad.id, Kinds.CHANNEL).size == 1 && repo.count() == 1
        }
        mem.close()

        check("a file database keeps its data after being closed and reopened") {
            val name = "selftest-profiles.db"
            deleteDatabase(name)
            val first = Room.databaseBuilder(this, OpenStreamDb::class.java, name).build()
            ProfileRepository(first).create("Persisted", 3, isKids = true)
            first.close()
            val second = Room.databaseBuilder(this, OpenStreamDb::class.java, name).build()
            val ok = ProfileRepository(second).profiles().single().let { it.name == "Persisted" && it.isKids }
            second.close(); deleteDatabase(name)
            ok
        }
        report(if (failures == 0) "ALL CHECKS PASSED" else "$failures CHECK(S) FAILED")
        Log.i(TAG, if (failures == 0) "RESULT PASS" else "RESULT FAIL")
    }

    companion object { const val TAG = "OpenStream.selftest" }
}

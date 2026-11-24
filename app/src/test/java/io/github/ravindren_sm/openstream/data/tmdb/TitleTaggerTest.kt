package io.github.ravindren_sm.openstream.data.tmdb

import io.github.ravindren_sm.openstream.profiles.db.CatalogTitleEntity
import io.github.ravindren_sm.openstream.profiles.db.TitleTagEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleTaggerTest {
    private fun title(id: String, name: String, year: String? = "2013", kind: String = "MOVIE") =
        CatalogTitleEntity(kind = kind, id = id, name = name, nameKey = name.lowercase(), year = year, posterUri = null, cmd = null, age = null, categoryId = "1")

    private class FakeStorage(val pending: MutableList<CatalogTitleEntity>) : TagStorage {
        val saved = mutableMapOf<String, TitleTagEntity>()
        override suspend fun nextUntagged(limit: Int) = pending.filter { it.id !in saved }.take(limit)
        override suspend fun nextNeedingImdbId(limit: Int) = saved.values.filter { it.tmdbId > 0 && it.imdbId == null }.take(limit)
        override suspend fun save(tag: TitleTagEntity) { saved[tag.id] = tag }
        override suspend fun counts() = TaggerCounts(saved.size, saved.values.count { it.tmdbId > 0 }, saved.values.count { it.certAge != null })
    }

    private class FakeApi(val onSearch: (String) -> TmdbResult<List<TmdbCandidate>>, val onDetails: (Long) -> TmdbResult<TmdbDetails?>) : TmdbApi {
        var searches = 0
        override fun search(name: String, year: String?, series: Boolean): TmdbResult<List<TmdbCandidate>> { searches++; return onSearch(name) }
        override fun details(id: Long, series: Boolean) = onDetails(id)
    }

    private fun details(cert: String?, keywords: List<String> = emptyList()) =
        TmdbDetails(11, false, if (cert == null) emptyMap() else mapOf("US" to cert), listOf("Animation"), "en", 7.0, keywords)

    private fun run(storage: FakeStorage, api: TmdbApi?, enabled: BooleanArray = booleanArrayOf(true)): TitleTagger {
        val tagger = TitleTagger(storage, { api }, { enabled[0] }, { enabled[0] = it }, pauseMillis = 0)
        runBlocking { tagger.run { true } }
        return tagger
    }

    @Test fun `a confident match is stored with its certification`() {
        val storage = FakeStorage(mutableListOf(title("1", "Frozen")))
        val api = FakeApi({ TmdbResult.Ok(listOf(TmdbCandidate(11, "Frozen", null, "2013", 5.0, 900))) }, { TmdbResult.Ok(details("PG")) })
        run(storage, api)
        val tag = storage.saved["1"]!!
        assertEquals(11L, tag.tmdbId); assertEquals("PG", tag.certLabel); assertEquals(8, tag.certAge); assertFalse(tag.flagged)
    }

    @Test fun `no confident match is recorded so it is not asked again`() {
        val storage = FakeStorage(mutableListOf(title("2", "Obscure Film")))
        val api = FakeApi({ TmdbResult.Ok(emptyList()) }, { error("not called") })
        run(storage, api)
        assertEquals(0L, storage.saved["2"]!!.tmdbId); assertNull(storage.saved["2"]!!.certAge)
        assertEquals(1, api.searches)
    }

    @Test fun `a flagged keyword marks the title`() {
        val storage = FakeStorage(mutableListOf(title("3", "Frozen")))
        val api = FakeApi({ TmdbResult.Ok(listOf(TmdbCandidate(11, "Frozen", null, "2013", 5.0, 900))) }, { TmdbResult.Ok(details("PG", listOf("nudity"))) })
        run(storage, api)
        assertTrue(storage.saved["3"]!!.flagged)
    }

    @Test fun `a refused key stops the crawl and says so`() {
        val storage = FakeStorage(mutableListOf(title("4", "Frozen"), title("5", "Up", "2009")))
        val enabled = booleanArrayOf(true)
        val api = FakeApi({ TmdbResult.KeyRefused }, { error("not called") })
        val tagger = run(storage, api, enabled)
        assertFalse(enabled[0]); assertTrue(storage.saved.isEmpty()); assertTrue(tagger.status.message!!.contains("refused"))
    }

    @Test fun `with no key nothing is asked`() {
        val storage = FakeStorage(mutableListOf(title("6", "Frozen")))
        val enabled = booleanArrayOf(true)
        run(storage, null, enabled)
        assertFalse(enabled[0]); assertTrue(storage.saved.isEmpty())
    }

    @Test fun `it finishes when every title has a row`() {
        val storage = FakeStorage(mutableListOf(title("7", "Frozen"), title("8", "Up", "2009", kind = "MOVIE")))
        val api = FakeApi({ TmdbResult.Ok(emptyList()) }, { TmdbResult.Ok(null) })
        val enabled = booleanArrayOf(true)
        val tagger = run(storage, api, enabled)
        assertEquals(2, storage.saved.size); assertFalse(enabled[0]); assertEquals(2, tagger.status.counts.checked)
    }

    @Test fun `the IMDb id from the details is kept`() {
        val storage = FakeStorage(mutableListOf(title("9", "Frozen")))
        val api = FakeApi({ TmdbResult.Ok(listOf(TmdbCandidate(11, "Frozen", null, "2013", 5.0, 900))) }, { TmdbResult.Ok(details("PG").copy(imdbId = "tt2294629")) })
        run(storage, api)
        assertEquals("tt2294629", storage.saved["9"]!!.imdbId)
    }

    @Test fun `rows from before the id was stored are filled in once everything is checked`() {
        val storage = FakeStorage(mutableListOf())
        storage.saved["10"] = TitleTagEntity("MOVIE", "10", 11, "PG", 8, null, null, null, null, false, 0, imdbId = null)
        storage.saved["12"] = TitleTagEntity("MOVIE", "12", 12, null, null, null, null, null, null, false, 0, imdbId = null)
        val api = FakeApi({ error("not called") }, { id -> if (id == 11L) TmdbResult.Ok(details("PG").copy(imdbId = "tt0000011")) else TmdbResult.Ok(details(null)) })
        run(storage, api)
        assertEquals("tt0000011", storage.saved["10"]!!.imdbId); assertEquals("", storage.saved["12"]!!.imdbId)
    }
}

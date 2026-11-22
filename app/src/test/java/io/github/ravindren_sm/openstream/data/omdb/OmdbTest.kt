package io.github.ravindren_sm.openstream.data.omdb

import io.github.ravindren_sm.openstream.profiles.db.TitleTagEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmdbTest {
    @Test fun `a good answer gives the IMDb score, Rotten Tomatoes percentage and rating`() {
        val ok = OmdbParser.parse("""{"Title":"X","Rated":"PG","imdbRating":"7.4","Metascore":"65","Ratings":[{"Source":"Internet Movie Database","Value":"7.4/10"},{"Source":"Rotten Tomatoes","Value":"87%"}],"Response":"True"}""") as OmdbResult.Ok
        assertEquals(7.4, ok.info!!.imdbScore!!, 0.001); assertEquals(87, ok.info!!.rottenTomatoes); assertEquals(65, ok.info!!.metascore); assertEquals("PG", ok.info!!.rated)
    }

    @Test fun `missing values are null, not zero`() {
        val ok = OmdbParser.parse("""{"Rated":"N/A","imdbRating":"N/A","Metascore":"N/A","Ratings":[],"Response":"True"}""") as OmdbResult.Ok
        assertNull(ok.info!!.imdbScore); assertNull(ok.info!!.rottenTomatoes); assertNull(ok.info!!.rated)
    }

    @Test fun `errors are told apart`() {
        assertTrue(OmdbParser.parse("""{"Response":"False","Error":"Request limit reached!"}""") == OmdbResult.DailyLimit)
        assertTrue(OmdbParser.parse("""{"Response":"False","Error":"Invalid API key!"}""") == OmdbResult.KeyRefused)
        assertNull((OmdbParser.parse("""{"Response":"False","Error":"Incorrect IMDb ID."}""") as OmdbResult.Ok).info)
        assertTrue(OmdbParser.parse("not json") is OmdbResult.Failed)
    }

    private class Storage(val rows: MutableList<TitleTagEntity>) : OmdbStorage {
        val saved = mutableMapOf<String, TitleTagEntity>()
        override suspend fun nextPending(limit: Int) = rows.filter { it.id !in saved }.take(limit)
        override suspend fun save(tag: TitleTagEntity) { saved[tag.id] = tag }
        override suspend fun counts() = rows.size to saved.size
    }

    private fun row(id: String, cert: String? = null, age: Int? = null) = TitleTagEntity("MOVIE", id, 1, cert, age, null, null, null, null, false, 0, imdbId = "tt$id")

    private fun run(storage: Storage, api: OmdbApi?, enabled: BooleanArray = booleanArrayOf(true)): OmdbTagger {
        val t = OmdbTagger(storage, { api }, { enabled[0] }, { enabled[0] = it }, nowMillis = { 5L }, pauseMillis = 0, limitWaitMillis = 0)
        runBlocking { t.run { true } }
        return t
    }

    @Test fun `scores are stored and the rating fills a missing age label only`() {
        val s = Storage(mutableListOf(row("1"), row("2", "PG", 8)))
        run(s, object : OmdbApi { override fun byImdbId(imdbId: String) = OmdbResult.Ok(OmdbInfo(7.0, 80, null, "R")) })
        assertEquals(7.0, s.saved["1"]!!.imdbScore!!, 0.001); assertEquals(80, s.saved["1"]!!.rtScore); assertEquals("R", s.saved["1"]!!.certLabel); assertEquals(17, s.saved["1"]!!.certAge)
        assertEquals("PG", s.saved["2"]!!.certLabel)                 // an existing label is kept
    }

    @Test fun `an unknown id is marked read so it is not asked again`() {
        val s = Storage(mutableListOf(row("3")))
        run(s, object : OmdbApi { override fun byImdbId(imdbId: String) = OmdbResult.Ok(null) })
        assertEquals(5L, s.saved["3"]!!.omdbAt); assertNull(s.saved["3"]!!.imdbScore)
    }

    @Test fun `a refused key stops and says so`() {
        val s = Storage(mutableListOf(row("4")))
        val enabled = booleanArrayOf(true)
        val t = run(s, object : OmdbApi { override fun byImdbId(imdbId: String) = OmdbResult.KeyRefused }, enabled)
        assertFalse(enabled[0]); assertTrue(s.saved.isEmpty()); assertTrue(t.status.message!!.contains("refused"))
    }

    @Test fun `the daily limit pauses without losing anything and resumes`() {
        val s = Storage(mutableListOf(row("5"), row("6")))
        var calls = 0
        run(s, object : OmdbApi { override fun byImdbId(imdbId: String) = if (++calls <= 2) OmdbResult.DailyLimit else OmdbResult.Ok(OmdbInfo(6.0, null, null, null)) })
        assertEquals(2, s.saved.size)                                 // read on the later attempt, nothing skipped
    }
}

package io.github.ravindren_sm.openstream.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedSearchTest {
    private val full = SavedSearch("a1", "Date night", linkedSetOf("Comedy", "Sci-Fi"), true, linkedSetOf("en", "sv"), 6.5, 2010, SavedKind.SHOWS, BrowseSort.IMDB)

    @Test fun codecRoundTripsEveryField() {
        val other = SavedSearch("b2", "Plain")
        assertEquals(listOf(full, other), SavedSearchCodec.decode(SavedSearchCodec.encode(listOf(full, other))))
    }

    @Test fun codecToleratesUnknownMissingAndGarbage() {
        val s = SavedSearchCodec.decode("""[{"id":"x","name":"N","future":1,"kind":"WEIRD","sort":"NOPE"},{"name":"no id"},5]""")
        assertEquals(listOf(SavedSearch("x", "N")), s)
        assertTrue(SavedSearchCodec.decode("garbage").isEmpty())
        assertTrue(SavedSearchCodec.decode(null).isEmpty())
        assertTrue(SavedSearchCodec.decode("{}").isEmpty())
    }

    @Test fun summaryAndAutoName() {
        assertEquals("Shows · Comedy + Sci-Fi · English / Swedish · 6.5+ · from 2010", full.summary())
        assertEquals("Comedy / Sci-Fi · English · 6+ · from 2010",
            SavedSearch("i", "n", linkedSetOf("Comedy", "Sci-Fi"), false, linkedSetOf("en"), 6.0, 2010).summary())
        assertEquals("Everything", SavedSearch("i", "n").summary())
        assertEquals("English Comedy / Sci-Fi Movies 6+",
            SavedSearch("i", "n", linkedSetOf("Comedy", "Sci-Fi"), languages = linkedSetOf("en"), minRating = 6.0, kind = SavedKind.MOVIES).autoName())
        assertEquals("Titles 7+", SavedSearch("i", "n", minRating = 7.0).autoName())
        assertEquals("xx", SavedLanguages.nameOf("xx"))
    }

    @Test fun isEmptyMeansNoCriteria() {
        assertTrue(SavedSearch("i", "n").isEmpty)
        assertTrue(!SavedSearch("i", "n", yearFrom = 2000).isEmpty)
        assertTrue(!SavedSearch("i", "n", kind = SavedKind.SHOWS).isEmpty)
    }

    @Test fun sciFiHasTwoSpellings() {
        assertEquals("Sci-Fi" to "Science Fiction", SavedGenres.spellings("Science Fiction"))
        assertEquals("Sci-Fi" to "Science Fiction", SavedGenres.spellings(" sci-fi "))
        assertEquals("Comedy" to "Comedy", SavedGenres.spellings("Comedy"))
    }

    @Test fun storeKeepsProfilesSeparateAndOrderStable() {
        val store = SavedSearchStore(FakePrefs())
        val a = SavedSearch("1", "A"); val b = SavedSearch("2", "B")
        store.save(7, a); store.save(7, b)
        assertEquals(listOf("1", "2"), store.save(7, a.copy(name = "A2")).map { it.id })
        assertEquals("A2", store.get(7, "1")?.name)
        assertTrue(store.list(8).isEmpty())
        assertEquals(listOf("2"), store.delete(7, "1").map { it.id })
        assertNull(store.get(7, "1"))
        assertTrue(store.newId() != store.newId())
    }
}

/** Just enough of SharedPreferences for string values. */
private class FakePrefs : SharedPreferences {
    private val m = HashMap<String, String>()
    override fun getString(key: String, defValue: String?): String? = m[key] ?: defValue
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { if (value == null) m.remove(key) else m[key] = value }
        override fun apply() {}
        override fun commit() = true
        override fun putStringSet(key: String, values: MutableSet<String>?) = this
        override fun putInt(key: String, value: Int) = this
        override fun putLong(key: String, value: Long) = this
        override fun putFloat(key: String, value: Float) = this
        override fun putBoolean(key: String, value: Boolean) = this
        override fun remove(key: String) = apply { m.remove(key) }
        override fun clear() = apply { m.clear() }
    }
    override fun getAll(): MutableMap<String, *> = m
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String, defValue: Int) = defValue
    override fun getLong(key: String, defValue: Long) = defValue
    override fun getFloat(key: String, defValue: Float) = defValue
    override fun getBoolean(key: String, defValue: Boolean) = defValue
    override fun contains(key: String) = m.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
}

class SavedSearchFromFiltersTest {
    @Test fun theSearchTabsFiltersBecomeASavedSearch() {
        val f = SearchFilters(languages = setOf("English", "Swedish"), genres = setOf("Comedy", "Sci-Fi"), categoryIds = setOf("12"), minRating = 6.0, englishSubtitles = true)
        val s = SavedSearch.fromFilters("id1", f)
        assertEquals(setOf("Comedy", "Sci-Fi"), s.genres)
        assertEquals(setOf("en", "sv"), s.languages)
        assertEquals(6.0, s.minRating)
        assertEquals(false, s.allGenres)
        assertTrue(s.name.isNotBlank())
        assertTrue(dropsCategoriesOrSubtitles(f))
        assertEquals(false, dropsCategoriesOrSubtitles(SearchFilters(genres = setOf("Comedy"))))
        assertEquals(null, SavedLanguages.codeOf("Klingon"))
    }
}

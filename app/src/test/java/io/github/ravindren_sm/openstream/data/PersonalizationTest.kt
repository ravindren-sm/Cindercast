package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.LiveGenre
import io.github.ravindren_sm.openstream.provider.VodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizationTest {
    private fun v(id: String, name: String, genres: String? = null, imdb: Double? = null) =
        VodItem(id = id, name = name, isSeries = false, isSeason = false, isEpisode = false, cmd = "c", episodeNumbers = emptyList(), genres = genres, imdbRating = imdb)

    @Test fun genresAndLanguagesAreReadFromTheTitleText() {
        assertEquals(listOf("Comedy", "Drama"), WatchTags.genresOf("comedy, Drama / comedy"))
        assertEquals(emptyList<String>(), WatchTags.genresOf(null))
        assertEquals(setOf("Hindi"), WatchTags.languagesOf("Film [Hindi] HD"))
        assertEquals("toy story 5", WatchTags.titleKey("Toy Story 5 - 4K"))
    }

    @Test fun aTitleTheViewerWatchesLeadsThenGenreAndLanguageFit() {
        val p = Personalization(genres = mapOf("Comedy" to 8, "Drama" to 2), languages = mapOf("English" to 10), titles = mapOf("friends" to 3))
        val friends = p.score(v("1", "Friends [English]", "Comedy"))
        val comedy = p.score(v("2", "Other Comedy", "Comedy"))
        val drama = p.score(v("3", "Other Drama", "Drama"))
        val none = p.score(v("4", "Unrelated", "Horror"))
        assertTrue(friends > comedy); assertTrue(comedy > drama); assertTrue(drama > none); assertEquals(0.0, none, 0.0)
    }

    @Test fun versionsOfAWatchedTitleCountTogether() {
        val p = Personalization(titles = mapOf("toy story 5" to 1))
        assertTrue(p.score(v("1", "Toy Story 5 - 4K")) > 0)
        assertTrue(p.score(v("2", "Toy Story 5 [Hindi]")) > 0)
    }

    @Test fun anEmptyProfileScoresNothingAndIsEmpty() {
        assertTrue(Personalization().isEmpty); assertEquals(0.0, Personalization().score(v("1", "X", "Comedy")), 0.0)
    }

    @Test fun searchOrdersByRelevanceFirstThenByWhatTheViewerWatches() {
        val items = listOf(
            v("a", "The Space Show", "Drama"),                // contains "space"
            v("b", "Space Cowboys", "Drama"),                  // starts with
            v("c", "Space Jam", "Comedy"),                     // starts with, and the viewer likes comedy
            v("d", "Deep Space Nine", "Comedy"),               // contains, comedy
        )
        val p = Personalization(genres = mapOf("Comedy" to 9, "Drama" to 1))
        assertEquals(listOf("c", "b", "d", "a"), SearchRanking.rank(items, "space", p).map { it.id })
        assertEquals(listOf("b", "c", "a", "d"), SearchRanking.rank(items, "space", null).map { it.id })       // no stats: the old order
        assertEquals(listOf("b", "c", "a", "d"), SearchRanking.rank(items, "space", Personalization()).map { it.id })
    }
}

class DefaultScopeTest {
    private val cats = listOf(VodCategory("1", "ENGLISH MOVIES"), VodCategory("2", "HINDI MOVIES"), VodCategory("3", "KIDS"))
    private fun v(genres: String?, imdb: Double? = null, name: String = "N") =
        VodItem(id = "1", name = name, isSeries = false, isSeason = false, isEpisode = false, cmd = "c", episodeNumbers = emptyList(), genres = genres, imdbRating = imdb)

    @Test fun languageAndCategoryNarrowTheCategoryList() {
        val english = DefaultScope(SearchFilters(languages = setOf("English")))
        assertEquals(listOf("1"), cats.filter(english::categoryAllowed).map { it.id })
        val onlyKids = DefaultScope(SearchFilters(categoryIds = setOf("3")))
        assertEquals(listOf("3"), cats.filter(onlyKids::categoryAllowed).map { it.id })
        assertEquals(3, cats.count(DefaultScope(SearchFilters(genres = setOf("Comedy")))::categoryAllowed))
    }

    @Test fun titlesAreCheckedForGenreRatingAndSubtitleWording() {
        val s = DefaultScope(SearchFilters(genres = setOf("Comedy"), minRating = 6.0))
        assertTrue(s.titlePasses(v("Comedy", 7.0))); assertFalse(s.titlePasses(v("Drama", 7.0))); assertFalse(s.titlePasses(v("Comedy", 5.0)))
        assertTrue(DefaultScope(SearchFilters(englishSubtitles = true)).titlePasses(v(null, name = "Film [Eng Sub]")))
        assertFalse(DefaultScope(SearchFilters(englishSubtitles = true)).titlePasses(v(null)))
    }

    @Test fun liveGroupsAreHiddenOnlyWhenTheyNameAnotherLanguage() {
        val s = DefaultScope(SearchFilters(languages = setOf("English")))
        assertTrue(s.groupAllowed(LiveGenre("*", "All channels")))
        assertTrue(s.groupAllowed(LiveGenre("1", "ENGLISH | NEWS")))
        assertTrue(s.groupAllowed(LiveGenre("2", "SPORTS")))                  // names no language: stays
        assertFalse(s.groupAllowed(LiveGenre("3", "HINDI | MOVIES")))
        assertTrue(DefaultScope(SearchFilters(genres = setOf("Comedy"))).groupAllowed(LiveGenre("3", "HINDI | MOVIES")))   // no language default: nothing hidden
    }

    @Test fun theDeviceCopyCanDoUpToThreeGenresButNotTheSubtitleCheck() {
        assertEquals(listOf("Action", "Comedy"), DefaultScope(SearchFilters(genres = setOf("Comedy", "Action"))).indexGenres)
        assertEquals(null, DefaultScope(SearchFilters(genres = setOf("A1", "B1", "C1", "D1"))).indexGenres)
        assertEquals(null, DefaultScope(SearchFilters(englishSubtitles = true)).indexGenres)
        assertEquals(emptyList<String>(), DefaultScope(SearchFilters(minRating = 6.0)).indexGenres)
    }
}

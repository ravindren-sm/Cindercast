package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.VodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFiltersTest {
    private val cats = listOf(
        VodCategory("10", "ENGLISH MOVIES 4K"), VodCategory("11", "HINDI MOVIES"), VodCategory("12", "KIDS"), VodCategory("13", "COMEDY"),
        VodCategory("14", "ENGLISH COMEDY"), VodCategory("20", "HULU | ENGLISH SERIES"), VodCategory("21", "HINDI WEB SERIES"),
    )
    private fun v(genres: String?, imdb: Double? = null, kp: Double? = null) = VodItem(
        id = "1", name = "N", isSeries = false, isSeason = false, isEpisode = false, cmd = "c", episodeNumbers = emptyList(),
        genres = genres, imdbRating = imdb, kinopoiskRating = kp,
    )

    @Test fun filtersSurviveSavingAndLoading() {
        val f = SearchFilters(setOf("English", "Hindi"), setOf("Comedy"), setOf("13", "14"), 7.0, 75)
        assertEquals(f, SearchFilters.decode(f.encode()))
        assertEquals(SearchFilters(), SearchFilters.decode(""))
        assertEquals(SearchFilters(), SearchFilters.decode(null))
        assertEquals("", SearchFilters().encode())
        assertEquals(SearchFilters(genres = setOf("Comedy")), SearchFilters.decode("genre=Comedy;bogus;rating=x"))
    }

    @Test fun languagesAreReadFromCategoryNames() {
        assertEquals(setOf("English"), FilterRules.languagesIn("HULU | ENGLISH SERIES"))
        assertEquals(setOf("Hindi"), FilterRules.languagesIn("hindi web series"))
        assertEquals(emptySet<String>(), FilterRules.languagesIn("KIDS"))
        assertEquals(listOf("English", "Hindi"), FilterRules.languageOptions(cats))
    }

    @Test fun noCategoryOrLanguageMeansSearchEverything() {
        val plan = FilterRules.plan(cats, SearchFilters(genres = setOf("Comedy"), minRating = 7.0))
        assertNull(plan.ids); assertFalse(plan.none)
    }

    @Test fun languageNarrowsToItsCategoriesAndCategoriesNarrowFurther() {
        assertEquals(listOf("10", "14", "20"), FilterRules.plan(cats, SearchFilters(languages = setOf("English"))).ids)
        assertEquals(listOf("13", "14"), FilterRules.plan(cats, SearchFilters(categoryIds = setOf("13", "14"))).ids)
        // English AND (comedy categories): only the category that is both.
        assertEquals(listOf("14"), FilterRules.plan(cats, SearchFilters(languages = setOf("English"), categoryIds = setOf("13", "14"))).ids)
        assertEquals(listOf("10", "11", "14", "20", "21"), FilterRules.plan(cats, SearchFilters(languages = setOf("English", "Hindi"))).ids)
    }

    @Test fun conflictingChoicesFindNoCategory() {
        assertTrue(FilterRules.plan(cats, SearchFilters(languages = setOf("Hindi"), categoryIds = setOf("13"))).none)
    }

    @Test fun manyCategoriesAreCutAndReportedAsCapped() {
        val many = List(10) { VodCategory("$it", "ENGLISH $it") }
        val plan = FilterRules.plan(many, SearchFilters(languages = setOf("English")), cap = 4)
        assertEquals(4, plan.ids!!.size); assertTrue(plan.capped); assertEquals(10, plan.matching)
    }

    @Test fun genreAndRatingMustBothHold() {
        val f = SearchFilters(genres = setOf("Comedy"), minRating = 7.0)
        assertTrue(FilterRules.passes(v("Comedy, Family", imdb = 7.5), f))
        assertFalse(FilterRules.passes(v("Drama", imdb = 8.0), f))                 // wrong genre
        assertFalse(FilterRules.passes(v("Comedy", imdb = 6.0), f))                // too low
        assertFalse(FilterRules.passes(v("Comedy"), f))                            // unrated is excluded when a rating is asked for
        assertTrue(FilterRules.passes(v("Comedy", kp = 7.2), f))                   // the Kinopoisk value stands in when IMDb is missing
        assertTrue(FilterRules.passes(v(null), SearchFilters()))                   // no filters: everything passes
    }

    @Test fun anyOfSeveralGenresIsEnough() {
        assertTrue(FilterRules.passes(v("Action"), SearchFilters(genres = setOf("Comedy", "Action"))))
    }

    @Test fun rottenTomatoesNeedsAScoreSource() {
        val f = SearchFilters(minRottenTomatoes = 75)
        assertFalse(FilterRules.passes(v("Comedy"), f))
        assertTrue(FilterRules.passes(v("Comedy"), f) { 80 })
        assertFalse(FilterRules.passes(v("Comedy"), f) { 60 })
    }

    @Test fun englishSubtitleWordingIsRecognised() {
        listOf("Film [Eng Sub]", "Film (English Subtitles)", "Film ESub", "Film | Eng Subs", "Film English Sub", "FILM HARDSUB", "Film Subbed English").forEach {
            assertTrue(it, FilterRules.mentionsEnglishSubtitles(it))
        }
        listOf("Subway Surfers", "The Sub", "Film", "Substitute Teacher", "English Movies", "Hindi Sub-Plot").forEach {
            assertFalse(it, FilterRules.mentionsEnglishSubtitles(it))
        }
    }

    @Test fun theSubtitleFilterKeepsNamedTitlesAndTitlesFromAMentioningCategory() {
        val f = SearchFilters(englishSubtitles = true)
        fun named(name: String) = v(null).let { VodItem(id = "1", name = name, isSeries = false, isSeason = false, isEpisode = false, cmd = "c", episodeNumbers = emptyList()) }
        assertTrue(FilterRules.passes(named("Film [Eng Sub]"), f))
        assertFalse(FilterRules.passes(named("Film"), f))
        assertTrue(FilterRules.passes(named("Film"), f, categoryHint = true))
        assertTrue(f.isActive)
        assertEquals(f, SearchFilters.decode(f.encode()))
        assertEquals("subs=1", f.encode())
    }

    @Test fun theGenrePickerListsGenresAlphabetically() {
        assertEquals(SearchFilters.GENRES.sortedBy { it.lowercase() }, SearchFilters.GENRES)
        assertEquals("Action", SearchFilters.GENRES.first())
    }
}

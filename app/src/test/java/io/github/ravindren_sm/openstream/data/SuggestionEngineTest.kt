package io.github.ravindren_sm.openstream.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionEngineTest {
    private fun texts(q: String, s: Map<SuggestionSource, List<String>>) = SuggestionEngine.suggest(q, s).map { it.text }

    @Test fun nothingTypedOffersRecentSearches() {
        assertEquals(listOf("toy story", "cartoon"), texts("", mapOf(SuggestionSource.RECENT to listOf("toy story", "cartoon"), SuggestionSource.CHANNEL to listOf("News"))))
    }

    @Test fun wholeTextPrefixesRankAboveWordPrefixes() {
        val s = mapOf(SuggestionSource.TITLE to listOf("The Toy Story", "Toy Story 2", "Story of Toys"))
        assertEquals(listOf("Toy Story 2", "The Toy Story", "Story of Toys"), texts("toy", s))
    }

    @Test fun personalSourcesWinTiesAndDuplicatesCountOnce() {
        val s = mapOf(
            SuggestionSource.RECENT to listOf("cartoon network"),
            SuggestionSource.CHANNEL to listOf("Cartoon Network", "CARTOON NETWORK HD"),
            SuggestionSource.TITLE to listOf("Cartoon Network Movie"),
        )
        assertEquals(listOf("cartoon network", "Cartoon Network Movie"), texts("cart", s))
    }

    @Test fun qualityVariantsOfATitleAreSuggestedOnce() {
        assertEquals(listOf("Toy Story 5"), texts("toy", mapOf(SuggestionSource.TITLE to listOf("Toy Story 5", "Toy Story 5 - 4K", "Toy Story 5 [Hindi]"))))
    }

    @Test fun aFullyTypedTextIsNotSuggestedBack() {
        assertEquals(emptyList<String>(), texts("toy story", mapOf(SuggestionSource.RECENT to listOf("Toy Story"))))
    }

    @Test fun matchingIgnoresCaseEntitiesAndLimitsTheCount() {
        val many = List(20) { "News $it" }
        assertEquals(SuggestionEngine.MAX, SuggestionEngine.suggest("NEWS", mapOf(SuggestionSource.CHANNEL to many)).size)
        assertEquals(listOf("Tom &amp; Jerry"), texts("tom &", mapOf(SuggestionSource.TITLE to listOf("Tom &amp; Jerry"))))
    }
}

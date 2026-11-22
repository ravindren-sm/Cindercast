package io.github.ravindren_sm.openstream

import io.github.ravindren_sm.openstream.data.SubtitleMime
import io.github.ravindren_sm.openstream.data.TrackInfo
import io.github.ravindren_sm.openstream.data.TrackLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The wording rules of the Language and Subtitles overlay. Plain Kotlin: no player involved. */
class TrackLabelsTest {
    @Test fun `language codes become names, in two and three letter form`() {
        assertEquals("English", TrackLabels.languageName("en"))
        assertEquals("English", TrackLabels.languageName("eng"))
        assertEquals("Hindi", TrackLabels.languageName("hi"))
        assertEquals("Hindi", TrackLabels.languageName("hin"))
        assertEquals("Spanish", TrackLabels.languageName("es-MX"))
        assertEquals("French", TrackLabels.languageName("fre"))
    }

    @Test fun `undetermined or missing languages have no name, and unknown codes show upper-cased`() {
        assertNull(TrackLabels.languageName(null)); assertNull(TrackLabels.languageName("")); assertNull(TrackLabels.languageName("und"))
        assertEquals("QQQ", TrackLabels.languageName("qqq"))
    }

    @Test fun `the title prefers the label, then the language, then the track number`() {
        assertEquals("Director's commentary", TrackLabels.title(TrackInfo(language = "en", label = " Director's commentary "), 1))
        assertEquals("Hindi", TrackLabels.title(TrackInfo(language = "hin"), 2))
        assertEquals("Track 3", TrackLabels.title(TrackInfo(), 3))
    }

    @Test fun `audio tags show the channel layout and the roles`() {
        assertEquals(listOf("5.1"), TrackLabels.audioTags(TrackInfo(channelCount = 6)))
        assertEquals(listOf("2.0", "ORIG"), TrackLabels.audioTags(TrackInfo(channelCount = 2, isMain = true)))
        assertEquals(listOf("2.0", "AD"), TrackLabels.audioTags(TrackInfo(channelCount = 2, describesVideo = true)))
        assertEquals(listOf("1.0", "SAP", "DUB"), TrackLabels.audioTags(TrackInfo(channelCount = 1, isAlternate = true, isDub = true)))
        assertEquals(listOf("7.1"), TrackLabels.audioTags(TrackInfo(channelCount = 8)))
        assertEquals(listOf("4ch"), TrackLabels.audioTags(TrackInfo(channelCount = 4)))
        assertEquals(emptyList<String>(), TrackLabels.audioTags(TrackInfo()))
    }

    @Test fun `text tags name the subtitle format`() {
        assertEquals(listOf("VTT"), TrackLabels.textTags(TrackInfo(mimeType = "text/vtt")))
        assertEquals(listOf("SRT"), TrackLabels.textTags(TrackInfo(mimeType = "application/x-subrip")))
        assertEquals(listOf("CC"), TrackLabels.textTags(TrackInfo(mimeType = "application/cea-608")))
        assertEquals(listOf("CC"), TrackLabels.textTags(TrackInfo(mimeType = "application/cea-708")))
        assertEquals(listOf("DVB"), TrackLabels.textTags(TrackInfo(mimeType = "application/dvbsubs")))
        assertEquals(emptyList<String>(), TrackLabels.textTags(TrackInfo(mimeType = "application/unknown")))
    }

    @Test fun `the summary names the chosen audio and subtitles`() {
        assertEquals("Audio: English 5.1 · Subtitles: Off", TrackLabels.summary("English 5.1", null))
        assertEquals("Audio: default · Subtitles: Hindi", TrackLabels.summary(null, "Hindi"))
    }

    @Test fun `a subtitle file's type comes from its address, ignoring query and fragment`() {
        assertEquals("text/vtt", SubtitleMime.forUrl("http://x.invalid/a/b.vtt?token=1"))
        assertEquals("application/x-subrip", SubtitleMime.forUrl("http://x.invalid/a/b.SRT#t"))
        assertEquals("text/x-ssa", SubtitleMime.forUrl("/subs/c.ass"))
        assertEquals("application/ttml+xml", SubtitleMime.forUrl("http://x.invalid/d.ttml"))
        assertEquals("application/x-subrip", SubtitleMime.forUrl("http://x.invalid/download?id=5"))   // no extension: the common case
    }
}

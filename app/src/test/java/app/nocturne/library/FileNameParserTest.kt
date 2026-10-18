package app.nocturne.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameParserTest {
    @Test fun artistDashTitle() {
        val p = FileNameParser.parse("Nick Cave - Into My Arms.mp3")
        assertEquals("Nick Cave", p.artist); assertEquals("Into My Arms", p.title)
    }

    @Test fun extraDashesStayInTitle() {
        val p = FileNameParser.parse("A - B - C.flac")
        assertEquals("A", p.artist); assertEquals("B - C", p.title)
    }

    @Test fun plainNameIsUnknownArtist() {
        val p = FileNameParser.parse("track01.mp3")
        assertEquals(FileNameParser.UNKNOWN_ARTIST, p.artist); assertEquals("track01", p.title)
    }

    @Test fun audioAndHiddenDetection() {
        assertTrue(FileNameParser.isAudio("x.MP3")); assertTrue(FileNameParser.isAudio("x.opus"))
        assertFalse(FileNameParser.isAudio("cover.jpg")); assertFalse(FileNameParser.isAudio("noext"))
        assertTrue(FileNameParser.isHidden(".recycle")); assertTrue(FileNameParser.isHidden("\$RECYCLE.BIN"))
        assertFalse(FileNameParser.isHidden("Music"))
    }

    @Test fun folderIsParentDirectory() {
        assertEquals("Album", FileNameParser.folderOf("Artist/Album/01.mp3"))
        assertEquals(FileNameParser.ROOT_FOLDER, FileNameParser.folderOf("01.mp3"))
    }

    @Test fun trackNumberIsNotAnArtist() {
        val p = FileNameParser.parse("09 - In My Head.mp3", "12 Stones")
        assertEquals("12 Stones", p.artist); assertEquals("In My Head", p.title)
    }

    @Test fun trackNumberWithoutFallbackIsUnknownArtist() {
        val p = FileNameParser.parse("09 - In My Head.mp3")
        assertEquals(FileNameParser.UNKNOWN_ARTIST, p.artist); assertEquals("In My Head", p.title)
    }

    @Test fun leadingNumberVariantsAreStripped() {
        assertEquals("Moonlit Serenade", FileNameParser.parse("01 Moonlit Serenade.mp3").title)
        assertEquals("Moonlit Serenade", FileNameParser.parse("01. Moonlit Serenade.mp3").title)
        assertEquals("Moonlit Serenade", FileNameParser.parse("01_Moonlit Serenade.mp3").title)
        assertEquals("Moonlit Serenade", FileNameParser.parse("1) Moonlit Serenade.mp3").title)
    }

    @Test fun numberWithoutSeparatorIsKeptAsTitle() {
        assertEquals("1999", FileNameParser.parse("1999.mp3").title)
        assertEquals("Nena", FileNameParser.parse("Nena - 99 Luftballons.mp3").artist)
        assertEquals("99 Luftballons", FileNameParser.parse("Nena - 99 Luftballons.mp3").title)
    }

    /** Known limit of filename-only guessing: "99 Luftballons.mp3" reads as track 99. Tags replace it once read. */
    @Test fun leadingNumberThenSpaceIsTreatedAsTrackNumber() {
        assertEquals("Luftballons", FileNameParser.parse("99 Luftballons.mp3").title)
    }

    @Test fun artistFolderComesFromTwoLevelsUp() {
        assertEquals("12 Stones", FileNameParser.artistFolderOf("beets2/Album/12 Stones/12 Stones [2002]/09 - X.mp3"))
        assertEquals("Artist", FileNameParser.artistFolderOf("Artist/Album/01.mp3"))
        assertEquals(null, FileNameParser.artistFolderOf("Album/01.mp3"))
    }

    @Test fun numberThenArtistThenTitle() {
        val p = FileNameParser.parse("01 - Danny Elfman - The Little Things.mp3", "Some Folder")
        assertEquals("Danny Elfman", p.artist); assertEquals("The Little Things", p.title)
    }
}

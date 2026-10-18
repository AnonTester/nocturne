package app.nocturne.abs

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AbsParserTest {
    private fun arr(vararg o: String) = JSONArray("[" + o.joinToString(",") + "]")

    @Test fun picksNewestUnfinishedBook() {
        val p = AbsParser.pickLastPlayed(arr(
            """{"libraryItemId":"old","progress":0.2,"lastUpdate":100,"isFinished":false}""",
            """{"libraryItemId":"new","progress":0.4,"lastUpdate":300,"isFinished":false}""",
            """{"libraryItemId":"mid","progress":0.3,"lastUpdate":200,"isFinished":false}""",
        ))
        assertEquals("new", p?.itemId)
    }

    @Test fun skipsFinishedBooksAndPodcastEpisodes() {
        val p = AbsParser.pickLastPlayed(arr(
            """{"libraryItemId":"done","progress":1,"lastUpdate":900,"isFinished":true}""",
            """{"libraryItemId":"pod","episodeId":"e1","progress":0.5,"lastUpdate":800,"isFinished":false}""",
            """{"libraryItemId":"book","progress":0.1,"lastUpdate":10,"isFinished":false}""",
        ))
        assertEquals("book", p?.itemId)
    }

    @Test fun skipsUnstartedItems() {
        assertNull(AbsParser.pickLastPlayed(arr("""{"libraryItemId":"x","progress":0,"currentTime":0,"lastUpdate":5,"isFinished":false}""")))
    }

    @Test fun nullOrEmptyListGivesNull() {
        assertNull(AbsParser.pickLastPlayed(null))
        assertNull(AbsParser.pickLastPlayed(JSONArray()))
    }

    @Test fun buildsBookFromItemJson() {
        val item = JSONObject("""{"media":{"duration":36000,"metadata":{"title":"The Long Way Home","authorName":"A. Writer"}}}""")
        val book = AbsParser.toBook(AbsProgress("li_1", 0.37, 123L), item)
        assertEquals("The Long Way Home", book.title)
        assertEquals("A. Writer", book.author)
        assertEquals(0.63 * 36000, book.remainingSec, 1e-6)
    }

    @Test fun missingMetadataFallsBack() {
        val book = AbsParser.toBook(AbsProgress("x", 1.7, 0L), JSONObject("{}"))
        assertEquals("Unknown title", book.title)
        assertEquals(1.0, book.progress, 0.0)
    }

    @Test fun serverUrlNormalisation() {
        assertEquals("http://abs.local:13378", AbsClient.normalize(" abs.local:13378/ "))
        assertEquals("https://abs.example.org", AbsClient.normalize("https://abs.example.org///"))
        assertEquals("", AbsClient.normalize("  "))
    }

    @Test fun skipsBooksHiddenFromContinueListening() {
        val p = AbsParser.pickLastPlayed(arr(
            """{"libraryItemId":"hidden","progress":0.5,"lastUpdate":900,"isFinished":false,"hideFromContinueListening":true}""",
            """{"libraryItemId":"shown","progress":0.2,"lastUpdate":10,"isFinished":false,"hideFromContinueListening":false}""",
        ))
        assertEquals("shown", p?.itemId)
    }

    @Test fun durationAndPositionComeFromTheProgressRecord() {
        val me = arr("""{"libraryItemId":"b","progress":0.76,"currentTime":27360,"duration":36000,"lastUpdate":5,"isFinished":false}""")
        val p = AbsParser.pickLastPlayed(me)!!
        val book = AbsParser.toBook(p, JSONObject("""{"media":{"metadata":{"title":"Heir of Fire"}}}"""))
        assertEquals(36000.0, book.durationSec, 0.0)
        assertEquals(0.76, book.progress, 1e-9)
        assertEquals(8640.0, book.remainingSec, 1e-6)
    }

    @Test fun authorFromAuthorsArray() {
        val item = JSONObject("""{"media":{"metadata":{"title":"T","authors":[{"id":"1","name":"Sarah J. Maas"},{"id":"2","name":"Co Author"}]}}}""")
        assertEquals("Sarah J. Maas, Co Author", AbsParser.toBook(AbsProgress("x", 0.1, 0), item).author)
    }
}

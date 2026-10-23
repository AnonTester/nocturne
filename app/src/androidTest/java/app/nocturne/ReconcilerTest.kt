package app.nocturne

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nocturne.data.NocturneDatabase
import app.nocturne.data.Source
import app.nocturne.data.Track
import app.nocturne.data.TrackDao
import app.nocturne.library.Reconciler
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReconcilerTest {
    private lateinit var db: NocturneDatabase
    private lateinit var dao: TrackDao
    private lateinit var reconciler: Reconciler

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, NocturneDatabase::class.java).allowMainThreadQueries().build()
        dao = db.tracks()
        reconciler = Reconciler(dao)
    }

    @After fun tearDown() = db.close()

    private fun track(id: String, mtime: Long = 1, size: Long = 100, source: String = Source.SAMBA, title: String = id) = Track(
        id = id, title = title, artist = "a", album = "al", folder = "f", folderPath = "p", filename = "$id.mp3",
        uri = "smb://h/s/$id.mp3", mtime = mtime, size = size, source = source,
    )

    @Test fun newFilesAreAddedAndCounted() = runBlocking {
        val added = reconciler.reconcile(Source.SAMBA, listOf(track("a"), track("b")))
        assertEquals(2, added)
        assertEquals(2, dao.count(Source.SAMBA))
    }

    @Test fun vanishedFilesAreRemovedWhenScanIsComplete() = runBlocking {
        reconciler.reconcile(Source.SAMBA, listOf(track("a"), track("b"), track("c")))
        reconciler.reconcile(Source.SAMBA, listOf(track("a")))
        assertEquals(1, dao.count(Source.SAMBA))
        assertNotNull(dao.byId("a"))
    }

    @Test fun incompleteScanNeverDeletes() = runBlocking {
        reconciler.reconcile(Source.SAMBA, listOf(track("a"), track("b"), track("c")))
        reconciler.reconcile(Source.SAMBA, listOf(track("a")), deleteMissing = false)
        assertEquals(3, dao.count(Source.SAMBA))
    }

    @Test fun incompleteScanStillAddsNewFiles() = runBlocking {
        reconciler.reconcile(Source.SAMBA, listOf(track("a")))
        val added = reconciler.reconcile(Source.SAMBA, listOf(track("a"), track("n")), deleteMissing = false)
        assertEquals(1, added)
        assertEquals(2, dao.count(Source.SAMBA))
    }

    @Test fun unchangedFilesKeepTheirReadTags() = runBlocking {
        reconciler.reconcile(Source.SAMBA, listOf(track("a")))
        dao.updateMetadata("a", "Real Title", "Real Artist", "Real Album", 123_000, false)
        reconciler.reconcile(Source.SAMBA, listOf(track("a"))) // same mtime and size
        val t = dao.byId("a")!!
        assertEquals("Real Title", t.title)
        assertTrue(t.metaDone)
    }

    @Test fun changedFilesAreReplacedAndTagsReadAgain() = runBlocking {
        reconciler.reconcile(Source.SAMBA, listOf(track("a")))
        dao.updateMetadata("a", "Real Title", "Real Artist", "Real Album", 123_000, false)
        reconciler.reconcile(Source.SAMBA, listOf(track("a", mtime = 2, size = 150, title = "from filename")))
        val t = dao.byId("a")!!
        assertEquals("from filename", t.title)
        assertFalse(t.metaDone)
    }

    @Test fun sourcesAreIndependent() = runBlocking {
        reconciler.reconcile(Source.SAMBA, listOf(track("s1")))
        reconciler.reconcile(Source.LOCAL, listOf(track("l1", source = Source.LOCAL)))
        reconciler.reconcile(Source.LOCAL, emptyList())
        assertEquals(1, dao.count(Source.SAMBA))
        assertEquals(0, dao.count(Source.LOCAL))
    }

    @Test fun folderQueryReturnsOnlyThatFolder() = runBlocking {
        reconciler.reconcile(Source.SAMBA, listOf(track("a"), track("b"), track("c").copy(folderPath = "other")))
        assertEquals(listOf("a", "b"), dao.idsInFolder("p", Source.SAMBA))
        assertEquals(listOf("c"), dao.idsInFolder("other", Source.SAMBA))
    }

    @Test fun sameFolderPathInTwoSourcesStaysSeparate() = runBlocking {
        reconciler.reconcile(Source.SAMBA, listOf(track("a")))
        reconciler.reconcile("smb-x", listOf(track("smb:smb-x:a", source = "smb-x")))
        assertEquals(listOf("a"), dao.tracksInFolder("p", Source.SAMBA).map { it.id })
        assertEquals(listOf("smb:smb-x:a"), dao.tracksInFolder("p", "smb-x").map { it.id })
    }

    @Test fun mainQueueOnlyContainsMergedSources() = runBlocking {
        reconciler.reconcile(Source.SAMBA, listOf(track("a")))
        reconciler.reconcile("smb-x", listOf(track("x1", source = "smb-x")))
        assertEquals(listOf("a"), dao.allIds(listOf(Source.SAMBA)))
        assertEquals(listOf("a", "x1"), dao.allIds(listOf(Source.SAMBA, "smb-x")).sorted())
        assertEquals(listOf("x1"), dao.idsInSource("smb-x"))
        assertEquals(emptyList<String>(), dao.allIds(emptyList()))
    }
}

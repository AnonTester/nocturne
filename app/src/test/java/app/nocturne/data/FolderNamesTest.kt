package app.nocturne.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderNamesTest {
    private fun show(path: String) = FolderNames.display(path, path.substringAfterLast('/'))

    @Test fun discFolderGetsItsAlbum() {
        assertEquals("Abbey Road • CD1", show("Beatles/Abbey Road/CD1"))
        assertEquals("Live • CD 2", show("Band/Live/CD 2"))
        assertEquals("Live • Disc 3 - Encore", show("Band/Live/Disc 3 - Encore"))
        assertEquals("Hits • disk1", show("Band/Hits/disk1"))
        assertEquals("Box • Vol. 2", show("Band/Box/Vol. 2"))
        assertEquals("Box • Part 1", show("Band/Box/Part 1"))
        assertEquals("Vinyl • Side A", show("Band/Vinyl/Side A"))
    }

    @Test fun otherFoldersAreUnchanged() {
        assertEquals("Abbey Road", show("Beatles/Abbey Road"))
        assertEquals("Acid Drop", show("Band/Acid Drop"))
        assertEquals("Discovery", show("Band/Discovery"))
        assertEquals("Partisans", show("Band/Partisans"))
        assertEquals("Cd1x", show("Band/Album/Cd1x"))
    }

    @Test fun discFolderWithoutParentIsUnchanged() {
        assertEquals("CD1", show("CD1"))
        assertEquals("CD1", FolderNames.display("/CD1", "CD1"))
    }
}

import android.content.Context
import com.example.kokoro82m.utils.Bookmark
import com.example.kokoro82m.utils.BookmarkManager
import com.example.kokoro82m.utils.DatabaseManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class BookmarkManagerTest {
    private val context = mock(Context::class.java)
    private val dbManager = mock(DatabaseManager::class.java)

    @Test
    fun saveLoadAndClearBookmark() {
        val uri = "sample"
        val bookmark = Bookmark(5)
        `when`(dbManager.getBookmark(context, uri)).thenReturn(bookmark)
        BookmarkManager.save(context, uri, 5)
        verify(dbManager).setBookmark(context, uri, 5)
        assertEquals(bookmark, BookmarkManager.load(context, uri))

        `when`(dbManager.getBookmark(context, uri)).thenReturn(null)
        BookmarkManager.clear(context, uri)
        verify(dbManager).clearBookmark(context, uri)
        assertEquals(null, BookmarkManager.load(context, uri))
    }
}

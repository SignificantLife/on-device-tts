import com.example.kokoro82m.utils.AudioPlayer
import com.example.kokoro82m.utils.PlayerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

@ExperimentalCoroutinesApi
class AudioPlayerSimpleTest {
    private lateinit var player: AudioPlayer

    @Before
    fun setup() {
        player = AudioPlayer(mock()) {}
    }

    @Test
    fun `pause from playing`() = runTest {
        player.prepare(FloatArray(100))
        player.play()
        player.pause()
        assertEquals(PlayerState.PAUSED, player.getState())
    }

    @Test
    fun `play resumes from paused`() = runTest {
        player.prepare(FloatArray(100))
        player.play()
        player.pause()
        player.play()
        assertEquals(PlayerState.PLAYING, player.getState())
    }

    @Test
    fun `stop releases resources`() = runTest {
        player.prepare(FloatArray(100))
        player.play()
        player.stop()
        assertEquals(PlayerState.IDLE, player.getState())
        assertEquals(0, player.getPosition())
    }
}

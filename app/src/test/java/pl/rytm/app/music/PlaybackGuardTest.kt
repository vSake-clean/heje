package pl.rytm.app.music

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.rytm.app.music.PlaybackGuard.Action

class PlaybackGuardTest {

    @Test
    fun `playing music with advancing position needs nothing`() {
        val g = PlaybackGuard()
        var pos = 0L
        repeat(20) { i ->
            pos += 3_000
            assertEquals(Action.NONE, g.onProbe(i * 3_000L, wantPlaying = true, online = true, ready = true, paused = false, positionMs = pos))
        }
    }

    @Test
    fun `unexpected pause is resumed immediately`() {
        val g = PlaybackGuard()
        g.onProbe(0, true, true, true, paused = false, positionMs = 1_000)
        assertEquals(Action.PLAY, g.onProbe(3_000, true, true, true, paused = true, positionMs = 2_000))
    }

    @Test
    fun `pause requested by the session is respected`() {
        val g = PlaybackGuard()
        assertEquals(Action.NONE, g.onProbe(0, wantPlaying = false, online = true, ready = true, paused = true, positionMs = 0))
    }

    @Test
    fun `short buffering is tolerated, a real stall is nudged`() {
        val g = PlaybackGuard(toleratedStuckProbes = 2)
        g.onProbe(0, true, true, true, false, 5_000)
        assertEquals(Action.NONE, g.onProbe(3_000, true, true, true, false, 5_000))
        assertEquals(Action.NONE, g.onProbe(6_000, true, true, true, false, 5_000))
        assertEquals(Action.PLAY, g.onProbe(9_000, true, true, true, false, 5_000))
    }

    @Test
    fun `repeated failures reload the player`() {
        val g = PlaybackGuard(maxNudges = 3)
        g.onProbe(0, true, true, true, false, 1_000)
        assertEquals(Action.PLAY, g.onProbe(3_000, true, true, true, true, 1_000))
        assertEquals(Action.PLAY, g.onProbe(6_000, true, true, true, true, 1_000))
        assertEquals(Action.PLAY, g.onProbe(9_000, true, true, true, true, 1_000))
        assertEquals(Action.RELOAD, g.onProbe(12_000, true, true, true, true, 1_000))
    }

    @Test
    fun `player that never becomes ready is reloaded`() {
        val g = PlaybackGuard(notReadyTimeoutMs = 25_000)
        assertEquals(Action.NONE, g.onProbe(0, true, true, ready = false, paused = true, positionMs = 0))
        assertEquals(Action.NONE, g.onProbe(24_000, true, true, ready = false, paused = true, positionMs = 0))
        assertEquals(Action.RELOAD, g.onProbe(25_000, true, true, ready = false, paused = true, positionMs = 0))
    }

    @Test
    fun `offline - waits instead of fighting`() {
        val g = PlaybackGuard()
        assertEquals(Action.NONE, g.onProbe(0, true, online = false, ready = true, paused = true, positionMs = 0))
        assertEquals(Action.NONE, g.onProbe(60_000, true, online = false, ready = true, paused = true, positionMs = 0))
    }

    @Test
    fun `loop back to the start of the track counts as progress`() {
        val g = PlaybackGuard()
        g.onProbe(0, true, true, true, false, 213_000)
        assertEquals(Action.NONE, g.onProbe(3_000, true, true, true, false, 1_500))
    }
}

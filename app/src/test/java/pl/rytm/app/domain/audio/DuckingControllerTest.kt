package pl.rytm.app.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DuckingControllerTest {

    private fun sample(d: DuckingController, from: Long, to: Long, step: Long = 10) =
        (from..to step step).map { it to d.level(it) }

    @Test
    fun `ducks by about 25 percent and never mutes`() {
        val d = DuckingController()
        d.duck(0)
        assertEquals(0.75f, d.level(1_000), 0.0001f)
        assertTrue(sample(d, 0, 5_000).all { it.second >= 0.75f - 1e-4f })
    }

    @Test
    fun `transition down and up is smooth and monotonic`() {
        val d = DuckingController(attackMs = 400, holdMs = 0, releaseMs = 900)
        d.duck(0)
        val down = sample(d, 0, 400)
        down.zipWithNext().forEach { (a, b) ->
            assertTrue(b.second <= a.second + 1e-6f)
            assertTrue("krok ${a.second}→${b.second}", abs(b.second - a.second) < 0.02f)
        }
        d.release(1_000)
        val up = sample(d, 1_000, 2_000)
        up.zipWithNext().forEach { (a, b) ->
            assertTrue(b.second >= a.second - 1e-6f)
            assertTrue(abs(b.second - a.second) < 0.01f)
        }
    }

    @Test
    fun `returns exactly to the previous level`() {
        val d = DuckingController()
        d.duck(0)
        d.release(2_000)
        assertEquals(1f, d.level(10_000), 0f)
        assertTrue(d.isIdle(10_000))
    }

    @Test
    fun `close announcements keep music ducked without pumping`() {
        val d = DuckingController(holdMs = 1_200)
        d.duck(0)
        d.release(1_500) // koniec pierwszego komunikatu
        d.duck(2_300) // kolejny po 0,8 s – w czasie przytrzymania
        val between = sample(d, 1_500, 2_300)
        assertTrue(between.all { abs(it.second - 0.75f) < 1e-4f })
    }

    @Test
    fun `re-duck during release continues from current level without a jump`() {
        val d = DuckingController(holdMs = 0, releaseMs = 1_000)
        d.duck(0)
        d.release(1_000)
        val mid = d.level(1_400)
        assertTrue(mid > 0.75f && mid < 1f)
        d.duck(1_400)
        assertEquals(mid, d.level(1_400), 1e-4f)
        assertEquals(0.75f, d.level(3_000), 1e-4f)
    }

    @Test
    fun `duck strength is configurable and capped`() {
        val d = DuckingController(duckFraction = 0.3f)
        d.duck(0)
        assertEquals(0.7f, d.level(5_000), 1e-4f)
        d.duckFraction = 0.95f
        assertEquals(DuckingController.MAX_DUCK_FRACTION, d.duckFraction, 0f)
    }

    @Test
    fun `settled state is reported when no transition is running`() {
        val d = DuckingController(attackMs = 400, holdMs = 1_000, releaseMs = 900)
        assertTrue(d.isSettled(0))
        d.duck(0)
        assertTrue(!d.isSettled(200))
        assertTrue(d.isSettled(400))
        d.release(1_000)
        assertTrue(!d.isSettled(1_500)) // czeka na powrót – poziom zaraz się zmieni
        assertTrue(!d.isSettled(2_300))
        assertTrue(d.isSettled(3_000))
    }
}

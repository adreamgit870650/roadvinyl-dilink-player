package com.car.mp3player.ui

import com.car.mp3player.model.LrcChar
import com.car.mp3player.model.LrcLine
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricRendererLineBudgetTest {

    @Test
    fun `one line setting keeps a bilingual current lyric intact and hides next lyric`() {
        assertEquals(
            LyricRenderer.OverlayRowAllocation(currentRows = 2, nextRows = 0),
            LyricRenderer.allocateOverlayRows(
                maxVisualLines = 1,
                requestedCurrentRows = 2,
                requestedNextRows = 2
            )
        )
    }

    @Test
    fun `two line setting keeps both bilingual lyric blocks intact`() {
        assertEquals(
            LyricRenderer.OverlayRowAllocation(currentRows = 2, nextRows = 2),
            LyricRenderer.allocateOverlayRows(
                maxVisualLines = 2,
                requestedCurrentRows = 2,
                requestedNextRows = 2
            )
        )
    }

    @Test
    fun `a lyric block is capped independently`() {
        assertEquals(
            LyricRenderer.OverlayRowAllocation(currentRows = 4, nextRows = 4),
            LyricRenderer.allocateOverlayRows(
                maxVisualLines = 4,
                requestedCurrentRows = 8,
                requestedNextRows = 7
            )
        )
    }

    @Test
    fun `parallel lyric rows use one shared progress percentage`() {
        val line = LrcLine(
            chars = "가나다\n甲乙".map { LrcChar(it.toString(), 1_000L) },
            startTimeMs = 1_000L,
            endTimeMs = 5_000L
        )

        assertEquals(0f, LyricRenderer.lineProgressFraction(line, 500f), 0.0001f)
        assertEquals(0.5f, LyricRenderer.lineProgressFraction(line, 3_000f), 0.0001f)
        assertEquals(1f, LyricRenderer.lineProgressFraction(line, 6_000f), 0.0001f)
    }
}

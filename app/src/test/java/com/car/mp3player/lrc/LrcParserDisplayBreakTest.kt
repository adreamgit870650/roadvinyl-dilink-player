package com.car.mp3player.lrc

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserDisplayBreakTest {

    @Test
    fun `space slash space becomes a visual line break`() {
        val line = LrcParser.parseContent("[00:01.00]또 너처럼 / 又像你").single()

        assertEquals("또 너처럼\n又像你", line.text)
    }

    @Test
    fun `slashes without spaces remain unchanged`() {
        val lines = listOf(
            LrcParser.parseContent("[00:01.00]A/B").single().text,
            LrcParser.parseContent("[00:01.00]A /B").single().text,
            LrcParser.parseContent("[00:01.00]A/ B").single().text
        )

        assertEquals(listOf("A/B", "A /B", "A/ B"), lines)
    }

    @Test
    fun `parallel language rows share the same karaoke timeline`() {
        val line = LrcParser.parseContent(
            "[00:01.00]가나 / 甲乙\n[00:05.00]다음 / 下一句"
        ).first()
        val breakIndex = line.chars.indexOfFirst { it.char == "\n" }
        val koreanTimes = line.chars
            .subList(0, breakIndex)
            .filter { it.char.isNotBlank() }
            .map { it.startTimeMs }
        val chineseTimes = line.chars
            .subList(breakIndex + 1, line.chars.size)
            .filter { it.char.isNotBlank() }
            .map { it.startTimeMs }

        assertEquals(listOf(1_000L, 3_000L), koreanTimes)
        assertEquals(koreanTimes, chineseTimes)
    }
}

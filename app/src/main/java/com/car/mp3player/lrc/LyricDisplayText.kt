package com.car.mp3player.lrc

/** Converts the bilingual lyric separator used by the lyric sources into a visual line break. */
object LyricDisplayText {
    private const val LINE_BREAK_SEPARATOR = " / "

    fun withLineBreaks(text: String): String = text.replace(LINE_BREAK_SEPARATOR, "\n")
}

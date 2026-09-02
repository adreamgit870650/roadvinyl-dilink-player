package com.car.mp3player.util

import java.text.Normalizer
import java.util.Locale
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

/**
 * Orders library titles for a mixed Chinese/Latin music collection:
 * symbols and numbers first, then Chinese and Latin titles together by
 * their complete, tone-free pinyin/Latin spelling.
 */
class LibraryTitleComparator : Comparator<String> {
    private val pinyinFormat = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
        vCharType = HanyuPinyinVCharType.WITH_V
    }

    override fun compare(left: String, right: String): Int {
        val leftText = left.trim()
        val rightText = right.trim()
        if (leftText == rightText) return 0

        val leftKey = sortKey(leftText)
        val rightKey = sortKey(rightText)

        compareValues(leftKey.group, rightKey.group).takeIf { it != 0 }?.let { return it }
        leftKey.phonetic.compareTo(rightKey.phonetic).takeIf { it != 0 }?.let { return it }

        return leftText.compareTo(rightText)
    }

    fun sectionOf(title: String): Char? {
        val key = sortKey(title.trim())
        if (key.group != GROUP_ALPHABETIC) return null
        return key.phonetic.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' }
    }

    private fun sortKey(text: String): SortKey {
        if (text.isEmpty()) return SortKey(GROUP_SYMBOL_OR_NUMBER, FILE_EXTENSION_MARKER)

        val codePoint = text.codePointAt(0)
        val script = Character.UnicodeScript.of(codePoint)

        val group = when {
            !Character.isLetter(codePoint) -> GROUP_SYMBOL_OR_NUMBER
            script == Character.UnicodeScript.LATIN || script == Character.UnicodeScript.HAN -> {
                GROUP_ALPHABETIC
            }
            else -> GROUP_OTHER_LETTER
        }

        return SortKey(group, fullPhoneticKey(text) + FILE_EXTENSION_MARKER)
    }

    private fun fullPhoneticKey(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        return buildString(normalized.length * 2) {
            var offset = 0
            while (offset < normalized.length) {
                val codePoint = normalized.codePointAt(offset)
                when {
                    isCombiningMark(codePoint) -> Unit
                    Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN &&
                        Character.isBmpCodePoint(codePoint) -> {
                        val pinyin = PinyinHelper.toHanyuPinyinStringArray(
                            codePoint.toChar(),
                            pinyinFormat
                        )?.firstOrNull()
                        if (pinyin != null) append(pinyin) else appendCodePoint(codePoint)
                    }
                    else -> appendCodePoint(codePoint)
                }
                offset += Character.charCount(codePoint)
            }
        }.lowercase(Locale.ROOT)
    }

    private fun isCombiningMark(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt() -> true
        else -> false
    }

    private data class SortKey(
        val group: Int,
        val phonetic: String
    )

    companion object {
        private const val GROUP_SYMBOL_OR_NUMBER = 0
        private const val GROUP_ALPHABETIC = 1
        private const val GROUP_OTHER_LETTER = 2

        // The implicit extension separator reproduces normal filename ordering:
        // "Ah Yeah.mp3" precedes "AH.mp3", while "Air.mp3" precedes "Airplane.mp3".
        private const val FILE_EXTENSION_MARKER = "."
    }
}

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
    private val sortKeyCache = HashMap<String, SortKey>()
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
        return sortKeyCache[text] ?: createSortKey(text).also { sortKeyCache[text] = it }
    }

    private fun createSortKey(text: String): SortKey {
        if (text.isEmpty()) return SortKey(GROUP_SYMBOL_OR_NUMBER, FILE_EXTENSION_MARKER)

        val codePoint = text.codePointAt(0)
        val group = when {
            !Character.isLetter(codePoint) -> GROUP_SYMBOL_OR_NUMBER
            isLatin(codePoint) || isHan(codePoint) -> {
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
                    isHan(codePoint) &&
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

    // UnicodeScript is only available on Android 7+. Use code-point ranges so
    // the same filename sorting and index bar also work on Android 5.1.
    private fun isLatin(codePoint: Int): Boolean =
        codePoint in 0x0041..0x007A || codePoint in 0x00C0..0x02E4 ||
            codePoint in 0x1D00..0x1DBF || codePoint in 0x1E00..0x1EFF ||
            codePoint in 0x2C60..0x2C7F || codePoint in 0xA720..0xA7FF ||
            codePoint in 0xAB30..0xAB6F || codePoint in 0xFB00..0xFB06 ||
            codePoint in 0xFF21..0xFF3A || codePoint in 0xFF41..0xFF5A

    private fun isHan(codePoint: Int): Boolean =
        codePoint in 0x3400..0x4DBF || codePoint in 0x4E00..0x9FFF ||
            codePoint in 0xF900..0xFAFF || codePoint in 0x20000..0x2EE5F ||
            codePoint in 0x2F800..0x2FA1F || codePoint in 0x30000..0x323AF

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

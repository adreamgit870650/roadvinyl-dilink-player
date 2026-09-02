package com.car.mp3player.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryTitleComparatorTest {
    private val comparator = LibraryTitleComparator()

    @Test
    fun `sorts symbols numbers Latin and Han titles by the library rules`() {
        val titles = listOf(
            "曹操",
            "Celebrate",
            "冰河时代",
            "Bad Romance",
            "白桦林",
            "阿衣莫",
            "Adios",
            "9号歌曲",
            "#收藏"
        )

        assertEquals(
            listOf(
                "#收藏",
                "9号歌曲",
                "Adios",
                "阿衣莫",
                "Bad Romance",
                "白桦林",
                "冰河时代",
                "曹操",
                "Celebrate"
            ),
            titles.sortedWith(comparator)
        )
    }

    @Test
    fun `inserts Chinese titles into Latin titles by complete pinyin`() {
        val titles = listOf(
            "Airplane",
            "爱的飞行日记",
            "AH",
            "Ah Yeah",
            "AGASSY",
            "Again",
            "After LIKE",
            "阿尔罕布拉宫的回忆",
            "Adventure",
            "Air"
        )

        assertEquals(
            listOf(
                "Adventure",
                "阿尔罕布拉宫的回忆",
                "After LIKE",
                "Again",
                "AGASSY",
                "Ah Yeah",
                "AH",
                "爱的飞行日记",
                "Air",
                "Airplane"
            ),
            titles.sortedWith(comparator)
        )
    }

    @Test
    fun `sorts Han titles with the same initial by full pinyin order`() {
        val titles = listOf("周杰伦", "赵四", "张三")

        assertEquals(listOf("张三", "赵四", "周杰伦"), titles.sortedWith(comparator))
    }

    @Test
    fun `sorts Latin titles without case sensitivity`() {
        val titles = listOf("beta", "Alpha", "apple")

        assertEquals(listOf("Alpha", "apple", "beta"), titles.sortedWith(comparator))
    }

    @Test
    fun `returns Latin or pinyin section initials for the index bar`() {
        assertEquals('A', comparator.sectionOf("Adios"))
        assertEquals('A', comparator.sectionOf("阿衣莫"))
        assertEquals('B', comparator.sectionOf("冰河时代"))
        assertEquals('Z', comparator.sectionOf("周杰伦"))
        assertEquals('E', comparator.sectionOf("Élan"))
        assertNull(comparator.sectionOf("9号歌曲"))
        assertNull(comparator.sectionOf("#收藏"))
    }
}

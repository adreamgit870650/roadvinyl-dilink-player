package com.car.mp3player.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SelectedDirectoryAccessTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `grant succeeds only when persistent read and directory access both exist`() {
        assertTrue(validateDirectoryGrant({}, { true }, { true }))
        assertFalse(validateDirectoryGrant({}, { true }, { false }))
    }

    @Test fun `temporary access alone does not count as persistent access`() {
        assertFalse(validateDirectoryGrant({}, { false }, { error("Must short circuit") }))
    }

    @Test fun `failed grant is not silently accepted`() {
        assertFalse(validateDirectoryGrant({ throw SecurityException() }, { true }, { true }))
    }

    @Test fun `broken provider is rejected without crashing`() {
        assertFalse(validateDirectoryGrant({}, { true }, { throw IllegalArgumentException() }))
    }

    @Test fun `manual fallback accepts only a readable absolute directory`() {
        val folder = temp.newFolder("selected music")
        assertEquals(folder.canonicalPath, SelectedDirectoryAccess.readablePath(folder.absolutePath))
        assertNull(SelectedDirectoryAccess.readablePath("relative/Music"))
        assertNull(SelectedDirectoryAccess.readablePath(temp.newFile("song.mp3").absolutePath))
        assertNull(SelectedDirectoryAccess.readablePath(folder.resolve("missing").absolutePath))
    }
}

package io.github.openkeys.soundfonts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SoundFontLibraryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun library() = SoundFontLibrary(tmp.root.resolve("sf"), Dispatchers.Unconfined)

    @Test
    fun entriesPersistAndMissingFilesAreDropped() = runBlocking {
        val lib = library()
        val a = SoundFontEntry("a", "Piano.sf2", "a.sf")
        val b = SoundFontEntry("b", "Strings.sf2", "b.sf")
        lib.newFile("a").writeText("x")
        lib.newFile("b").writeText("x")
        lib.add(a)
        lib.add(b)
        lib.file(b).delete()

        val reloaded = library().apply { load() }
        assertEquals(listOf(a), reloaded.entries.value)
        assertEquals("a.sf", reloaded.file("a")!!.name)
    }

    @Test
    fun removeDeletesEntryAndFile() = runBlocking {
        val lib = library()
        val a = SoundFontEntry("a", "Piano.sf2", "a.sf")
        lib.newFile("a").writeText("x")
        lib.add(a)
        lib.remove("a")
        assertTrue(lib.entries.value.isEmpty())
        assertTrue(!lib.file(a).exists())
        assertTrue(library().apply { load() }.entries.value.isEmpty())
    }

    @Test
    fun corruptIndexLoadsEmpty() = runBlocking {
        tmp.root.resolve("sf").apply { mkdirs() }.resolve("index.json").writeText("{not json")
        val lib = library().apply { load() }
        assertTrue(lib.entries.value.isEmpty())
    }
}

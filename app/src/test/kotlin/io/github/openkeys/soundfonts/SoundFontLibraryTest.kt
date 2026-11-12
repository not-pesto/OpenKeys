package io.github.openkeys.soundfonts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    fun namesPersistWithoutChangingSoundFontOrOtherPrograms() = runBlocking {
        val lib = library()
        val entry = SoundFontEntry("a", "Original.sf2", "a.sf")
        val file = lib.newFile("a").apply { writeText("original data") }
        lib.add(entry)
        lib.rename("a", "  Stage piano  ")
        lib.renameInstrument("a", 0, 1, "  Warm piano  ")
        lib.renameInstrument("a", 1, 1, "Other bank")

        val reloaded = library().apply { load() }.entries.value.single()
        assertEquals("Stage piano", reloaded.name)
        assertEquals("Warm piano", reloaded.instrumentName(0, 1, "Original"))
        assertEquals("Other bank", reloaded.instrumentName(1, 1, "Original"))
        assertEquals("Original", reloaded.instrumentName(0, 2, "Original"))
        assertEquals("original data", file.readText())

        lib.renameInstrument("a", 0, 1, null)
        val reset = library().apply { load() }.entries.value.single()
        assertEquals("Original", reset.instrumentName(0, 1, "Original"))
        assertEquals("Other bank", reset.instrumentName(1, 1, "Original"))
    }

    @Test
    fun legacyIndexLoadsWithoutInstrumentNames() = runBlocking {
        val lib = library()
        lib.newFile("a").writeText("x")
        tmp.root.resolve("sf/index.json").writeText("""[{"id":"a","name":"Piano","fileName":"a.sf"}]""")
        lib.load()
        assertEquals("Original", lib.entries.value.single().instrumentName(0, 0, "Original"))
    }

    @Test
    fun invalidRenamesDoNotChangeLibrary() = runBlocking {
        val lib = library()
        val entry = SoundFontEntry("a", "Piano", "a.sf")
        lib.newFile("a").writeText("x")
        lib.add(entry)
        val attempts: List<suspend () -> Unit> = listOf(
            { lib.rename("a", "   ") },
            { lib.renameInstrument("a", 0, 0, "") },
            { lib.renameInstrument("a", 0, 128, "Bad") },
            { lib.rename("missing", "Name") },
        )
        attempts.forEach { attempt ->
            try {
                attempt()
                fail("Expected invalid rename to fail")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertEquals(listOf(entry), lib.entries.value)
        assertEquals(listOf(entry), library().apply { load() }.entries.value)
    }

    @Test
    fun corruptIndexLoadsEmpty() = runBlocking {
        tmp.root.resolve("sf").apply { mkdirs() }.resolve("index.json").writeText("{not json")
        val lib = library().apply { load() }
        assertTrue(lib.entries.value.isEmpty())
    }
}

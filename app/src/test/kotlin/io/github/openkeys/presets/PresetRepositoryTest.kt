package io.github.openkeys.presets

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PresetRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var nextId = 0
    private fun repo() = PresetRepository(
        file = tmp.root.resolve("presets/presets.json"),
        io = Dispatchers.Unconfined,
        newId = { "id${nextId++}" },
    )

    @Test
    fun crudPersistsAcrossInstances() = runBlocking {
        val first = repo()
        val a = first.create("  Piano ")
        val b = first.duplicate(a.id, "Piano copy")
        first.rename(a.id, "Grand")
        first.save(b.copy(splitEnabled = true, splitNote = 48))

        val second = repo()
        assertTrue(second.load().isEmpty())
        assertEquals(listOf("Grand", "Piano copy"), second.presets.value.map { it.name })
        assertEquals(48, second.presets.value[1].splitNote)
        assertTrue(second.presets.value[1].splitEnabled)

        second.delete(a.id)
        assertEquals(listOf(b.id), repo().apply { load() }.presets.value.map { it.id })
    }

    @Test
    fun loadWithoutFileIsEmpty() = runBlocking {
        val r = repo()
        assertTrue(r.load().isEmpty())
        assertTrue(r.presets.value.isEmpty())
    }

    @Test
    fun invalidSaveIsRejectedAndNothingIsWritten() = runBlocking {
        val r = repo()
        val p = r.create("Ok")
        val result = runCatching { r.save(p.copy(masterReverb = 2f)) }
        assertTrue(result.isFailure)
        assertEquals(0.3f, repo().apply { load() }.presets.value.single().masterReverb)
        assertFalse(tmp.root.resolve("presets/presets.json.tmp").exists())
    }
}

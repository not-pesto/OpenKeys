package io.github.openkeys.routing

import io.github.openkeys.routing.RoutedEvent.NoteOff
import io.github.openkeys.routing.RoutedEvent.NoteOn
import io.github.openkeys.routing.VoiceSlotId.LEFT_1
import io.github.openkeys.routing.VoiceSlotId.RIGHT_1
import io.github.openkeys.routing.VoiceSlotId.RIGHT_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteRouterTest {

    private val on = SlotRouting(enabled = true)

    private fun splitConfig(splitNote: Int = 60) = RoutingConfig(
        splitEnabled = true, splitNote = splitNote, right1 = on, right2 = on, left1 = on,
    )

    @Test
    fun right1AloneCoversWholeKeyboard() {
        val router = NoteRouter(RoutingConfig(right1 = on))
        assertEquals(listOf(NoteOn(SlotNote(RIGHT_1, 21), 100)), router.noteOn(21, 100))
        assertEquals(listOf(NoteOn(SlotNote(RIGHT_1, 108), 100)), router.noteOn(108, 100))
    }

    @Test
    fun right1AndRight2LayerWithoutSplit() {
        val router = NoteRouter(RoutingConfig(right1 = on, right2 = on, left1 = on))
        assertEquals(
            listOf(NoteOn(SlotNote(RIGHT_1, 40), 90), NoteOn(SlotNote(RIGHT_2, 40), 90)),
            router.noteOn(40, 90),
        )
    }

    @Test
    fun leftIsInactiveWhenSplitDisabled() {
        val router = NoteRouter(RoutingConfig(right1 = SlotRouting(), left1 = on))
        assertTrue(router.noteOn(30, 100).isEmpty())
    }

    @Test
    fun splitNoteBoundaryGoesToRightSlots() {
        val router = NoteRouter(splitConfig(splitNote = 60))
        assertEquals(listOf(NoteOn(SlotNote(LEFT_1, 59), 80)), router.noteOn(59, 80))
        assertEquals(
            listOf(NoteOn(SlotNote(RIGHT_1, 60), 80), NoteOn(SlotNote(RIGHT_2, 60), 80)),
            router.noteOn(60, 80),
        )
    }

    @Test
    fun disabledSlotsAreSkippedInSplitMode() {
        val router = NoteRouter(splitConfig().copy(right2 = SlotRouting(), left1 = SlotRouting()))
        assertTrue(router.noteOn(40, 100).isEmpty())
        assertEquals(listOf(NoteOn(SlotNote(RIGHT_1, 70), 100)), router.noteOn(70, 100))
    }

    @Test
    fun transposeIsPerSlotAndDropsOutOfRangeNotes() {
        val router = NoteRouter(
            RoutingConfig(
                right1 = SlotRouting(enabled = true, transposeSemitones = 12),
                right2 = SlotRouting(enabled = true, transposeSemitones = -12),
            ),
        )
        assertEquals(
            listOf(NoteOn(SlotNote(RIGHT_1, 72), 100), NoteOn(SlotNote(RIGHT_2, 48), 100)),
            router.noteOn(60, 100),
        )
        assertEquals(listOf(NoteOn(SlotNote(RIGHT_2, 115), 100)), router.noteOn(127, 100))
    }

    @Test
    fun noteOffReleasesOriginalTargetsAfterConfigChange() {
        val router = NoteRouter(splitConfig(splitNote = 60))
        router.noteOn(55, 100)
        router.config = RoutingConfig(right1 = SlotRouting(enabled = true, transposeSemitones = 5))
        assertEquals(listOf(NoteOff(SlotNote(LEFT_1, 55))), router.noteOff(55))
    }

    @Test
    fun velocityZeroIsNoteOff() {
        val router = NoteRouter(RoutingConfig(right1 = on))
        router.noteOn(60, 100)
        assertEquals(listOf(NoteOff(SlotNote(RIGHT_1, 60))), router.noteOn(60, 0))
        assertTrue(router.noteOff(60).isEmpty())
    }

    @Test
    fun repeatedNoteOnReleasesPreviousVoicesFirst() {
        val router = NoteRouter(RoutingConfig(right1 = on))
        router.noteOn(60, 100)
        assertEquals(
            listOf(NoteOff(SlotNote(RIGHT_1, 60)), NoteOn(SlotNote(RIGHT_1, 60), 70)),
            router.noteOn(60, 70),
        )
        assertEquals(listOf(NoteOff(SlotNote(RIGHT_1, 60))), router.noteOff(60))
    }

    @Test
    fun allNotesOffReleasesEverythingHeld() {
        val router = NoteRouter(splitConfig())
        router.noteOn(40, 100)
        router.noteOn(70, 100)
        assertEquals(3, router.allNotesOff().size)
        assertTrue(router.allNotesOff().isEmpty())
        assertTrue(router.noteOff(40).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidSplitNote() {
        RoutingConfig(splitNote = 128)
    }
}

package com.seif.stream.ui.capture

import com.seif.stream.MainDispatcherRule
import com.seif.stream.data.CapturePersistence
import com.seif.stream.data.Entry
import com.seif.stream.data.RecoveredDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun firstChangeStampsOnce_buffersAt275ms_andKeepsTextAfterRealSave() = runTest {
        val persistence = FakeCapturePersistence()
        var now = 1_000L
        val viewModel = CaptureViewModel(persistence) { now }

        viewModel.onTextChanged("A thought")
        runCurrent()

        assertEquals(1_000L, viewModel.state.value.timestamp)
        assertTrue(viewModel.state.value.dirty)
        assertTrue(persistence.draftWrites.isEmpty())

        advanceTimeBy(275L)
        runCurrent()
        assertEquals(listOf("A thought" to 1_000L), persistence.draftWrites)
        assertTrue(persistence.commits.isEmpty())

        advanceTimeBy(1_725L)
        runCurrent()
        assertEquals(listOf("A thought" to 1_000L), persistence.commits)
        assertEquals("A thought", viewModel.state.value.text)
        assertEquals(CaptureSaveStatus.Saved, viewModel.state.value.saveStatus)
        assertFalse(viewModel.state.value.dirty)

        now = 9_000L
        viewModel.onTextChanged("A thought, continued")
        runCurrent()
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals(2, persistence.commits.size)
        assertEquals(1_000L, persistence.commits.last().second)
        assertEquals("A thought, continued", viewModel.state.value.text)
    }

    @Test
    fun recoveredDraftIsVisibleAndAutomaticallyCommitted() = runTest {
        val persistence = FakeCapturePersistence(
            recovered = RecoveredDraft("Recovered words", 44L),
        )
        val viewModel = CaptureViewModel(persistence) { 99L }

        assertEquals("Recovered words", viewModel.state.value.text)
        assertEquals(44L, viewModel.state.value.timestamp)
        assertTrue(viewModel.state.value.recovered)

        runCurrent()
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals(listOf("Recovered words" to 44L), persistence.commits)
        assertFalse(viewModel.state.value.recovered)
        assertEquals(CaptureSaveStatus.Saved, viewModel.state.value.saveStatus)
    }

    @Test
    fun lifecycleStopCommitsWithoutWaitingForDebounces() {
        val persistence = FakeCapturePersistence()
        val viewModel = CaptureViewModel(persistence) { 123L }

        viewModel.onTextChanged("Last keystroke")
        viewModel.flushOnStop()

        assertEquals(listOf("Last keystroke" to 123L), persistence.commits)
        assertEquals(CaptureSaveStatus.Saved, viewModel.state.value.saveStatus)
    }

    @Test
    fun freshCaptureCommitsDirtyTextBeforeResetting() = runTest {
        val persistence = FakeCapturePersistence()
        val viewModel = CaptureViewModel(persistence) { 321L }
        viewModel.onTextChanged("Keep this safe")

        assertTrue(viewModel.startFresh())

        assertEquals(listOf("Keep this safe" to 321L), persistence.commits)
        assertEquals("", viewModel.state.value.text)
        assertEquals(null, viewModel.state.value.timestamp)
    }

    @Test
    fun openingAndEditingExistingEntryKeepsItsOriginalTimestamp() = runTest {
        val persistence = FakeCapturePersistence()
        val viewModel = CaptureViewModel(persistence) { 9_999L }
        val original = Entry(timestamp = 50L, text = "Original", updatedAt = 60L)

        assertTrue(viewModel.openEntry(original))
        assertEquals(50L, viewModel.state.value.timestamp)
        assertEquals("Original", viewModel.state.value.text)

        viewModel.onTextChanged("Edited")
        runCurrent()
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals("Edited" to 50L, persistence.commits.single())
        assertEquals(50L, viewModel.state.value.timestamp)
    }

    @Test
    fun trashingActiveEntryCommitsLatestTextThenResetsCapture() = runTest {
        val persistence = FakeCapturePersistence()
        val viewModel = CaptureViewModel(persistence) { 75L }
        viewModel.onTextChanged("Latest text")

        assertTrue(viewModel.prepareEntryForTrash(75L))

        assertEquals(listOf("Latest text" to 75L), persistence.commits)
        assertEquals("", viewModel.state.value.text)
        assertEquals(null, viewModel.state.value.timestamp)
    }
}

private class FakeCapturePersistence(
    private val recovered: RecoveredDraft? = null,
) : CapturePersistence {
    val draftWrites = mutableListOf<Pair<String, Long>>()
    val commits = mutableListOf<Pair<String, Long>>()

    override fun recoverDraft(): RecoveredDraft? = recovered

    override suspend fun writeDraft(text: String, timestamp: Long) {
        draftWrites += text to timestamp
    }

    override suspend fun commitCapture(text: String, timestamp: Long) {
        commits += text to timestamp
    }
}

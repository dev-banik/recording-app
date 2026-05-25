package com.callrecorder.app

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.callrecorder.app.domain.model.CallType
import com.callrecorder.app.domain.model.RecordingDomain
import com.callrecorder.app.domain.repository.RecordingRepository
import com.callrecorder.app.domain.usecase.DeleteRecordingUseCase
import com.callrecorder.app.domain.usecase.GetRecordingsUseCase
import com.callrecorder.app.domain.usecase.SearchRecordingsUseCase
import com.callrecorder.app.ui.recordings.RecordingsViewModel
import com.callrecorder.app.ui.recordings.SortOrder
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingsViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: RecordingsViewModel
    private lateinit var repository: RecordingRepository

    private fun makeRecording(id: Long, name: String, ts: Long, duration: Long) = RecordingDomain(
        id = id, filePath = "/test_$id.m4a", fileName = "test_$id.m4a",
        callerName = name, phoneNumber = "", callType = CallType.PHONE,
        isIncoming = true, timestamp = ts, durationMs = duration,
        fileSizeBytes = 1024, isFavorite = false, customLabel = "", recordingSource = "test"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)

        val records = listOf(
            makeRecording(1L, "Alice", 1000L, 60_000L),
            makeRecording(2L, "Bob",   2000L, 30_000L),
            makeRecording(3L, "Carol", 3000L, 90_000L),
        )

        every { repository.getAllRecordings() }      returns flowOf(records)
        every { repository.getRecordingsByType(any()) } returns flowOf(records.take(1))
        every { repository.search(any()) }           returns flowOf(records.filter { it.callerName.contains("Bob") })

        viewModel = RecordingsViewModel(
            getRecordings    = GetRecordingsUseCase(repository),
            searchRecordings = SearchRecordingsUseCase(repository),
            deleteRecording  = DeleteRecordingUseCase(repository),
            repository       = repository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads all recordings sorted by date desc`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(3, state.recordings.size)
            // Most recent first
            assertEquals(3L, state.recordings[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setFilter emits filtered recordings`() = runTest {
        viewModel.setFilter("PHONE")
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.recordings.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSortOrder DATE_ASC orders ascending`() = runTest {
        viewModel.setSortOrder(SortOrder.DATE_ASC)
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(1L, state.recordings[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSortOrder DURATION_DESC orders by duration`() = runTest {
        viewModel.setSortOrder(SortOrder.DURATION_DESC)
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(90_000L, state.recordings[0].durationMs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setQuery searches when 2+ chars`() = runTest {
        viewModel.setQuery("Bo")
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.recordings.size)
            assertEquals("Bob", state.recordings[0].callerName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete calls use case`() = runTest {
        val rec = makeRecording(1L, "Alice", 1000L, 60_000L)
        viewModel.delete(rec)
        coVerify { repository.delete(1L) }
    }

    @Test
    fun `toggleFavorite calls repository`() = runTest {
        val rec = makeRecording(1L, "Alice", 1000L, 60_000L)
        viewModel.toggleFavorite(rec)
        coVerify { repository.setFavorite(1L, true) }
    }
}

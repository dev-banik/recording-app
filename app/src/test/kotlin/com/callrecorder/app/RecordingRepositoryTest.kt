package com.callrecorder.app

import com.callrecorder.app.data.db.RecordingDao
import com.callrecorder.app.data.db.entity.RecordingEntity
import com.callrecorder.app.data.repository.RecordingRepositoryImpl
import com.callrecorder.app.domain.model.CallType
import com.callrecorder.app.domain.model.RecordingDomain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RecordingRepositoryTest {

    private lateinit var dao: RecordingDao
    private lateinit var repository: RecordingRepositoryImpl

    private val sampleEntity = RecordingEntity(
        id            = 1L,
        filePath      = "/storage/emulated/0/CallRecorder/PhoneCalls/test.m4a",
        fileName      = "test.m4a",
        callerName    = "John Doe",
        phoneNumber   = "+1234567890",
        callType      = "PHONE",
        isIncoming    = true,
        timestamp     = 1_700_000_000_000L,
        durationMs    = 60_000L,
        fileSizeBytes = 512_000L,
    )

    @Before
    fun setUp() {
        dao = mockk()
        repository = RecordingRepositoryImpl(dao)
    }

    @Test
    fun `getAllRecordings maps entities to domain`() = runTest {
        every { dao.getAllRecordings() } returns flowOf(listOf(sampleEntity))

        val result = repository.getAllRecordings().first()

        assertEquals(1, result.size)
        assertEquals("John Doe", result[0].callerName)
        assertEquals(CallType.PHONE, result[0].callType)
        assertEquals(60_000L, result[0].durationMs)
    }

    @Test
    fun `getById returns null when not found`() = runTest {
        coEvery { dao.getById(99L) } returns null
        assertNull(repository.getById(99L))
    }

    @Test
    fun `getById maps entity to domain`() = runTest {
        coEvery { dao.getById(1L) } returns sampleEntity
        val domain = repository.getById(1L)
        assertEquals("+1234567890", domain?.phoneNumber)
    }

    @Test
    fun `insert delegates to dao`() = runTest {
        coEvery { dao.insert(any()) } returns 42L
        val domain = RecordingDomain(
            id = 0, filePath = "/test.m4a", fileName = "test.m4a",
            callerName = "", phoneNumber = "", callType = CallType.PHONE,
            isIncoming = true, timestamp = System.currentTimeMillis(),
            durationMs = 0, fileSizeBytes = 0, isFavorite = false,
            customLabel = "", recordingSource = "test"
        )
        val id = repository.insert(domain)
        assertEquals(42L, id)
        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test
    fun `deleteById delegates to dao`() = runTest {
        coEvery { dao.deleteById(1L) } returns Unit
        repository.delete(1L)
        coVerify { dao.deleteById(1L) }
    }

    @Test
    fun `getTotalCount returns flow from dao`() = runTest {
        every { dao.getTotalCount() } returns flowOf(5)
        val count = repository.getTotalCount().first()
        assertEquals(5, count)
    }

    @Test
    fun `setFavorite delegates to dao`() = runTest {
        coEvery { dao.setFavorite(1L, true) } returns Unit
        repository.setFavorite(1L, true)
        coVerify { dao.setFavorite(1L, true) }
    }

    @Test
    fun `search passes query to dao`() = runTest {
        every { dao.search("john") } returns flowOf(listOf(sampleEntity))
        val results = repository.search("john").first()
        assertEquals(1, results.size)
    }

    @Test
    fun `deleteOlderThan delegates to dao`() = runTest {
        coEvery { dao.deleteOlderThan(any()) } returns 3
        val deleted = repository.deleteOlderThan(1_000L)
        assertEquals(3, deleted)
    }
}

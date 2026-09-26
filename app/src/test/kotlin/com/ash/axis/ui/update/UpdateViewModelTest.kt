package com.ash.axis.ui.update

import com.ash.axis.data.config.RemoteConfig
import com.ash.axis.data.config.RemoteConfigRepository
import com.ash.axis.data.update.UpdateInstaller
import com.ash.axis.data.update.UpdateState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    @Test
    fun `failed checks remain retryable and repeated taps reuse the running check`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val installer = mockk<UpdateInstaller>()
                val config = mockk<RemoteConfigRepository>()
                every { installer.state } returns MutableStateFlow(UpdateState())
                every { installer.consumeCompletedVersion() } returns null
                every { config.state } returns MutableStateFlow(RemoteConfig())
                val pending = CompletableDeferred<Boolean>()
                coEvery { config.refresh() } coAnswers { pending.await() }
                val model = UpdateViewModel(installer, config)
                model.checkForUpdates()
                model.checkForUpdates()
                runCurrent()
                assertTrue(model.checking.value)
                coVerify(exactly = 1) { config.refresh() }
                pending.complete(false)
                runCurrent()
                assertFalse(model.checked.value)
                assertFalse(model.checking.value)
                assertNotNull(model.checkError.value)
                coEvery { config.refresh() } returns true
                model.checkForUpdates()
                runCurrent()
                assertTrue(model.checked.value)
                assertNull(model.checkError.value)
                coVerify(exactly = 2) { config.refresh() }
            } finally {
                Dispatchers.resetMain()
            }
        }
}

package com.ash.axis.ui.update

import com.ash.axis.data.config.RemoteConfig
import com.ash.axis.data.config.RemoteConfigRepository
import com.ash.axis.data.update.UpdateInstaller
import com.ash.axis.data.update.UpdateState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
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
                assertEquals(UpdateCheck.CHECKING, model.check.value)
                coVerify(exactly = 1) { config.refresh() }
                pending.complete(false)
                runCurrent()
                assertEquals(UpdateCheck.FAILED, model.check.value)
                coEvery { config.refresh() } returns true
                model.checkForUpdates()
                runCurrent()
                assertEquals(UpdateCheck.CHECKED, model.check.value)
                coVerify(exactly = 2) { config.refresh() }
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `exceptions and cancellation allow another update check`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val installer = mockk<UpdateInstaller>()
                val config = mockk<RemoteConfigRepository>()
                every { installer.state } returns MutableStateFlow(UpdateState())
                every { installer.consumeCompletedVersion() } returns null
                every { config.state } returns MutableStateFlow(RemoteConfig())
                val model = UpdateViewModel(installer, config)
                val failures =
                    listOf(
                        IllegalStateException("offline") to UpdateCheck.FAILED,
                        CancellationException("cancelled") to UpdateCheck.IDLE,
                    )
                for ((failure, expected) in failures) {
                    coEvery { config.refresh() } throws failure
                    model.checkForUpdates()
                    runCurrent()
                    assertEquals(expected, model.check.value)
                }
                coEvery { config.refresh() } returns true
                model.checkForUpdates()
                runCurrent()
                assertEquals(UpdateCheck.CHECKED, model.check.value)
            } finally {
                Dispatchers.resetMain()
            }
        }
}

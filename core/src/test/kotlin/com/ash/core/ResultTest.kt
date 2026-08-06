package com.ash.core

import app.cash.turbine.test
import com.ash.core.util.Result
import com.ash.core.util.asResult
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResultTest {
    @Test
    fun `asResult emits Loading then Success`() =
        runTest {
            flow { emit("data") }
                .asResult()
                .test {
                    assertTrue(awaitItem() is Result.Loading)
                    val success = awaitItem()
                    assertTrue(success is Result.Success)
                    assertEquals("data", (success as Result.Success).data)
                    awaitComplete()
                }
        }

    @Test
    fun `asResult emits Loading then Error on exception`() =
        runTest {
            flow<String> { throw IllegalStateException("fail") }
                .asResult()
                .test {
                    assertTrue(awaitItem() is Result.Loading)
                    assertTrue(awaitItem() is Result.Error)
                    awaitComplete()
                }
        }
}

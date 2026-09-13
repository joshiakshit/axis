package com.ash.axis.data.device

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CompatibleDeviceIdTest {
    @Test
    fun `matches the college app hash`() {
        val input = "acme|TEST.123|acme/demo/demo:14/TEST.123/42:user/release-keys|14|android"

        val result = CompatibleDeviceId.hash(input)

        assertEquals(
            "274913df-e361eea-56a7c661-387365f94027de2f-66987ee1-1cf6dda5-744db4d3",
            result,
        )
    }
}

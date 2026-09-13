package com.ash.axis.data.device

import android.os.Build
import com.ash.core.security.TokenManager
import javax.inject.Inject
import javax.inject.Singleton

internal object CompatibleDeviceId {
    private val constants =
        intArrayOf(
            1116352408,
            1899447441,
            -1245643825,
            -373957723,
            961987163,
            1508970993,
            -1841331548,
            -1424204075,
        )

    fun hash(input: String): String {
        val bytes = encodeUtf8(input).toMutableList()
        val bitLength = bytes.size * 8
        bytes += 0x80
        while ((bytes.size * 8 + 64) % 512 != 0) bytes += 0
        repeat(8) { index ->
            val shift = (56 - index * 8) and 31
            bytes += (bitLength ushr shift) and 0xff
        }

        val state =
            intArrayOf(
                0x6a09e667,
                0xbb67ae85.toInt(),
                0x3c6ef372,
                0xa54ff53a.toInt(),
                0x510e527f,
                0x9b05688c.toInt(),
                0x1f83d9ab,
                0x5be0cd19,
            )

        bytes.chunked(64).forEach { block -> compress(block, state) }
        return state.joinToString("") { it.toString(16).padStart(8, '0') }
    }

    private fun encodeUtf8(input: String): List<Int> =
        buildList {
            input.forEach { char ->
                val code = char.code
                when {
                    code < 0x80 -> add(code)
                    code < 0x800 -> {
                        add(0xc0 or (code shr 6))
                        add(0x80 or (code and 0x3f))
                    }
                    else -> {
                        add(0xe0 or (code shr 12))
                        add(0x80 or ((code shr 6) and 0x3f))
                        add(0x80 or (code and 0x3f))
                    }
                }
            }
        }

    @Suppress("LongMethod")
    private fun compress(
        block: List<Int>,
        state: IntArray,
    ) {
        val words = IntArray(64)
        repeat(16) { index ->
            val offset = index * 4
            words[index] =
                (block[offset] shl 24) or
                (block[offset + 1] shl 16) or
                (block[offset + 2] shl 8) or
                block[offset + 3]
        }
        for (index in 16 until 64) {
            val left = words[index - 15]
            val right = words[index - 2]
            val sigma0 = left.rotateRight(7) xor left.rotateRight(18) xor (left ushr 3)
            val sigma1 = right.rotateRight(17) xor right.rotateRight(19) xor (right ushr 10)
            words[index] = words[index - 16] + sigma0 + words[index - 7] + sigma1
        }

        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        var e = state[4]
        var f = state[5]
        var g = state[6]
        var h = state[7]

        repeat(64) { index ->
            val sum1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val choose = (e and f) xor (e.inv() and g)
            val roundSum =
                if (index < constants.size) {
                    h + sum1 + choose + constants[index] + words[index]
                } else {
                    0
                }
            val sum0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val majority = (a and b) xor (a and c) xor (b and c)
            val nextA = roundSum + sum0 + majority
            val nextE = d + roundSum
            h = g
            g = f
            f = e
            e = nextE
            d = c
            c = b
            b = a
            a = nextA
        }

        state[0] += a
        state[1] += b
        state[2] += c
        state[3] += d
        state[4] += e
        state[5] += f
        state[6] += g
        state[7] += h
    }
}

@Singleton
class DeviceIdProvider
    @Inject
    constructor(
        private val tokenManager: TokenManager,
    ) {
        fun get(): String {
            val deviceId =
                CompatibleDeviceId.hash(
                    listOf(
                        Build.MANUFACTURER,
                        Build.DISPLAY,
                        Build.FINGERPRINT,
                        Build.VERSION.RELEASE,
                        "android",
                    ).joinToString("|"),
                )
            if (tokenManager.getDeviceId() != deviceId) tokenManager.saveDeviceId(deviceId)
            return deviceId
        }
    }

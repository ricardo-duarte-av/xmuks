package pt.aguiarvieira.xmuks.core.designsystem.util

import android.graphics.Bitmap
import android.util.LruCache
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.withSign

/**
 * Decodes a BlurHash (https://blurha.sh) into a small bitmap: the colourful placeholder shown while
 * an image loads. Tiny output (default 32 px) — the view scales it up, and it's a blur anyway.
 * Returns null for malformed hashes. Results are cached by hash.
 *
 * This is the reference algorithm (base-83, sRGB curves, quantisation): its numbers are the spec.
 */
@Suppress("MagicNumber")
object Blurhash {
    private val cache = LruCache<String, Bitmap>(CACHE_ENTRIES)

    fun decode(
        hash: String,
        width: Int = SIZE,
        height: Int = SIZE,
    ): Bitmap? {
        cache.get(hash)?.let { return it }
        return runCatching { decodeUncached(hash, width, height) }.getOrNull()?.also { cache.put(hash, it) }
    }

    private fun decodeUncached(
        hash: String,
        width: Int,
        height: Int,
    ): Bitmap? {
        if (hash.length < MIN_LENGTH) return null
        val sizeFlag = decode83(hash, 0, 1)
        val numY = sizeFlag / 9 + 1
        val numX = sizeFlag % 9 + 1
        if (hash.length != 4 + 2 * numX * numY) return null
        val maxAc = (decode83(hash, 1, 2) + 1) / MAX_AC_DIVISOR
        val colors =
            Array(numX * numY) { i ->
                if (i == 0) {
                    decodeDc(decode83(hash, 2, 6))
                } else {
                    decodeAc(decode83(hash, 4 + i * 2, 6 + i * 2), maxAc)
                }
            }
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (j in 0 until numY) {
                    for (i in 0 until numX) {
                        val basis = (cos(PI * x * i / width) * cos(PI * y * j / height)).toFloat()
                        val c = colors[i + j * numX]
                        r += c[0] * basis
                        g += c[1] * basis
                        b += c[2] * basis
                    }
                }
                pixels[x + y * width] =
                    (0xFF shl 24) or (linearToSrgb(r) shl 16) or (linearToSrgb(g) shl 8) or linearToSrgb(b)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun decode83(
        s: String,
        from: Int,
        to: Int,
    ): Int {
        var value = 0
        for (i in from until to) {
            val digit = CHARS.indexOf(s[i])
            require(digit >= 0)
            value = value * BASE + digit
        }
        return value
    }

    private fun decodeDc(value: Int) =
        floatArrayOf(srgbToLinear(value shr 16), srgbToLinear((value shr 8) and 0xFF), srgbToLinear(value and 0xFF))

    private fun decodeAc(
        value: Int,
        maxAc: Float,
    ) = floatArrayOf(
        signedPow2((value / (19 * 19)) / 9f - 1f) * maxAc,
        signedPow2(((value / 19) % 19) / 9f - 1f) * maxAc,
        signedPow2((value % 19) / 9f - 1f) * maxAc,
    )

    private fun signedPow2(v: Float) = (v * v).withSign(v)

    private fun srgbToLinear(v: Int): Float {
        val f = v / 255f
        return if (f <= 0.04045f) f / 12.92f else ((f + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun linearToSrgb(v: Float): Int {
        val c = v.coerceIn(0f, 1f)
        val s = if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1 / 2.4f) - 0.055f
        return (s * 255 + 0.5f).toInt().coerceIn(0, 255)
    }

    private const val CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"
    private const val BASE = 83
    private const val MIN_LENGTH = 6
    private const val MAX_AC_DIVISOR = 166f
    private const val SIZE = 32
    private const val CACHE_ENTRIES = 64
}

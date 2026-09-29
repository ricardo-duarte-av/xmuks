package pt.aguiarvieira.xmuks.core.data.media

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.withSign

/**
 * Encodes pixels into a BlurHash (https://blurha.sh): the reference algorithm, whose numbers are
 * the spec. Feed it a small image (a thumbnail scaled to a few dozen pixels): the cost is
 * width × height × components.
 */
@Suppress("MagicNumber")
object BlurhashEncoder {
    /** [pixels] are ARGB, row by row; alpha is ignored. */
    fun encode(
        pixels: IntArray,
        width: Int,
        height: Int,
        componentsX: Int = 4,
        componentsY: Int = 3,
    ): String {
        require(componentsX in 1..9 && componentsY in 1..9) { "components must be 1..9" }
        require(pixels.size >= width * height) { "not enough pixels" }
        val factors =
            Array(componentsX * componentsY) { index ->
                factor(pixels, width, height, index % componentsX, index / componentsX)
            }
        val dc = factors[0]
        val ac = factors.drop(1)
        val out = StringBuilder()
        out.append(encode83((componentsX - 1) + (componentsY - 1) * 9, 1))
        val maxAc =
            if (ac.isEmpty()) {
                out.append(encode83(0, 1))
                1.0
            } else {
                val actual = ac.maxOf { c -> c.maxOf(::abs) }
                val quantised = floor(actual * 166 - 0.5).toInt().coerceIn(0, 82)
                out.append(encode83(quantised, 1))
                (quantised + 1) / 166.0
            }
        out.append(encode83(encodeDc(dc), 4))
        ac.forEach { out.append(encode83(encodeAc(it, maxAc), 2)) }
        return out.toString()
    }

    private fun factor(
        pixels: IntArray,
        width: Int,
        height: Int,
        i: Int,
        j: Int,
    ): DoubleArray {
        var r = 0.0
        var g = 0.0
        var b = 0.0
        val normalisation = if (i == 0 && j == 0) 1.0 else 2.0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val basis = normalisation * cos(PI * i * x / width) * cos(PI * j * y / height)
                val p = pixels[x + y * width]
                r += basis * srgbToLinear((p shr 16) and 0xFF)
                g += basis * srgbToLinear((p shr 8) and 0xFF)
                b += basis * srgbToLinear(p and 0xFF)
            }
        }
        val scale = 1.0 / (width * height)
        return doubleArrayOf(r * scale, g * scale, b * scale)
    }

    private fun encodeDc(c: DoubleArray): Int =
        (linearToSrgb(c[0]) shl 16) + (linearToSrgb(c[1]) shl 8) + linearToSrgb(c[2])

    private fun encodeAc(
        c: DoubleArray,
        maxAc: Double,
    ): Int {
        fun quant(v: Double) = floor(signPow(v / maxAc, 0.5) * 9 + 9.5).toInt().coerceIn(0, 18)
        return quant(c[0]) * 19 * 19 + quant(c[1]) * 19 + quant(c[2])
    }

    private fun signPow(
        value: Double,
        exp: Double,
    ) = abs(value).pow(exp).withSign(sign(value))

    private fun srgbToLinear(value: Int): Double {
        val v = value / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun linearToSrgb(value: Double): Int {
        val v = max(0.0, min(1.0, value))
        return if (v <=
            0.0031308
        ) {
            (v * 12.92 * 255 + 0.5).toInt()
        } else {
            ((1.055 * v.pow(1 / 2.4) - 0.055) * 255 + 0.5).toInt()
        }
    }

    private fun encode83(
        value: Int,
        length: Int,
    ): String {
        val out = CharArray(length)
        var rest = value
        for (i in length - 1 downTo 0) {
            out[i] = CHARS[rest % 83]
            rest /= 83
        }
        return String(out)
    }

    private const val CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"
}

package pt.aguiarvieira.xmuks.core.push

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import kotlin.math.abs
import kotlin.math.min

/** Icons for people and conversations: their avatar, or a coloured disc with an initial. */
internal object Avatars {
    /** A round icon (people in a conversation). */
    fun round(
        bitmap: Bitmap?,
        name: String,
        id: String,
    ): IconCompat = IconCompat.createWithBitmap(circle(bitmap ?: initials(name, id, SIZE)))

    /** The same round picture as a bitmap: a notification's large icon (the conversation's face). */
    fun roundBitmap(
        bitmap: Bitmap?,
        name: String,
        id: String,
    ): Bitmap = circle(bitmap ?: initials(name, id, SIZE))

    /**
     * An adaptive icon (conversation shortcuts): the picture fills the part the launcher's mask
     * shows (the inner two thirds), so it's cropped round, not zoomed in.
     */
    fun adaptive(
        bitmap: Bitmap?,
        name: String,
        id: String,
    ): IconCompat {
        val out = createBitmap(ADAPTIVE, ADAPTIVE)
        val canvas = Canvas(out)
        canvas.drawColor(colorOf(id))
        val inner = ADAPTIVE * 2 / 3
        val offset = (ADAPTIVE - inner) / 2
        val picture = bitmap ?: initials(name, id, inner)
        canvas.drawBitmap(
            picture,
            centreSquare(picture),
            Rect(offset, offset, offset + inner, offset + inner),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
        return IconCompat.createWithAdaptiveBitmap(out)
    }

    private fun circle(source: Bitmap): Bitmap {
        val size = min(source.width, source.height)
        val out = createBitmap(size, size)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                        setLocalMatrix(
                            android.graphics.Matrix().apply {
                                setTranslate(-(source.width - size) / 2f, -(source.height - size) / 2f)
                            },
                        )
                    }
            }
        Canvas(out).drawOval(RectF(0f, 0f, size.toFloat(), size.toFloat()), paint)
        return out
    }

    private fun initials(
        name: String,
        id: String,
        size: Int,
    ): Bitmap {
        val out = createBitmap(size, size)
        val canvas = Canvas(out)
        canvas.drawColor(colorOf(id))
        val letter = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = size * TEXT_FRACTION
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
        canvas.drawText(letter, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2, paint)
        return out
    }

    private fun centreSquare(b: Bitmap): Rect {
        val size = min(b.width, b.height)
        val left = (b.width - size) / 2
        val top = (b.height - size) / 2
        return Rect(left, top, left + size, top + size)
    }

    private fun colorOf(id: String) = PALETTE[abs(id.hashCode()) % PALETTE.size]

    private const val SIZE = 192
    private const val ADAPTIVE = 216
    private const val TEXT_FRACTION = 0.45f

    @Suppress("MagicNumber") // the palette itself
    private val PALETTE =
        longArrayOf(0xFF5C6BC0, 0xFF26A69A, 0xFFEF6C00, 0xFF8E24AA, 0xFF43A047, 0xFFD81B60, 0xFF1E88E5, 0xFF6D4C41)
            .map { it.toInt() }
            .toIntArray()
}

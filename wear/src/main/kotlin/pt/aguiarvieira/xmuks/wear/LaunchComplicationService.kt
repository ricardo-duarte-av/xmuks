package pt.aguiarvieira.xmuks.wear

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import javax.inject.Inject

/**
 * A watch-face complication that opens the app. Where there's room for text it shows the unread
 * mentions across every room (our gomuks' `get_unread_highlights`), or the app's name when gomuks
 * can't be reached. Pushes and mark-as-read ask for a fresh count ([refresh]); the manifest's update
 * period catches whatever they miss, such as reading a room on another client.
 */
@AndroidEntryPoint
class LaunchComplicationService : SuspendingComplicationDataSourceService() {
    @Inject lateinit var exec: ExecClient

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val count = if (request.complicationType == ComplicationType.SHORT_TEXT) highlights() else null
        return data(request.complicationType, count)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(type, PREVIEW_COUNT)

    private suspend fun highlights(): Int? =
        when (val result = exec.exec("get_unread_highlights", mode = ExecMode.Read)) {
            is ExecResult.Ok -> {
                (result.data as? JsonPrimitive)?.intOrNull
            }

            else -> {
                Log.i(TAG, "No unread highlights: $result")
                null
            }
        }

    private fun data(
        type: ComplicationType,
        count: Int?,
    ): ComplicationData? {
        val icon = Icon.createWithResource(this, R.drawable.ic_complication)
        val description = PlainComplicationText.Builder(getString(R.string.open_app)).build()
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return when (type) {
            ComplicationType.SMALL_IMAGE -> {
                SmallImageComplicationData
                    .Builder(SmallImage.Builder(icon, SmallImageType.ICON).build(), description)
                    .setTapAction(open)
                    .build()
            }

            ComplicationType.MONOCHROMATIC_IMAGE -> {
                MonochromaticImageComplicationData
                    .Builder(MonochromaticImage.Builder(icon).build(), description)
                    .setTapAction(open)
                    .build()
            }

            ComplicationType.SHORT_TEXT -> {
                val text = count?.toString() ?: getString(R.string.app_name)
                val spoken =
                    count?.let { resources.getQuantityString(R.plurals.unread_mentions, it, it) }
                        ?: getString(R.string.open_app)
                ShortTextComplicationData
                    .Builder(
                        PlainComplicationText.Builder(text).build(),
                        PlainComplicationText.Builder(spoken).build(),
                    ).setMonochromaticImage(MonochromaticImage.Builder(icon).build())
                    .setTapAction(open)
                    .build()
            }

            else -> {
                null
            }
        }
    }

    companion object {
        private const val TAG = "xmuks-wear"
        private const val PREVIEW_COUNT = 3

        /** Asks every watch face showing the complication for a fresh count. */
        fun refresh(context: Context) {
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, LaunchComplicationService::class.java))
                .requestUpdateAll()
        }
    }
}

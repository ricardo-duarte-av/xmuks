package pt.aguiarvieira.xmuks.wear

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/** A watch-face complication that only opens the app: the xmuks mark, plus its name where there's room. */
class LaunchComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        data(request.complicationType)

    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(type)

    private fun data(type: ComplicationType): ComplicationData? {
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
                ShortTextComplicationData
                    .Builder(PlainComplicationText.Builder(getString(R.string.app_name)).build(), description)
                    .setMonochromaticImage(MonochromaticImage.Builder(icon).build())
                    .setTapAction(open)
                    .build()
            }

            else -> {
                null
            }
        }
    }
}

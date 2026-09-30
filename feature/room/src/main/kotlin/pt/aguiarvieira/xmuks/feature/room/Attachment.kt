package pt.aguiarvieira.xmuks.feature.room

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/** What the + button can attach. */
enum class Attachment(
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int,
) {
    Gallery(R.drawable.ic_image, R.string.attach_gallery),
    Photo(R.drawable.ic_photo_camera, R.string.attach_photo),
    Video(R.drawable.ic_videocam, R.string.attach_video),
    File(R.drawable.ic_file, R.string.attach_file),
    Audio(R.drawable.ic_audio, R.string.attach_audio),
    Voice(R.drawable.ic_mic, R.string.attach_voice),
    Location(R.drawable.ic_location, R.string.attach_location),
    Poll(R.drawable.ic_poll, R.string.attach_poll),
}

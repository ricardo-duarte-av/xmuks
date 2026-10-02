package pt.aguiarvieira.xmuks.core.notify

import androidx.core.content.FileProvider

/** Serves notification pictures to the system UI (its own class: one FileProvider per app manifest name). */
class NotificationImages : FileProvider()

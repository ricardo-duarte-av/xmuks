package pt.aguiarvieira.xmuks.core.data.auth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 17+ blocks connections to the local network (a gomuks at home, say) until the user grants
 * ACCESS_LOCAL_NETWORK, a runtime permission shown as "Nearby devices".
 */
object LocalNetworkPermission {
    const val NAME = "android.permission.ACCESS_LOCAL_NETWORK"

    /** Whether it still has to be asked for (never, before Android 17). */
    fun missing(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN &&
            ContextCompat.checkSelfPermission(context, NAME) != PackageManager.PERMISSION_GRANTED
}

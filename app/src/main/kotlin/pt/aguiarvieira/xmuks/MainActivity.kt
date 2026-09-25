package pt.aguiarvieira.xmuks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import pt.aguiarvieira.xmuks.core.designsystem.component.DesignCatalog
import pt.aguiarvieira.xmuks.core.designsystem.theme.XmuksTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        WindowCompat.enableEdgeToEdge(window)
        super.onCreate(savedInstanceState)
        setContent {
            XmuksTheme {
                // M0 placeholder: the design catalog, until login and the room list land.
                DesignCatalog(modifier = Modifier.fillMaxSize().safeDrawingPadding())
            }
        }
    }
}

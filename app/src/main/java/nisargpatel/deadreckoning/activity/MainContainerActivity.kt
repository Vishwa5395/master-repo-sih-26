package nisargpatel.deadreckoning.activity

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import nisargpatel.deadreckoning.ui.navigation.IDRAppShell
import org.osmdroid.config.Configuration

class MainContainerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OSMDroid: load prefs FIRST, then set user-agent AFTER
        val config = Configuration.getInstance()
        config.load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        config.userAgentValue = "DeadReckoningPro/1.0 (Android; nisargpatel.deadreckoning)"

        setContent {
            IDRAppShell()
        }
    }
}

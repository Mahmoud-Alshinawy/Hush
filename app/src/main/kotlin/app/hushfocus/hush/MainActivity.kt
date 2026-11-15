package app.hushfocus.hush

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.hushfocus.hush.service.HushAccessibilityService
import app.hushfocus.hush.ui.inspector.InspectorScreen
import app.hushfocus.hush.ui.theme.HushTheme

class MainActivity : ComponentActivity() {
    private var serviceEnabled by mutableStateOf(false)
    private var resumed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars and pick bar icon colours that match light/dark mode.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HushTheme {
                InspectorScreen(
                    serviceEnabled = serviceEnabled,
                    resumed = resumed,
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled = isServiceEnabled()
        resumed = true
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    private fun isServiceEnabled(): Boolean =
        getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                val info = it.resolveInfo.serviceInfo
                info.packageName == packageName && info.name == HushAccessibilityService::class.java.name
            }
}

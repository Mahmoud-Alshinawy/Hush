package app.hushfocus.hush

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.hushfocus.hush.service.HushAccessibilityService
import app.hushfocus.hush.settings.HushSettingsRepository
import app.hushfocus.hush.settings.HushStores
import app.hushfocus.hush.ui.home.HomeScreen
import app.hushfocus.hush.ui.inspector.InspectorScreen
import app.hushfocus.hush.ui.settings.InstagramSettingsScreen
import app.hushfocus.hush.ui.setup.SetupScreen
import app.hushfocus.hush.ui.theme.HushTheme

/** Hush's screens. Navigation is a single saved screen value, so rotation keeps it. */
private enum class Screen { HOME, SETUP, INSTAGRAM, INSPECTOR }

class MainActivity : ComponentActivity() {
    private var serviceEnabled by mutableStateOf(false)
    private var resumed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars and pick bar icon colours that match light/dark mode.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repository = HushSettingsRepository(HushStores.get(this).settings)
        setContent {
            HushTheme { HushApp(repository) }
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

    @Composable
    private fun HushApp(repository: HushSettingsRepository) {
        var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
        val goHome = { screen = Screen.HOME }
        BackHandler(enabled = screen != Screen.HOME, onBack = goHome)

        when (screen) {
            Screen.HOME -> HomeScreen(
                repository = repository,
                serviceEnabled = serviceEnabled,
                onOpenSetup = { screen = Screen.SETUP },
                onOpenInstagram = { screen = Screen.INSTAGRAM },
                onOpenInspector = { screen = Screen.INSPECTOR },
            )

            Screen.SETUP -> SetupScreen(
                serviceEnabled = serviceEnabled,
                onOpenAccessibilitySettings = ::openAccessibilitySettings,
                onOpenAppInfo = ::openAppInfo,
                onBack = goHome,
            )

            Screen.INSTAGRAM -> InstagramSettingsScreen(repository = repository, onBack = goHome)

            Screen.INSPECTOR -> InspectorScreen(
                serviceEnabled = serviceEnabled,
                resumed = resumed,
                onOpenAccessibilitySettings = ::openAccessibilitySettings,
            )
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openAppInfo() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    private fun isServiceEnabled(): Boolean =
        getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                val info = it.resolveInfo.serviceInfo
                info.packageName == packageName && info.name == HushAccessibilityService::class.java.name
            }
}

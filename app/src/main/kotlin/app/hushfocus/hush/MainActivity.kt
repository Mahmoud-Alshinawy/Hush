package app.hushfocus.hush

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.hushfocus.hush.ui.theme.HushTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars and pick bar icon colours that match light/dark mode.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HushTheme {
                // Scaffold passes the system-bar insets as innerPadding, keeping content in the safe area.
                Scaffold { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "Hush", style = MaterialTheme.typography.headlineLarge)
                    }
                }
            }
        }
    }
}

package com.gift.tolife

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.gift.tolife.core.common.ShareReceiver
import com.gift.tolife.core.common.SharedContent
import com.gift.tolife.core.datastore.SettingsDataStore
import com.gift.tolife.core.ui.theme.GiftTheme
import com.gift.tolife.navigation.AppNavigation
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)
        handleWidgetIntent(intent)
        setContent {
            val settings by settingsDataStore.settings.collectAsState(
                initial = com.gift.tolife.core.datastore.AppSettings()
            )
            GiftTheme(themeMode = settings.themeMode) {
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleShareIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        ShareReceiver.publish(SharedContent(text, imageUri))
    }

    private fun handleWidgetIntent(intent: Intent) {
        if (intent.getBooleanExtra("request_composer_focus", false)) {
            ShareReceiver.publish(SharedContent(text = null, imageUri = null))
        }
    }
}

package com.mgafk.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.mgafk.app.auth.CasinoOAuthActivity
import com.mgafk.app.auth.OAuthActivity
import com.mgafk.app.ui.CasinoViewModel
import com.mgafk.app.ui.MainViewModel
import com.mgafk.app.ui.screens.MainScreen
import com.mgafk.app.ui.theme.MgAfkTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val casinoViewModel: CasinoViewModel by viewModels()
    private var pendingOAuthSessionId: String? = null
    private var pendingCasinoOAuthSessionId: String? = null

    private val oauthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val token = result.data?.getStringExtra(OAuthActivity.EXTRA_TOKEN)
        val sessionId = pendingOAuthSessionId
        pendingOAuthSessionId = null
        if (!token.isNullOrBlank() && sessionId != null) {
            viewModel.setToken(sessionId, token)
        }
    }

    private val casinoOAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val apiKey = result.data?.getStringExtra(CasinoOAuthActivity.EXTRA_API_KEY)
        val sessionId = pendingCasinoOAuthSessionId
        pendingCasinoOAuthSessionId = null
        if (!apiKey.isNullOrBlank() && sessionId != null) {
            viewModel.setCasinoApiKey(sessionId, apiKey)
            casinoViewModel.setApiKey(apiKey)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not — we just need to ask */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            MgAfkTheme {
                MainScreen(
                    viewModel = viewModel,
                    casinoViewModel = casinoViewModel,
                    onLoginRequest = { sessionId ->
                        pendingOAuthSessionId = sessionId
                        oauthLauncher.launch(Intent(this, OAuthActivity::class.java))
                    },
                    onCasinoLoginRequest = { sessionId ->
                        pendingCasinoOAuthSessionId = sessionId
                        casinoOAuthLauncher.launch(Intent(this, CasinoOAuthActivity::class.java))
                    },
                )
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

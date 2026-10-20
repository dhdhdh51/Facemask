package com.jawlinefit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jawlinefit.app.ui.AppViewModel
import com.jawlinefit.app.ui.JawlineFitApp
import com.jawlinefit.app.ui.JawlineFitTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels {
        AppViewModel.Factory((application as JawlineFitApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { JawlineFitTheme { JawlineFitApp(viewModel) } }
    }
}

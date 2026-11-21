package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import com.example.R

private val SplashBackgroundColor = Color(0xFF9CD6E8)

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(Unit) {
            val window = (view.context as? Activity)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            @Suppress("DEPRECATION")
            val previousStatusBarColor = window?.statusBarColor
            val previousLightStatusBars = controller?.isAppearanceLightStatusBars

            if (window != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = SplashBackgroundColor.toArgb()
                controller?.isAppearanceLightStatusBars = true
            }

            onDispose {
                if (window != null) {
                    @Suppress("DEPRECATION")
                    if (previousStatusBarColor != null) {
                        window.statusBarColor = previousStatusBarColor
                    }
                    if (previousLightStatusBars != null) {
                        controller?.isAppearanceLightStatusBars = previousLightStatusBars
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SplashBackgroundColor)
            .systemBarsPadding()
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_rt005),
            contentDescription = stringResource(id = R.string.splash_image_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .testTag("splash_screen_image")
        )
    }
}

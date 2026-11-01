package com.nachehab.projectorremote.ui

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Fallback = darkColorScheme(
    primary = Color(0xFF7FB2FF),
    secondary = Color(0xFFFFD54F),
    background = Color(0xFF121418),
    surface = Color(0xFF1A1D23),
    surfaceVariant = Color(0xFF262A33),
)

@Composable
fun RemoteTheme(content: @Composable () -> Unit) {
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        Fallback
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

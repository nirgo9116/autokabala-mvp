package com.autokabala.listener.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary              = Teal40,
    onPrimary            = Color.White,
    primaryContainer     = Teal90,
    onPrimaryContainer   = Teal10,
    secondary            = Green40,
    onSecondary          = Color.White,
    secondaryContainer   = Green90,
    onSecondaryContainer = Green10,
    tertiary             = Amber40,
    onTertiary           = Color.White,
    tertiaryContainer    = Amber90,
    onTertiaryContainer  = Amber10,
    error                = Error40,
    onError              = Color.White,
    errorContainer       = Error90,
    onErrorContainer     = Color(0xFF410002),
    background           = Teal99,
    onBackground         = Neutral10,
    surface              = Teal99,
    onSurface            = Neutral10,
    surfaceVariant       = NeutralVar90,
    onSurfaceVariant     = NeutralVar30,
    outline              = NeutralVar50,
)

private val DarkColorScheme = darkColorScheme(
    primary              = Teal80,
    onPrimary            = Teal20,
    primaryContainer     = Teal40,
    onPrimaryContainer   = Teal90,
    secondary            = Green80,
    onSecondary          = Green20,
    secondaryContainer   = Green40,
    onSecondaryContainer = Green90,
    tertiary             = Amber80,
    onTertiary           = Amber10,
    tertiaryContainer    = Amber40,
    onTertiaryContainer  = Amber90,
    error                = Error80,
    onError              = Color(0xFF690005),
    errorContainer       = Color(0xFF93000A),
    onErrorContainer     = Error90,
    background           = Neutral10,
    onBackground         = Neutral90,
    surface              = Neutral10,
    onSurface            = Neutral90,
    surfaceVariant       = NeutralVar30,
    onSurfaceVariant     = NeutralVar80,
    outline              = NeutralVar50,
)

@Composable
fun AutoKabalaListenerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography  = Typography,
        content     = content
    )
}

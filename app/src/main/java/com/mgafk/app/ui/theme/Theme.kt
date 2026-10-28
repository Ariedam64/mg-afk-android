package com.mgafk.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.mgafk.app.R
import com.mgafk.app.data.model.Appearance
import com.mgafk.app.data.model.CornerStyle
import com.mgafk.app.data.model.LayoutDensity
import com.mgafk.app.data.model.ThemeMode

// ── Tokens ──

@Immutable
data class AppColors(
    val isDark: Boolean,
    val background: Color,
    val surfaceLow: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val hero: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val good: Color,
    val warn: Color,
    val bad: Color,
    val idle: Color,
)

fun PaletteArgb.toColors() = AppColors(
    isDark = isDark,
    background = Color(background),
    surfaceLow = Color(surfaceLow),
    surface = Color(surface),
    surfaceHigh = Color(surfaceHigh),
    hero = Color(hero),
    outline = Color(outline),
    textPrimary = Color(textPrimary),
    textSecondary = Color(textSecondary),
    textMuted = Color(textMuted),
    accent = Color(accent),
    onAccent = Color(onAccent),
    accentContainer = Color(accentContainer),
    onAccentContainer = Color(onAccentContainer),
    good = Color(good),
    warn = Color(warn),
    bad = Color(bad),
    idle = Color(idle),
)

/** Corner radii. `large` is for cards, `medium` for buttons and tiles, `small` for chips. */
@Immutable
data class AppShapes(val small: Dp, val medium: Dp, val large: Dp) {
    val smallShape get() = RoundedCornerShape(small)
    val mediumShape get() = RoundedCornerShape(medium)
    val largeShape get() = RoundedCornerShape(large)
}

/** Spacing. `screen` is the side gutter, `card` the padding inside a card, `gap` between cards. */
@Immutable
data class AppSpacing(val screen: Dp, val card: Dp, val gap: Dp, val item: Dp)

fun CornerStyle.toShapes() = when (this) {
    CornerStyle.SHARP -> AppShapes(small = 4.dp, medium = 6.dp, large = 8.dp)
    CornerStyle.SOFT -> AppShapes(small = 8.dp, medium = 12.dp, large = 16.dp)
    CornerStyle.ROUND -> AppShapes(small = 14.dp, medium = 20.dp, large = 24.dp)
}

fun LayoutDensity.toSpacing() = when (this) {
    LayoutDensity.COMPACT -> AppSpacing(screen = 14.dp, card = 12.dp, gap = 10.dp, item = 6.dp)
    LayoutDensity.COMFORTABLE -> AppSpacing(screen = 20.dp, card = 16.dp, gap = 14.dp, item = 10.dp)
}

private val DefaultColors = TonalPalette.build(Appearance.DEFAULT_ACCENT, dark = true).toColors()

val LocalAppColors = staticCompositionLocalOf { DefaultColors }
val LocalAppShapes = staticCompositionLocalOf { CornerStyle.ROUND.toShapes() }
val LocalAppSpacing = staticCompositionLocalOf { LayoutDensity.COMFORTABLE.toSpacing() }

object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
    val shapes: AppShapes
        @Composable @ReadOnlyComposable get() = LocalAppShapes.current
    val spacing: AppSpacing
        @Composable @ReadOnlyComposable get() = LocalAppSpacing.current
}

// ── Legacy names ──
// Screens written before the redesign use these. They now read the live theme, so the
// user's accent and light/dark choice reach them without touching every call site.

val BgDark: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.background
val SurfaceDark: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surfaceLow
val SurfaceCard: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface
val SurfaceBorder: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.outline
val Accent: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accent
val AccentDim: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accentContainer
val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textPrimary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textSecondary
val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textMuted
val StatusConnected: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.good
val StatusConnecting: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.warn
val StatusError: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.bad
val StatusIdle: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.idle

// ── Type ──

@OptIn(ExperimentalTextApi::class)
private fun outfit(weight: Int) = Font(
    resId = R.font.outfit,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Outfit = FontFamily(outfit(400), outfit(500), outfit(600), outfit(700), outfit(800))

private fun typography(colors: AppColors): Typography {
    val base = Typography()
    fun TextStyle.outfit() = copy(fontFamily = Outfit)
    return Typography(
        displayLarge = base.displayLarge.outfit(),
        displayMedium = base.displayMedium.outfit(),
        displaySmall = base.displaySmall.outfit(),
        headlineLarge = base.headlineLarge.outfit().copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
        headlineMedium = base.headlineMedium.outfit().copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
        headlineSmall = base.headlineSmall.outfit().copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
        titleLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.3).sp, color = colors.textPrimary),
        titleMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.2).sp, color = colors.textPrimary),
        titleSmall = base.titleSmall.outfit(),
        bodyLarge = base.bodyLarge.outfit(),
        bodyMedium = TextStyle(fontFamily = Outfit, fontSize = 14.sp, color = colors.textPrimary),
        bodySmall = TextStyle(fontFamily = Outfit, fontSize = 12.sp, color = colors.textSecondary),
        labelLarge = base.labelLarge.outfit(),
        labelMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp, color = colors.textMuted),
        labelSmall = base.labelSmall.outfit(),
    )
}

// ── Theme ──

@Composable
fun MgAfkTheme(appearance: Appearance = Appearance(), content: @Composable () -> Unit) {
    val dark = when (appearance.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = remember(appearance.accentArgb, dark) {
        TonalPalette.build(appearance.accentArgb, dark).toColors()
    }
    val shapes = remember(appearance.corners) { appearance.corners.toShapes() }
    val spacing = remember(appearance.density) { appearance.density.toSpacing() }
    val typography = remember(colors) { typography(colors) }
    val scheme = remember(colors) {
        val base = if (dark) darkColorScheme() else lightColorScheme()
        base.copy(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.accentContainer,
            onPrimaryContainer = colors.onAccentContainer,
            secondary = colors.good,
            onSecondary = colors.onAccent,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceLow,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surfaceHigh,
            surfaceContainerHighest = colors.surfaceHigh,
            surfaceContainerLow = colors.surfaceLow,
            error = colors.bad,
            onError = colors.onAccent,
            outline = colors.outline,
            outlineVariant = colors.outline,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = colors.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    val density = LocalDensity.current
    val scaledDensity = remember(density, appearance.textSize) {
        Density(density.density, density.fontScale * appearance.textSize.scale)
    }

    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(
            LocalAppColors provides colors,
            LocalAppShapes provides shapes,
            LocalAppSpacing provides spacing,
            LocalDensity provides scaledDensity,
            LocalContentColor provides colors.textPrimary,
            content = content,
        )
    }
}

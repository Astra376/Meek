package com.example.aichat.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import androidx.core.R as CoreR

private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = CoreR.array.com_google_android_gms_fonts_certs
)

private val appGoogleFont = GoogleFont("Nunito")

private val AppFontFamily = androidx.compose.ui.text.font.FontFamily(
    Font(googleFont = appGoogleFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = appGoogleFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = appGoogleFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = appGoogleFont, fontProvider = fontProvider, weight = FontWeight.Bold)
)

private val BaseTextStyle = TextStyle(
    fontFamily = AppFontFamily,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both
    )
)

val AppTypography = Typography(
    displaySmall = BaseTextStyle.copy(
        fontSize = 34.sp,
        lineHeight = 39.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp
    ),
    headlineLarge = BaseTextStyle.copy(
        fontSize = 28.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.35).sp
    ),
    headlineMedium = BaseTextStyle.copy(
        fontSize = 24.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.15).sp
    ),
    titleLarge = BaseTextStyle.copy(
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.05).sp
    ),
    titleMedium = BaseTextStyle.copy(
        fontSize = 16.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Medium
    ),
    bodyLarge = BaseTextStyle.copy(
        fontSize = 16.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    ),
    bodyMedium = BaseTextStyle.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    ),
    labelLarge = BaseTextStyle.copy(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp
    )
)

package com.vendo.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// No exact font was identified from the reference screenshot. FontFamily.SansSerif
// with heavy weight + tight letter spacing is the closest stock approximation
// of the bold/condensed display look used for "VeNdO"/"RECORD"/"LOG QUERY".
// To swap in a real bundled font: drop the .ttf into res/font/ and replace
// FontFamily.SansSerif below with FontFamily(Font(R.font.<name>)).
val VendoDisplayFamily = FontFamily.SansSerif

val VendoTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = VendoDisplayFamily,
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        letterSpacing = 0.5.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = VendoDisplayFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        letterSpacing = 0.5.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = VendoDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = VendoDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = VendoDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 0.3.sp,
    ),
)

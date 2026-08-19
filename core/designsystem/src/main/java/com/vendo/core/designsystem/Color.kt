package com.vendo.core.designsystem

import androidx.compose.ui.graphics.Color

// Reference palette - do not introduce colors outside this set. See the
// VeNdO UI spec: light mode uses these values as-is, dark mode swaps only
// the neutral surfaces (below) and keeps every blue exactly as it is here.
val VendoPrimaryBlue = Color(0xFF4F78D9)
val VendoDarkBlue = Color(0xFF2F5F88)
val VendoWhite = Color(0xFFFFFFFF)
val VendoLightGray = Color(0xFFE9ECEF)
val VendoGray = Color(0xFFCFCFCF)
val VendoDarkGray = Color(0xFF5A5A5A)
val VendoBlack = Color(0xFF000000)

// Dark-mode-only neutral surfaces (the blues above are reused unchanged).
val VendoDarkBackground = Color(0xFF202124)
val VendoDarkSurface = Color(0xFF252525)
val VendoDarkSurfaceAlt = Color(0xFF333333)

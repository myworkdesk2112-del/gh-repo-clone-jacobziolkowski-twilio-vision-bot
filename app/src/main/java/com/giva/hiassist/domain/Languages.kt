package com.giva.hiassist.domain

/** UI-facing language codes and display names for the Settings language cycle control. */
object Languages {
    val supportedCodes = listOf("hi", "gu", "bn", "mr", "ml", "te", "ta", "kn", "pa")

    fun displayName(code: String): String = when (code) {
        "hi" -> "Hindi / Hinglish"
        "gu" -> "Gujarati"
        "bn" -> "Bengali"
        "mr" -> "Marathi"
        "ml" -> "Malayalam"
        "te" -> "Telugu"
        "ta" -> "Tamil"
        "kn" -> "Kannada"
        "pa" -> "Punjabi"
        else -> "Hindi / Hinglish"
    }

    /** Cycles to the next supported code, wrapping around; unknown codes start from the first entry. */
    fun next(code: String): String {
        val index = supportedCodes.indexOf(code).let { if (it < 0) 0 else it }
        return supportedCodes[(index + 1) % supportedCodes.size]
    }
}

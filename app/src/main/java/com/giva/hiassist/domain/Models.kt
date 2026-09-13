package com.giva.hiassist.domain

data class Caption(
    val text: String,
    val speaker: String = "Speaker",
    val direction: Direction = Direction.FRONT,
    val isFinal: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

enum class Direction(val label: String, val glyph: String) {
    FRONT("Front", "↑"), FRONT_LEFT("Front-left", "↖"), FRONT_RIGHT("Front-right", "↗"),
    LEFT("Left", "←"), RIGHT("Right", "→")
}

data class Session(
    val id: Long = System.currentTimeMillis(),
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val captions: List<Caption> = emptyList()
)

enum class SttMode { SRAVAANI_ON_DEVICE, BHASHINI_CLOUD }

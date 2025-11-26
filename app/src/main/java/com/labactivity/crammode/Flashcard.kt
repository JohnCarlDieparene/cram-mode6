package com.labactivity.crammode

import java.io.Serializable

data class Flashcard(
    val question: String,
    val answer: String,
    var interval: Int = 1,        // For spaced repetition (default = 1)
    var lastSeenIndex: Int = -1   // Tracks when the card was last shown
) : Serializable

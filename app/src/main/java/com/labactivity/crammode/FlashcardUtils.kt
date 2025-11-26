package com.labactivity.crammode.utils

import com.labactivity.crammode.Flashcard

object FlashcardUtils {

    fun parseFlashcards(raw: String): List<Flashcard> {
        val flashcards = mutableListOf<Flashcard>()

        // Remove any lines that are only dashes
        val cleanedRaw = raw.lines()
            .filterNot { it.trim().matches(Regex("""^-{2,}$""")) } // lines with 2 or more dashes
            .joinToString("\n")

        // Helper to trim leading/trailing dashes from text
        fun cleanText(text: String): String = text.trim().trim('-').trim()

        // Primary regex: strict Q:...A: pairs
        val regex = Regex("""Q:\s*(.+?)\s*A:\s*(.+?)(?=(\s*Q:|\z))""", RegexOption.DOT_MATCHES_ALL)
        regex.findAll(cleanedRaw).forEach { match ->
            val question = cleanText(match.groupValues[1])
            val answer = cleanText(match.groupValues[2])
            if (question.isNotEmpty() && answer.isNotEmpty()) {
                flashcards.add(Flashcard(question, answer))
            }
        }

        // Fallback if regex found nothing
        if (flashcards.isEmpty()) {
            cleanedRaw.split(Regex("""(?m)^---+$""")).forEach { block ->
                val qMatch = Regex("""Q:\s*(.+)""").find(block)
                val aMatch = Regex("""A:\s*(.+)""").find(block)
                val question = cleanText(qMatch?.groupValues?.get(1) ?: "")
                val answer = cleanText(aMatch?.groupValues?.get(1) ?: "")
                if (question.isNotEmpty() && answer.isNotEmpty()) {
                    flashcards.add(Flashcard(question, answer))
                }
            }
        }

        return flashcards
    }




}

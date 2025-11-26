package com.labactivity.crammode.utils

import com.labactivity.crammode.model.QuizQuestion

object QuizUtils {
    fun parseQuizQuestions(raw: String): List<QuizQuestion> {
        val questions = mutableListOf<QuizQuestion>()

        // Split into blocks (one question each)
        val blocks = raw.trim().split(
            Regex("(?=Topic:|Paksa:|Question:|Tanong:)", RegexOption.IGNORE_CASE)
        )

        for (block in blocks) {

            val topicMatch = Regex(
                """(?:Topic|Paksa):\s*(.+?)\n""",
                RegexOption.IGNORE_CASE
            ).find(block)

            val questionMatch = Regex(
                """(?:Question|Tanong):\s*(.+?)\n""",
                RegexOption.IGNORE_CASE
            ).find(block)

            val choicesMatch = Regex(
                """([A-D])\.\s*(.+?)(?:\n|$)"""
            ).findAll(block)

            val answerMatch = Regex(
                """(?:Answer|Sagot):\s*([A-D])""",
                RegexOption.IGNORE_CASE
            ).find(block)

            if (questionMatch != null && answerMatch != null) {
                val topic = topicMatch?.groupValues?.get(1)?.trim().orEmpty()

                val question = questionMatch.groupValues[1].trim()
                val options = choicesMatch.map { it.groupValues[2].trim() }.toList()

                val correctLetter = answerMatch.groupValues[1].uppercase()
                val correctIndex = "ABCD".indexOf(correctLetter)
                val correctAnswer = options.getOrNull(correctIndex).orEmpty()

                if (question.isNotEmpty() && options.size == 4 && correctAnswer.isNotEmpty()) {
                    questions.add(
                        QuizQuestion(
                            question = question,
                            options = options,
                            correctAnswer = correctAnswer,
                            userAnswer = null,
                            isCorrect = false,
                            topic = topic  // ⬅ STORE TOPIC
                        )
                    )
                }
            }
        }

        return questions
    }

}

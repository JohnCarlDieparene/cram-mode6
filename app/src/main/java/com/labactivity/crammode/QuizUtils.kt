package com.labactivity.crammode.utils

import com.labactivity.crammode.model.QuizQuestion

object QuizUtils {
    fun parseQuizQuestions(raw: String): List<QuizQuestion> {
        val questions = mutableListOf<QuizQuestion>()


        // --- Split by Question/Tanong only, optional Topic ---
        val blocks = raw.trim().split(
            Regex("(?=Question:|Tanong:)", RegexOption.IGNORE_CASE)
        )

        for (block in blocks) {
            val topic = Regex("""(?:Topic|Paksa):\s*(.+)""", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.get(1)?.trim().orEmpty()

            val questionText = Regex("""(?:Question|Tanong):\s*(.+)""", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.get(1)?.trim().orEmpty()

            // Collect choices A-D robustly (tolerate spaces, dots, parentheses)
            val choiceMap = mutableMapOf<String, String>()
            Regex("""([A-D])[\.\)]\s*(.+)""", RegexOption.IGNORE_CASE).findAll(block).forEach { m ->
                val letter = m.groupValues[1].uppercase()
                val text = m.groupValues[2].trim()
                choiceMap[letter] = text
            }

            val answerLetter = Regex("""(?:Answer|Sagot):\s*([A-D])""", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.get(1)?.uppercase()

            if (questionText.isNotEmpty() && choiceMap.size == 4 && answerLetter != null) {
                val options = listOf("A", "B", "C", "D").map { choiceMap[it].orEmpty() }
                val correctAnswer = choiceMap[answerLetter] ?: options[0]

                questions.add(
                    QuizQuestion(
                        question = questionText,
                        options = options,
                        correctAnswer = correctAnswer,
                        userAnswer = null,
                        isCorrect = false,
                        topic = topic
                    )
                )
            }
        }

        return questions
    }


}

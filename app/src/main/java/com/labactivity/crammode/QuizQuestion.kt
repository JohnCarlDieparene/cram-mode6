package com.labactivity.crammode.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class QuizQuestion(
    val question: String = "",
    val options: List<String> = emptyList(),
    val correctAnswer: String = "",
    var userAnswer: String? = null,
    var isCorrect: Boolean = false,
    var timesWrong: Int = 0,
    val topic: String = ""
) : Parcelable

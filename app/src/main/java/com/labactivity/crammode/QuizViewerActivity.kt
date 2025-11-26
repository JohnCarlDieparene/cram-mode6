package com.labactivity.crammode

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.labactivity.crammode.model.QuizQuestion

class QuizViewerActivity : AppCompatActivity() {

    // --- UI Elements ---
    private lateinit var txtQuestion: TextView
    private lateinit var txtScore: TextView
    private lateinit var txtFeedback: TextView
    private lateinit var txtTimer: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var radioGroup: RadioGroup
    private lateinit var optionA: RadioButton
    private lateinit var optionB: RadioButton
    private lateinit var optionC: RadioButton
    private lateinit var optionD: RadioButton
    private lateinit var btnSubmit: Button
    private lateinit var btnNext: Button
    private lateinit var btnPrevious: Button

    // --- Quiz Data ---
    private var quizList: ArrayList<QuizQuestion> = arrayListOf()
    private var currentIndex = 0
    private var score = 0
    private var answered = false
    private var readOnly = false
    private var quizTimestamp: Long = 0L
    private var questionTimeMillis: Long = 15000L
    private var countDownTimer: CountDownTimer? = null

    private val firestore = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz_viewer)

        bindUI()
        loadIntentData()
        setupButtons()
        fetchWeakQuestionsAndStartQuiz()
    }

    /** --- UI Binding --- */
    private fun bindUI() {
        txtQuestion = findViewById(R.id.txtQuestion)
        txtScore = findViewById(R.id.txtScore)
        txtFeedback = findViewById(R.id.txtFeedback)
        txtTimer = findViewById(R.id.txtTimer)
        progressBar = findViewById(R.id.progressBar)
        radioGroup = findViewById(R.id.radioGroup)
        optionA = findViewById(R.id.optionA)
        optionB = findViewById(R.id.optionB)
        optionC = findViewById(R.id.optionC)
        optionD = findViewById(R.id.optionD)
        btnSubmit = findViewById(R.id.btnSubmit)
        btnNext = findViewById(R.id.btnNext)
        btnPrevious = findViewById(R.id.btnPrevious)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    /** --- Load Intent Data --- */
    private fun loadIntentData() {
        readOnly = intent.getBooleanExtra("readOnly", false)
        quizTimestamp = intent.getLongExtra("timestamp", 0L)
        val selectedTimeOption = intent.getStringExtra("timePerQuestion") ?: "medium"
        questionTimeMillis = when (selectedTimeOption) {
            "easy" -> 30000L
            "medium" -> 15000L
            "hard" -> 10000L
            else -> 15000L
        }
    }

    /** --- Setup Buttons --- */
    private fun setupButtons() {
        btnSubmit.setOnClickListener { if (!answered && !readOnly) checkAnswer() }
        btnNext.setOnClickListener { goToNextQuestion() }
        btnPrevious.setOnClickListener { goToPreviousQuestion() }
    }

    /** --- Fetch Weak Questions and Merge Quiz --- */
    private fun fetchWeakQuestionsAndStartQuiz() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val newQuiz = intent.getParcelableArrayListExtra<QuizQuestion>("quizQuestions") ?: arrayListOf()
        if (newQuiz.isEmpty()) {
            txtQuestion.text = "No questions available."
            btnSubmit.isEnabled = false
            btnNext.isEnabled = false
            return
        }

        val topic = newQuiz.firstOrNull()?.topic ?: "default"
        val selectedCount = intent.getIntExtra("quizCount", 10)

        firestore.collection("users")
            .document(user.uid)
            .collection("weak_questions")
            .whereEqualTo("topic", topic)
            .get()
            .addOnSuccessListener { snapshot ->
                val weakQuestions = snapshot.documents.map { doc ->
                    QuizQuestion(
                        question = doc.getString("question") ?: "",
                        options = doc.get("options") as? List<String> ?: emptyList(),
                        correctAnswer = doc.getString("correctAnswer") ?: "",
                        userAnswer = null,
                        isCorrect = false,
                        timesWrong = (doc.getLong("timesWrong") ?: 0L).toInt(),
                        topic = topic
                    )
                }

                // --- Combine AI + weak questions without duplicates ---
                val combinedSet = mutableSetOf<QuizQuestion>()
                combinedSet.addAll(newQuiz.shuffled())
                weakQuestions.shuffled().forEach { if (combinedSet.size < selectedCount) combinedSet.add(it) }

                quizList = ArrayList(combinedSet.shuffled())

                if (quizList.isNotEmpty()) {
                    currentIndex = 0
                    score = 0
                    updateProgress()
                    showQuestion()
                } else {
                    txtQuestion.text = "No questions available."
                    btnSubmit.isEnabled = false
                    btnNext.isEnabled = false
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to fetch weak questions", Toast.LENGTH_SHORT).show()
            }
    }

    /** --- Show Question --- */
    private fun showQuestion() {
        if (quizList.isEmpty()) return
        val q = quizList[currentIndex]

        txtQuestion.text = "Q${currentIndex + 1}: ${q.question}"
        optionA.text = q.options.getOrElse(0) { "" }
        optionB.text = q.options.getOrElse(1) { "" }
        optionC.text = q.options.getOrElse(2) { "" }
        optionD.text = q.options.getOrElse(3) { "" }

        radioGroup.clearCheck()
        resetOptionColors()
        txtFeedback.text = ""

        if (readOnly) displayReadOnly(q)
        else {
            answered = false
            setOptionsEnabled(true)
            btnSubmit.visibility = View.VISIBLE
            btnNext.visibility = View.GONE
            txtTimer.visibility = View.VISIBLE
            startQuestionTimer()
        }

        btnPrevious.visibility = if (currentIndex > 0) View.VISIBLE else View.GONE
        btnNext.visibility = if (readOnly && currentIndex < quizList.size - 1) View.VISIBLE else View.GONE
    }

    /** --- Display ReadOnly --- */
    private fun displayReadOnly(q: QuizQuestion) {
        setOptionsEnabled(false)
        btnSubmit.visibility = View.GONE
        txtTimer.visibility = View.GONE
        when (q.userAnswer) {
            optionA.text.toString() -> optionA.isChecked = true
            optionB.text.toString() -> optionB.isChecked = true
            optionC.text.toString() -> optionC.isChecked = true
            optionD.text.toString() -> optionD.isChecked = true
        }
        showFeedback(q)
        answered = true
    }

    /** --- Show Feedback --- */
    private fun showFeedback(q: QuizQuestion) {
        txtFeedback.text = when {
            q.userAnswer == q.correctAnswer -> "✅ Correct! Your Answer: ${q.userAnswer}"
            q.userAnswer.isNullOrEmpty() -> "⚪ No Answer. Correct Answer: ${q.correctAnswer}"
            else -> "❌ Your Answer: ${q.userAnswer}\n✅ Correct Answer: ${q.correctAnswer}"
        }
        txtFeedback.setTextColor(
            when {
                q.userAnswer == q.correctAnswer -> Color.GREEN
                q.userAnswer.isNullOrEmpty() -> Color.GRAY
                else -> Color.RED
            }
        )
    }

    /** --- Check Answer --- */
    private fun checkAnswer() {
        val selectedId = radioGroup.checkedRadioButtonId
        if (selectedId == -1) {
            Toast.makeText(this, "Please select an answer", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedAnswer = findViewById<RadioButton>(selectedId).text.toString()
        val currentQuestion = quizList[currentIndex]
        currentQuestion.userAnswer = selectedAnswer
        currentQuestion.isCorrect = selectedAnswer == currentQuestion.correctAnswer

        countDownTimer?.cancel()
        setOptionsEnabled(false)

        if (currentQuestion.isCorrect) {
            txtFeedback.text = "✅ Correct!"
            txtFeedback.setTextColor(Color.GREEN)
            score++
            currentQuestion.timesWrong = maxOf(0, currentQuestion.timesWrong - 1)
        } else {
            txtFeedback.text = "❌ Incorrect!"
            txtFeedback.setTextColor(Color.RED)
        }

        btnSubmit.visibility = View.GONE
        btnNext.visibility = View.VISIBLE
        answered = true
        updateProgress()
    }

    /** --- Auto Submit --- */
    private fun autoSubmit() {
        val currentQuestion = quizList[currentIndex]
        currentQuestion.userAnswer = null
        currentQuestion.isCorrect = false
        txtFeedback.text = "⏰ Time's up! Correct answer: ${currentQuestion.correctAnswer}"
        txtFeedback.setTextColor(Color.RED)
        setOptionsEnabled(false)
        btnSubmit.visibility = View.GONE
        btnNext.visibility = View.VISIBLE
        answered = true
        updateProgress()
    }

    /** --- Navigation --- */
    private fun goToNextQuestion() {
        if (currentIndex < quizList.size - 1) currentIndex++ else showQuizSummary()
        showQuestion()
    }

    private fun goToPreviousQuestion() {
        if (currentIndex > 0) currentIndex--
        showQuestion()
    }

    /** --- Quiz Summary + Weak Question Review --- */
    private fun showQuizSummary() {
        val total = quizList.size
        val correctCount = quizList.count { it.isCorrect }
        val weakQuestions = quizList.filter { !it.isCorrect }

        val builder = AlertDialog.Builder(this)
            .setTitle("Quiz Completed")
            .setMessage("You scored $correctCount out of $total\nPercentage: ${correctCount * 100 / total}%")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                saveResultsToFirestore()
                saveWeakQuestions()
                finish()
            }
            .setCancelable(false)

        if (weakQuestions.isNotEmpty()) {
            builder.setNeutralButton("Review Weak Questions") { dialog, _ ->
                dialog.dismiss()
                reviewWeakQuestions(weakQuestions)
            }
        }
        builder.show()
    }

    /** --- Review Weak Questions --- */
    private fun reviewWeakQuestions(weakQuestions: List<QuizQuestion>) {
        quizList = ArrayList(weakQuestions.shuffled())
        currentIndex = 0
        score = 0
        answered = false
        readOnly = false
        countDownTimer?.cancel()
        Toast.makeText(this, "Reviewing ${weakQuestions.size} weak questions!", Toast.LENGTH_SHORT).show()
        updateProgress()
        showQuestion()
    }

    /** --- Save Weak Questions --- */
    private fun saveWeakQuestions() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val weakQuestions = quizList.filter { !it.isCorrect || it.timesWrong > 0 }

        for (q in weakQuestions) {
            val docRef = firestore.collection("users")
                .document(user.uid)
                .collection("weak_questions")
                .document("${q.topic}_${q.question.hashCode()}")

            val weakQuestionData = mapOf(
                "question" to q.question,
                "options" to q.options,
                "correctAnswer" to q.correctAnswer,
                "timesWrong" to q.timesWrong,
                "topic" to q.topic
            )

            docRef.set(weakQuestionData)
                .addOnSuccessListener { Log.d("Firestore", "Saved weak question: ${q.question} (timesWrong=${q.timesWrong})") }
                .addOnFailureListener { e -> Log.e("Firestore", "Failed to save", e) }
        }
    }

    /** --- Save Results to Firestore --- */
    private fun saveResultsToFirestore() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (quizTimestamp == 0L) return
        firestore.collection("study_history")
            .whereEqualTo("uid", user.uid)
            .whereEqualTo("timestamp", quizTimestamp)
            .get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot) {
                    firestore.collection("study_history")
                        .document(doc.id)
                        .update("quiz", quizList)
                }
            }
    }

    /** --- Timer --- */
    private fun startQuestionTimer() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(questionTimeMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                txtTimer.text = "Time left: ${millisUntilFinished / 1000}s"
            }
            override fun onFinish() { autoSubmit() }
        }.start()
    }

    /** --- Helpers --- */
    private fun setOptionsEnabled(enabled: Boolean) {
        for (i in 0 until radioGroup.childCount) radioGroup.getChildAt(i).isEnabled = enabled
    }

    private fun resetOptionColors() {
        val defaultColor = Color.BLACK
        optionA.setTextColor(defaultColor)
        optionB.setTextColor(defaultColor)
        optionC.setTextColor(defaultColor)
        optionD.setTextColor(defaultColor)
    }

    private fun updateProgress() {
        val percent = if (quizList.isNotEmpty()) ((currentIndex + 1) * 100 / quizList.size) else 0
        progressBar.progress = percent
        txtScore.text = if (readOnly) {
            "Score: ${quizList.count { it.isCorrect }} / ${quizList.size}"
        } else {
            "Score: $score / ${quizList.size}"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}

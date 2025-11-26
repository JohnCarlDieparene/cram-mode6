package com.labactivity.crammode

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.content.Intent

class FlashcardViewerActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnReveal: Button
    private lateinit var btnCorrect: Button
    private lateinit var btnIncorrect: Button
    private lateinit var btnReviewMistakes: Button

    private val firestore = FirebaseFirestore.getInstance()
    private val flashcardAttempts = mutableListOf<FlashcardAttempt>()
    private var studyDeck = arrayListOf<Flashcard>()
    private var sessionCompleted = false
    private var currentIndex = 0

    private var weakFlashcards = mutableListOf<Flashcard>()
    private var isReviewingWeakFlashcards = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flashcard_viewer)

        initUI()
        loadFlashcardsFromIntent()
    }

    private fun initUI() {
        viewPager = findViewById(R.id.viewPager)
        btnReveal = findViewById(R.id.btnReveal)
        btnCorrect = findViewById(R.id.btnCorrect)
        btnIncorrect = findViewById(R.id.btnIncorrect)
        btnReviewMistakes = findViewById(R.id.btnReviewMistakes)

        btnCorrect.visibility = View.GONE
        btnIncorrect.visibility = View.GONE
        btnReviewMistakes.visibility = View.GONE

        viewPager.isUserInputEnabled = false

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        btnReveal.setOnClickListener { revealAnswerForCurrentCard() }
        btnCorrect.setOnClickListener { recordAttempt(true) }
        btnIncorrect.setOnClickListener { recordAttempt(false) }
        btnReviewMistakes.setOnClickListener { loadWeakFlashcards() }
    }

    private fun loadFlashcardsFromIntent() {
        val flashcards = intent.getSerializableExtra("flashcards") as? ArrayList<Flashcard>
        if (flashcards.isNullOrEmpty()) {
            Toast.makeText(this, "No flashcards received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        studyDeck = flashcards.map { it.copy(interval = 1, lastSeenIndex = -1) } as ArrayList
        showNextCard()
    }

    private fun showNextCard() {
        if (studyDeck.isEmpty() || currentIndex >= studyDeck.size) {
            endSession()
            return
        }

        val nextCard = studyDeck[currentIndex]

        val adapter = FlashcardAdapter(arrayListOf(nextCard)) { isBack ->
            // Buttons reflect the side currently showing
            btnReveal.visibility = if (!isBack) View.VISIBLE else View.GONE
            btnCorrect.visibility = if (isBack) View.VISIBLE else View.GONE
            btnIncorrect.visibility = if (isBack) View.VISIBLE else View.GONE
        }

        viewPager.adapter = adapter
        viewPager.currentItem = 0
    }



    private fun revealAnswerForCurrentCard() {
        val adapter = viewPager.adapter as FlashcardAdapter
        val holder = (viewPager.getChildAt(0) as RecyclerView)
            .findViewHolderForAdapterPosition(0) as FlashcardAdapter.ViewHolder
        holder.revealAnswer()
    }

    private fun recordAttempt(isCorrect: Boolean) {
        val adapter = viewPager.adapter as FlashcardAdapter
        val currentFlashcard = adapter.getFlashcard(0)
        flashcardAttempts.add(FlashcardAttempt(currentFlashcard, isCorrect))

        if (isCorrect) currentFlashcard.interval *= 2 else currentFlashcard.interval = 1
        currentFlashcard.lastSeenIndex = currentIndex
        updateFlashcardStats(currentFlashcard, isCorrect)

        if (isReviewingWeakFlashcards) {
            if (isCorrect) weakFlashcards.remove(currentFlashcard)
            if (weakFlashcards.isEmpty()) { endSession(); return }
            else { studyDeck = ArrayList(weakFlashcards); currentIndex = 0 }
        } else currentIndex++

        showNextCard()
    }

    private fun endSession() {
        sessionCompleted = true
        val total = flashcardAttempts.size
        val correct = flashcardAttempts.count { it.isCorrect }
        val incorrect = total - correct
        val weakFlashcards = flashcardAttempts.filter { !it.isCorrect }.map { it.flashcard } as ArrayList

        val intent = Intent(this, FlashcardSummaryActivity::class.java)
        intent.putExtra("total", total)
        intent.putExtra("correct", correct)
        intent.putExtra("incorrect", incorrect)
        intent.putExtra("weakFlashcards", weakFlashcards)
        startActivity(intent)
        finish()
    }

    private fun updateFlashcardStats(flashcard: Flashcard, isCorrect: Boolean) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val docRef = firestore.collection("user_flashcard_stats")
            .document("${user.uid}-${flashcard.question}")

        firestore.runTransaction { txn ->
            val snap = txn.get(docRef)
            val total = (snap.getLong("totalAttempts") ?: 0) + 1
            val wrong = (snap.getLong("wrongAttempts") ?: 0) + if (!isCorrect) 1 else 0

            txn.set(
                docRef,
                mapOf(
                    "uid" to user.uid,
                    "question" to flashcard.question,
                    "answer" to flashcard.answer,
                    "totalAttempts" to total,
                    "wrongAttempts" to wrong,
                    "accuracy" to if (total > 0) 1f - (wrong.toFloat() / total) else 1f,
                    "lastReviewed" to System.currentTimeMillis()
                )
            )
        }
    }

    private fun loadWeakFlashcards() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        firestore.collection("user_flashcard_stats")
            .whereEqualTo("uid", user.uid)
            .get()
            .addOnSuccessListener { snap ->
                weakFlashcards = snap.documents.mapNotNull { doc ->
                    val total = doc.getLong("totalAttempts") ?: 0
                    val wrong = doc.getLong("wrongAttempts") ?: 0
                    if (wrong > 0 && total > 0) {
                        Flashcard(
                            question = doc.getString("question") ?: return@mapNotNull null,
                            answer = doc.getString("answer") ?: return@mapNotNull null,
                            interval = 1,
                            lastSeenIndex = -1
                        )
                    } else null
                }.toMutableList()

                if (weakFlashcards.isNotEmpty()) {
                    isReviewingWeakFlashcards = true
                    studyDeck = ArrayList(weakFlashcards)
                    currentIndex = 0
                    sessionCompleted = false
                    flashcardAttempts.clear()
                    showNextCard()
                    Toast.makeText(this, "You have ${weakFlashcards.size} weak flashcards to review!", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this, "No weak flashcards found.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load flashcards.", Toast.LENGTH_SHORT).show()
            }
    }

    data class FlashcardAttempt(val flashcard: Flashcard, val isCorrect: Boolean, val timestamp: Long = System.currentTimeMillis())
}

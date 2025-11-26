package com.labactivity.crammode

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class FlashcardAdapter(
    private val flashcards: List<Flashcard>,
    private val onSideChange: ((isBack: Boolean) -> Unit)? = null
) : RecyclerView.Adapter<FlashcardAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardFront: CardView = view.findViewById(R.id.cardFront)
        private val cardBack: CardView = view.findViewById(R.id.cardBack)
        private val questionText: TextView = view.findViewById(R.id.textQuestion)
        private val answerText: TextView = view.findViewById(R.id.textAnswer)
        private var isFront = true

        init {
            val flipListener = View.OnClickListener { flipCard() }
            cardFront.setOnClickListener(flipListener)
            cardBack.setOnClickListener(flipListener)
            questionText.setOnClickListener(flipListener)
            answerText.setOnClickListener(flipListener)
        }

        fun bind(flashcard: Flashcard) {
            questionText.text = "Q: ${flashcard.question}"
            answerText.text = "A: ${flashcard.answer}"

            cardFront.visibility = View.VISIBLE
            cardBack.visibility = View.GONE
            isFront = true
            cardFront.rotationY = 0f
            cardBack.rotationY = 0f

            onSideChange?.invoke(false) // Front side initially
        }

        fun revealAnswer() {
            if (isFront) flipCard() // Flip only if front
        }

        private fun flipCard() {
            val scale = cardFront.context.resources.displayMetrics.density
            cardFront.cameraDistance = 8000 * scale
            cardBack.cameraDistance = 8000 * scale

            val visibleCard = if (isFront) cardFront else cardBack
            val hiddenCard = if (isFront) cardBack else cardFront

            visibleCard.animate()
                .rotationY(90f)
                .setDuration(200)
                .withEndAction {
                    visibleCard.visibility = View.GONE
                    hiddenCard.visibility = View.VISIBLE
                    hiddenCard.rotationY = -90f
                    hiddenCard.animate()
                        .rotationY(0f)
                        .setDuration(200)
                        .withEndAction {
                            isFront = !isFront
                            onSideChange?.invoke(!isFront) // Notify activity which side is visible
                        }.start()
                }.start()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_flashcard, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(flashcards[position])

    override fun getItemCount() = flashcards.size
    fun getFlashcard(position: Int) = flashcards[position]
}

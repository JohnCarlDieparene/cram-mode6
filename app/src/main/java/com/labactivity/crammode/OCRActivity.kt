package com.labactivity.crammode

import android.app.Activity
import android.content.*
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.canhub.cropper.*
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.labactivity.crammode.utils.FlashcardUtils
import retrofit2.*
import androidx.activity.result.ActivityResultLauncher
import android.text.method.ScrollingMovementMethod
import android.widget.Scroller
import java.io.File
import java.io.FileOutputStream
import com.labactivity.crammode.CohereClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.labactivity.crammode.model.StudyHistory
import com.google.android.material.button.MaterialButton
import com.labactivity.crammode.utils.QuizUtils
import androidx.core.content.FileProvider
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat
import com.labactivity.crammode.model.QuizQuestion
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.app.AlertDialog
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen









class   OCRActivity : AppCompatActivity() {

    private lateinit var btnSelectImage: Button
    private lateinit var btnTakePhoto: Button
    private lateinit var btnSummarize: Button
    private lateinit var btnClear: Button
    private lateinit var btnCopyOcr: Button
    private lateinit var btnCopySummary: Button

    private lateinit var ocrResult: EditText
    private lateinit var txtSummary: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var spinnerLength: Spinner
    private lateinit var spinnerFormat: Spinner

    private lateinit var modeToggleGroup: MaterialButtonToggleGroup

    private lateinit var numberPickerQuizCount: NumberPicker

    private lateinit var spinnerTimePerQuestion: Spinner
    private lateinit var imagePreviewList: LinearLayout

    private lateinit var pdfPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var docxPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var spinnerLanguage: Spinner

    private lateinit var editTextFlashcardCount: EditText


    private var selectedLanguage = "English"
    private var customQuizCount: Int? = null

    private var selectedImageUri: Uri? = null
    private var currentRotation = 0f
    private val imageCropQueue = ArrayDeque<Uri>()


    private val selectedImageUris = mutableListOf<Uri>()
    private val ocrResultsList = mutableListOf<String>()
    private lateinit var btnAddToOcr: Button
    private lateinit var editTextQuizCount: EditText
    private lateinit var layoutQuizCount: LinearLayout


    private lateinit var cropLauncher: ActivityResultLauncher<CropImageContractOptions>
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>

    private var recognizedText: String = ""
    private var currentMode: Mode = Mode.SUMMARIZE

    enum class Mode { SUMMARIZE, FLASHCARDS, QUIZ }

    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ocr)


        // Bind views
        btnSelectImage = findViewById(R.id.btnSelectImage)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        btnSummarize = findViewById(R.id.btnSummarize)
        btnClear = findViewById(R.id.btnClear)
        btnCopyOcr = findViewById(R.id.btnCopyOcr)
        btnCopySummary = findViewById(R.id.btnCopySummary)
        ocrResult = findViewById(R.id.ocrResult)
        txtSummary = findViewById(R.id.txtSummary)
        progressBar = findViewById(R.id.progressBar)
        spinnerLength = findViewById(R.id.spinnerLength)
        spinnerFormat = findViewById(R.id.spinnerFormat)

        modeToggleGroup = findViewById(R.id.modeToggleGroup)

        spinnerTimePerQuestion = findViewById(R.id.spinnerTimePerQuestion)
        spinnerLanguage = findViewById(R.id.spinnerLanguage)
        layoutQuizCount = findViewById(R.id.layoutQuizCount)
        editTextQuizCount = findViewById(R.id.editTextQuizCount)

        editTextFlashcardCount = findViewById(R.id.editTextFlashcardCount)




        ocrResult.setScroller(Scroller(this))
        ocrResult.movementMethod = ScrollingMovementMethod()
        ocrResult.isVerticalScrollBarEnabled = true
        ocrResult.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }


        imagePreviewList = findViewById(R.id.imagePreviewList)











        findViewById<MaterialButton>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

















        spinnerLength.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Short", "Medium", "Long")
        )
        spinnerFormat.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                "Paragraph",
                "Bullets",
                "Numbered List",
                "Code Blocks",
                "Mixed (Bullets + Code)"
            )
        )


        val btnUserProfile: ImageButton = findViewById(R.id.btnUserProfile)

        btnUserProfile.setOnClickListener {
            val intent = Intent(this, UserProfileActivity::class.java)
            startActivity(intent)
        }


        val timeOptions = listOf("Easy (30s)", "Medium (20s)", "Hard (10s)")
        val adapterTime = ArrayAdapter(this, android.R.layout.simple_spinner_item, timeOptions)
        adapterTime.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTimePerQuestion.adapter = adapterTime


        val ocrResult = findViewById<EditText>(R.id.ocrResult)
        val btnSelectFile = findViewById<Button>(R.id.btnSelectFile)

        filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val uri = result.data?.data
                    uri?.let {
                        val mimeType = contentResolver.getType(it) ?: ""

                        when {
                            mimeType.contains("pdf") -> {
                                extractTextFromPdf(it)
                            }

                            mimeType.contains("officedocument.wordprocessingml") || uri.toString()
                                .endsWith(".docx") -> {
                                try {
                                    val file = uriToFile(it)
                                    val text = DocxTextExtractor.extractText(file)
                                    ocrResult.setText(text)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(
                                        this,
                                        "Failed to extract DOCX text.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }

                            else -> {
                                Toast.makeText(this, "Unsupported file type.", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                }
            }



        btnSelectFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(
                    Intent.EXTRA_MIME_TYPES, arrayOf(
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    )
                )
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            filePickerLauncher.launch(Intent.createChooser(intent, "Select PDF or DOCX file"))
        }


        spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                selectedLanguage = parent.getItemAtPosition(position).toString()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }


        // Toggle group selection
        modeToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                currentMode = when (checkedId) {
                    R.id.btnModeSummarize -> Mode.SUMMARIZE
                    R.id.btnModeFlashcards -> Mode.FLASHCARDS
                    R.id.btnModeQuiz -> Mode.QUIZ
                    else -> Mode.SUMMARIZE
                }
                updateModeUI()
            }
        }

        updateModeUI()

        pickImageLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val data = result.data

                    data?.clipData?.let { clipData ->
                        for (i in 0 until clipData.itemCount) {
                            imageCropQueue.add(clipData.getItemAt(i).uri)
                        }
                        processNextCrop()
                    }

                    data?.data?.let { uri ->
                        imageCropQueue.add(uri)
                        processNextCrop()
                    }
                }
            }



        cropLauncher = registerForActivityResult(CropImageContract()) { result ->
            if (result.isSuccessful) {
                result.uriContent?.let { uri ->
                    selectedImageUri = uri
                    selectedImageUris.add(uri)
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    addImageToPreviewList(bitmap)
                    runTextRecognition(bitmap)
                }
            } else {
                Toast.makeText(this, "Crop failed: ${result.error?.message}", Toast.LENGTH_SHORT)
                    .show()
            }

            // ✅ Move to next image in the queue
            processNextCrop()
        }



        btnTakePhoto.setOnClickListener {
            val cropOptions = CropImageContractOptions(
                uri = null,
                cropImageOptions = CropImageOptions(
                    guidelines = CropImageView.Guidelines.ON,
                    imageSourceIncludeCamera = true,
                    imageSourceIncludeGallery = false
                )
            )
            cropLauncher.launch(cropOptions)
        }

        btnSelectImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            pickImageLauncher.launch(Intent.createChooser(intent, "Select Pictures"))

        }

        btnSummarize.setOnClickListener {
            recognizedText = ocrResult.text.toString()
            if (recognizedText.isNotBlank()) {
                when (currentMode) {
                    Mode.SUMMARIZE -> summarizeText(recognizedText)
                    Mode.FLASHCARDS -> generateFlashcards(recognizedText)
                    Mode.QUIZ -> generateQuiz(recognizedText)
                }
            } else {
                txtSummary.text = "No text found to process."
            }
        }

        btnClear.setOnClickListener {
            ocrResult.setText("")
            txtSummary.text = ""

            imagePreviewList.removeAllViews()
            selectedImageUris.clear()
            ocrResultsList.clear()

        }

        val btnSelectFocus: Button = findViewById(R.id.btnSelectFocus)
        btnSelectFocus.setOnClickListener {
            showFocusSelectionDialog()
        }



        // Copy OCR Text
        btnCopyOcr.setOnClickListener {
            val ocrText = ocrResult.text.toString()
            if (ocrText.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("OCR Text", ocrText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "OCR text copied", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No OCR text to copy", Toast.LENGTH_SHORT).show()
            }
        }

// Copy Summary Text
        btnCopySummary.setOnClickListener {
            val summaryText = txtSummary.text.toString()
            if (summaryText.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Summary Text", summaryText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Summary copied", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No summary to copy", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun uriToFile(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri)!!
        val file = File.createTempFile("temp", ".docx", cacheDir)
        inputStream.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file
    }


    private fun extractTextFromPdf(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(cacheDir, "temp.pdf")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()

            val fileDescriptor =
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fileDescriptor)

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val textResults = mutableListOf<String>()

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)

                val bitmap = Bitmap.createBitmap(
                    page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val image = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        textResults.add(visionText.text)
                        ocrResult.setText(textResults.joinToString("\n\n"))
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "OCR failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }

            renderer.close()
            fileDescriptor.close()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to read PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


    private fun addImageToPreviewList(bitmap: Bitmap) {
        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                400 // You can adjust height here
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bitmap)
        }

        imageView.setOnClickListener {

            val tempUri = getImageUriFromBitmap(bitmap)

            if (tempUri == null) {
                Toast.makeText(this, "Failed to process image.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this@OCRActivity, FullscreenImageActivity::class.java)
            intent.putExtra("imageUri", tempUri.toString())
            startActivity(intent)
        }


        imagePreviewList.addView(imageView)
    }

    private fun getImageUriFromBitmap(bitmap: Bitmap): Uri? {
        return try {
            val file = File(cacheDir, "temp_${System.currentTimeMillis()}.jpg")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            out.flush()
            out.close()

            if (!file.exists()) {
                Log.e("OCR", "File not created: ${file.absolutePath}")
                return null
            }

            FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    private fun processNextCrop() {
        if (imageCropQueue.isNotEmpty()) {
            val nextUri = imageCropQueue.removeFirst()
            val cropOptions = CropImageContractOptions(
                nextUri,
                CropImageOptions(guidelines = CropImageView.Guidelines.ON)
            )
            cropLauncher.launch(cropOptions)
        }
    }


    private fun updateModeUI() {
        val layoutSummaryOptions = findViewById<LinearLayout>(R.id.layoutSummaryOptions)
        val layoutFlashcardCount = findViewById<LinearLayout>(R.id.layoutFlashcardCount)
        val labelResult = findViewById<TextView>(R.id.labelResult)
        val resultContainer = findViewById<ScrollView>(R.id.resultContainer)

        val layoutQuizTime = findViewById<LinearLayout>(R.id.layoutQuizTime)

        when (currentMode) {
            Mode.SUMMARIZE -> {
                btnSummarize.text = "Summarize Text"
                layoutSummaryOptions.visibility = View.VISIBLE
                layoutFlashcardCount.visibility = View.GONE
                labelResult.visibility = View.VISIBLE
                resultContainer.visibility = View.VISIBLE
                btnClear.visibility = View.VISIBLE
                btnCopyOcr.visibility = View.VISIBLE
                btnCopySummary.visibility = View.VISIBLE
                layoutQuizCount.visibility = View.GONE
                layoutQuizTime.visibility = View.GONE

            }

            Mode.FLASHCARDS -> {
                btnSummarize.text = "Generate Flashcards"
                layoutSummaryOptions.visibility = View.GONE
                layoutFlashcardCount.visibility = View.VISIBLE
                labelResult.visibility = View.GONE
                resultContainer.visibility = View.GONE
                btnClear.visibility = View.VISIBLE
                btnCopyOcr.visibility = View.VISIBLE
                btnCopySummary.visibility = View.VISIBLE
                layoutQuizCount.visibility = View.GONE
                layoutQuizTime.visibility = View.GONE
            }

            Mode.QUIZ -> {
                btnSummarize.text = "Generate Quiz"
                layoutSummaryOptions.visibility = View.GONE
                layoutFlashcardCount.visibility = View.GONE
                labelResult.visibility = View.GONE
                resultContainer.visibility = View.GONE
                btnClear.visibility = View.VISIBLE
                btnCopyOcr.visibility = View.VISIBLE
                btnCopySummary.visibility = View.VISIBLE
                layoutQuizCount.visibility = View.VISIBLE
                layoutQuizTime.visibility = View.VISIBLE
            }
        }
    }

    private fun runTextRecognition(bitmap: Bitmap) {
        progressBar.visibility = View.VISIBLE
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                recognizedText = visionText.text
                ocrResultsList.add(recognizedText)
                ocrResult.setText(ocrResultsList.joinToString("\n\n"))
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                ocrResult.setText("Failed to recognize text: ${e.message}")
                progressBar.visibility = View.GONE
            }
    }

    // ---------------- FOCUS AREAS ----------------
    private val focusOptions = arrayOf(
        "Definitions",
        "Formulas",
        "Main Concepts",
        "Examples",
        "Code Snippets",
        "Important Dates"
    )
    private val selectedFocus = BooleanArray(focusOptions.size) { false }

    private fun showFocusSelectionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Select Focus Areas")
            .setMultiChoiceItems(focusOptions, selectedFocus) { _, which, isChecked ->
                selectedFocus[which] = isChecked
            }
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun getSelectedFocusAreas(): String {
        val selected = mutableListOf<String>()
        for (i in focusOptions.indices) {
            if (selectedFocus[i]) selected.add(focusOptions[i])
        }
        return if (selected.isEmpty()) "main keywords and key concepts" else selected.joinToString(", ")
    }


    private var lastRequestTime = 0L
    private val COOLDOWN_MS = 6000L // 6 seconds cooldown between requests

    private fun canMakeRequest(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastRequestTime < COOLDOWN_MS) {
            Toast.makeText(this, "Please wait before making another request.", Toast.LENGTH_SHORT)
                .show()
            return false
        }
        lastRequestTime = now
        return true
    }

    data class WeightedFlashcard(
        val flashcard: Flashcard,
        val weight: Float
    )


    // ---------------- KEYWORD EXTRACTION WITH AI ----------------
    private fun extractKeywordsAI(text: String, callback: (List<String>) -> Unit) {
        val prompt = """
        Extract the most important keywords from the text below.
        Keywords must include:
        - Named entities (people, places, organizations, dates)
        - Core concepts (main subjects, objects)
        - Key verbs (critical actions)
        Exclude common articles, prepositions, and single letters.
        Return keywords as a comma-separated list.

        Text:
        $text
    """.trimIndent()

        val request = ChatRequest(
            model = "command-a-03-2025",
            messages = listOf(
                ChatMessage(
                    "system",
                    listOf(MessageContent(text = "You are a smart study assistant."))
                ),
                ChatMessage("user", listOf(MessageContent(text = prompt)))
            )
        )

        sendChatRequest(request) { reply ->
            val keywords =
                reply?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            callback(keywords)
        }
    }

    // ---------------- HIGHLIGHT FUNCTION ----------------
    private fun highlightKeywords(text: String, keywords: List<String>): SpannableString {
        val spannable = SpannableString(text)
        val color = ContextCompat.getColor(this, R.color.teal_200) // highlight color

        for (keyword in keywords) {
            var startIndex = text.indexOf(keyword, 0, ignoreCase = true)
            while (startIndex >= 0) {
                val endIndex = startIndex + keyword.length
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    startIndex,
                    endIndex,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    ForegroundColorSpan(color),
                    startIndex,
                    endIndex,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                startIndex = text.indexOf(keyword, endIndex, ignoreCase = true)
            }
        }
        return spannable
    }

    // ---------------- SUMMARIZATION ----------------
    private fun summarizeText(text: String) {
        val length = spinnerLength.selectedItem.toString().lowercase()
        val format = spinnerFormat.selectedItem.toString().lowercase()
        val focusAreas = getSelectedFocusAreas()

        progressBar.visibility = View.VISIBLE
        btnSummarize.isEnabled = false

        val systemPrompt = if (selectedLanguage == "Filipino") {
            """
        Ikaw ay isang AI study assistant. Buodin ang ibinigay na teksto sa malinaw at maikling paraan.
    Ituon ang buod sa: $focusAreas
    Haba: $length
    Format: $format
    • Kung may Code Snippets sa teksto:
      - Ipakita ang bawat snippet sa bullet point o code block depende sa napiling Format.
      - Panatilihin ang tamang indentation at syntax (ayusin ang maliit na error kung kinakailangan).
      - Magbigay ng 1–2 linya ng paliwanag bawat snippet.
    • Huwag gawing mahaba o paulit-ulit ang paliwanag.
    """.trimIndent()
        } else {
            """
         You are an AI study assistant. Summarize the given text clearly and concisely.
        Focus the summary on: $focusAreas
        Length: $length
        Format: $format
         If there are Code Snippets in the text:
    • Show each snippet in bullet points.
    • Keep proper indentation and code syntax.
    • Provide a short one- or two-line explanation for each snippet.
    """.trimIndent()
        }

        val userPrompt = if (selectedLanguage == "Filipino") {
            "Buodin ang tekstong ito sa pamamagitan ng pagtutok sa: $focusAreas\\n\\n$text"
        } else {
            "Summarize this text by focusing on: $focusAreas\\n\\n$text"
        }

        val request = ChatRequest(
            messages = listOf(
                ChatMessage("system", listOf(MessageContent(text = systemPrompt))),
                ChatMessage("user", listOf(MessageContent(text = userPrompt)))
            )
        )

        sendChatRequest(request) { reply ->
            val summary = reply?.replace("**", "")?.trim().takeIf { it?.isNotBlank() == true }
                ?: "No summary generated."

            // Extract keywords using AI
            extractKeywordsAI(summary) { keywords ->
                txtSummary.text = highlightKeywords(summary, keywords)
            }

            // Save to Firestore
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                val history = StudyHistory(
                    uid = user.uid,
                    type = "summary",
                    inputText = text,
                    resultText = summary,
                    timestamp = System.currentTimeMillis()
                )
                Firebase.firestore.collection("study_history").add(history)
            }
        }
    }

    private fun generateFlashcards(text: String) {

        val inputText = editTextFlashcardCount.text.toString()
        val count = inputText.toIntOrNull()

        if (count == null || count < 1 || count > 20) {
            Toast.makeText(this, "Please enter a number between 1 and 20.", Toast.LENGTH_SHORT)
                .show()
            return
        }

        val shortenedText = text.take(3000)

        progressBar.visibility = View.VISIBLE
        btnSummarize.isEnabled = false

        val systemPrompt = if (selectedLanguage == "Filipino") {
            "Ikaw ay isang AI tutor na lumilikha ng flashcards sa anyong Tanong at Sagot batay lamang sa pangunahing salita at mahahalagang konsepto."
        } else {
            "You are an AI tutor generating study flashcards in Q&A format based only on main keywords and key concepts."
        }

        val userPrompt = if (selectedLanguage == "Filipino") {
            """
        Gumawa ng eksaktong $count flashcards mula sa sumusunod na teksto.
        ⚠️ Format strictly:
        Q: [Tanong]
        A: [Sagot]
        --- (use this as delimiter between flashcards)
        Teksto:
        $shortenedText
        """.trimIndent()
        } else {
            """
        Create exactly $count flashcards from the following text.
        ⚠️ Format strictly:
        Q: [Question]
        A: [Answer]
        --- (use this as delimiter between flashcards)
        Text:
        $shortenedText
        """.trimIndent()
        }

        val request = ChatRequest(
            model = "command-a-03-2025",
            messages = listOf(
                ChatMessage("system", listOf(MessageContent(text = systemPrompt))),
                ChatMessage("user", listOf(MessageContent(text = userPrompt)))
            )
        )

        sendChatRequest(request) { reply ->

            // Log raw AI response
            Log.d("FlashcardAIReply", reply)

            val newFlashcards = try {
                val parsed = FlashcardUtils.parseFlashcards(reply).shuffled()

                // Log all parsed flashcards
                parsed.forEachIndexed { index, flashcard ->
                    Log.d("FlashcardParsed", "Flashcard ${index + 1}: Q=${flashcard.question}, A=${flashcard.answer}")
                }

                parsed
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Failed to parse flashcards.", Toast.LENGTH_LONG).show()
                }
                emptyList<Flashcard>()
            }

            if (newFlashcards.isEmpty()) {
                runOnUiThread {
                    txtSummary.text = reply
                    Toast.makeText(this, "No flashcards generated.", Toast.LENGTH_LONG).show()
                    progressBar.visibility = View.GONE
                    btnSummarize.isEnabled = true
                }
                return@sendChatRequest
            }

            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                runOnUiThread {
                    Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    btnSummarize.isEnabled = true
                }
                return@sendChatRequest
            }

            // Fetch past weak flashcards
            Firebase.firestore.collection("user_flashcard_stats")
                .whereEqualTo("uid", user.uid)
                .get()
                .addOnSuccessListener { snapshot ->
                    val weightedFlashcards = snapshot.documents.mapNotNull { doc ->
                        val question = doc.getString("question") ?: return@mapNotNull null
                        val answer = doc.getString("answer") ?: return@mapNotNull null
                        val total = (doc.getLong("totalAttempts") ?: 1).coerceAtLeast(1L)
                        val wrong = doc.getLong("wrongAttempts") ?: 0L
                        val weight = wrong.toFloat() / total.toFloat()
                        WeightedFlashcard(Flashcard(question, answer), weight)
                    }

                    // Keep only weak flashcards related to new AI-generated questions
                    val newQuestions = newFlashcards.map { it.question }.toSet()
                    val relevantWeightedFlashcards = weightedFlashcards
                        .filter { it.flashcard.question in newQuestions }
                        .sortedByDescending { it.weight }

                    // Merge weak + AI-generated flashcards
                    val finalFlashcards =
                        (relevantWeightedFlashcards.map { it.flashcard } + newFlashcards)
                            .distinctBy { it.question }
                            .take(count)

                    // Save final flashcards to Firestore
                    finalFlashcards.forEach { flashcard ->
                        val docRef = Firebase.firestore.collection("user_flashcard_stats")
                            .document("${user.uid}-${flashcard.question.hashCode()}")
                        docRef.set(
                            mapOf(
                                "uid" to user.uid,
                                "question" to flashcard.question,
                                "answer" to flashcard.answer,
                                "totalAttempts" to 0,
                                "wrongAttempts" to 0,
                                "accuracy" to 1.0,
                                "lastReviewed" to System.currentTimeMillis()
                            )
                        )
                    }

                    // Launch FlashcardViewer
                    runOnUiThread {
                        val intent = Intent(this, FlashcardViewerActivity::class.java)
                        intent.putExtra("flashcards", ArrayList(finalFlashcards))
                        startActivity(intent)

                        progressBar.visibility = View.GONE
                        btnSummarize.isEnabled = true
                    }
                }
                .addOnFailureListener {
                    runOnUiThread {
                        Toast.makeText(this, "Failed to load past flashcards.", Toast.LENGTH_SHORT)
                            .show()
                        progressBar.visibility = View.GONE
                        btnSummarize.isEnabled = true
                    }
                }
        }
    }









    private fun generateQuiz(text: String) {
        val inputText = editTextQuizCount.text.toString()
        val count = inputText.toIntOrNull()
        if (count == null || count < 1 || count > 30) {
            Toast.makeText(this, "Please enter a number between 1 and 30.", Toast.LENGTH_SHORT).show()
            return
        }

        val shortenedText = text.take(3000)
        progressBar.visibility = View.VISIBLE
        btnSummarize.isEnabled = false

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedTimeText = spinnerTimePerQuestion.selectedItem.toString()
        val timeOption = when {
            selectedTimeText.contains("30") -> "easy"
            selectedTimeText.contains("20") -> "medium"
            selectedTimeText.contains("10") -> "hard"
            else -> "medium"
        }

        val systemPrompt = if (selectedLanguage == "Filipino") {
            """
        Ikaw ay isang AI quiz generator. Gumawa ng eksaktong $count multiple-choice na tanong mula sa pangunahing salita at mahahalagang konsepto ng teksto.
        Bawat tanong AY DAPAT may paksa.
        Gamitin ang eksaktong format na ito:
        
        Tanong: <question text>
        A. <choice1>
        B. <choice2>
        C. <choice3>
        D. <choice4>
        Sagot: <tamang letra A-D>
        """.trimIndent()
        } else {
            """
        You are an AI quiz generator. Generate exactly $count multiple-choice questions based on main keywords and key concepts.
        Each question MUST include a topic.
        Use this exact format:
        
        Question: <question text>
        A. <choice1>
        B. <choice2>
        C. <choice3>
        D. <choice4>
        Answer: <correct letter A-D>
        """.trimIndent()
        }

        val userPrompt = if (selectedLanguage == "Filipino") {
            "Gumawa ng $count tanong mula sa pangunahing salita at mahahalagang konsepto ng tekstong ito:\n\n$shortenedText\n\nSundin ang format."
        } else {
            "Generate $count questions from the main keywords and key concepts of this text:\n\n$shortenedText\n\nFollow the format exactly."
        }

        val request = ChatRequest(
            messages = listOf(
                ChatMessage("system", listOf(MessageContent(text = systemPrompt))),
                ChatMessage("user", listOf(MessageContent(text = userPrompt)))
            )
        )

        sendChatRequest(request) { reply ->
            Log.d("QuizAIReply", reply)

            val cleanedReply = reply.replace("**", "")
                .replace("#", "")
                .replace(Regex("\\n{3,}"), "\n\n")

            // Parse all questions
            val questions = QuizUtils.parseQuizQuestions(cleanedReply).take(count)

            if (questions.isEmpty()) {
                Toast.makeText(this, "No quiz questions could be generated.", Toast.LENGTH_LONG).show()
                progressBar.visibility = View.GONE
                btnSummarize.isEnabled = true
                return@sendChatRequest
            }

            // Save used questions
            val batch = Firebase.firestore.batch()
            questions.filter { !it.question.startsWith("Option") }.forEach { q ->
                val docRef = Firebase.firestore.collection("users")
                    .document(user.uid)
                    .collection("used_questions")
                    .document(q.question.hashCode().toString())
                batch.set(docRef, mapOf("question" to q.question))
            }
            batch.commit()

            // Save history
            val history = StudyHistory(
                uid = user.uid,
                type = "quiz",
                inputText = text,
                resultText = cleanedReply,
                timestamp = System.currentTimeMillis(),
                quiz = ArrayList(questions)
            )
            Firebase.firestore.collection("study_history").add(history)

            // Launch QuizViewer
            val intent = Intent(this, QuizViewerActivity::class.java)
            intent.putParcelableArrayListExtra("quizQuestions", ArrayList(questions))
            intent.putExtra("timestamp", history.timestamp)
            intent.putExtra("readOnly", false)
            intent.putExtra("quizCount", count)
            intent.putExtra("timePerQuestion", timeOption)
            startActivity(intent)

            progressBar.visibility = View.GONE
            btnSummarize.isEnabled = true
        }
    }













    // ---------------- COMMON FUNCTION ----------------
    private fun sendChatRequest(request: ChatRequest, onResult: (String) -> Unit) {
        CohereClient.api.chat("Bearer ${BuildConfig.COHERE_API_KEY}", request)
            .enqueue(object : Callback<ChatResponse> {
                override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                    progressBar.visibility = View.GONE
                    btnSummarize.isEnabled = true

                    if (response.isSuccessful) {
                        val reply = response.body()
                            ?.message
                            ?.content
                            ?.joinToString("\n") { it.text.trim() }
                            ?.takeIf { it.isNotBlank() }
                            ?: ""

                        onResult(reply)
                    } else {
                        val error = response.errorBody()?.string()
                        Log.e("CohereDebug", "Error ${response.code()} $error")
                        Toast.makeText(this@OCRActivity, "API Error ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    btnSummarize.isEnabled = true
                    Log.e("CohereDebug", "API call failed", t)
                    Toast.makeText(this@OCRActivity, "API call failed: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }
}
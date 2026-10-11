package com.chatai.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var memory: MemoryStore
    private lateinit var ai: AIClient
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var voiceButton: Button
    private val prefs by lazy { getSharedPreferences("chat_ai_settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        memory = MemoryStore(this)
        ai = AIClient().apply {
            endpoint = prefs.getString("endpoint", "") ?: ""
            apiKey = prefs.getString("api_key", "") ?: ""
            model = prefs.getString("model", "") ?: ""
        }
        tts = TextToSpeech(this, this)
        buildUi()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 24, 24, 20)
            setBackgroundColor(Color.BLACK)
        }
        val top = LinearLayout(this).apply { gravity = Gravity.END; orientation = LinearLayout.HORIZONTAL }
        top.addView(button("Settings") { showSettings() })
        top.addView(button("Memory") { showMemory() })
        root.addView(top, LinearLayout.LayoutParams(-1, -2))

        val icon = TextView(this).apply {
            text = "◉"
            textSize = 68f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        root.addView(icon, LinearLayout.LayoutParams(-1, 0, 1f))
        status = TextView(this).apply {
            text = "Chat AI • Ready"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        transcript = TextView(this).apply {
            text = "Tap the microphone to talk. Configure an AI endpoint in Settings."
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(8, 18, 8, 18)
        }
        root.addView(transcript, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        voiceButton = button("🎙 Talk") { startListening() }
        actions.addView(voiceButton)
        actions.addView(button("Read screen") { readScreen() })
        root.addView(actions, LinearLayout.LayoutParams(-1, -2))
        root.addView(button("Type message") { showMessageInput() }, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(Color.WHITE)
        setBackgroundColor(0xFF242424.toInt())
        setOnClickListener { action() }
    }

    private fun showSettings() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        fun field(hint: String, value: String, secret: Boolean = false): EditText {
            return EditText(this).apply {
                this.hint = hint
                setText(value)
                setSingleLine(true)
                if (secret) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                layout.addView(this, LinearLayout.LayoutParams(-1, -2))
            }
        }
        val endpoint = field("HTTPS chat completions endpoint", ai.endpoint)
        val model = field("Model name", ai.model)
        val key = field("API key", ai.apiKey, true)
        AlertDialog.Builder(this).setTitle("Chat AI settings").setView(layout)
            .setPositiveButton("Save") { _, _ ->
                ai.endpoint = endpoint.text.toString().trim()
                ai.model = model.text.toString().trim()
                ai.apiKey = key.text.toString().trim()
                prefs.edit().putString("endpoint", ai.endpoint).putString("model", ai.model).putString("api_key", ai.apiKey).apply()
                status.text = "Chat AI • Settings saved"
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showMemory() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 8, 20, 8) }
        val notes = EditText(this).apply {
            hint = "Personal notes for Chat AI"
            setText(memory.userNotes())
            minLines = 3
            gravity = Gravity.TOP
        }
        layout.addView(notes, LinearLayout.LayoutParams(-1, -2))
        val recent = TextView(this).apply { text = "Stored memory\n" + memory.readAll().take(3500); setTextColor(Color.DKGRAY) }
        val scroll = ScrollView(this).apply { addView(recent) }
        layout.addView(scroll, LinearLayout.LayoutParams(-1, 300))
        AlertDialog.Builder(this).setTitle("Memory").setView(layout)
            .setPositiveButton("Save notes") { _, _ -> memory.saveUserNotes(notes.text.toString()); Toast.makeText(this, "Memory saved", Toast.LENGTH_SHORT).show() }
            .setNegativeButton("Close", null).show()
    }

    private fun showMessageInput() {
        val input = EditText(this).apply { hint = "Ask Chat AI anything"; minLines = 2 }
        AlertDialog.Builder(this).setTitle("Message Chat AI").setView(input)
            .setPositiveButton("Send") { _, _ -> ask(input.text.toString()) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition is unavailable on this device.", Toast.LENGTH_LONG).show(); return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41); return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { status.text = "Listening…" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status.text = "Thinking…" }
                override fun onError(error: Int) { status.text = "Chat AI • Ready"; Toast.makeText(this@MainActivity, "Didn't catch that. Try again.", Toast.LENGTH_SHORT).show() }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) ask(text) else status.text = "Chat AI • Ready"
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            recognizer.startListening(intent)
        }
    }

    private fun readScreen() {
        val service = ScreenAccessService.instance
        val text = service?.refreshScreenText() ?: "Enable Chat AI in Android Settings → Accessibility to read the current app."
        transcript.text = text.take(5000)
        status.text = "Screen text captured"
    }

    private fun ask(message: String) {
        if (message.isBlank()) return
        transcript.text = "You: $message\n\nChat AI is thinking…"
        status.text = "Thinking…"
        memory.add("recent", "User: $message")
        val screen = ScreenAccessService.latestScreenText.take(7000)
        val context = "User notes:\n${memory.userNotes()}\nMemory:\n${memory.readAll().take(5000)}\nVisible screen text:\n$screen"
        worker.execute {
            try {
                val response = ai.reply(message, context)
                memory.add("recent", "Chat AI: $response")
                runOnUiThread {
                    transcript.text = "You: $message\n\nChat AI: $response"
                    status.text = "Chat AI • Ready"
                    tts?.speak(response, TextToSpeech.QUEUE_FLUSH, null, "chat-ai-reply")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    transcript.text = "${e.message ?: "Something went wrong."}\n\nOpen Settings to check the HTTPS endpoint, model and API key."
                    status.text = "Chat AI • Needs attention"
                }
            }
        }
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.stop(); tts?.shutdown()
        worker.shutdownNow()
        super.onDestroy()
    }
}

package psiolent.caliper.integrations

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * A dictation session that stays open until [stop] is called, so pausing to think
 * does not end it. If the recognizer finishes a segment on its own — which it does
 * on silence regardless of the timeout hints — the session restarts and appends
 * instead of closing.
 *
 * Must be created and driven from the main thread, as [SpeechRecognizer] requires.
 */
class VoiceInput(
    context: Context,
    private val onText: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
) : RecognitionListener {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var text = ""
    private var errorStreak = 0

    fun start(initialText: String = "") {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("No speech recognition is available on this device.")
            return
        }
        text = initialText
        errorStreak = 0
        active = true
        onText(text)
        onListeningChanged(true)
        listen()
    }

    fun stop() {
        active = false
        onListeningChanged(false)
        recognizer?.stopListening()
    }

    fun release() {
        active = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
    }

    private fun listen() {
        val recognizer = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            it.setRecognitionListener(this)
            recognizer = it
        }
        recognizer.startListening(intent())
    }

    private fun restartSoon() {
        if (!active) return
        main.postDelayed({ if (active) listen() }, RESTART_DELAY_MS)
    }

    private fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        // Hints only; the recognizer may ignore them, which is why we also restart.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 0L)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val heard = partialResults.firstResult()
        if (heard.isNotBlank()) onText(join(text, heard))
    }

    override fun onResults(results: Bundle?) {
        errorStreak = 0
        val heard = results.firstResult()
        if (heard.isNotBlank()) {
            text = join(text, heard)
            onText(text)
        }
        restartSoon()
    }

    override fun onError(error: Int) {
        errorStreak++
        if (active && errorStreak < MAX_ERROR_STREAK) {
            restartSoon()
            return
        }
        active = false
        onListeningChanged(false)
        onError(messageFor(error))
    }

    override fun onEndOfSpeech() = Unit
    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun Bundle?.firstResult(): String =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private fun join(a: String, b: String): String =
        listOf(a.trim(), b.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    private fun messageFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "There was a problem with the microphone."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice input."
        SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs a network connection."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition is busy; try again."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything."
        else -> "Voice input stopped (error $error)."
    }

    private companion object {
        const val RESTART_DELAY_MS = 400L
        const val SILENCE_MS = 10_000L
        const val MAX_ERROR_STREAK = 3
    }
}

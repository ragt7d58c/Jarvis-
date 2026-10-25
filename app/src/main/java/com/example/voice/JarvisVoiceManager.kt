package com.example.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.data.GeminiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class JarvisVoiceManager(
    private val context: Context,
    private val geminiService: GeminiService
) {
    companion object {
        private const val TAG = "JarvisVoiceManager"
        private const val UTTERANCE_WAKE_ACK = "utterance_wake_ack"
        private const val UTTERANCE_RESPONSE = "utterance_response"
        private const val WAKE_ACK_TEXT = "हाँ सर!"

        private val WAKE_PATTERNS = listOf(
            "hey jarvis", "hey jarvis", "hay jarvis", "hi jarvis", "hello jarvis",
            "ok jarvis", "jarvis", "jarvis please", "हे जार्विस", "हे जारविस",
            "जार्विस", "जारविस", "सुनो जार्विस", "नमस्ते जार्विस"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<JarvisVoiceState>(JarvisVoiceState.Standby)
    val state: StateFlow<JarvisVoiceState> = _state.asStateFlow()

    private val _audioRms = MutableStateFlow(0f)
    val audioRms: StateFlow<Float> = _audioRms.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private val isWakingOrSpeaking = AtomicBoolean(false)
    private var isDestroyed = false
    private var pendingFollowUpCommand: String? = null

    init {
        initTts()
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureHindiMaleVoice()
                isTtsReady = true
                Log.d(TAG, "TTS initialized successfully")
            } else {
                Log.e(TAG, "TTS initialization failed with status $status")
            }
        }

        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS started utterance: $utteranceId")
                _audioRms.value = 0.8f
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS completed utterance: $utteranceId")
                mainHandler.post {
                    _audioRms.value = 0f
                    when (utteranceId) {
                        UTTERANCE_WAKE_ACK -> {
                            isWakingOrSpeaking.set(false)
                            val command = pendingFollowUpCommand
                            pendingFollowUpCommand = null
                            if (!command.isNullOrBlank()) {
                                processCommandWithGemini(command)
                            } else {
                                startListeningForCommand()
                            }
                        }
                        UTTERANCE_RESPONSE -> {
                            isWakingOrSpeaking.set(false)
                            // After replying, settle into continuous wake-word standby mode
                            scope.launch {
                                delay(600)
                                if (!isDestroyed && !isWakingOrSpeaking.get()) {
                                    startStandbyListening()
                                }
                            }
                        }
                        else -> {
                            isWakingOrSpeaking.set(false)
                            startStandbyListening()
                        }
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error on utterance: $utteranceId")
                mainHandler.post {
                    isWakingOrSpeaking.set(false)
                    startStandbyListening()
                }
            }
        })
    }

    /**
     * Configures the natural Hindi male voice cadence and audio attributes.
     */
    private fun configureHindiMaleVoice() {
        val tts = textToSpeech ?: return
        val hindiLocale = Locale("hi", "IN")
        val result = tts.setLanguage(hindiLocale)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        tts.setAudioAttributes(audioAttributes)

        try {
            val voices = tts.voices
            if (voices != null && voices.isNotEmpty()) {
                // Look for high-quality Hindi male voice
                val hindiVoice = voices.firstOrNull { voice ->
                    voice.locale.language.equals("hi", ignoreCase = true) &&
                            (voice.name.contains("male", ignoreCase = true) ||
                             voice.name.contains("in-hi", ignoreCase = true))
                } ?: voices.firstOrNull { voice ->
                    voice.locale.language.equals("hi", ignoreCase = true)
                }

                if (hindiVoice != null) {
                    tts.voice = hindiVoice
                    Log.d(TAG, "Selected TTS voice: ${hindiVoice.name}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying TTS voices", e)
        }

        // Slightly deeper, crisp JARVIS tone and crisp speed
        tts.setPitch(0.92f)
        tts.setSpeechRate(1.08f)
    }

    /**
     * Start continuous hands-free wake word listening in the foreground.
     */
    fun startStandbyListening() {
        if (isDestroyed || isWakingOrSpeaking.get()) return

        mainHandler.post {
            try {
                safeDestroySpeechRecognizer()
                _state.value = JarvisVoiceState.Standby

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer = recognizer

                recognizer.setRecognitionListener(object : SimpleRecognitionListener() {
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            for (text in matches) {
                                val match = checkWakeWordMatch(text)
                                if (match != null) {
                                    triggerWakeActivation(match.extractedCommand)
                                    break
                                }
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            for (text in matches) {
                                val match = checkWakeWordMatch(text)
                                if (match != null) {
                                    triggerWakeActivation(match.extractedCommand)
                                    return
                                }
                            }
                        }
                        // If no wake word matched, silently restart standby listening
                        restartStandbyAfterSilence()
                    }

                    override fun onError(error: Int) {
                        Log.d(TAG, "Standby listener error: $error")
                        // In standby, NO_MATCH or SPEECH_TIMEOUT are normal silence events
                        restartStandbyAfterSilence()
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Keep RMS subtle in standby
                        _audioRms.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 0.4f)
                    }
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "en-US"))
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

                recognizer.startListening(intent)
                Log.d(TAG, "Standby listening started")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting standby speech recognition", e)
                restartStandbyAfterSilence()
            }
        }
    }

    private fun restartStandbyAfterSilence() {
        if (isDestroyed || isWakingOrSpeaking.get()) return
        mainHandler.postDelayed({
            if (!isDestroyed && !isWakingOrSpeaking.get() && _state.value == JarvisVoiceState.Standby) {
                startStandbyListening()
            }
        }, 200)
    }

    private data class WakeMatch(val extractedCommand: String?)

    private fun checkWakeWordMatch(rawText: String): WakeMatch? {
        val lower = rawText.lowercase().trim()
        for (pattern in WAKE_PATTERNS) {
            val index = lower.indexOf(pattern)
            if (index != -1) {
                val after = lower.substring(index + pattern.length).trim()
                val command = if (after.length >= 3) after else null
                return WakeMatch(command)
            }
        }
        return null
    }

    /**
     * Instantly acknowledges wake word with Hindi male voice "हाँ सर!" and transitions.
     * Prevents duplicate wake activations.
     */
    fun triggerWakeActivation(immediateCommand: String? = null) {
        if (!isWakingOrSpeaking.compareAndSet(false, true)) {
            Log.d(TAG, "Wake already in progress, ignoring duplicate trigger")
            return
        }

        safeDestroySpeechRecognizer()
        pendingFollowUpCommand = immediateCommand
        _state.value = JarvisVoiceState.Waking
        _audioRms.value = 0.6f

        // Speak instant natural Hindi male voice "हाँ सर!"
        speakTts(WAKE_ACK_TEXT, UTTERANCE_WAKE_ACK)
    }

    /**
     * Listen for the user's command query with high sensitivity and RMS waveform tracking.
     */
    private fun startListeningForCommand() {
        if (isDestroyed) return

        mainHandler.post {
            try {
                safeDestroySpeechRecognizer()
                _state.value = JarvisVoiceState.ListeningCommand

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer = recognizer

                recognizer.setRecognitionListener(object : SimpleRecognitionListener() {
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spokenQuery = matches?.firstOrNull()?.trim()
                        if (!spokenQuery.isNullOrBlank()) {
                            processCommandWithGemini(spokenQuery)
                        } else {
                            // If no command caught, speak brief acknowledgement and standby
                            speakQuickReplyAndStandby("जी सर, कोई आदेश?")
                        }
                    }

                    override fun onError(error: Int) {
                        Log.d(TAG, "Command listening error: $error")
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            speakQuickReplyAndStandby("सर, मैंने सुना नहीं।")
                        } else {
                            startStandbyListening()
                        }
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Normalize -2dB..10dB to 0.0..1.0 for real-time reactive HUD
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _audioRms.value = normalized
                    }
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "en-US"))
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

                recognizer.startListening(intent)
                Log.d(TAG, "Listening for command started")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting command speech recognition", e)
                startStandbyListening()
            }
        }
    }

    /**
     * Sub-2-Second processing: sends query to Gemini and initiates vocal reply immediately.
     */
    private fun processCommandWithGemini(userQuery: String) {
        safeDestroySpeechRecognizer()
        _state.value = JarvisVoiceState.Processing
        _audioRms.value = 0.3f
        isWakingOrSpeaking.set(true)

        scope.launch {
            val startTime = System.currentTimeMillis()
            val answer = geminiService.askJarvis(userQuery)
            val durationMs = System.currentTimeMillis() - startTime
            Log.d(TAG, "Gemini response received in ${durationMs}ms: $answer")

            _state.value = JarvisVoiceState.Speaking
            speakTts(answer, UTTERANCE_RESPONSE)
        }
    }

    private fun speakQuickReplyAndStandby(text: String) {
        safeDestroySpeechRecognizer()
        _state.value = JarvisVoiceState.Speaking
        isWakingOrSpeaking.set(true)
        speakTts(text, UTTERANCE_RESPONSE)
    }

    private fun speakTts(text: String, utteranceId: String) {
        val tts = textToSpeech
        if (tts != null && isTtsReady) {
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } else {
            Log.w(TAG, "TTS not ready to speak '$text'")
            isWakingOrSpeaking.set(false)
            startStandbyListening()
        }
    }

    /**
     * Manual trigger when user taps the HUD Arc Reactor center.
     */
    fun triggerManualWake() {
        if (_state.value == JarvisVoiceState.Speaking || _state.value == JarvisVoiceState.Processing) {
            // If already speaking, tap interrupts and listens for a new command
            textToSpeech?.stop()
            isWakingOrSpeaking.set(false)
            startListeningForCommand()
        } else {
            triggerWakeActivation(null)
        }
    }

    private fun safeDestroySpeechRecognizer() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer", e)
        } finally {
            speechRecognizer = null
        }
    }

    fun stop() {
        safeDestroySpeechRecognizer()
        textToSpeech?.stop()
        isWakingOrSpeaking.set(false)
        _state.value = JarvisVoiceState.Standby
        _audioRms.value = 0f
    }

    fun destroy() {
        isDestroyed = true
        scope.cancel()
        safeDestroySpeechRecognizer()
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS", e)
        }
        textToSpeech = null
    }
}

/**
 * Convenience abstract adapter so listeners only implement what is needed.
 */
abstract class SimpleRecognitionListener : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onError(error: Int) {}
    override fun onResults(results: Bundle?) {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}

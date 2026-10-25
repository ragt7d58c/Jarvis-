package com.example.voice

sealed class JarvisVoiceState(val statusLabel: String) {
    object Standby : JarvisVoiceState("STANDBY // AWAITING 'HEY JARVIS'")
    object Waking : JarvisVoiceState("ACTIVATED // HAAN SIR!")
    object ListeningCommand : JarvisVoiceState("LISTENING // RECEIVING COMMAND")
    object Processing : JarvisVoiceState("COMPUTING // GEMINI NEURAL CORE")
    object Speaking : JarvisVoiceState("SPEAKING // VOCAL INTERFACE")
    data class Error(val message: String) : JarvisVoiceState("DIAGNOSTIC // RE-ALIGNING")
}

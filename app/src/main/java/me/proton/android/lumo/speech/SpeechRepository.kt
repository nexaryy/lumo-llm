package me.proton.android.lumo.speech

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the speech recognition pipeline with the new native chat UI.
 *
 * Previously, transcribed text was injected into the Proton WebView via `WebAppInterface.injectSpeechOutput`.
 * Now that the WebView is gone, we expose the text as a SharedFlow so that any active chat input
 * can collect it and pre-fill the text field.
 */
interface SpeechRepository {
    fun injectText(spokenText: String)
    fun emittedText(): SharedFlow<String>
}

@Singleton
class SpeechRepositoryImpl @Inject constructor() : SpeechRepository {

    private val _emittedText = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun injectText(spokenText: String) {
        // Strip the surrounding quotes that the old WebView bridge expected.
        val cleaned = spokenText
            .removeSurrounding("\"")
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\\\", "\\")
        _emittedText.tryEmit(cleaned)
    }

    override fun emittedText(): SharedFlow<String> = _emittedText.asSharedFlow()
}

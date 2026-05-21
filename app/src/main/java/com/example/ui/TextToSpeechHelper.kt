package com.example.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TextToSpeechHelper(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Language is not supported")
            } else {
                isInitialized = true
            }
        } else {
            Log.e("TTS", "Initialization failed")
        }
    }

    fun speak(text: String, languageCode: String? = "en") {
        if (!isInitialized) {
            Log.e("TTS", "Not initialized yet")
            return
        }
        val locale = when (languageCode?.lowercase()?.take(2)) {
            "es" -> Locale("es", "ES")
            "fr" -> Locale("fr", "FR")
            "de" -> Locale("de", "DE")
            "zh" -> Locale("zh", "CN")
            "ja" -> Locale("ja", "JP")
            "ko" -> Locale("ko", "KR")
            "hi" -> Locale("hi", "IN")
            "pt" -> Locale("pt", "PT")
            "it" -> Locale("it", "IT")
            "ru" -> Locale("ru", "RU")
            "ar" -> Locale("ar", "AE")
            else -> Locale.US
        }
        tts?.language = locale
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TranslationSpeak")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AppDatabase
import com.example.data.Translation
import com.example.data.TranslationRepository
import com.example.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class SupportedLanguage(val name: String, val code: String)

class TranslatorViewModel(application: Application) : AndroidViewModel(application) {

    val languagesList = listOf(
        SupportedLanguage("English", "en"),
        SupportedLanguage("Spanish", "es"),
        SupportedLanguage("French", "fr"),
        SupportedLanguage("German", "de"),
        SupportedLanguage("Chinese (Simplified)", "zh"),
        SupportedLanguage("Japanese", "ja"),
        SupportedLanguage("Korean", "ko"),
        SupportedLanguage("Hindi", "hi"),
        SupportedLanguage("Portuguese", "pt"),
        SupportedLanguage("Italian", "it"),
        SupportedLanguage("Russian", "ru"),
        SupportedLanguage("Arabic", "ar")
    )

    private val database = AppDatabase.getDatabase(application)
    private val repository = TranslationRepository(database.translationDao())

    val history: StateFlow<List<Translation>> = repository.allTranslations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val ttsHelper = TextToSpeechHelper(application)

    private val _targetLanguage = MutableStateFlow(languagesList[1]) // Default to Spanish
    val targetLanguage: StateFlow<SupportedLanguage> = _targetLanguage.asStateFlow()

    private val _sourceLanguage = MutableStateFlow("Auto Detect")
    val sourceLanguage: StateFlow<String> = _sourceLanguage.asStateFlow()

    private val _translationResult = MutableStateFlow<TranslationResult?>(null)
    val translationResult: StateFlow<TranslationResult?> = _translationResult.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isFlashOn = MutableStateFlow(false)
    val isFlashOn: StateFlow<Boolean> = _isFlashOn.asStateFlow()

    fun setTargetLanguage(language: SupportedLanguage) {
        _targetLanguage.value = language
    }

    fun setSourceLanguage(langName: String) {
        _sourceLanguage.value = langName
    }

    fun toggleFlash() {
        _isFlashOn.value = !_isFlashOn.value
    }

    fun speakTranslatedText() {
        val result = _translationResult.value
        if (result != null && result.translatedText.isNotBlank()) {
            ttsHelper.speak(result.translatedText, _targetLanguage.value.code)
        }
    }

    fun stopSpeaking() {
        ttsHelper.stop()
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    fun translateImage(bitmap: Bitmap) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            _errorMessage.value = "Gemini API Key is missing. Please set your key using the Secrets panel in AI Studio."
            return
        }

        _isLoading.value = true
        _errorMessage.value = null
        _translationResult.value = null

        viewModelScope.launch {
            try {
                val base64Image = withContext(Dispatchers.IO) {
                    bitmap.toBase64()
                }

                val prompt = """
                    You are an expert live camera translation and OCR assistant.
                    Analyze this image and translate ALL text detected into ${_targetLanguage.value.name}. 
                    Identify key blocks of text and their approximate relative coordinates as integer percentages (0 to 100) on the image canvas (x, y, w, h). 
                    - 'x' is distance from the left edge (0 to 100)
                    - 'y' is distance from the top edge (0 to 100)
                    - 'w' is segment width relative to overall width (0 to 100)
                    - 'h' is segment height relative to overall height (0 to 100)
                    
                    Return ONLY a JSON object with this exact structure:
                    {
                      "detectedLanguage": "Name of the source language detected, e.g., French",
                      "translatedText": "The complete, consolidated translation of all text in ${_targetLanguage.value.name}",
                      "originalText": "The complete, consolidated original text extracted from the image",
                      "phrases": [
                        {
                          "original": "original phrase segment in source language",
                          "translated": "translated phrase segment in ${_targetLanguage.value.name}",
                          "x": 20,
                          "y": 15,
                          "w": 65,
                          "h": 12
                        }
                      ]
                    }
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(
                                Part(text = prompt),
                                Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                            )
                        )
                    ),
                    generationConfig = GenerationConfig(responseMimeType = "application/json")
                )

                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.service.generateContent(apiKey, request)
                }

                val rawJsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (rawJsonText != null) {
                    val result = withContext(Dispatchers.Default) {
                        try {
                            RetrofitClient.moshi.adapter(TranslationResult::class.java).fromJson(rawJsonText)
                        } catch (e: Exception) {
                            Log.e("TranslatorVM", "Failed to parse JSON response: $rawJsonText", e)
                            null
                        }
                    }

                    if (result != null) {
                        _translationResult.value = result
                        _sourceLanguage.value = result.detectedLanguage

                        // Save this translation to Room local database history
                        withContext(Dispatchers.IO) {
                            repository.insert(
                                Translation(
                                    originalText = result.originalText,
                                    translatedText = result.translatedText,
                                    sourceLanguage = result.detectedLanguage,
                                    targetLanguage = _targetLanguage.value.name
                                )
                            )
                        }
                    } else {
                        // Attempt fallback extraction if JSON was correct but parsing failed
                        _errorMessage.value = "Failed to parse API metadata response. Raw text: " + rawJsonText.take(150) + "..."
                    }
                } else {
                    _errorMessage.value = "Gemini returned an empty result. Please point at clear, readable text."
                }
            } catch (e: Exception) {
                Log.e("TranslatorVM", "Network exception", e)
                _errorMessage.value = "Could not connect to Translation service: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectTranslationFromHistory(translation: Translation) {
        _translationResult.value = TranslationResult(
            detectedLanguage = translation.sourceLanguage,
            translatedText = translation.translatedText,
            originalText = translation.originalText,
            phrases = emptyList()
        )
        _sourceLanguage.value = translation.sourceLanguage
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsHelper.shutdown()
    }
}

package com.suryaprakash.medlog.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import java.util.concurrent.Executors

/**
 * The phone's own speech recogniser: the same engine as the keyboard's microphone (Google or Samsung),
 * far more accurate than the small model bundled with MedLog, in English and Indian languages.
 *
 * It tries, in order:
 * 1. the ON-DEVICE recogniser (Android 12+): audio never leaves the phone;
 * 2. if that has no pack for the language, the phone's normal speech service, asked to work offline
 *    (it may use the internet when it has no offline pack; only when [allowOnline] is on).
 * [biasing] words (problem names, "vomiting", "dizzy"...) are given to the engine so they are heard right.
 */
class PhoneRecognizer(private val ctx: Context) {
    private var sr: SpeechRecognizer? = null
    var allowOnline: () -> Boolean = { true }
    var biasing: () -> List<String> = { emptyList() }

    fun onDevice(): Boolean = Build.VERSION.SDK_INT >= 31 && runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx) }.getOrDefault(false)
    private fun normal(): Boolean = runCatching { SpeechRecognizer.isRecognitionAvailable(ctx) }.getOrDefault(false)
    fun available(): Boolean = onDevice() || (allowOnline() && normal())

    fun start(
        languages: List<String>,
        silenceMs: Long,
        onPartial: (String) -> Unit,
        onLevel: (Float) -> Unit,
        onDone: (text: String, language: String?) -> Unit,
    ) {
        stop()
        val useOnDevice = onDevice()
        if (!useOnDevice && !(allowOnline() && normal())) { onDone("", null); return }
        run(useOnDevice, languages, silenceMs, onPartial, onLevel, onDone)
    }

    private fun run(onDevice: Boolean, languages: List<String>, silenceMs: Long, onPartial: (String) -> Unit, onLevel: (Float) -> Unit, onDone: (String, String?) -> Unit) {
        val r = if (onDevice && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx) else SpeechRecognizer.createSpeechRecognizer(ctx)
        sr = r
        var detected: String? = null
        var finished = false
        var lastPartial = ""
        fun finish(text: String) { if (!finished) { finished = true; onDone(text, detected) } }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) = onLevel(((rms + 2f) / 12f).coerceIn(0f, 1f))
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                // no pack for this language on the device: the phone's normal service usually has one
                val noPack = error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                    error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_SERVER
                if (onDevice && noPack && !finished && allowOnline() && normal()) {
                    finished = true
                    runCatching { r.destroy() }
                    run(false, languages, silenceMs, onPartial, onLevel, onDone)
                    return
                }
                // what was heard before a timeout still counts
                finish(lastPartial)
            }
            override fun onResults(b: Bundle) = finish(b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().ifBlank { lastPartial })
            override fun onPartialResults(b: Bundle) { b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { lastPartial = it; onPartial(it) } }
            override fun onEvent(type: Int, p: Bundle?) {}
            override fun onLanguageDetection(results: Bundle) {
                if (Build.VERSION.SDK_INT >= 34) detected = results.getString(SpeechRecognizer.DETECTED_LANGUAGE)
            }
        })
        r.startListening(intent(languages, silenceMs, biasing()))
    }

    /** Ends listening now; what was heard so far is still delivered. */
    fun finish() { sr?.stopListening() }

    fun stop() { runCatching { sr?.cancel(); sr?.destroy() }; sr = null }

    private fun intent(languages: List<String>, silenceMs: Long, bias: List<String> = emptyList()) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languages.firstOrNull() ?: "en-IN")
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs)
        // older people pause mid-sentence: don't cut them off in the first seconds
        if (silenceMs >= 2000) putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
        if (Build.VERSION.SDK_INT >= 33 && bias.isNotEmpty()) putExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(bias.take(500)))
        if (Build.VERSION.SDK_INT >= 34 && languages.size > 1) {
            putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, ArrayList(languages))
            putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, ArrayList(languages))
        }
    }

    // ── offline language packs (Android 13+) ──

    data class Support(val installed: List<String>, val downloadable: List<String>, val pending: List<String>)

    @RequiresApi(33)
    fun checkSupport(onResult: (Support?) -> Unit) {
        if (!onDevice()) { onResult(null); return }
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
        r.checkRecognitionSupport(intent(listOf("en-IN"), 2000), Executors.newSingleThreadExecutor(), object : RecognitionSupportCallback {
            override fun onSupportResult(s: RecognitionSupport) {
                onResult(Support(s.installedOnDeviceLanguages, s.supportedOnDeviceLanguages, s.pendingOnDeviceLanguages)); runCatching { r.destroy() }
            }
            override fun onError(error: Int) { onResult(null); runCatching { r.destroy() } }
        })
    }

    /** Asks Android to download the offline pack. Android downloads it, not MedLog. */
    @RequiresApi(33)
    fun download(tag: String) {
        if (!onDevice()) return
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
        runCatching { r.triggerModelDownload(intent(listOf(tag), 2000)) }
        r.destroy()
    }
}

/**
 * Speech in Indian scripts → Latin letters, so it can be matched and shown to an English-reading doctor.
 * For scripts with an unwritten final vowel (Hindi, Marathi, Bengali, Gujarati, Punjabi, Odia) the silent
 * final "a" is dropped, so "दर्द" becomes "dard", not "darda".
 */
object Translit {
    private val toLatin by lazy { if (Build.VERSION.SDK_INT >= 29) runCatching { android.icu.text.Transliterator.getInstance("Any-Latin") }.getOrNull() else null }
    private val toAscii by lazy { if (Build.VERSION.SDK_INT >= 29) runCatching { android.icu.text.Transliterator.getInstance("Latin-ASCII; Lower") }.getOrNull() else null }
    private val SCHWA = Regex("(?<=[bcdfghjklmnpqrstvwxyz\u1E6D\u1E0D\u1E47\u1E63\u015B\u1E45\u00F1\u1E37\u1E5B])a\\b")

    fun toLatin(s: String): String {
        if (s.all { it.code < 128 }) return s
        val latin = runCatching { toLatin?.transliterate(s) }.getOrNull() ?: return s
        val schwaScript = s.any { it.code in 0x0900..0x0B7F }
        val trimmed = if (schwaScript) SCHWA.replace(latin, "") else latin
        return runCatching { toAscii?.transliterate(trimmed) }.getOrNull() ?: trimmed
    }

    fun isLatin(s: String) = s.all { it.code < 0x250 }
}

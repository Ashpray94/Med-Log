package com.suryaprakash.medlog.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Read Aloud. Uses the phone's own offline text-to-speech voice, slower than normal by default.
 * [speaking] drives the Read Aloud button ("Stop" while talking).
 */
class Speaker(private val ctx: Context, private val rate: () -> Float) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayList<Pair<String, (() -> Unit)?>>()
    private val done = HashMap<String, () -> Unit>()
    private val ids = AtomicInteger()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking
    /** false when the phone has no usable voice; Settings then shows how to add one. */
    val available = MutableStateFlow(true)

    fun init() {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) { available.value = false; return@TextToSpeech }
            val t = tts ?: return@TextToSpeech
            val res = t.setLanguage(Locale("en", "IN")).takeIf { it >= TextToSpeech.LANG_AVAILABLE } ?: t.setLanguage(Locale.UK).takeIf { it >= TextToSpeech.LANG_AVAILABLE } ?: t.setLanguage(Locale.US)
            available.value = res >= TextToSpeech.LANG_AVAILABLE
            t.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { _speaking.value = true }
                override fun onDone(id: String?) { finish(id) }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { finish(id) }
                override fun onError(id: String?, code: Int) { finish(id) }
                override fun onStop(id: String?, interrupted: Boolean) { finish(id) }
            })
            ready = true
            synchronized(pending) { pending.forEach { (text, cb) -> speakNow(text, false, cb) }; pending.clear() }
        }
    }

    private fun finish(id: String?) {
        val cb = synchronized(done) { id?.let { done.remove(it) } }
        if (synchronized(done) { done.isEmpty() }) _speaking.value = false
        cb?.invoke()
    }

    /** Says [text]. [queue] adds after what is being said; otherwise it replaces it. */
    fun say(text: String, queue: Boolean = false, onDone: (() -> Unit)? = null) = sayIn(text, null, queue, onDone)

    /** Says [text] in [lang] (e.g. "ta-IN") when that voice is on the phone; otherwise in English. */
    fun sayIn(text: String, lang: String?, queue: Boolean = false, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) { onDone?.invoke(); return }
        // the same words asked for twice in a row (a screen opening twice, a double tap) are said once
        val now = System.currentTimeMillis()
        if (!queue && onDone == null && text == lastText && (now - lastAt < 1500 || _speaking.value)) return
        lastText = text; lastAt = now
        if (!ready) { init(); synchronized(pending) { if (!queue) pending.clear(); pending += text to onDone }; return }
        // in the person's language when the phone has a voice for it; English otherwise
        if (lang == null && I18n.active) {
            val tag = "${I18n.lang}-IN"
            val local = I18n.tr(text)
            if (local != text && hasVoice(tag)) { voiceLang = tag; speakNow(local, queue, onDone); return }
        }
        voiceLang = lang
        speakNow(text, queue, onDone)
    }
    private var voiceLang: String? = null
    @Volatile private var lastText = ""
    @Volatile private var lastAt = 0L

    fun hasVoice(lang: String): Boolean = runCatching { (tts?.isLanguageAvailable(Locale.forLanguageTag(lang)) ?: -2) >= TextToSpeech.LANG_AVAILABLE }.getOrDefault(false)

    /** If the phone's media volume is off, Read Aloud would be silent: bring it to half and show the slider. */
    private fun ensureAudible() {
        runCatching {
            val am = ctx.getSystemService(android.media.AudioManager::class.java)
            val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            if (am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == 0) am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, max / 2, android.media.AudioManager.FLAG_SHOW_UI)
        }
    }

    private fun speakNow(text: String, queue: Boolean, onDone: (() -> Unit)?) {
        val t = tts ?: return
        ensureAudible()
        val id = "u${ids.incrementAndGet()}"
        if (!queue) synchronized(done) { done.clear() }
        onDone?.let { synchronized(done) { done[id] = it } } ?: synchronized(done) { done[id] = {} }
        t.setSpeechRate(rate())
        val want = voiceLang?.let { Locale.forLanguageTag(it) }
        if (want != null && t.isLanguageAvailable(want) >= TextToSpeech.LANG_AVAILABLE) t.setLanguage(want)
        else t.setLanguage(Locale("en", "IN")).takeIf { it >= TextToSpeech.LANG_AVAILABLE } ?: t.setLanguage(Locale.US)
        t.speak(clean(text), if (queue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH, Bundle(), id)
    }

    fun stop() {
        lastText = ""
        tts?.stop()
        synchronized(done) { done.clear() }
        _speaking.value = false
    }

    /** Symbols read badly by voices: "°F", "mg/dL", "BP 150/90". */
    private fun clean(s: String) = s
        .replace("°F", " degrees").replace("°C", " degrees Celsius")
        .replace("mg/dL", " ").replace(Regex("(\\d{2,3})/(\\d{2,3})")) { m -> "${m.groupValues[1]} by ${m.groupValues[2]}" }
        .replace("◇", "").replace("•", ",").replace("·", ",")

    fun shutdown() { tts?.shutdown(); tts = null; ready = false }
}

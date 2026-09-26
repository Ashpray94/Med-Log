package com.suryaprakash.medlog.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.sqrt

/**
 * Speech recognition. Uses the phone's own recogniser when it has one ([PhoneRecognizer]); otherwise the small
 * offline Indian-English model bundled in the app (Vosk).
 * Records the person's voice to a small WAV file at the same time, so the helper or doctor can replay it.
 *
 * Stops by itself after [SILENCE_MS] of quiet once speech has started, or at [MAX_MS].
 */
class Listener(private val ctx: Context) {

    sealed interface State {
        data object Loading : State
        data object Ready : State
        data class Failed(val why: String) : State
    }

    /** [text] is in Latin letters (transliterated if spoken in an Indian script); [original] is what the recogniser wrote. */
    data class Heard(val text: String, val confidence: Float, val audio: File?, val lowWords: List<String>, val original: String = text, val language: String? = null)

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state
    /** 0..1 loudness, for the listening animation */
    val level = MutableStateFlow(0f)
    val partial = MutableStateFlow("")
    val listening = MutableStateFlow(false)

    private var model: Model? = null
    val phone = PhoneRecognizer(ctx)
    /** Use the phone's on-device recogniser (Indian languages) instead of the bundled English model. */
    var usePhone: () -> Boolean = { false }
    var languages: () -> List<String> = { listOf("en-IN") }
    @Volatile private var phoneActive = false

    fun phoneAvailable() = phone.available()
    @Volatile private var stopFlag = false
    @Volatile private var discard = false
    private var thread: Thread? = null
    private val main = Handler(Looper.getMainLooper())

    fun prepare() {
        if (model != null || _state.value is State.Failed) return
        StorageService.unpack(ctx.applicationContext, "model-en", "model",
            { m -> model = m; _state.value = State.Ready },
            { e -> _state.value = State.Failed(e.message ?: "Speech model missing") })
    }

    /**
     * Starts listening. [grammar] limits what can be heard (e.g. yes/no answers), which makes short answers very accurate.
     * [onDone] is called on the main thread with everything heard.
     */
    @SuppressLint("MissingPermission")
    fun start(grammar: List<String>? = null, keepAudio: Boolean = true, maxMs: Long = MAX_MS, silenceMs: Long = SILENCE_MS, onDone: (Heard) -> Unit) {
        if (usePhone() && phone.available()) { startPhone(silenceMs, onDone); return }
        val m = model ?: run { onDone(Heard("", 0f, null, emptyList())); return }
        stop()
        stopFlag = false
        discard = false
        partial.value = ""
        listening.value = true
        val t = Thread({
            val rate = 16000
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = try {
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate))
            } catch (e: Exception) { null }
            if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
                main.post { listening.value = false; onDone(Heard("", 0f, null, emptyList())) }
                return@Thread
            }
            val ns = if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true } }.getOrNull() else null
            val recognizer = if (grammar != null) Recognizer(m, rate.toFloat(), JSONArray(grammar + "[unk]").toString()) else Recognizer(m, rate.toFloat())
            recognizer.setWords(true)
            val raw = if (keepAudio) File(ctx.cacheDir, "rec_${System.currentTimeMillis()}.pcm") else null
            val out = raw?.let { FileOutputStream(it) }
            val buf = ShortArray(rate / 10)
            val bytes = ByteArray(buf.size * 2)
            val parts = ArrayList<String>()
            val confs = ArrayList<Float>()
            val low = ArrayList<String>()
            val started = System.currentTimeMillis()
            var lastChange = started
            var lastPartial = ""
            var heardSomething = false
            rec.startRecording()
            try {
                while (!stopFlag) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    var sum = 0.0
                    for (i in 0 until n) { val s = buf[i].toInt(); sum += s * s; bytes[i * 2] = (s and 0xff).toByte(); bytes[i * 2 + 1] = (s shr 8 and 0xff).toByte() }
                    out?.write(bytes, 0, n * 2)
                    level.value = (sqrt(sum / n) / 6000.0).toFloat().coerceIn(0f, 1f)
                    val now = System.currentTimeMillis()
                    if (recognizer.acceptWaveForm(buf, n)) {
                        collect(recognizer.result, parts, confs, low)
                        lastPartial = ""
                        lastChange = now
                        if (parts.isNotEmpty()) heardSomething = true
                    } else {
                        val p = JSONObject(recognizer.partialResult).optString("partial")
                        if (p != lastPartial) { lastPartial = p; lastChange = now; if (p.isNotBlank()) heardSomething = true }
                        val shown = (parts + p).filter { it.isNotBlank() }.joinToString(" ")
                        if (shown != partial.value) partial.value = shown
                    }
                    if (heardSomething && now - lastChange > silenceMs) break
                    if (!heardSomething && now - started > NOTHING_MS) break
                    if (now - started > maxMs) break
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
                ns?.release()
                out?.close()
            }
            collect(recognizer.finalResult, parts, confs, low)
            recognizer.close()
            val text = parts.filter { it.isNotBlank() && it != "[unk]" }.joinToString(" ").trim()
            val wav = raw?.let { if (text.isNotEmpty()) toWav(it, rate) else { it.delete(); null } }
            val conf = if (confs.isEmpty()) 0f else confs.average().toFloat()
            level.value = 0f
            val dropped = discard
            main.post { listening.value = false; partial.value = if (dropped) "" else text; if (!dropped) onDone(Heard(text, conf, wav, low)) }
        }, "medlog-listen")
        thread = t
        t.start()
    }

    private fun collect(json: String, parts: MutableList<String>, confs: MutableList<Float>, low: MutableList<String>) {
        val o = JSONObject(json)
        val text = o.optString("text")
        if (text.isNotBlank()) parts += text
        o.optJSONArray("result")?.let { a ->
            for (i in 0 until a.length()) {
                val w = a.getJSONObject(i)
                val c = w.optDouble("conf", 1.0).toFloat()
                confs += c
                if (c < 0.6f) low += w.optString("word")
            }
        }
    }

    private fun startPhone(silenceMs: Long, onDone: (Heard) -> Unit) {
        stop()
        discard = false
        phoneActive = true
        partial.value = ""
        listening.value = true
        phone.start(languages(), maxOf(silenceMs, 1500L),
            onPartial = { partial.value = it },
            onLevel = { level.value = it },
            onDone = { text, lang ->
                phoneActive = false
                listening.value = false; level.value = 0f
                if (!discard) { partial.value = text; onDone(Heard(Translit.toLatin(text), 1f, null, emptyList(), text, lang)) }
            })
    }

    /** Ends listening now; whatever was heard is still delivered. */
    fun finish() { stopFlag = true; if (phoneActive) phone.finish() }

    /** Stops and throws away what was heard (the person moved on). */
    fun stop() {
        discard = true
        stopFlag = true
        if (phoneActive) { phone.stop(); phoneActive = false; listening.value = false }
        thread?.join(1500)
        thread = null
    }

    private fun toWav(pcm: File, rate: Int): File {
        val wav = File(ctx.filesDir, "audio").apply { mkdirs() }.let { File(it, pcm.nameWithoutExtension + ".wav") }
        val len = pcm.length()
        RandomAccessFile(wav, "rw").use { f ->
            fun int(v: Int) { f.write(v and 0xff); f.write(v shr 8 and 0xff); f.write(v shr 16 and 0xff); f.write(v shr 24 and 0xff) }
            fun short(v: Int) { f.write(v and 0xff); f.write(v shr 8 and 0xff) }
            f.writeBytes("RIFF"); int((36 + len).toInt()); f.writeBytes("WAVEfmt "); int(16); short(1); short(1)
            int(rate); int(rate * 2); short(2); short(16); f.writeBytes("data"); int(len.toInt())
            pcm.inputStream().use { it.copyTo(java.nio.channels.Channels.newOutputStream(f.channel)) }
        }
        pcm.delete()
        return wav
    }

    companion object {
        const val SILENCE_MS = 2000L
        const val MAX_MS = 60_000L
        /** give up if nothing at all is said */
        const val NOTHING_MS = 12_000L
        val YES_NO = listOf("yes", "yeah", "yep", "yes please", "no", "nope", "not", "no no", "i don't know", "don't know", "stop", "skip")
        val NUMBERS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
            "fifteen", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety", "hundred", "and", "point", "a", "half",
            "times", "degrees", "i don't know", "don't know", "stop", "skip", "no", "yes")
    }
}

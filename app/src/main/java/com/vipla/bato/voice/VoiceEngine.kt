package com.vipla.bato.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.io.File
import java.net.URL
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

data class VoiceResult(
    val status: String, val provider: String, val model: String, val voiceId: String,
    val locale: String, val provenance: String, val fallback: String, val httpStatus: Int,
    val normalizedText: String, val evidence: String, val audioFormat: String = "NONE",
    val bytesReceived: Long = 0, val decode: String = "NOT_ATTEMPTED",
    val playback: String = "NOT_STARTED"
)
interface VoiceEngine { fun speak(text: String, onResult: (VoiceResult) -> Unit = {}); fun shutdown(); fun identity(): String }

object SerbianSpeechNormalizer {
    private val abbr = mapOf("dr." to "doktor", "npr." to "na primer", "itd." to "i tako dalje", "tj." to "to jest", "AI" to "veštačka inteligencija", "VIPLA" to "Vipla", "BATO" to "Bato")
    fun normalize(text: String): String {
        var out = text.trim().replace(Regex("\\s+"), " ")
        abbr.forEach { (a, b) -> out = out.replace(a, b) }
        out = out.replace(Regex("(\\d{4})-(\\d{2})-(\\d{2})")) { m -> "${m.groupValues[3]}. ${m.groupValues[2]}. ${m.groupValues[1]}. godine" }
        return out.replace("/", " kroz ").replace("—", ", ")
    }
}

class RemoteBatoVoiceEngine(private val context: Context, private val endpoint: String = "https://bato-sigma.vercel.app/api/voice") : VoiceEngine {
    @Volatile private var player: MediaPlayer? = null
    @Volatile private var generation = 0L
    override fun speak(text: String, onResult: (VoiceResult) -> Unit) {
        val normalized = SerbianSpeechNormalizer.normalize(text)
        val pronunciation = SerbianProsodyResolver.resolve(normalized)
        if (!pronunciation.resolved) {
            onResult(VoiceResult(
                "BLOCKED", "SERBIAN_PROSODY_LAYER", "BATO_ACCENT_PROSODY_LEXICON_1", "NONE",
                "sr-RS", "BLOCKED_UNRESOLVED_PRONUNCIATION", "NONE", 0, normalized,
                "UNRESOLVED_TOKENS=" + pronunciation.unresolved.joinToString(",") +
                    "; TTS was not called and did not guess stress"
            ))
            return
        }
        val requestGeneration = synchronized(this) { generation += 1; generation }
        Thread {
            var connection: HttpsURLConnection? = null
            var audioFile: File? = null
            try {
                connection = URL(endpoint).openConnection() as HttpsURLConnection
                connection.requestMethod = "POST"; connection.connectTimeout = 15_000; connection.readTimeout = 90_000
                connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json")
                fun json(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                val requestBody = "{\"text\":\"" + json(normalized) +
                    "\",\"pronunciation\":{\"resolved\":true,\"ssml\":\"" + json(pronunciation.ssml) +
                    "\",\"batoMarkup\":\"" + json(pronunciation.batoMarkup) +
                    "\",\"lexiconVersion\":\"" + json(pronunciation.lexiconVersion) +
                    "\",\"grammarVersion\":\"" + json(pronunciation.grammarVersion) + "\"}}"
                connection.outputStream.use { it.write(requestBody.toByteArray()) }
                val http = connection.responseCode
                if (http !in 200..299) {
                    val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().take(400)
                    throw IllegalStateException("TTS_HTTP_STATUS=$http $detail")
                }
                val format = connection.contentType?.substringBefore(';')?.lowercase().orEmpty()
                if (!format.startsWith("audio/")) throw IllegalStateException("TTS_FORMAT_INVALID=$format")
                val suffix = when (format) { "audio/wav", "audio/x-wav" -> ".wav"; "audio/ogg" -> ".ogg"; else -> ".mp3" }
                audioFile = File.createTempFile("bato-voice-", suffix, context.cacheDir)
                val bytes = connection.inputStream.use { input -> audioFile.outputStream().use { output -> input.copyTo(output) } }
                if (bytes < 128) throw IllegalStateException("TTS_AUDIO_EMPTY bytes=$bytes")
                val base = VoiceResult(
                    "AUDIO_RECEIVED", connection.getHeaderField("X-Bato-Voice-Provider") ?: "unreported",
                    connection.getHeaderField("X-Bato-Voice-Model") ?: "unreported",
                    connection.getHeaderField("X-Bato-Voice-Id") ?: "unreported",
                    connection.getHeaderField("X-Bato-Voice-Locale") ?: "sr-RS",
                    connection.getHeaderField("X-Bato-Voice-Provenance") ?: "QA_CANDIDATE",
                    connection.getHeaderField("X-Bato-Voice-Fallback") ?: "NONE", http, normalized,
                    "Remote audio persisted; PROSODY_RESOLUTION=" +
                        (connection.getHeaderField("X-Bato-Prosody-Resolution") ?: "UNREPORTED") +
                        "; LEXICON=" + (connection.getHeaderField("X-Bato-Prosody-Lexicon") ?: "UNREPORTED"),
                    format, bytes, "PENDING", "NOT_STARTED"
                )
                onResult(base)
                val file = requireNotNull(audioFile)
                val mp = MediaPlayer()
                synchronized(this) {
                    if (requestGeneration != generation) { mp.release(); file.delete(); return@Thread }
                    player?.release(); player = mp
                }
                mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                mp.setDataSource(file.absolutePath)
                mp.setOnPreparedListener { prepared ->
                    onResult(base.copy(status = "DECODE_SUCCESS", evidence = "MediaPlayer prepared the downloaded $format payload", decode = "SUCCESS", playback = "READY"))
                    prepared.start()
                    onResult(base.copy(status = "PLAYBACK_STARTED", evidence = "MediaPlayer.isPlaying=${prepared.isPlaying}", decode = "SUCCESS", playback = if (prepared.isPlaying) "STARTED" else "START_REQUESTED"))
                }
                mp.setOnCompletionListener { completed ->
                    onResult(base.copy(status = "PLAYBACK_COMPLETED", evidence = "Android MediaPlayer completion callback received", decode = "SUCCESS", playback = "COMPLETED"))
                    synchronized(this) { if (player === completed) player = null }
                    completed.release(); file.delete()
                }
                mp.setOnErrorListener { failed, what, extra ->
                    onResult(base.copy(status = "PLAYBACK_FAILED", provenance = "BLOCKED", evidence = "MediaPlayer error what=$what extra=$extra", decode = "FAILED", playback = "FAILED"))
                    synchronized(this) { if (player === failed) player = null }
                    failed.release(); file.delete(); true
                }
                mp.prepareAsync()
            } catch (error: Exception) {
                audioFile?.delete()
                onResult(VoiceResult("BLOCKED", "NONE", "NONE", "NONE", "sr-RS", "BLOCKED", "NONE", connection?.responseCode ?: 0, normalized, error.message ?: error.javaClass.simpleName, playback = "FAILED"))
            } finally { connection?.disconnect() }
        }.start()
    }
    override fun shutdown() = synchronized(this) { generation += 1; player?.release(); player = null }
    override fun identity() = "REMOTE_SERBIAN_MALE_QA_CANDIDATE_NO_SILENT_FALLBACK"
}

class AndroidSystemVoiceFallback(context: Context) : VoiceEngine {
    private var ready = false; private var selected: Voice? = null; private var tts: TextToSpeech? = null
    init { tts = TextToSpeech(context) { status -> tts?.let { engine -> if (status == TextToSpeech.SUCCESS) { engine.language = Locale.forLanguageTag("sr-RS"); selected = engine.voices.orEmpty().filter { it.locale.language == "sr" }.maxByOrNull { it.quality }; selected?.let { engine.voice = it }; ready = true } } } }
    override fun speak(text: String, onResult: (VoiceResult) -> Unit) {
        val n = SerbianSpeechNormalizer.normalize(text)
        if (!ready) { onResult(VoiceResult("BLOCKED", "ANDROID_SYSTEM_TTS", "system", selected?.name ?: "unavailable", "sr-RS", "LOCAL_FALLBACK", "ANDROID_SYSTEM_TTS", 0, n, "Fallback unavailable")); return }
        tts?.speak(n, TextToSpeech.QUEUE_FLUSH, null, "bato-fallback")
        onResult(VoiceResult("QA_FAILED_FALLBACK", "ANDROID_SYSTEM_TTS", "system", selected?.name ?: "default", "sr-RS", "LOCAL_FALLBACK", "ANDROID_SYSTEM_TTS", 0, n, "VOICE_FALLBACK=ANDROID_SYSTEM_TTS; broadcaster requirement NOT proven"))
    }
    override fun shutdown() { tts?.shutdown() }
    override fun identity() = "VOICE_FALLBACK=ANDROID_SYSTEM_TTS;QA_FAILED"
}
object SerbianVoiceCorpus {
    val samples = listOf("Добар дан. Настављамо тамо где смо стали.", "Римско царство није једноставно нестало 476. године.", "Становници Константинопоља себе су називали Ромејима, односно Римљанима.", "Драган разговара са Батом.").map { it to SerbianSpeechNormalizer.normalize(it) }
}

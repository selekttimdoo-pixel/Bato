package com.vipla.bato.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

data class VoiceResult(val status:String,val engine:String,val normalizedText:String,val voiceName:String,val evidence:String)
interface VoiceEngine { fun speak(text:String,onResult:(VoiceResult)->Unit={}); fun shutdown(); fun identity():String }

object SerbianSpeechNormalizer {
    private val abbreviations=mapOf("dr." to "doktor", "npr." to "na primer", "itd." to "i tako dalje", "tj." to "to jest", "AI" to "veštačka inteligencija", "VIPLA" to "Vipla", "BATO" to "Bato")
    fun normalize(text:String):String {
        var out=text.trim().replace(Regex("\\s+")," ")
        abbreviations.forEach{(from,to)->out=out.replace(from,to,ignoreCase=false)}
        out=out.replace(Regex("(\\d{4})-(\\d{2})-(\\d{2})")){m->"${m.groupValues[3]}. ${m.groupValues[2]}. ${m.groupValues[1]}. godine"}
        return out.replace("/"," kroz ").replace("—",", ")
    }
}

class AndroidSerbianVoiceEngine(context:Context):VoiceEngine {
    private var ready=false; private var selected:Voice?=null; private var tts:TextToSpeech?=null
    init { tts=TextToSpeech(context){status-> val engine=tts; if(status==TextToSpeech.SUCCESS&&engine!=null){ engine.language=Locale.forLanguageTag("sr-RS"); selected=selectVoice(engine.voices); selected?.let{engine.voice=it}; engine.setSpeechRate(.92f); engine.setPitch(.86f); ready=true } } }
    private fun selectVoice(voices:Set<Voice>?):Voice?=voices.orEmpty().filter{it.locale.language=="sr"}.sortedByDescending{v->(if(v.name.contains("male",true))100 else 0)+(if(!v.isNetworkConnectionRequired)10 else 0)+v.quality}.firstOrNull()
    override fun speak(text:String,onResult:(VoiceResult)->Unit){val normalized=SerbianSpeechNormalizer.normalize(text); if(!ready){onResult(VoiceResult("BLOCKED","ANDROID_TTS",normalized,"UNAVAILABLE","Serbian voice engine not initialized; no audio claimed"));return}; tts?.speak(normalized,TextToSpeech.QUEUE_FLUSH,null,"bato-response");onResult(VoiceResult("AUDIO_REQUESTED","ANDROID_TTS_ADAPTER",normalized,selected?.name?:"DEFAULT_SR","Provider-neutral adapter active; male/broadcaster quality requires physical listening QA"))}
    override fun shutdown(){tts?.shutdown()}; override fun identity()="BATO_SERBIAN_VOICE/ANDROID_ADAPTER/${selected?.name?:"INITIALIZING"}"
}

object SerbianVoiceCorpus {
    val samples=listOf(
        "Добар дан. Настављамо тамо где смо стали.",
        "Разговарамо о Римском царству, његовом успону и паду Западног римског царства.",
        "Драган разговара са Звезданом о Бату и Випли.",
        "Јуче сам радио, данас радим, а сутра ћу наставити.",
        "У Београду, из Београда, ка Београду, са Београдом.",
        "Датум је 20. 9. 2026. године, а време је 14 часова и 35 минута.",
        "На пример, доктор Петровић користи вештачку интелигенцију.",
        "Да ли желиш да наставимо? Одлично — почнимо.",
        "OpenAI и Android имају српске изговорне облике када су познати.",
        "лук, лук; град, град — акценат и контекст мењају значење."
    ).map{it to SerbianSpeechNormalizer.normalize(it)}
}

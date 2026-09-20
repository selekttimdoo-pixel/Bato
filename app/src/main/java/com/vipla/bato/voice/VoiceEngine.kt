package com.vipla.bato.voice
import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.io.File
import java.net.URL
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

data class VoiceResult(val status:String,val provider:String,val model:String,val voiceId:String,val locale:String,val provenance:String,val fallback:String,val httpStatus:Int,val normalizedText:String,val evidence:String)
interface VoiceEngine { fun speak(text:String,onResult:(VoiceResult)->Unit={});fun shutdown();fun identity():String }
object SerbianSpeechNormalizer{
 private val abbr=mapOf("dr." to "doktor","npr." to "na primer","itd." to "i tako dalje","tj." to "to jest","AI" to "veštačka inteligencija","VIPLA" to "Vipla","BATO" to "Bato")
 fun normalize(text:String):String{var out=text.trim().replace(Regex("\\s+")," ");abbr.forEach{(a,b)->out=out.replace(a,b)};out=out.replace(Regex("(\\d{4})-(\\d{2})-(\\d{2})")){m->"${m.groupValues[3]}. ${m.groupValues[2]}. ${m.groupValues[1]}. godine"};return out.replace("/"," kroz ").replace("—",", ")}
}
class RemoteBatoVoiceEngine(private val context:Context,private val endpoint:String="https://bato-sigma.vercel.app/api/voice",private val fallback:VoiceEngine=AndroidSystemVoiceFallback(context)):VoiceEngine{
 @Volatile private var player:MediaPlayer?=null
 override fun speak(text:String,onResult:(VoiceResult)->Unit){val n=SerbianSpeechNormalizer.normalize(text);Thread{try{val c=URL(endpoint).openConnection() as HttpsURLConnection;c.requestMethod="POST";c.connectTimeout=15_000;c.readTimeout=90_000;c.doOutput=true;c.setRequestProperty("Content-Type","application/json");val escaped=n.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");c.outputStream.use{it.write("{\"text\":\"$escaped\"}".toByteArray())};val status=c.responseCode;if(status !in 200..299)throw IllegalStateException("TTS_HTTP_STATUS=$status ${(c.errorStream?.bufferedReader()?.readText()).orEmpty().take(240)}");val audio=File.createTempFile("bato-voice-",".mp3",context.cacheDir);c.inputStream.use{i->audio.outputStream().use{i.copyTo(it)}};val r=VoiceResult("AUDIO_PLAYING",c.getHeaderField("X-Bato-Voice-Provider")?:"vercel-ai-gateway/openai",c.getHeaderField("X-Bato-Voice-Model")?:"openai/tts-1-hd",c.getHeaderField("X-Bato-Voice-Id")?:"onyx",c.getHeaderField("X-Bato-Voice-Locale")?:"sr-RS",c.getHeaderField("X-Bato-Voice-Provenance")?:"PROVIDER_REAL","NONE",status,n,"Remote neural audio received; physical listening QA required");c.disconnect();val mp=MediaPlayer();player?.release();player=mp;mp.setDataSource(audio.absolutePath);mp.setOnCompletionListener{it.release();audio.delete();player=null};mp.setOnErrorListener{p,_,_->p.release();audio.delete();player=null;true};mp.prepare();mp.start();onResult(r)}catch(e:Exception){fallback.speak(n){r->onResult(r.copy(evidence="REMOTE_BLOCKED=${e.message}; ${r.evidence}"))}}}.start()}
 override fun shutdown(){player?.release();player=null;fallback.shutdown()};override fun identity()="BATO_REMOTE_VOICE/openai-tts-1-hd/onyx/sr-RS"
}
class AndroidSystemVoiceFallback(context:Context):VoiceEngine{
 private var ready=false;private var selected:Voice?=null;private var tts:TextToSpeech?=null
 init{tts=TextToSpeech(context){s->val e=tts;if(s==TextToSpeech.SUCCESS&&e!=null){e.language=Locale.forLanguageTag("sr-RS");selected=e.voices.orEmpty().filter{it.locale.language=="sr"}.maxByOrNull{it.quality};selected?.let{e.voice=it};ready=true}}}
 override fun speak(text:String,onResult:(VoiceResult)->Unit){val n=SerbianSpeechNormalizer.normalize(text);if(!ready){onResult(VoiceResult("BLOCKED","ANDROID_SYSTEM_TTS","system",selected?.name?:"unavailable","sr-RS","LOCAL_FALLBACK","ANDROID_SYSTEM_TTS",0,n,"Fallback unavailable"));return};tts?.speak(n,TextToSpeech.QUEUE_FLUSH,null,"bato-fallback");onResult(VoiceResult("AUDIO_PLAYING","ANDROID_SYSTEM_TTS","system",selected?.name?:"default","sr-RS","LOCAL_FALLBACK","ANDROID_SYSTEM_TTS",0,n,"VOICE_FALLBACK=ANDROID_SYSTEM_TTS; broadcaster requirement NOT proven"))}
 override fun shutdown(){tts?.shutdown()};override fun identity()="VOICE_FALLBACK=ANDROID_SYSTEM_TTS"
}
object SerbianVoiceCorpus{val samples=listOf("Добар дан. Настављамо тамо где смо стали.","Римско царство није једноставно нестало 476. године.","Становници Константинопоља себе су називали Ромејима, односно Римљанима.","Драган разговара са Батом.").map{it to SerbianSpeechNormalizer.normalize(it)}}

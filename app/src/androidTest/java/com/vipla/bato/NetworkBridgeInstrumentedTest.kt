package com.vipla.bato

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipla.bato.ai.*
import com.vipla.bato.data.RetrievedItem
import com.vipla.bato.data.StenoEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID
import java.net.URL
import javax.net.ssl.HttpsURLConnection

@RunWith(AndroidJUnit4::class)
class NetworkBridgeInstrumentedTest {
 private val endpoint="https://bato-sigma.vercel.app/api/bato"
 private fun empty()=ProviderContext(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),"UNRESOLVED",emptyList())
 private fun event(id:Long,role:String,text:String)=StenoEvent(id,role,text,System.currentTimeMillis(),System.currentTimeMillis(),UUID.randomUUID().toString(),UUID.randomUUID().toString(),"STENO_FIRST:TEST")
 private fun item(id:String,type:String,text:String,entity:String?=null)=RetrievedItem(id,type,System.currentTimeMillis(),text,.98,"TEST_EXACT",entity,"test:continuity","test")
 private suspend fun ask(message:String,context:ProviderContext)=HttpProviderBridge(endpoint).respond(message,context) as ProviderResult.Response
 private fun prove(name:String,ok:Boolean,result:ProviderResult.Response){
  if(!ok) throw AssertionError("$name | text=${result.text} | ids=${result.retrievedItemIds} | datetime=${result.currentDatetimeUsed} | graph=${result.graphResolution}")
 }

 @Test fun liveGroundedContinuitySuite()=runBlocking {
  val app=InstrumentationRegistry.getInstrumentation().targetContext
  assertEquals(PackageManager.PERMISSION_GRANTED,app.packageManager.checkPermission(Manifest.permission.INTERNET,app.packageName))

  val date=ask("Koji je danas dan? Navedi YYYY-MM-DD.",empty())
  prove("DATE_TEXT",date.text.contains(LocalDate.now().toString()),date); prove("DATE_PROVENANCE",date.currentDatetimeUsed,date)

  val orion=ask("Koje je moje test ime?",ProviderContext(listOf(event(1,"USER","Moje test ime je ORION-742.")),emptyList(),emptyList(),emptyList(),emptyList(),listOf("ORION-742"),"RESOLVED:ORION-742",emptyList()))
  prove("ORION_MEMORY",orion.text.contains("ORION-742"),orion)

  val multi=ask("Koja je bila ona prva šifra?",ProviderContext(listOf(event(1,"USER","Šifra projekta Aurora je POLARIS-318."),event(2,"USER","Usput, vreme je sunčano."),event(3,"ASSISTANT","Razumem.")),listOf(item("STENO:1","LOCAL_STENO","Šifra projekta Aurora je POLARIS-318.","AURORA")),emptyList(),emptyList(),emptyList(),listOf("AURORA"),"RESOLVED:AURORA",emptyList()))
  prove("MULTI_TURN_TEXT",multi.text.contains("POLARIS-318"),multi); prove("MULTI_TURN_ID","STENO:1" in multi.retrievedItemIds,multi)

  val graph=ask("Šta smo zaključili o Zapadnom Rimu?",ProviderContext(emptyList(),listOf(item("STENO:44","IMPORTED_STENO","Naš zaključak: kontinuitet analiziramo kroz poresku logistiku, a ne kroz jedan datum.","WESTERN_ROMAN_EMPIRE")),emptyList(),listOf(item("GRAPH:entity:western_roman_empire","FAST_GRAPH","aliases=Zapadni Rim|Zapadno rimsko carstvo; relation=PART_OF:Roman Empire","WESTERN_ROMAN_EMPIRE")),emptyList(),listOf("WESTERN_ROMAN_EMPIRE"),"RESOLVED:WESTERN_ROMAN_EMPIRE",emptyList()))
  prove("FAST_GRAPH_TEXT",graph.text.contains("pores",true)&&graph.text.contains("logist",true),graph); prove("FAST_GRAPH_ID",graph.retrievedItemIds.contains("STENO:44"),graph)

  val history=ask("Nastavi baš našu prethodnu analizu, bez opšteg enciklopedijskog odgovora.",ProviderContext(emptyList(),listOf(item("STENO:77","IMPORTED_STENO","U našoj raspravi pad Zapadnog rimskog carstva bio je okvir za raspad poreske logistike i lokalnog kontinuiteta.","WESTERN_ROMAN_EMPIRE")),emptyList(),emptyList(),emptyList(),listOf("WESTERN_ROMAN_EMPIRE"),"RESOLVED:WESTERN_ROMAN_EMPIRE",emptyList()))
  prove("HISTORY_TEXT",history.text.contains("pores",true)||history.text.contains("logist",true),history); prove("HISTORY_ID","STENO:77" in history.retrievedItemIds,history)

  val ambiguity=ask("Nastavi priču o Aleksandru.",ProviderContext(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),listOf("ALEXANDAR_PERSON","ALEXANDER_EMPEROR"),"AMBIGUOUS:ALEXANDAR_PERSON|ALEXANDER_EMPEROR",listOf("ALEXANDAR_PERSON","ALEXANDER_EMPEROR")))
  prove("AMBIGUITY_QUESTION",ambiguity.text.contains("?"),ambiguity)
 }

 @Test fun romanIdentityContinuitySuite()=runBlocking {
  val romanFacts=listOf(
   item("RIZNICA:ROMAN-1","RIZNICA","Zapadna carska vlast okončana je 476, ali je Istočno rimsko carstvo sa sedištem u Konstantinopolju nastavilo do 1453.","ROMAN_EMPIRE"),
   item("RIZNICA:ROMAN-2","RIZNICA","Stanovnici carstva u Konstantinopolju sebe su nazivali Rimljanima, odnosno Rhomaioi/Romejima; Vizantijsko carstvo je kasniji istoriografski naziv.","EASTERN_ROMAN_EMPIRE")
  )
  val graph=listOf(
   item("GRAPH:ROMAN","FAST_GRAPH","ROMAN_EMPIRE NOT_SYNONYM_OF WESTERN_ROMAN_EMPIRE; HAS_CONTINUATION EASTERN_ROMAN_EMPIRE","ROMAN_EMPIRE"),
   item("GRAPH:EAST","FAST_GRAPH","EASTERN_ROMAN_EMPIRE CONTINUATION_OF ROMAN_EMPIRE; end=1453; self_identity=Romans|Rhomaioi|Romeji; later_label=BYZANTINE_EMPIRE","EASTERN_ROMAN_EMPIRE")
  )
  val c1=ProviderContext(emptyList(),emptyList(),romanFacts,graph,emptyList(),listOf("ROMAN_EMPIRE","WESTERN_ROMAN_EMPIRE","EASTERN_ROMAN_EMPIRE"),"RESOLVED:ROMAN_EMPIRE",emptyList())
  val a1=ask("Kada je palo Rimsko carstvo?",c1)
  prove("ROMAN_476_1453",a1.text.contains("476")&&a1.text.contains("1453"),a1)
  val recent2=listOf(event(101,"USER","Kada je palo Rimsko carstvo?"),event(102,"ASSISTANT",a1.text))
  val a2=ask("Kako je onda palo ako je nastavilo da postoji?",c1.copy(recentConversation=recent2))
  prove("ROMAN_WESTERN_GOVERNMENT",a2.text.contains("zapad",true)&&a2.text.contains("476"),a2)
  val recent3=recent2+event(103,"USER","Kako je onda palo ako je nastavilo da postoji?")+event(104,"ASSISTANT",a2.text)
  val a3=ask("Kako su sebe zvali ljudi u Carigradu 1100. godine?",c1.copy(recentConversation=recent3))
  prove("ROMAN_SELF_IDENTITY",(a3.text.contains("Romej",true)||a3.text.contains("Rhoma",true)||a3.text.contains("Rimljan",true))&&!a3.text.contains("sebe Vizant",true),a3)
 }

 @Test fun remoteVoiceProviderReturnsRealAudio(){
  val c=URL("https://bato-sigma.vercel.app/api/voice").openConnection() as HttpsURLConnection
  c.requestMethod="POST";c.connectTimeout=15_000;c.readTimeout=90_000;c.doOutput=true;c.setRequestProperty("Content-Type","application/json")
  c.outputStream.use{it.write("{\"text\":\"Добар дан. Настављамо тамо где смо стали.\"}".toByteArray())}
  assertEquals("VOICE_HTTP",200,c.responseCode);val bytes=c.inputStream.use{it.readBytes()};assertTrue("VOICE_AUDIO_SIZE=${bytes.size}",bytes.size>1000)
  assertEquals("PROVIDER_REAL",c.getHeaderField("X-Bato-Voice-Provenance"));assertEquals("onyx",c.getHeaderField("X-Bato-Voice-Id"));assertEquals("sr-RS",c.getHeaderField("X-Bato-Voice-Locale"));c.disconnect()
 }
}

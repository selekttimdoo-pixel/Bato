package com.vipla.bato

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipla.bato.ai.*
import com.vipla.bato.data.RetrievedItem
import com.vipla.bato.data.StenoEvent
import com.vipla.bato.data.AppDatabase
import com.vipla.bato.data.StenoRepository
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
 private fun empty()=ProviderContext(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),"UNRESOLVED",emptyList(),false)
 private fun event(id:Long,role:String,text:String)=StenoEvent(id,role,text,System.currentTimeMillis(),System.currentTimeMillis(),UUID.randomUUID().toString(),UUID.randomUUID().toString(),"STENO_FIRST:TEST")
 private fun item(id:String,type:String,text:String,entity:String?=null)=RetrievedItem(id,type,System.currentTimeMillis(),text,.98,"TEST_EXACT",entity,"test:continuity","test")
 private suspend fun ask(message:String,context:ProviderContext):ProviderResult.Response = when(val result=HttpProviderBridge(endpoint).respond(message,context)){
  is ProviderResult.Response -> result
  is ProviderResult.Blocked -> throw AssertionError("LIVE_PROVIDER_BLOCKED for '$message': ${result.reason}")
 }
 private fun prove(name:String,ok:Boolean,result:ProviderResult.Response){
  if(!ok) throw AssertionError("$name | text=${result.text} | ids=${result.retrievedItemIds} | datetime=${result.currentDatetimeUsed} | graph=${result.graphResolution}")
 }

 @Test fun localResolverCarriesRomanEntityAcrossPronouns()=runBlocking {
  val app=InstrumentationRegistry.getInstrumentation().targetContext
  val repo=StenoRepository(AppDatabase.get(app).stenoDao())
  repo.putKnowledge("entity:roman_empire","FAST GRAPH","entity=ROMAN_EMPIRE; aliases=Rimsko carstvo|Rim; cluster=history:roman; domain=history; relation=HAS_WESTERN_GOVERNMENT:WESTERN_ROMAN_EMPIRE; relation=CONTINUATION:EASTERN_ROMAN_EMPIRE")
  repo.putKnowledge("entity:western_roman_empire","FAST GRAPH","entity=WESTERN_ROMAN_EMPIRE; aliases=Zapadno rimsko carstvo; cluster=history:roman; domain=history; conventional_end=476; relation=PART_OF:ROMAN_EMPIRE")
  val u1=repo.append("USER","Bato, kada je palo Rimsko carstvo?","STENO_FIRST:TEST")
  repo.append("ASSISTANT","Pitanje se odnosi na Rimsko carstvo kao celinu, ne samo na zapadnu carsku vlast.","PROVIDER_REAL")
  repo.append("USER","Šta sam te ja pitao?","STENO_FIRST:TEST")
  repo.append("ASSISTANT","Pitao si kada je palo Rimsko carstvo.","PROVIDER_REAL")
  val u3=repo.append("USER","Onda me očigledno to zanima.","STENO_FIRST:TEST")
  val recent=repo.context(8).filter{it.id!=u3.id}.takeLast(8)
  val result=repo.retrieve(u3.rawText,u3.id,recent)
  assertEquals("RESOLVED:ROMAN_EMPIRE",result.graphResolution)
  assertTrue("STENO must be retrieved for anaphoric follow-up",result.steno.any{it.id=="STENO:${u1.id}"})
  assertTrue("FAST GRAPH Roman entity must be attached",result.fastGraph.any{it.entityId.equals("roman_empire",true) || it.canonicalText.contains("ROMAN_EMPIRE")})
  assertTrue("Relevant context must trigger hard retrieval assertion",result.retrievalRequired)
  assertTrue("Context must suppress generic ambiguity",result.ambiguityCandidates.isEmpty())
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
  val c1=ProviderContext(emptyList(),emptyList(),romanFacts,graph,emptyList(),listOf("ROMAN_EMPIRE"),"RESOLVED:ROMAN_EMPIRE",emptyList(),true)
  val u1="Bato, kada je palo Rimsko carstvo?"; val a1=ask(u1,c1)
  prove("U1_ENTITY_NOT_SUBSTITUTED",a1.text.contains("476")&&a1.text.contains("1453")&&a1.retrievalUsed,a1)
  val h2=listOf(event(101,"USER",u1),event(102,"ASSISTANT",a1.text)); val a2=ask("Šta sam te ja pitao?",c1.copy(recentConversation=h2))
  prove("U2_EXACT_SEMANTIC_RECALL",a2.text.contains("Rimsk",true)&&a2.text.contains("pitao",true)&&a2.retrievalUsed,a2)
  val h3=h2+event(103,"USER","Šta sam te ja pitao?")+event(104,"ASSISTANT",a2.text); val a3=ask("Onda me očigledno to zanima.",c1.copy(recentConversation=h3))
  prove("U3_ANAPHORA_NO_RESET",!a3.text.contains("da li misliš",true)&&!a3.text.trim().endsWith("?")&&a3.text.contains("Rimsk",true)&&a3.retrievalUsed,a3)
  val h4=h3+event(105,"USER","Onda me očigledno to zanima.")+event(106,"ASSISTANT",a3.text); val a4=ask("Kako je onda palo ako je nastavilo da postoji?",c1.copy(recentConversation=h4))
  prove("U4_CONTRADICTION_RESOLVED",a4.text.contains("zapad",true)&&a4.text.contains("476")&&a4.text.contains("1453")&&a4.retrievalUsed,a4)
 }

 @Test fun generalStenoGraphRiznicaContinuityAndInspectableBundle()=runBlocking {
  val recent=listOf(
   event(201,"USER","Za projekat Svetionik, moj izraz 'plava soba' znači rezervni laboratorijski čvor."),
   event(202,"ASSISTANT","Razumem: plava soba u projektu Svetionik označava rezervni laboratorijski čvor."),
   event(203,"USER","Sada kratko pričamo o vremenu."),
   event(204,"ASSISTANT","U redu.")
  )
  val steno=listOf(item("STENO:SVETIONIK-201","LOCAL_STENO","Korisnikov izraz 'plava soba' u projektu Svetionik znači rezervni laboratorijski čvor.","SVETIONIK"))
  val riznica=listOf(item("RIZNICA:SVETIONIK-7","RIZNICA","Rezervni laboratorijski čvor projekta Svetionik koristi kod LUMEN-909.","SVETIONIK"))
  val graph=listOf(item("GRAPH:SVETIONIK","FAST_GRAPH","entity=SVETIONIK; alias=plava soba; relation=HAS_BACKUP_NODE:LAB_BACKUP; cluster=project:svetionik","SVETIONIK"))
  val context=ProviderContext(recent,steno,riznica,graph,emptyList(),listOf("SVETIONIK"),"RESOLVED:SVETIONIK",emptyList(),true)
  val answer=ask("A koji je njegov kod?",context)
  prove("GENERAL_ANAPHORA_TEXT",answer.text.contains("LUMEN-909"),answer)
  prove("GENERAL_STENO_ID","STENO:SVETIONIK-201" in answer.retrievedItemIds,answer)
  prove("GENERAL_RIZNICA_ID","RIZNICA:SVETIONIK-7" in answer.retrievedItemIds,answer)
  prove("GENERAL_GRAPH_RESOLUTION",answer.graphResolution=="RESOLVED:SVETIONIK",answer)
  prove("GENERAL_CONTEXT_BUNDLE_IDS",listOf("STENO:SVETIONIK-201","RIZNICA:SVETIONIK-7","GRAPH:SVETIONIK").all{answer.contextBundle.contains(it)},answer)

  val ambiguity=ask("Nastavi o čvoru.",ProviderContext(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),listOf("NETWORK_NODE","GRAPH_NODE"),"AMBIGUOUS:NETWORK_NODE|GRAPH_NODE",listOf("NETWORK_NODE","GRAPH_NODE")))
  prove("GENERAL_TRUE_AMBIGUITY_ONE_QUESTION",ambiguity.text.count{it=='?'}==1,ambiguity)
 }

 @Test fun unacceptedVoiceIsFailClosed(){
  val c=URL("https://bato-sigma.vercel.app/api/voice").openConnection() as HttpsURLConnection
  c.requestMethod="POST";c.connectTimeout=15_000;c.readTimeout=90_000;c.doOutput=true;c.setRequestProperty("Content-Type","application/json")
  c.outputStream.use{it.write("{\"text\":\"Добар дан. Настављамо тамо где смо стали.\"}".toByteArray())}
  assertEquals("VOICE_MUST_REMAIN_BLOCKED_UNTIL_PROVIDER_IS_PROVISIONED",503,c.responseCode);val error=c.errorStream.bufferedReader().use{it.readText()};assertTrue(error.contains("not provisioned"));c.disconnect()
 }
}

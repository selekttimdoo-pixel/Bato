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
}

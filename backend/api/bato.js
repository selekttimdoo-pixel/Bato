const GATEWAY="https://ai-gateway.vercel.sh/v1/chat/completions";
const MODEL=process.env.BATO_MODEL||"openai/gpt-5.6-sol";
const MAX={recent:14,steno:10,riznica:8,graph:8,lexical:4,chars:5000};

function timezone(value){try{const z=typeof value==="string"&&value.length<100?value:"UTC";new Intl.DateTimeFormat("en-CA",{timeZone:z}).format();return z}catch{return"UTC"}}
function clock(z){const now=new Date();const p=new Intl.DateTimeFormat("en-CA",{timeZone:z,year:"numeric",month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit",second:"2-digit",hourCycle:"h23"}).formatToParts(now).reduce((a,x)=>({...a,[x.type]:x.value}),{});return{CURRENT_DATETIME:`${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}:${p.second}[${z}]`,CURRENT_DATE:`${p.year}-${p.month}-${p.day}`,CURRENT_TIME:`${p.hour}:${p.minute}:${p.second}`,TIMEZONE:z}}
function recent(value){return(Array.isArray(value)?value:[]).slice(-MAX.recent).flatMap(x=>x&&typeof x.content==="string"?[{role:x.role==="assistant"?"assistant":"user",content:x.content.slice(0,MAX.chars),timestamp_ms:x.timestamp_ms??null,provenance:String(x.provenance||"UNREPORTED"),source_id:String(x.source_id||"UNREPORTED")}]:[])}
function items(value,limit){return(Array.isArray(value)?value:[]).slice(0,limit).flatMap(x=>{const text=x?.canonical_text??x?.content;if(typeof text!=="string"||!x?.source_id)return[];return[{source_type:String(x.source_type||"UNKNOWN"),source_id:String(x.source_id),timestamp_ms:x.timestamp_ms??null,canonical_text:text.slice(0,MAX.chars),confidence:Number(x.confidence)||0,provenance:String(x.provenance||"UNREPORTED"),entity_id:x.entity_id||null,cluster_id:x.cluster_id||null,domain:x.domain||null}]}).sort((a,b)=>b.confidence-a.confidence)}
function attachedIds(bundle){return[...bundle.STENO_RETRIEVAL,...bundle.RIZNICA_RETRIEVAL,...bundle.FAST_GRAPH_CONTEXT,...bundle.LEXICAL_GRAMMAR_CONTEXT].map(x=>x.source_id)}
function evidenceOverlap(answer,item){const stop=new Set(["kroz","koji","koja","koje","biti","smo","sam","nije","jedan","jednog","na","u","i","je","se","da","za","od","o","a"]);const tok=s=>new Set(String(s).toLocaleLowerCase("sr").match(/[\p{L}\p{N}-]{3,}/gu)?.filter(x=>!stop.has(x))||[]);const a=tok(answer),b=tok(item.canonical_text);if(!b.size)return 0;let hit=0;b.forEach(x=>{if(a.has(x))hit++});return hit/b.size}

export default async function handler(req,res){
 if(req.method!=="POST")return res.status(405).json({error:"POST required",provenance:"BLOCKED"});
 const token=process.env.AI_GATEWAY_API_KEY||process.env.VERCEL_OIDC_TOKEN;if(!token)return res.status(503).json({error:"Provider credential unavailable; no answer fabricated",provenance:"BLOCKED"});
 const message=String(req.body?.current_user_message||req.body?.message||"").trim();if(!message)return res.status(400).json({error:"current_user_message is required",provenance:"BLOCKED"});
 const current=clock(timezone(req.body?.timezone));
 const bundle={...current,RECENT_CONVERSATION:recent(req.body?.recent_conversation),RESOLVED_ENTITIES:(Array.isArray(req.body?.resolved_entities)?req.body.resolved_entities:[]).slice(0,12),FAST_GRAPH_CONTEXT:items(req.body?.fast_graph_context,MAX.graph),STENO_RETRIEVAL:items(req.body?.steno_retrieval,MAX.steno),RIZNICA_RETRIEVAL:items(req.body?.riznica_retrieval,MAX.riznica),LEXICAL_GRAMMAR_CONTEXT:items(req.body?.lexical_grammar_context,MAX.lexical),FAST_GRAPH_RESOLUTION:String(req.body?.fast_graph_resolution||"UNRESOLVED"),AMBIGUITY_CANDIDATES:(Array.isArray(req.body?.ambiguity_candidates)?req.body.ambiguity_candidates:[]).slice(0,3),CURRENT_USER_MESSAGE:message};
 const ids=attachedIds(bundle);
 const contract=[
  "You are BATO, one continuous Serbian-first assistant, not a stateless encyclopedia.",
  "Interpret CURRENT_USER_MESSAGE literally before retrieval. Retrieval informs reasoning but must never silently replace the entity named by the user with a narrower related entity.",
  "Roman ontology: ROMAN_EMPIRE is not synonymous with WESTERN_ROMAN_EMPIRE. WESTERN_ROMAN_EMPIRE ended conventionally in 476; EASTERN_ROMAN_EMPIRE continued at Constantinople until 1453; BYZANTINE_EMPIRE is a later historiographical label; its inhabitants normally identified as Romans/Rhomaioi (Romeji). Preserve this distinction across follow-ups.",
  "Return ONLY a JSON object: {answer:string, used_item_ids:string[], grounding_summary:string, current_datetime_used:boolean}.",
  "Continue the user's specific prior framing. When relevant retrieved conversation exists, it outranks generic background knowledge.",
  "Never claim you used an item unless its exact source_id appears in used_item_ids and materially controls the answer.",
  "If retrieved context conflicts with world knowledge, explicitly separate 'our prior discussion/project framing' from general historical facts.",
  "If AMBIGUITY_CANDIDATES has two plausible entries, ask one short disambiguation question and do not guess.",
  "Use natural idiomatic Serbian with correct cases, verb forms and sentence structure. Do not translate word by word.",
  "CURRENT_* fields are authoritative. Never guess date or time.",
  "Do not mention internal source IDs in the conversational answer.",
  `STRUCTURED_CONTEXT=${JSON.stringify(bundle)}`
 ].join("\n");
 const messages=[{role:"system",content:contract},...bundle.RECENT_CONVERSATION.map(x=>({role:x.role,content:`[${x.source_id}; ${x.provenance}; ${x.timestamp_ms??"unknown"}] ${x.content}`})),{role:"user",content:message}];
 try{
  const upstream=await fetch(GATEWAY,{method:"POST",headers:{"content-type":"application/json",authorization:`Bearer ${token}`},body:JSON.stringify({model:MODEL,messages,response_format:{type:"json_object"},temperature:.2})});
  const raw=await upstream.text();if(!upstream.ok)return res.status(502).json({error:`Provider ${upstream.status}`,detail:raw.slice(0,700),provenance:"BLOCKED",http_status:502});
  const data=JSON.parse(raw);const content=data?.choices?.[0]?.message?.content;if(!content)return res.status(502).json({error:"Provider returned no content; no answer fabricated",provenance:"BLOCKED"});
  let grounded;try{grounded=JSON.parse(content)}catch{return res.status(502).json({error:"Provider violated grounded JSON contract",detail:content.slice(0,500),provenance:"BLOCKED"})}
  if(bundle.AMBIGUITY_CANDIDATES.length>1&&!String(grounded.answer||"").includes("?")){
   const ambiguityMessages=[...messages,{role:"assistant",content:JSON.stringify(grounded)},{role:"user",content:`AMBIGUITY GATE: You selected a candidate without evidence. Do not answer the topic. Return the JSON contract with one short Serbian disambiguation question ending in ?, asking which of these candidates the user means: ${JSON.stringify(bundle.AMBIGUITY_CANDIDATES)}. used_item_ids must be empty.`}];
   const correction=await fetch(GATEWAY,{method:"POST",headers:{"content-type":"application/json",authorization:`Bearer ${token}`},body:JSON.stringify({model:MODEL,messages:ambiguityMessages,response_format:{type:"json_object"},temperature:0})});
   if(!correction.ok)return res.status(502).json({error:"Ambiguity correction provider failed",provenance:"BLOCKED"});
   const correctionData=await correction.json();try{grounded=JSON.parse(correctionData?.choices?.[0]?.message?.content||"")}catch{return res.status(502).json({error:"Provider violated ambiguity JSON contract",provenance:"BLOCKED"})}
   if(typeof grounded.answer!=="string"||!grounded.answer.includes("?"))return res.status(502).json({error:"Provider failed ambiguity gate; no candidate was guessed",provenance:"BLOCKED"});
  }
  let declared=Array.isArray(grounded.used_item_ids)?grounded.used_item_ids.filter(id=>ids.includes(id)):[];
  if(ids.length&&declared.length===0){
   const auditMessages=[...messages,{role:"assistant",content:JSON.stringify(grounded)},{role:"user",content:`GROUNDING AUDIT: The answer may paraphrase attached evidence. Return the same JSON contract. If any exact/canonical retrieved fact materially controlled the answer, list every corresponding source_id from this allowed set: ${JSON.stringify(ids)}. If none controlled it, keep used_item_ids empty and rewrite the answer so it does not imply retrieved-memory grounding.`}];
   const audit=await fetch(GATEWAY,{method:"POST",headers:{"content-type":"application/json",authorization:`Bearer ${token}`},body:JSON.stringify({model:MODEL,messages:auditMessages,response_format:{type:"json_object"},temperature:0})});
   if(audit.ok){const auditData=await audit.json();const auditContent=auditData?.choices?.[0]?.message?.content;try{const checked=JSON.parse(auditContent);if(typeof checked.answer==="string"&&checked.answer.trim()){grounded=checked;declared=Array.isArray(checked.used_item_ids)?checked.used_item_ids.filter(id=>ids.includes(id)):[]}}catch{}}
  }
  if(declared.length===0){
   const all=[...bundle.STENO_RETRIEVAL,...bundle.RIZNICA_RETRIEVAL,...bundle.FAST_GRAPH_CONTEXT,...bundle.LEXICAL_GRAMMAR_CONTEXT];
   declared=all.filter(x=>evidenceOverlap(grounded.answer,x)>=0.45).map(x=>x.source_id);
   if(declared.length)grounded.grounding_summary=`SERVER_VALIDATED_TEXTUAL_GROUNDING: ${declared.join(",")}; ${String(grounded.grounding_summary||"")}`;
  }
  if(typeof grounded.answer!=="string"||!grounded.answer.trim())return res.status(502).json({error:"Provider returned no answer; no answer fabricated",provenance:"BLOCKED"});
  const used=[...new Set(declared)];
  const sources=[...new Set(used.map(id=>id.split(":")[0]==="GRAPH"?"FAST_GRAPH_CONTEXT":id.split(":")[0]==="STENO"?"STENO_RETRIEVAL":id.split(":")[0]==="RIZNICA"?"RIZNICA_RETRIEVAL":"LEXICAL_GRAMMAR_CONTEXT"))];
  return res.status(200).json({response:grounded.answer.trim(),provenance:"PROVIDER_REAL",local_fallback:false,imported:false,http_status:200,provider:"vercel-ai-gateway",provider_model:data.model||MODEL,retrieval_used:used.length>0,retrieval_sources:sources,retrieved_item_ids:used,fast_graph_resolution:bundle.FAST_GRAPH_RESOLUTION,current_datetime_used:grounded.current_datetime_used===true,grounding_summary:String(grounded.grounding_summary||""),context_bundle:bundle});
 }catch(error){return res.status(502).json({error:"Provider request failed",detail:String(error?.message||error),provenance:"BLOCKED",http_status:502})}
}

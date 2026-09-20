const GATEWAY="https://ai-gateway.vercel.sh/v4/ai/speech-model";
const MODEL=process.env.BATO_VOICE_MODEL||"openai/tts-1-hd";
const VOICE=process.env.BATO_VOICE_ID||"onyx";


export default async function handler(req,res){
 if(req.method!=="POST")return res.status(405).json({error:"POST required",voice_provenance:"BLOCKED"});
 if(process.env.BATO_VOICE_PHYSICALLY_ACCEPTED!=="true")return res.status(503).json({error:"BATO voice is blocked: no native Serbian adult male provider has passed physical listening QA",voice_provenance:"BLOCKED",voice_fallback:"NONE",rejected_candidate:"openai/tts-1-hd/onyx"});
 const token=process.env.AI_GATEWAY_API_KEY||process.env.VERCEL_OIDC_TOKEN;
 if(!token)return res.status(503).json({error:"Voice provider credential unavailable",voice_provenance:"BLOCKED"});
 const text=String(req.body?.text||"").trim();
 if(!text||text.length>4096)return res.status(400).json({error:"text must contain 1-4096 characters",voice_provenance:"BLOCKED"});
 try{
  const upstream=await fetch(GATEWAY,{method:"POST",headers:{"content-type":"application/json",authorization:`Bearer ${token}`,"ai-gateway-protocol-version":"0.0.1","ai-speech-model-specification-version":"4","ai-model-id":MODEL},body:JSON.stringify({text,voice:VOICE,outputFormat:"mp3",speed:0.94,language:"sr",instructions:"Govori toplim, ozbiljnim glasom odraslog muškog radio-naratora. Izgovaraj srpski prirodno, bez stranog akcenta, uz smirene pauze i razgovornu intonaciju."})});
  if(!upstream.ok){const detail=(await upstream.text()).slice(0,600);return res.status(502).json({error:`Voice provider ${upstream.status}`,detail,voice_provenance:"BLOCKED"})}
  const payload=await upstream.json();
  const audio=Buffer.from(String(payload?.audio||""),"base64");if(!audio.length)return res.status(502).json({error:"Voice provider returned empty audio",warnings:payload?.warnings||[],voice_provenance:"BLOCKED"});
  res.setHeader("Content-Type","audio/mpeg");res.setHeader("Cache-Control","no-store");res.setHeader("X-Bato-Voice-Provider","vercel-ai-gateway/openai");res.setHeader("X-Bato-Voice-Model",MODEL);res.setHeader("X-Bato-Voice-Id",VOICE);res.setHeader("X-Bato-Voice-Locale","sr-RS");res.setHeader("X-Bato-Voice-Provenance","PROVIDER_REAL");res.setHeader("X-Bato-Voice-Fallback","false");res.setHeader("X-Bato-Tts-Http-Status","200");
  return res.status(200).send(audio);
 }catch(error){return res.status(502).json({error:"Voice request failed",detail:String(error?.message||error),voice_provenance:"BLOCKED"})}
}


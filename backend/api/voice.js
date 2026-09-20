const PROVIDER = "azure-speech";
const MODEL = process.env.BATO_VOICE_MODEL || "azure-neural-tts";
const VOICE = process.env.BATO_VOICE_ID || "sr-RS-NicholasNeural";
const LOCALE = "sr-RS";
const FORMAT = "audio-24khz-160kbitrate-mono-mp3";

function escapeXml(value) {
  return value.replace(/[<>&"']/g, c => ({"<":"&lt;",">":"&gt;","&":"&amp;",'"':"&quot;","'":"&apos;"}[c]));
}

export default async function handler(req, res) {
  if (req.method !== "POST") return res.status(405).json({error:"POST required", voice_provenance:"BLOCKED"});
  const text = String(req.body?.text || "").trim();
  if (!text || text.length > 4096) return res.status(400).json({error:"text must contain 1-4096 characters", voice_provenance:"BLOCKED"});

  // QA_ENABLED serves the candidate so the phone can perform physical listening QA.
  // PHYSICALLY_ACCEPTED controls provenance only; API 200 never claims listening acceptance.
  if (process.env.BATO_VOICE_QA_ENABLED !== "true") {
    return res.status(503).json({error:"Serbian male neural voice candidate is not provisioned", required_action:"Configure AZURE_SPEECH_KEY, AZURE_SPEECH_REGION and BATO_VOICE_QA_ENABLED=true", voice_provenance:"BLOCKED", voice_fallback:"NONE"});
  }
  const key = process.env.AZURE_SPEECH_KEY;
  const region = process.env.AZURE_SPEECH_REGION;
  if (!key || !region) return res.status(503).json({error:"Azure Speech server credential unavailable", voice_provenance:"BLOCKED", voice_fallback:"NONE"});

  const accepted = process.env.BATO_VOICE_PHYSICALLY_ACCEPTED === "true";
  const ssml = `<speak version="1.0" xml:lang="${LOCALE}"><voice name="${VOICE}"><prosody rate="-4%">${escapeXml(text)}</prosody></voice></speak>`;
  try {
    const upstream = await fetch(`https://${region}.tts.speech.microsoft.com/cognitiveservices/v1`, {
      method:"POST",
      headers:{"Ocp-Apim-Subscription-Key":key,"Content-Type":"application/ssml+xml","X-Microsoft-OutputFormat":FORMAT,"User-Agent":"VIPLA-BATO"},
      body:ssml
    });
    const body = Buffer.from(await upstream.arrayBuffer());
    if (!upstream.ok) return res.status(502).json({error:`Voice provider ${upstream.status}`, detail:body.toString("utf8").slice(0,600), voice_provenance:"BLOCKED"});
    if (body.length < 128) return res.status(502).json({error:"Voice provider returned empty audio", voice_provenance:"BLOCKED"});

    res.setHeader("Content-Type","audio/mpeg");
    res.setHeader("Content-Length",String(body.length));
    res.setHeader("Cache-Control","no-store");
    res.setHeader("X-Bato-Voice-Provider",PROVIDER);
    res.setHeader("X-Bato-Voice-Model",MODEL);
    res.setHeader("X-Bato-Voice-Id",VOICE);
    res.setHeader("X-Bato-Voice-Locale",LOCALE);
    res.setHeader("X-Bato-Voice-Format",FORMAT);
    res.setHeader("X-Bato-Voice-Provenance",accepted ? "PHYSICALLY_ACCEPTED" : "QA_CANDIDATE_NOT_YET_ACCEPTED");
    res.setHeader("X-Bato-Voice-Fallback","NONE");
    res.setHeader("X-Bato-Tts-Http-Status","200");
    return res.status(200).send(body);
  } catch (error) {
    return res.status(502).json({error:"Voice request failed", detail:String(error?.message || error), voice_provenance:"BLOCKED", voice_fallback:"NONE"});
  }
}

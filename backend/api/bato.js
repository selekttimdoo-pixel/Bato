const GATEWAY = "https://ai-gateway.vercel.sh/v1/chat/completions";
const MAX_ITEMS = 16;
const MAX_CONTENT = 4000;

function boundedItems(value, limit = MAX_ITEMS) {
  return (Array.isArray(value) ? value : []).slice(-limit).flatMap((item) => {
    if (!item || typeof item.content !== "string") return [];
    return [{
      role: item.role === "assistant" ? "assistant" : "user",
      content: item.content.slice(0, MAX_CONTENT),
      timestamp_ms: Number.isFinite(item.timestamp_ms) ? item.timestamp_ms : null,
      provenance: typeof item.provenance === "string" ? item.provenance.slice(0, 200) : "UNREPORTED",
      source: typeof item.source === "string" ? item.source.slice(0, 200) : "UNREPORTED"
    }];
  });
}

function boundedKnowledge(value, limit = 8) {
  return (Array.isArray(value) ? value : []).slice(-limit).flatMap((item) => {
    if (!item || typeof item.content !== "string") return [];
    return [{
      key: String(item.key || "unknown").slice(0, 200),
      layer: String(item.layer || "unknown").slice(0, 200),
      content: item.content.slice(0, MAX_CONTENT),
      timestamp_ms: Number.isFinite(item.timestamp_ms) ? item.timestamp_ms : null,
      provenance: String(item.provenance || "UNREPORTED").slice(0, 200)
    }];
  });
}

function validTimezone(candidate) {
  const timezone = typeof candidate === "string" && candidate.length <= 100 ? candidate : "UTC";
  try {
    new Intl.DateTimeFormat("en-CA", { timeZone: timezone }).format(new Date());
    return timezone;
  } catch {
    return "UTC";
  }
}

function currentClock(timezone) {
  const now = new Date();
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: timezone, year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23"
  }).formatToParts(now).reduce((result, part) => ({ ...result, [part.type]: part.value }), {});
  const date = `${parts.year}-${parts.month}-${parts.day}`;
  const time = `${parts.hour}:${parts.minute}:${parts.second}`;
  return { current_datetime: `${date}T${time} [${timezone}]`, current_date: date, current_time: time, timezone };
}

export default async function handler(request, response) {
  if (request.method !== "POST") return response.status(405).json({ error: "POST required" });
  const token = process.env.AI_GATEWAY_API_KEY || process.env.VERCEL_OIDC_TOKEN;
  if (!token) return response.status(503).json({ error: "Provider credential unavailable on server; no response fabricated", provenance: "BLOCKED" });
  const message = typeof request.body?.message === "string" ? request.body.message.trim() : "";
  if (!message) return response.status(400).json({ error: "message is required", provenance: "BLOCKED" });

  const timezone = validTimezone(request.body?.timezone);
  const clock = currentClock(timezone);
  const recent = boundedItems(request.body?.recent_conversation || request.body?.history);
  const steno = boundedItems(request.body?.steno_retrieval, 8);
  const riznica = boundedKnowledge(request.body?.riznica_retrieval, 8);
  const fastGraph = boundedKnowledge(request.body?.fast_graph_context, 8);
  const retrievalSources = [
    ...(steno.length ? ["STENO_RETRIEVAL"] : []),
    ...(riznica.length ? ["RIZNICA_RETRIEVAL"] : []),
    ...(fastGraph.length ? ["FAST_GRAPH_CONTEXT"] : [])
  ];
  const contextEnvelope = {
    CURRENT_DATETIME: clock.current_datetime,
    CURRENT_DATE: clock.current_date,
    CURRENT_TIME: clock.current_time,
    TIMEZONE: clock.timezone,
    RECENT_CONVERSATION: recent,
    STENO_RETRIEVAL: steno,
    RIZNICA_RETRIEVAL: riznica,
    FAST_GRAPH_CONTEXT: fastGraph
  };
  const messages = [
    {
      role: "system",
      content: [
        "You are BATO, a Serbian-first local-first assistant.",
        "The CURRENT_* values in the attached context are authoritative for this request. Never guess the date or time.",
        "Use recent conversation and retrieved facts when relevant. Prefer exact stored facts over generic guesses.",
        "Treat provenance labels as evidence boundaries. Never claim an external effect without evidence.",
        "Answer honestly and concisely in the user's language.",
        `CONTEXT_ENVELOPE=${JSON.stringify(contextEnvelope)}`
      ].join("\n")
    },
    ...recent.map(({ role, content, timestamp_ms, provenance }) => ({
      role,
      content: `[timestamp_ms=${timestamp_ms ?? "unknown"}; provenance=${provenance}] ${content}`
    })),
    { role: "user", content: message }
  ];
  const requestedModel = process.env.BATO_MODEL || "openai/gpt-5.4";
  try {
    const upstream = await fetch(GATEWAY, {
      method: "POST",
      headers: { "content-type": "application/json", authorization: `Bearer ${token}` },
      body: JSON.stringify({ model: requestedModel, messages })
    });
    const raw = await upstream.text();
    if (!upstream.ok) return response.status(502).json({ error: `Provider ${upstream.status}`, detail: raw.slice(0, 500), provenance: "BLOCKED", http_status: 502 });
    const data = JSON.parse(raw);
    const text = data?.choices?.[0]?.message?.content;
    if (!text) return response.status(502).json({ error: "Provider returned no assistant content", provenance: "BLOCKED", http_status: 502 });
    return response.status(200).json({
      response: text, provenance: "PROVIDER_REAL", local_fallback: false, imported: false,
      retrieval_used: retrievalSources.length > 0, retrieval_sources: retrievalSources,
      http_status: 200, provider: "vercel-ai-gateway",
      provider_model: data.model || requestedModel, ...clock
    });
  } catch (error) {
    return response.status(502).json({ error: "Provider request failed", detail: String(error?.message || error), provenance: "BLOCKED", http_status: 502 });
  }
}

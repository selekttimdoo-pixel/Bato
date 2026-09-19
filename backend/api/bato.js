const GATEWAY = "https://ai-gateway.vercel.sh/v1/chat/completions";

export default async function handler(request, response) {
  if (request.method !== "POST") return response.status(405).json({ error: "POST required" });
  const token = process.env.AI_GATEWAY_API_KEY || process.env.VERCEL_OIDC_TOKEN;
  if (!token) return response.status(503).json({ error: "Provider credential unavailable on server; no response fabricated" });
  const message = typeof request.body?.message === "string" ? request.body.message.trim() : "";
  if (!message) return response.status(400).json({ error: "message is required" });
  const prior = Array.isArray(request.body?.history) ? request.body.history.slice(-16) : [];
  const messages = [
    { role: "system", content: "You are BATO, a Serbian-first local-first assistant. Answer honestly. Never claim an external effect without evidence." },
    ...prior.filter(x => x && ["user", "assistant"].includes(x.role) && typeof x.content === "string"),
    { role: "user", content: message }
  ];
  try {
    const upstream = await fetch(GATEWAY, {
      method: "POST",
      headers: { "content-type": "application/json", authorization: `Bearer ${token}` },
      body: JSON.stringify({ model: process.env.BATO_MODEL || "openai/gpt-4o-mini", messages })
    });
    const raw = await upstream.text();
    if (!upstream.ok) return response.status(502).json({ error: `Provider ${upstream.status}`, detail: raw.slice(0, 500) });
    const data = JSON.parse(raw);
    const text = data?.choices?.[0]?.message?.content;
    if (!text) return response.status(502).json({ error: "Provider returned no assistant content" });
    return response.status(200).json({ response: text, provider: "vercel-ai-gateway", model: data.model || process.env.BATO_MODEL || "configured" });
  } catch (error) {
    return response.status(502).json({ error: "Provider request failed", detail: String(error?.message || error) });
  }
}

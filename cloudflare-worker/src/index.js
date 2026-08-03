/**
 * AutoKabala AI proxy — Cloudflare Worker.
 *
 * Holds the real Anthropic / OpenAI / Gemini / Google Vision API keys as
 * Worker secrets. The Android app never sees them: it calls this Worker
 * with a shared app secret, and the Worker attaches the real provider key
 * server-side before forwarding the request upstream.
 *
 * Routes (all POST):
 *   /claude/v1/messages          -> https://api.anthropic.com/v1/messages
 *   /openai/v1/chat/completions  -> https://api.openai.com/v1/chat/completions
 *   /gemini/v1beta/generate      -> https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent
 *   /vision/v1/images:annotate   -> https://vision.googleapis.com/v1/images:annotate
 */

const ROUTES = {
  "/claude/v1/messages": handleClaude,
  "/openai/v1/chat/completions": handleOpenAi,
  "/gemini/v1beta/generate": handleGemini,
  "/vision/v1/images:annotate": handleVision,
};

export default {
  async fetch(request, env) {
    if (request.method !== "POST") {
      return new Response("Not found", { status: 404 });
    }

    // Shared-secret gate: only requests from the app (which embeds
    // APP_SHARED_SECRET) may use the proxy. This does not protect against a
    // determined attacker decompiling the APK, but it stops casual scraping
    // of the Worker URL and lets you rotate/revoke access independently of
    // the upstream provider keys.
    const appSecret = request.headers.get("X-App-Secret");
    if (!env.APP_SHARED_SECRET || appSecret !== env.APP_SHARED_SECRET) {
      return new Response("Unauthorized", { status: 401 });
    }

    const url = new URL(request.url);
    const handler = ROUTES[url.pathname];
    if (!handler) {
      return new Response("Not found", { status: 404 });
    }

    try {
      return await handler(request, env);
    } catch (err) {
      return new Response(`Proxy error: ${err.message}`, { status: 502 });
    }
  },
};

async function handleClaude(request, env) {
  const body = await request.text();
  const upstream = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-api-key": env.ANTHROPIC_API_KEY,
      "anthropic-version": "2023-06-01",
    },
    body,
  });
  return passThrough(upstream);
}

async function handleOpenAi(request, env) {
  const body = await request.text();
  const upstream = await fetch("https://api.openai.com/v1/chat/completions", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${env.OPENAI_API_KEY}`,
    },
    body,
  });
  return passThrough(upstream);
}

async function handleGemini(request, env) {
  const body = await request.text();
  const upstream = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${env.GEMINI_API_KEY}`,
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body,
    }
  );
  return passThrough(upstream);
}

async function handleVision(request, env) {
  const body = await request.text();
  const upstream = await fetch(
    `https://vision.googleapis.com/v1/images:annotate?key=${env.GOOGLE_VISION_API_KEY}`,
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body,
    }
  );
  return passThrough(upstream);
}

function passThrough(upstream) {
  return new Response(upstream.body, {
    status: upstream.status,
    headers: { "content-type": upstream.headers.get("content-type") ?? "application/json" },
  });
}

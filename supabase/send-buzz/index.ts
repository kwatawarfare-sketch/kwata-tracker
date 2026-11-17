// Kwata Tracker: push an "I love you" alert to everyone who gets a Buzz.
// Triggered by a Database Webhook on INSERT into public.buzzes.
// Secret needed: FCM_SERVICE_ACCOUNT (the Firebase service account JSON).
import { createClient } from "jsr:@supabase/supabase-js@2";

function b64url(bytes: Uint8Array): string {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

let cached: { token: string; exp: number } | null = null;
async function googleToken(sa: any): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cached && cached.exp - 60 > now) return cached.token;
  const enc = (o: unknown) => b64url(new TextEncoder().encode(JSON.stringify(o)));
  const unsigned = enc({ alg: "RS256", typ: "JWT" }) + "." + enc({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  });
  const pem = String(sa.private_key).replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const sig = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)));
  const r = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=" + unsigned + "." + b64url(sig),
  });
  const j = await r.json();
  if (!j.access_token) throw new Error("Google token failed: " + JSON.stringify(j));
  cached = { token: j.access_token, exp: now + (j.expires_in || 3600) };
  return j.access_token;
}

// the secret may carry stray text after the JSON: read the first complete {...} object
function parseSA(raw: string): any {
  const start = raw.indexOf("{");
  let depth = 0, inStr = false, esc = false;
  for (let i = start; i < raw.length; i++) {
    const c = raw[i];
    if (inStr) { if (esc) esc = false; else if (c === "\\") esc = true; else if (c === '"') inStr = false; continue; }
    if (c === '"') inStr = true;
    else if (c === "{") depth++;
    else if (c === "}" && --depth === 0) return JSON.parse(raw.slice(start, i + 1));
  }
  return JSON.parse(raw);
}
const json = (o: unknown, status = 200) => new Response(JSON.stringify(o), { status, headers: { "content-type": "application/json" } });

Deno.serve(async (req) => {
  try {
    const body = await req.json().catch(() => ({}));
    const rec = body.record;
    if (!rec || !rec.to_id || !rec.from_id) return json({ error: "no buzz record" }, 400);
    const raw = Deno.env.get("FCM_SERVICE_ACCOUNT");
    if (!raw) return json({ error: "FCM_SERVICE_ACCOUNT secret missing" }, 500);
    const sa = parseSA(raw);
    const sb = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
    const { data: from } = await sb.from("profiles").select("username,name").eq("id", rec.from_id).maybeSingle();
    const { data: toks } = await sb.from("push_tokens").select("token").eq("user_id", rec.to_id);
    if (!toks || !toks.length) return json({ sent: 0, reason: "no device" });
    const who = (from && (from.name || (from.username ? "@" + from.username : ""))) || "Someone";
    const access = await googleToken(sa);
    let sent = 0;
    for (const t of toks) {
      const r = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
        method: "POST",
        headers: { authorization: "Bearer " + access, "content-type": "application/json" },
        body: JSON.stringify({
          message: {
            token: t.token,
            notification: { title: "I love you ❤️", body: who + " buzzed you" },
            data: { type: "buzz", from_id: String(rec.from_id) },
            android: {
              priority: "HIGH",
              notification: {
                channel_id: "kwata_buzz",
                sound: "default",
                default_vibrate_timings: false,
                vibrate_timings: ["0.3s", "0.15s", "0.3s", "0.15s", "0.5s"],
                notification_priority: "PRIORITY_MAX",
                visibility: "PUBLIC",
                color: "#E53935",
              },
            },
          },
        }),
      });
      if (r.ok) sent++;
      else {
        const e = await r.text();
        if (/UNREGISTERED|NOT_FOUND|INVALID_ARGUMENT/.test(e)) await sb.from("push_tokens").delete().eq("token", t.token);
        console.error("FCM error", r.status, e);
      }
    }
    return json({ sent });
  } catch (e) {
    console.error(e);
    return json({ error: String(e) }, 500);
  }
});

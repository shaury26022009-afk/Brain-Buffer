// Supabase Edge Function: delete-account
// Deploy with: supabase functions deploy delete-account
//
// Security guarantees:
// - Accepts POST requests only.
// - Verifies the caller's Supabase JWT server-side via auth.getUser().
// - Deletes ONLY the authenticated user's own account using the server-side
//   SUPABASE_SERVICE_ROLE_KEY (never present in the Android client).
// - Logs internal failures on the server without tokens/secrets and returns
//   generic error payloads to clients.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const JSON_HEADERS = {
  "Content-Type": "application/json",
  "Cache-Control": "no-store",
};

function jsonResponse(body: Record<string, unknown>, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: JSON_HEADERS,
  });
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return jsonResponse({ error: "Method not allowed." }, 405);
  }

  const authHeader = req.headers.get("Authorization")?.trim() ?? "";
  if (!authHeader.startsWith("Bearer ") || authHeader.length <= 7) {
    return jsonResponse({ error: "Authentication required." }, 401);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

  if (!supabaseUrl || !supabaseAnonKey || !serviceRoleKey) {
    console.error("[delete-account] Missing required Supabase runtime environment variables.");
    return jsonResponse({ error: "Account deletion service is temporarily unavailable." }, 500);
  }

  try {
    // 1. Validate caller JWT using an anon client scoped strictly to the caller's Bearer token
    const callerClient = createClient(supabaseUrl, supabaseAnonKey, {
      global: { headers: { Authorization: authHeader } },
      auth: { persistSession: false, autoRefreshToken: false },
    });

    const {
      data: { user },
      error: userError,
    } = await callerClient.auth.getUser();

    if (userError || !user?.id) {
      if (userError) {
        console.warn("[delete-account] Rejected unauthenticated request:", userError.status ?? "unknown");
      }
      return jsonResponse({ error: "Invalid or expired session. Please sign in again." }, 401);
    }

    // 2. Delete the verified user via Admin API (ON DELETE CASCADE removes all owned rows)
    const adminClient = createClient(supabaseUrl, serviceRoleKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });

    const { error: deleteError } = await adminClient.auth.admin.deleteUser(user.id);

    if (deleteError) {
      console.error("[delete-account] Admin deleteUser failed for verified user:", {
        status: deleteError.status ?? "unknown",
        code: deleteError.code ?? "unknown",
      });
      return jsonResponse(
        { error: "Unable to complete account deletion right now. Please try again later." },
        500,
      );
    }

    return jsonResponse({ deleted: true }, 200);
  } catch (err) {
    console.error(
      "[delete-account] Unexpected server exception:",
      err instanceof Error ? err.name : "unknown_error",
    );
    return jsonResponse(
      { error: "Unable to complete account deletion right now. Please try again later." },
      500,
    );
  }
});

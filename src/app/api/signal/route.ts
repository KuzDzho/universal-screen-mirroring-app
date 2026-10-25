import { fail, json } from "@/lib/http";
import { isId } from "@/lib/network";
import { addSignal, clearSignals, listSignals } from "@/lib/receivers";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  const url = new URL(request.url);
  const receiverId = url.searchParams.get("receiverId") || "";
  const listenAs = url.searchParams.get("listenAs") === "sender" ? "sender" : "receiver";
  const after = Number(url.searchParams.get("after") || 0);
  if (!isId(receiverId)) return fail("Неверный телевизор", 400);
  const rows = await listSignals(receiverId, listenAs, Number.isFinite(after) ? after : 0);
  const last = rows.length ? rows[rows.length - 1]!.id : after;
  return json({
    signals: rows.map((row) => ({
      id: row.id,
      fromRole: row.fromRole,
      kind: row.kind,
      payload: row.payload,
    })),
    last,
  });
}

export async function POST(request: Request) {
  const body = (await request.json().catch(() => null)) as {
    receiverId?: string;
    fromRole?: string;
    kind?: string;
    payload?: string;
  } | null;
  if (!body?.receiverId || !isId(body.receiverId)) return fail("Неверный телевизор", 400);
  if (body.fromRole !== "sender" && body.fromRole !== "receiver") return fail("Неверная роль", 400);
  if (!body.kind || !["offer", "answer", "ice"].includes(body.kind)) return fail("Неверный сигнал", 400);
  if (!body.payload || body.payload.length > 200_000) return fail("Слишком большой сигнал", 413);
  const row = await addSignal({
    receiverId: body.receiverId,
    fromRole: body.fromRole,
    kind: body.kind,
    payload: body.payload,
  });
  return json({ id: row.id });
}

export async function DELETE(request: Request) {
  const receiverId = new URL(request.url).searchParams.get("receiverId") || "";
  if (!isId(receiverId)) return fail("Неверный телевизор", 400);
  await clearSignals(receiverId);
  return json({ ok: true });
}

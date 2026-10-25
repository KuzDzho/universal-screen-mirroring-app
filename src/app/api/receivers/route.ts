import { fail, json } from "@/lib/http";
import { clientIp } from "@/lib/network";
import { announceReceiver, listReceivers } from "@/lib/receivers";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  const tvs = await listReceivers(clientIp(request));
  return json({ tvs });
}

export async function POST(request: Request) {
  const body = (await request.json().catch(() => null)) as { id?: string; name?: string } | null;
  if (!body) return fail("Пустой запрос", 400);
  const row = await announceReceiver({
    id: body.id,
    name: body.name || "Телевизор",
    ip: clientIp(request),
  });
  return json({ id: row.id, name: row.name });
}

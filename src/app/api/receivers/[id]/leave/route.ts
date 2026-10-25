import { fail, json } from "@/lib/http";
import { isId } from "@/lib/network";
import { removeReceiver } from "@/lib/receivers";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST(_request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  if (!isId(id)) return fail("Неверный телевизор", 400);
  await removeReceiver(id);
  return json({ ok: true });
}

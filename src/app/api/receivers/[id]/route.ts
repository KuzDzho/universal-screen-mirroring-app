import { fail, json } from "@/lib/http";
import { isId } from "@/lib/network";
import { removeReceiver, touchReceiver } from "@/lib/receivers";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

type Params = { params: Promise<{ id: string }> };

export async function PATCH(_request: Request, context: Params) {
  const { id } = await context.params;
  if (!isId(id)) return fail("Неверный телевизор", 400);
  const row = await touchReceiver(id);
  if (!row) return fail("Телевизор не в сети", 404);
  return json({ ok: true });
}

export async function DELETE(_request: Request, context: Params) {
  const { id } = await context.params;
  if (!isId(id)) return fail("Неверный телевизор", 400);
  await removeReceiver(id);
  return json({ ok: true });
}

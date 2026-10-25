import { and, eq, gt } from "drizzle-orm";
import { db } from "@/db";
import { receivers, signals } from "@/db/schema";
import { isId } from "./network";

const FRESH_MS = 12_000;

export async function announceReceiver(input: { id?: string; name: string; ip: string }) {
  const name = input.name.trim().slice(0, 40) || "Телевизор";
  const now = new Date();
  if (input.id && isId(input.id)) {
    const [existing] = await db.select().from(receivers).where(eq(receivers.id, input.id)).limit(1);
    if (existing) {
      const [row] = await db
        .update(receivers)
        .set({ name, ip: input.ip || existing.ip, lastSeenAt: now })
        .where(eq(receivers.id, input.id))
        .returning();
      return row;
    }
  }
  const id = crypto.randomUUID();
  const [row] = await db
    .insert(receivers)
    .values({ id, name, ip: input.ip, lastSeenAt: now })
    .returning();
  return row;
}

export async function touchReceiver(id: string) {
  if (!isId(id)) return null;
  const [row] = await db
    .update(receivers)
    .set({ lastSeenAt: new Date() })
    .where(eq(receivers.id, id))
    .returning();
  return row ?? null;
}

export async function listReceivers(ip: string) {
  const since = new Date(Date.now() - FRESH_MS);
  const rows = await db.select().from(receivers).where(gt(receivers.lastSeenAt, since));
  const same = ip ? rows.filter((row) => row.ip === ip) : rows;
  return same.map((row) => ({ id: row.id, name: row.name }));
}

export async function removeReceiver(id: string) {
  if (!isId(id)) return;
  await db.delete(signals).where(eq(signals.receiverId, id));
  await db.delete(receivers).where(eq(receivers.id, id));
}

export async function clearSignals(receiverId: string) {
  if (!isId(receiverId)) return;
  await db.delete(signals).where(eq(signals.receiverId, receiverId));
}

export async function addSignal(input: { receiverId: string; fromRole: string; kind: string; payload: string }) {
  const [row] = await db.insert(signals).values(input).returning();
  return row;
}

export async function listSignals(receiverId: string, listenAs: "sender" | "receiver", after: number) {
  const fromRole = listenAs === "sender" ? "receiver" : "sender";
  return db
    .select()
    .from(signals)
    .where(and(eq(signals.receiverId, receiverId), eq(signals.fromRole, fromRole), gt(signals.id, after)))
    .orderBy(signals.id)
    .limit(40);
}

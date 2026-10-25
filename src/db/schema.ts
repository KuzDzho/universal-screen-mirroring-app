import { pgTable, serial, text, timestamp } from "drizzle-orm/pg-core";

export const receivers = pgTable("receivers", {
  id: text("id").primaryKey(),
  name: text("name").notNull(),
  ip: text("ip").notNull().default(""),
  lastSeenAt: timestamp("last_seen_at", { withTimezone: true }).notNull(),
  createdAt: timestamp("created_at", { withTimezone: true }).defaultNow().notNull(),
});

export const signals = pgTable("signals", {
  id: serial("id").primaryKey(),
  receiverId: text("receiver_id").notNull(),
  fromRole: text("from_role").notNull(),
  kind: text("kind").notNull(),
  payload: text("payload").notNull(),
  createdAt: timestamp("created_at", { withTimezone: true }).defaultNow().notNull(),
});

export type Receiver = typeof receivers.$inferSelect;

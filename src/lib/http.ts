export function json(data: unknown, status = 200) {
  return Response.json(data, {
    status,
    headers: { "Cache-Control": "no-store" },
  });
}

export function fail(message: string, status = 400) {
  return json({ error: message }, status);
}

export function isUniqueViolation(error: unknown) {
  const seen = new Set<unknown>();
  const queue = [error];
  while (queue.length) {
    const current = queue.shift();
    if (!current || seen.has(current) || typeof current !== "object") continue;
    seen.add(current);
    const code = (current as { code?: string }).code;
    if (code === "23505") return true;
    const cause = (current as { cause?: unknown }).cause;
    if (cause) queue.push(cause);
  }
  return false;
}

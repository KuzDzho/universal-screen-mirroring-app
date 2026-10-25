export function clientIp(request: Request) {
  const forwarded = request.headers.get("x-forwarded-for");
  if (forwarded) return forwarded.split(",")[0]?.trim() || "";
  return request.headers.get("x-real-ip")?.trim() || "";
}

export function isId(value: string) {
  return /^[0-9a-f-]{16,64}$/i.test(value);
}

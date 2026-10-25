import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

const TV = /SmartTV|SMART-TV|BRAVIA|Bravia|Tizen|Web0S|WebOS|HbbTV|NetCast|Viera|CrKey|AppleTV|AFT[A-Z]|MiTV|VIDAA|InettvBrowser|SonyCEBrowser/i;

export function middleware(request: NextRequest) {
  const ua = request.headers.get("user-agent") || "";
  if (request.nextUrl.pathname === "/" && TV.test(ua)) {
    const url = request.nextUrl.clone();
    url.pathname = "/tv";
    return NextResponse.rewrite(url);
  }
  return NextResponse.next();
}

export const config = { matcher: ["/"] };

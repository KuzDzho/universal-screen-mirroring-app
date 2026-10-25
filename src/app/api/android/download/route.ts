import fs from "node:fs/promises";
import path from "node:path";
import JSZip from "jszip";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

async function addDirectoryToZip(zip: JSZip, localDir: string, zipFolder: string) {
  const entries = await fs.readdir(localDir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(localDir, entry.name);
    const zipPath = path.join(zipFolder, entry.name).replace(/\\/g, "/");
    if (entry.isDirectory()) {
      await addDirectoryToZip(zip, fullPath, zipPath);
    } else if (entry.isFile()) {
      const content = await fs.readFile(fullPath);
      zip.file(zipPath, content);
    }
  }
}

export async function GET() {
  try {
    const zip = new JSZip();
    const androidDir = path.join(process.cwd(), "android");
    await addDirectoryToZip(zip, androidDir, "VistaMirror-Android");

    const zipBuffer = await zip.generateAsync({ type: "nodebuffer", compression: "DEFLATE" });
    const body = new Uint8Array(zipBuffer);

    return new Response(body, {
      status: 200,
      headers: {
        "Content-Type": "application/zip",
        "Content-Disposition": 'attachment; filename="VistaMirror-Android.zip"',
        "Content-Length": String(body.byteLength),
        "Cache-Control": "no-store",
      },
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Не удалось сформировать архив";
    return Response.json({ error: message }, { status: 500 });
  }
}

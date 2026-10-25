import Link from "next/link";

export default function NotFound() {
  return (
    <main className="grid min-h-screen place-items-center bg-black text-white">
      <Link href="/" className="text-sm text-white/60">
        Трансляция
      </Link>
    </main>
  );
}

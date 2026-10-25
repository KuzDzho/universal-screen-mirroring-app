"use client";

export default function GlobalError({ reset }: { error: Error; reset: () => void }) {
  return (
    <main className="grid min-h-screen place-items-center bg-black px-6 text-center text-white">
      <button type="button" onClick={() => reset()} className="text-sm text-white/60">
        Повторить
      </button>
    </main>
  );
}

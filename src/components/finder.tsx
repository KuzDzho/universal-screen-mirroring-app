"use client";

import { useEffect, useRef, useState } from "react";
import { ICE_SERVERS, pollSignals, postSignal, usesLan } from "@/lib/lan";

type Tv = { id: string; name: string };

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

export function Finder() {
  const [tvs, setTvs] = useState<Tv[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [phase, setPhase] = useState<"idle" | "connecting" | "live">("idle");
  const [error, setError] = useState("");
  const [seen, setSeen] = useState(false);
  const pcRef = useRef<RTCPeerConnection | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const stopRef = useRef<AbortController | null>(null);
  const activeRef = useRef<string | null>(null);

  useEffect(() => {
    let stopped = false;
    const look = async () => {
      while (!stopped) {
        try {
          const response = await fetch("/api/receivers", { cache: "no-store" });
          if (response.ok) {
            const data = (await response.json()) as { tvs?: Tv[] };
            if (!stopped) {
              setTvs(data.tvs ?? []);
              setSeen(true);
            }
          }
        } catch {
          /* keep searching */
        }
        await sleep(1200);
      }
    };
    void look();
    if ("serviceWorker" in navigator) void navigator.serviceWorker.register("/sw.js").catch(() => undefined);
    return () => {
      stopped = true;
    };
  }, []);

  function stopMirror(notify = true) {
    stopRef.current?.abort();
    stopRef.current = null;
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    pcRef.current?.close();
    pcRef.current = null;
    const id = activeRef.current;
    activeRef.current = null;
    setActiveId(null);
    setPhase("idle");
    if (notify && id) void fetch(`/api/signal?receiverId=${id}`, { method: "DELETE" });
  }

  useEffect(() => () => stopMirror(false), []);

  async function startMirror(tv: Tv) {
    setError("");
    activeRef.current = tv.id;
    setActiveId(tv.id);
    setPhase("connecting");
    const signal = new AbortController();
    stopRef.current = signal;
    try {
      const stream = await navigator.mediaDevices.getDisplayMedia({
        video: { frameRate: { ideal: 30, max: 30 } },
        audio: true,
        systemAudio: "include",
      } as DisplayMediaStreamOptions & { systemAudio?: string });
      if (signal.signal.aborted) {
        stream.getTracks().forEach((track) => track.stop());
        return;
      }
      streamRef.current = stream;
      stream.getVideoTracks()[0]?.addEventListener("ended", () => stopMirror());
      await fetch(`/api/signal?receiverId=${tv.id}`, { method: "DELETE" });
      const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });
      pcRef.current = pc;
      stream.getTracks().forEach((track) => pc.addTrack(track, stream));
      pc.onicecandidate = (event) => {
        if (event.candidate) void postSignal(tv.id, "sender", "ice", event.candidate.toJSON());
      };
      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      await postSignal(tv.id, "sender", "offer", { type: pc.localDescription?.type, sdp: pc.localDescription?.sdp });
      let after = 0;
      const started = Date.now();
      let lanOk = false;
      while (!signal.signal.aborted) {
        const batch = await pollSignals(tv.id, "sender", after);
        after = batch.last;
        for (const msg of batch.signals) {
          const payload = JSON.parse(msg.payload) as RTCSessionDescriptionInit | RTCIceCandidateInit;
          if (msg.kind === "answer" && pc.signalingState === "have-local-offer" && "type" in payload && payload.type) {
            await pc.setRemoteDescription(new RTCSessionDescription(payload));
          }
          if (msg.kind === "ice" && payload && "candidate" in payload) {
            try {
              await pc.addIceCandidate(new RTCIceCandidate(payload));
            } catch {
              /* late candidate */
            }
          }
        }
        if (pc.connectionState === "failed" || (lanOk && pc.connectionState === "disconnected")) throw new Error("Нет связи по Wi-Fi");
        if (pc.connectionState === "connected") {
          lanOk = lanOk || (await usesLan(pc));
          if (lanOk) setPhase("live");
          else if (Date.now() - started > 5000) throw new Error("Нет связи по Wi-Fi");
        }
        if (!lanOk && Date.now() - started > 8000) throw new Error("Нет связи по Wi-Fi");
        await sleep(400);
      }
    } catch (reason) {
      const aborted = signal.signal.aborted;
      stopMirror(false);
      if (aborted) return;
      const name = reason instanceof Error ? reason.name : "";
      if (name === "NotAllowedError") setError("Доступ к экрану закрыт");
      else if (!navigator.mediaDevices?.getDisplayMedia) setError("Телефон не отдаёт экран");
      else setError(reason instanceof Error && reason.message ? reason.message : "Нет связи по Wi-Fi");
    }
  }

  function onPick(tv: Tv) {
    if (phase !== "idle" && activeId === tv.id) {
      stopMirror();
      return;
    }
    if (phase !== "idle") return;
    void startMirror(tv);
  }

  return (
    <main className="mx-auto min-h-screen w-full max-w-md px-3 pt-[max(2rem,env(safe-area-inset-top))] pb-10">
      <div className="flex items-center justify-between px-3">
        <h1 className="text-[1.65rem] font-semibold tracking-tight">Трансляция</h1>
        <a
          href="/api/android/download"
          download="VistaMirror-Android.zip"
          title="Скачать исходный проект Android APK"
          className="flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5 text-xs font-medium text-white/80 transition-colors hover:bg-white/15"
        >
          <svg viewBox="0 0 24 24" className="h-4 w-4 fill-current" aria-hidden="true">
            <path d="M17.523 15.3414c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.551 0 .9993.4482.9993.9993 0 .5511-.4483.9997-.9993.9997m-11.046 0c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.5511 0 .9993.4482.9993.9993 0 .5511-.4482.9997-.9993.9997m11.4045-6.02l1.996-3.4572c.1556-.2698.0632-.6141-.2066-.7698-.2699-.1556-.6142-.0631-.7698.2066l-2.0231 3.5042c-1.472-.6731-3.1368-1.048-4.878-1.048s-3.406.3749-4.878 1.048l-2.0231-3.5042c-.1556-.2697-.5-.3622-.7699-.2066-.2698.1557-.3622.5-.2066.7698l1.996 3.4572C2.6842 11.233 0 14.887 0 19.1672h24c0-4.2802-2.6842-7.9342-6.1185-9.8458" />
          </svg>
          Android
        </a>
      </div>
      <p className="mt-7 flex items-center gap-3 px-3 text-sm text-white/45">
        <span className="spinner" aria-hidden />
        Поиск телевизоров в сети Wi-Fi
      </p>
      <ul className="mt-2">
        {tvs.map((tv) => {
          const current = activeId === tv.id;
          const status = current && phase === "live" ? "Дублируется" : current ? "Подключение" : "";
          return (
            <li key={tv.id}>
              <button type="button" onClick={() => onPick(tv)} className="flex w-full items-center gap-4 rounded-2xl px-3 py-3 text-left active:bg-white/8">
                <span className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-white/8">
                  <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden>
                    <rect x="3" y="5.5" width="18" height="12" rx="1.8" />
                    <path d="M8 20h8" strokeLinecap="round" />
                  </svg>
                </span>
                <span className="min-w-0">
                  <span className="block truncate text-[17px]">{tv.name}</span>
                  {status ? <span className="mt-0.5 block text-[13px] text-white/45">{status}</span> : null}
                </span>
              </button>
            </li>
          );
        })}
      </ul>
      {seen && !tvs.length ? <p className="px-3 pt-6 text-sm text-white/35">Телевизоры не найдены</p> : null}
      {error ? <p className="px-3 pt-4 text-sm text-white/55">{error}</p> : null}
    </main>
  );
}

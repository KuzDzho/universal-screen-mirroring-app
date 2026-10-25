"use client";

import { useEffect, useRef, useState } from "react";
import { ICE_SERVERS, pollSignals, postSignal } from "@/lib/lan";

function tvName() {
  const saved = localStorage.getItem("vista-tv-name");
  if (saved) return saved;
  const ua = navigator.userAgent;
  const name = /BRAVIA|Bravia/i.test(ua)
    ? "Sony Bravia"
    : /Tizen/i.test(ua)
      ? "Samsung"
      : /Web0S|WebOS/i.test(ua)
        ? "LG"
        : /CrKey/i.test(ua)
          ? "Chromecast"
          : /MiTV|MIBOX/i.test(ua)
            ? "Xiaomi"
            : "Телевизор";
  localStorage.setItem("vista-tv-name", name);
  return name;
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

export function Screen() {
  const [needSound, setNeedSound] = useState(true);
  const [name, setName] = useState("");
  const videoRef = useRef<HTMLVideoElement>(null);
  const idRef = useRef("");
  const pcRef = useRef<RTCPeerConnection | null>(null);

  useEffect(() => {
    const label = tvName();
    setName(label);
    let stopped = false;
    let id = localStorage.getItem("vista-tv-id") || "";
    const boot = async () => {
      const response = await fetch("/api/receivers", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ id, name: label }),
      });
      const data = (await response.json()) as { id?: string };
      if (!data.id || stopped) return;
      id = data.id;
      idRef.current = id;
      localStorage.setItem("vista-tv-id", id);
      await fetch(`/api/signal?receiverId=${id}`, { method: "DELETE" });
    };
    void boot();
    const beat = window.setInterval(() => {
      if (idRef.current) {
        void fetch(`/api/receivers/${idRef.current}`, { method: "PATCH" });
      }
    }, 3000);
    const leave = () => {
      if (idRef.current) navigator.sendBeacon(`/api/receivers/${idRef.current}/leave`);
    };
    window.addEventListener("pagehide", leave);
    return () => {
      stopped = true;
      window.clearInterval(beat);
      window.removeEventListener("pagehide", leave);
      pcRef.current?.close();
    };
  }, []);

  useEffect(() => {
    let stopped = false;
    let after = 0;
    let lastOffer = "";
    const pending: RTCIceCandidateInit[] = [];
    const loop = async () => {
      while (!stopped) {
        const id = idRef.current;
        if (!id) {
          await sleep(300);
          continue;
        }
        try {
          const batch = await pollSignals(id, "receiver", after);
          after = batch.last;
          for (const msg of batch.signals) {
            const payload = JSON.parse(msg.payload) as { type?: RTCSdpType; sdp?: string; candidate?: string };
            if (msg.kind === "offer" && payload.sdp && payload.type && payload.sdp !== lastOffer) {
              lastOffer = payload.sdp;
              pcRef.current?.close();
              const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });
              pcRef.current = pc;
              pc.onicecandidate = (event) => {
                if (event.candidate) void postSignal(id, "receiver", "ice", event.candidate.toJSON());
              };
              pc.ontrack = (event) => {
                const stream = event.streams[0] ?? new MediaStream([event.track]);
                const video = videoRef.current;
                if (!video) return;
                video.srcObject = stream;
                void video.play().then(() => setNeedSound(false)).catch(() => setNeedSound(true));
              };
              await pc.setRemoteDescription(new RTCSessionDescription({ type: payload.type, sdp: payload.sdp }));
              for (const candidate of pending.splice(0)) {
                try {
                  await pc.addIceCandidate(new RTCIceCandidate(candidate));
                } catch {
                  /* ignore */
                }
              }
              const answer = await pc.createAnswer();
              await pc.setLocalDescription(answer);
              await postSignal(id, "receiver", "answer", { type: pc.localDescription?.type, sdp: pc.localDescription?.sdp });
            }
            if (msg.kind === "ice" && payload.candidate) {
              const candidate = payload as RTCIceCandidateInit;
              if (!pcRef.current || !pcRef.current.remoteDescription) pending.push(candidate);
              else {
                try {
                  await pcRef.current.addIceCandidate(new RTCIceCandidate(candidate));
                } catch {
                  /* ignore */
                }
              }
            }
          }
        } catch {
          /* retry */
        }
        await sleep(400);
      }
    };
    void loop();
    return () => {
      stopped = true;
    };
  }, []);

  function unlock() {
    const video = videoRef.current;
    if (!video) return;
    video.muted = false;
    void video.play().then(() => setNeedSound(false)).catch(() => undefined);
    void document.documentElement.requestFullscreen?.().catch(() => undefined);
  }

  return (
    <main className="fixed inset-0 bg-black" onPointerDown={unlock}>
      <video ref={videoRef} autoPlay playsInline className="h-full w-full object-contain" />
      {needSound ? (
        <p className="absolute inset-x-0 bottom-8 text-center text-sm text-white/40">{name || "Телевизор"}</p>
      ) : null}
    </main>
  );
}

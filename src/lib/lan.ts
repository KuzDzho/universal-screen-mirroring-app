export const ICE_SERVERS: RTCIceServer[] = [{ urls: "stun:stun.l.google.com:19302" }];

export type SignalMsg = { id: number; fromRole: string; kind: string; payload: string };

export async function postSignal(receiverId: string, fromRole: "sender" | "receiver", kind: string, payload: unknown) {
  await fetch("/api/signal", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ receiverId, fromRole, kind, payload: JSON.stringify(payload) }),
  });
}

export async function pollSignals(receiverId: string, listenAs: "sender" | "receiver", after: number) {
  const response = await fetch(`/api/signal?receiverId=${receiverId}&listenAs=${listenAs}&after=${after}`, { cache: "no-store" });
  if (!response.ok) return { signals: [] as SignalMsg[], last: after };
  const data = (await response.json()) as { signals?: SignalMsg[]; last?: number };
  return { signals: data.signals ?? [], last: data.last ?? after };
}

type NetStat = {
  id?: string;
  type?: string;
  state?: string;
  nominated?: boolean;
  localCandidateId?: string;
  remoteCandidateId?: string;
  candidateType?: string;
  address?: string;
  ip?: string;
};

function isLocal(report?: NetStat) {
  if (!report) return false;
  if (report.candidateType === "host") return true;
  const address = report.address || report.ip || "";
  return /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[0-1])\.|169\.254\.|fd|fe80)/i.test(address) || address.endsWith(".local");
}

export async function usesLan(pc: RTCPeerConnection) {
  const stats = await pc.getStats();
  let localId = "";
  let remoteId = "";
  for (const report of stats.values()) {
    const row = report as NetStat;
    if (row.type === "candidate-pair" && row.state === "succeeded") {
      localId = String(row.localCandidateId || "");
      remoteId = String(row.remoteCandidateId || "");
    }
  }
  if (!localId && !remoteId) return false;
  let local: NetStat | undefined;
  let remote: NetStat | undefined;
  for (const report of stats.values()) {
    const row = report as NetStat;
    if (row.id === localId) local = row;
    if (row.id === remoteId) remote = row;
  }
  return isLocal(local) || isLocal(remote);
}

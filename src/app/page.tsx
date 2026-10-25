import type { Metadata } from "next";
import { Finder } from "@/components/finder";

export const metadata: Metadata = {
  title: "Трансляция",
  description: "Поиск телевизора в сети Wi-Fi и дублирование экрана.",
};

export const dynamic = "force-dynamic";

export default function HomePage() {
  return <Finder />;
}

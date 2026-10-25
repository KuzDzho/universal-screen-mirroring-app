import type { Metadata, Viewport } from "next";
import { Screen } from "@/components/screen";

export const metadata: Metadata = {
  title: "Телевизор",
};

export const viewport: Viewport = {
  themeColor: "#000000",
};

export default function TvPage() {
  return <Screen />;
}

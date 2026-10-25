import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: "Виста",
  description: "Дублирование экрана на телевизор в той же сети Wi-Fi.",
  applicationName: "Виста",
  manifest: "/manifest.json",
  icons: { icon: "/icons/icon-512.png", apple: "/icons/icon-192.png" },
  appleWebApp: { capable: true, title: "Виста", statusBarStyle: "black" },
};

export const viewport: Viewport = {
  themeColor: "#000000",
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ru">
      <body>{children}</body>
    </html>
  );
}

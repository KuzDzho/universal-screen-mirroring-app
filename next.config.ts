import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async redirects() {
    return [
      { source: "/cast", destination: "/", permanent: false },
      { source: "/guide", destination: "/", permanent: false },
      { source: "/devices", destination: "/", permanent: false },
      { source: "/history", destination: "/", permanent: false },
      { source: "/receiver", destination: "/tv", permanent: false },
      { source: "/bravia", destination: "/tv", permanent: false },
      { source: "/legacy.html", destination: "/tv", permanent: false },
    ];
  },
};

export default nextConfig;

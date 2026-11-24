import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  // Dev through an ngrok tunnel: without this the dev server blocks its HMR socket and assets for the
  // foreign hostname, and the client falls back to a full reload on every navigation.
  allowedDevOrigins: ["*.ngrok-free.dev"],
  experimental: {
    // Product images are up to 5 MB and travel through a server action; the default limit is 1 MB.
    serverActions: {
      bodySizeLimit: "6mb",
      allowedOrigins: ["*.ngrok-free.dev"],
    },
  },
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;

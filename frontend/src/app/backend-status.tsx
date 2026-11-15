"use client";

import { useEffect, useState } from "react";
import { getBackendStatus } from "@/lib/api";

// Runs in the browser on purpose: it exercises the real CORS path to the backend.
export default function BackendStatus() {
  const [status, setStatus] = useState("checking…");

  useEffect(() => {
    getBackendStatus()
      .then(setStatus)
      .catch(() => setStatus("unreachable"));
  }, []);

  return (
    <p className="text-sm text-zinc-500">
      Backend: <span data-testid="backend-status">{status}</span>
    </p>
  );
}

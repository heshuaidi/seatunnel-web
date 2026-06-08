export const API_BASE = (process.env.API_BASE || "").replace(/\/+$/, "");

export function withApiBase(path: string): string {
  if (!API_BASE || /^https?:\/\//i.test(path) || /^wss?:\/\//i.test(path) || path.startsWith(API_BASE)) {
    return path;
  }

  return path.startsWith("/") ? `${API_BASE}${path}` : `${API_BASE}/${path}`;
}

export function buildWebSocketUrl(path: string): string {
  const apiPath = withApiBase(path);

  if (/^wss?:\/\//i.test(apiPath)) {
    return apiPath;
  }

  if (/^https?:\/\//i.test(apiPath)) {
    return apiPath.replace(/^http/i, "ws");
  }

  if (typeof window === "undefined") {
    return apiPath;
  }

  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  const normalizedPath = apiPath.startsWith("/") ? apiPath : `/${apiPath}`;

  return `${protocol}//${window.location.host}${normalizedPath}`;
}

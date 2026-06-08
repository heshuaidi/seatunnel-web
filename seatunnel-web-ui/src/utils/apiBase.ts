export const API_BASE = (process.env.API_BASE || "").replace(/\/+$/, "");

export function withApiBase(path: string): string {
  if (!API_BASE || /^https?:\/\//i.test(path) || path.startsWith(API_BASE)) {
    return path;
  }

  return path.startsWith("/") ? `${API_BASE}${path}` : `${API_BASE}/${path}`;
}

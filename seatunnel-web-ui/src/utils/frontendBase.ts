export const FRONTEND_BASE = normalizeFrontendBase(process.env.FRONTEND_BASE || "/");

function normalizeFrontendBase(base: string): string {
  if (!base || base === "/") {
    return "/";
  }

  return `/${base.replace(/^\/+|\/+$/g, "")}/`;
}

export function withFrontendBase(path: string): string {
  if (/^https?:\/\//i.test(path)) {
    return path;
  }

  const nextPath = path || "/";
  const baseWithoutTrailingSlash = FRONTEND_BASE.replace(/\/$/, "");

  if (FRONTEND_BASE === "/") {
    return nextPath.startsWith("/") ? nextPath : `/${nextPath}`;
  }

  if (nextPath === baseWithoutTrailingSlash || nextPath.startsWith(FRONTEND_BASE)) {
    return nextPath;
  }

  return nextPath.startsWith("/")
    ? `${baseWithoutTrailingSlash}${nextPath}`
    : `${FRONTEND_BASE}${nextPath}`;
}

export function isFrontendPath(pathname: string, path: string): boolean {
  return pathname === path || pathname === withFrontendBase(path);
}

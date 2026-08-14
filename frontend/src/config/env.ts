/**
 * Requests go to a same-origin path, never to the backend's host directly: Vite
 * proxies it in development and nginx does in the container, so the session
 * cookie is first-party and the backend needs no CORS configuration.
 */
export const API_BASE_PATH = "/api";

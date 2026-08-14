import { API_BASE_PATH } from "@/config/env";
import type { ApiEnvelope, ApiResult } from "@/shared/types/api.types";
import { ApiError } from "./errors";

/**
 * The one place that knows the response envelope. Callers get the payload or an
 * ApiError; no screen branches on the shape of a response (backend-rules §1).
 */
export async function request<T>(path: string, init?: RequestInit): Promise<ApiResult<T>> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_PATH}${path}`, {
      // The session is a JSESSIONID cookie, so every call has to carry it.
      credentials: "include",
      ...init,
    });
  } catch {
    throw new ApiError(
      {
        code: "NETWORK_ERROR",
        message: "Could not reach the server. Check that it is running, then try again.",
        retriable: true,
      },
      0,
    );
  }

  let envelope: ApiEnvelope<T>;
  try {
    envelope = (await response.json()) as ApiEnvelope<T>;
  } catch {
    throw new ApiError(
      { code: "INVALID_RESPONSE", message: "The server sent an unreadable response.", retriable: true },
      response.status,
    );
  }

  if (envelope.status === "error" || envelope.error) {
    throw new ApiError(
      envelope.error ?? { code: "INTERNAL_ERROR", message: "Something went wrong.", retriable: false },
      response.status,
    );
  }

  return { data: envelope.data as T, loading: envelope.status === "loading" };
}

export function jsonBody(method: string, body: unknown): RequestInit {
  return { method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) };
}

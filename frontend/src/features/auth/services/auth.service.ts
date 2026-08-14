import { jsonBody, request } from "@/shared/lib/http";
import type { LoginPayload, User } from "../types/auth.types";

/** Find-or-create by email; an existing user keeps their original name (spec §4.1). */
export async function login(payload: LoginPayload): Promise<User> {
  const { data } = await request<User>("/session", jsonBody("POST", payload));
  return data;
}

/** Throws ApiError UNAUTHENTICATED when nobody is signed in. */
export async function fetchCurrentUser(): Promise<User> {
  const { data } = await request<User>("/session");
  return data;
}

export async function logout(): Promise<void> {
  await request<Record<string, never>>("/session", { method: "DELETE" });
}

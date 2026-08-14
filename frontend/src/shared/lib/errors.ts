import type { ApiErrorBody } from "@/shared/types/api.types";

/** What every failed request throws — a stable code plus a message written for the user. */
export class ApiError extends Error {
  readonly code: string;
  readonly retriable: boolean;
  readonly details: unknown;
  readonly httpStatus: number;

  constructor(body: ApiErrorBody, httpStatus: number) {
    super(body.message);
    this.name = "ApiError";
    this.code = body.code;
    this.retriable = body.retriable;
    this.details = body.details;
    this.httpStatus = httpStatus;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

/** Never render a raw exception: anything unexpected gets a message a user can act on. */
export function messageOf(error: unknown): string {
  if (isApiError(error)) {
    return error.message;
  }
  return "Something went wrong. Please try again.";
}

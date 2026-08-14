/** The single response envelope every backend endpoint returns (backend-rules §1). */

export interface ApiErrorBody {
  code: string;
  message: string;
  details?: unknown;
  retriable: boolean;
}

export interface ApiEnvelope<T> {
  status: "success" | "error" | "loading";
  data: T | null;
  error: ApiErrorBody | null;
}

export interface ApiResult<T> {
  data: T;
  /** The backend reports the step still running — render progress, not success. */
  loading: boolean;
}

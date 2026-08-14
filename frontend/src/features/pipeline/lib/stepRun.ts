/** Mirrors Project.LOCK_TTL on the backend (120 seconds). */
export const LOCK_TTL_SECONDS = 120;

/** Past this, a running step is worth reassuring the user about, not yet worth retrying. */
export const SLOW_STEP_SECONDS = 30;

export function elapsedSeconds(startedAt: string | null, now: number = Date.now()): number | null {
  if (!startedAt) {
    return null;
  }
  const started = new Date(startedAt).getTime();
  if (Number.isNaN(started)) {
    return null;
  }
  return Math.max(0, Math.floor((now - started) / 1000));
}

export function formatElapsed(seconds: number): string {
  if (seconds < 60) {
    return `${seconds}s`;
  }
  const minutes = Math.floor(seconds / 60);
  const remainder = seconds % 60;
  return remainder === 0 ? `${minutes}m` : `${minutes}m ${remainder}s`;
}

/**
 * A run that has held the lock longer than the backend's TTL can be reclaimed by
 * the next attempt (PostgresPipelineLock), so the user gets a way out without
 * touching the database (spec §4.3).
 */
export function isStepStuck(stepStartedAt: string | null, now: number = Date.now()): boolean {
  const elapsed = elapsedSeconds(stepStartedAt, now);
  return elapsed !== null && elapsed > LOCK_TTL_SECONDS;
}

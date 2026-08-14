import { describe, expect, it } from "vitest";
import { elapsedSeconds, formatElapsed, isStepStuck, LOCK_TTL_SECONDS } from "./stepRun";

const START = "2026-08-14T10:00:00Z";
const startedAtMs = Date.parse(START);

describe("elapsedSeconds", () => {
  it("is null when the step has not started", () => {
    expect(elapsedSeconds(null)).toBeNull();
  });

  it("counts whole seconds since the step started", () => {
    expect(elapsedSeconds(START, startedAtMs + 42_000)).toBe(42);
  });

  it("never goes negative when the clocks disagree", () => {
    expect(elapsedSeconds(START, startedAtMs - 5_000)).toBe(0);
  });
});

describe("formatElapsed", () => {
  it("stays in seconds under a minute", () => {
    expect(formatElapsed(45)).toBe("45s");
  });

  it("switches to minutes and seconds", () => {
    expect(formatElapsed(95)).toBe("1m 35s");
    expect(formatElapsed(120)).toBe("2m");
  });
});

describe("isStepStuck", () => {
  it("is false while the run is within the backend's lock TTL", () => {
    expect(isStepStuck(START, startedAtMs + LOCK_TTL_SECONDS * 1000)).toBe(false);
  });

  it("is true once the lock TTL has passed, which is when a retry can reclaim it", () => {
    expect(isStepStuck(START, startedAtMs + (LOCK_TTL_SECONDS + 1) * 1000)).toBe(true);
  });

  it("is false with no start time, since there is nothing to reclaim", () => {
    expect(isStepStuck(null)).toBe(false);
  });
});

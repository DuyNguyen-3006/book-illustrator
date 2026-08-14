import { describe, expect, it } from "vitest";
import { completedStepCount, stepProgress } from "./projectProgress";

describe("stepProgress", () => {
  it("marks nothing done on a fresh project", () => {
    const progress = stepProgress("DRAFT", "STYLE", "IDLE");

    expect(progress.map((entry) => entry.state)).toEqual([
      "pending",
      "pending",
      "pending",
      "pending",
      "pending",
    ]);
    expect(completedStepCount(progress)).toBe(0);
  });

  it("marks earlier steps done and the running one running", () => {
    const progress = stepProgress("RUNNING", "PORTRAITS", "RUNNING");

    expect(progress.map((entry) => entry.state)).toEqual([
      "done",
      "done",
      "running",
      "pending",
      "pending",
    ]);
    expect(completedStepCount(progress)).toBe(2);
  });

  it("keeps finished work visible when the current step failed", () => {
    const progress = stepProgress("RUNNING", "CHAPTERS", "FAILED");

    expect(progress.map((entry) => entry.state)).toEqual([
      "done",
      "done",
      "done",
      "failed",
      "pending",
    ]);
  });

  it("counts a completed-but-not-yet-advanced step as done", () => {
    const progress = stepProgress("RUNNING", "STYLE", "COMPLETED");

    expect(progress[0].state).toBe("done");
    expect(completedStepCount(progress)).toBe(1);
  });

  it("marks every step done once the project itself is complete", () => {
    const progress = stepProgress("COMPLETED", "ILLUSTRATIONS", "COMPLETED");

    expect(completedStepCount(progress)).toBe(5);
  });
});

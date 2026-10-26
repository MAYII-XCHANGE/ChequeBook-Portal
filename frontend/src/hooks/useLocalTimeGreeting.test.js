import { describe, expect, it } from "vitest";
import { getLocalTimeGreeting } from "./useLocalTimeGreeting";

const localTime = (hour) => new Date(2026, 0, 15, hour, 0, 0);

describe("getLocalTimeGreeting", () => {
  it.each([
    [5, "Good morning"],
    [11, "Good morning"],
    [12, "Good afternoon"],
    [16, "Good afternoon"],
    [17, "Good evening"],
    [20, "Good evening"],
    [21, "Good night"],
    [4, "Good night"],
  ])("returns the local greeting for hour %i", (hour, expected) => {
    expect(getLocalTimeGreeting(localTime(hour))).toBe(expected);
  });
});

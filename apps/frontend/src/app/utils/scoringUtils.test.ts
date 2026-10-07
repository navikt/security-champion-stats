import { describe, expect, it } from "vitest";
import { scoringProgress } from "./scoringUtils";
import type { ParticipantSeasonScore } from "./Variables";

const score: ParticipantSeasonScore = {
	season: {
		id: "season-1",
		startsOn: "2026-01-01",
		endsOn: null,
		nextResetDate: "2027-01-01",
	},
	level: "Starter",
	points: 4,
	rank: 1,
	tiers: [
		{ name: "Starter", points: 0 },
		{ name: "Champion", points: 5 },
	],
};

describe("configured scoring progress", () => {
	it("uses custom tier names and thresholds rather than the former fixed progression", () => {
		expect(scoringProgress(score)).toEqual({
			currentLevel: { level: "Starter", points: 0 },
			nextLevel: { level: "Champion", points: 5 },
			value: 4,
			valueMax: 5,
			pointsToNextLevel: 1,
		});
	});

	it("clamps negative scores and handles a single zero-threshold top tier", () => {
		expect(scoringProgress({ ...score, points: -4 })?.value).toBe(0);
		expect(scoringProgress({ ...score, tiers: [score.tiers[0]] })).toEqual({
			currentLevel: { level: "Starter", points: 0 },
			nextLevel: undefined,
			value: 1,
			valueMax: 1,
			pointsToNextLevel: null,
		});
	});
});

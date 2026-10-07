import type { ParticipantSeasonScore } from "./Variables";

export const LEVEL_THRESHOLDS = [
	{ level: "Novice", points: 0 },
	{ level: "Apprentice", points: 100 },
	{ level: "Adept", points: 250 },
	{ level: "Expert", points: 500 },
] as const;

export function scoringProgress(score: ParticipantSeasonScore) {
	const index = LEVEL_THRESHOLDS.findIndex(
		(threshold) => threshold.level === score.level,
	);
	const currentLevel = LEVEL_THRESHOLDS[index];
	if (!currentLevel) return null;
	const nextLevel = LEVEL_THRESHOLDS[index + 1];
	const valueMax = nextLevel
		? nextLevel.points - currentLevel.points
		: currentLevel.points;
	const value = nextLevel
		? Math.min(
				Math.max(score.points - currentLevel.points, 0),
				valueMax,
			)
		: valueMax;

	return {
		currentLevel,
		nextLevel,
		value,
		valueMax,
		pointsToNextLevel: nextLevel ? nextLevel.points - score.points : null,
	};
}

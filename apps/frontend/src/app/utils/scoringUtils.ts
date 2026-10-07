import type { ParticipantSeasonScore } from "./Variables";

export function scoringProgress(score: ParticipantSeasonScore) {
	const thresholds = score.tiers.map((tier) => ({
		level: tier.name,
		points: tier.points,
	}));
	const index = thresholds.findIndex(
		(threshold) => threshold.level === score.level,
	);
	const currentLevel = thresholds[index];
	if (!currentLevel) return null;
	const nextLevel = thresholds[index + 1];
	const valueMax = nextLevel
		? nextLevel.points - currentLevel.points
		: Math.max(1, currentLevel.points);
	const value = nextLevel
		? Math.min(Math.max(score.points - currentLevel.points, 0), valueMax)
		: valueMax;

	return {
		currentLevel,
		nextLevel,
		value,
		valueMax,
		pointsToNextLevel: nextLevel ? nextLevel.points - score.points : null,
	};
}

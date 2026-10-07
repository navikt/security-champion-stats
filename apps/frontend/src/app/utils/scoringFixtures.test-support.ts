import type { ScoringConfiguration } from "./Variables";

export const defaultScoringConfiguration: ScoringConfiguration = {
	version: 1,
	tiers: [
		{ name: "Novice", points: 0 },
		{ name: "Apprentice", points: 100 },
		{ name: "Adept", points: 250 },
		{ name: "Expert", points: 500 },
	],
	activities: [
		{ creditType: "SLACK_WEEK", points: 1 },
		{ creditType: "DELTA_REGISTRATION", points: 1 },
		{ creditType: "GITHUB_COMMIT", points: 1 },
		{ creditType: "GITHUB_PULL_REQUEST", points: 3 },
		{ creditType: "SECURITY_EVENT_CONTRIBUTION", points: 3 },
	],
};

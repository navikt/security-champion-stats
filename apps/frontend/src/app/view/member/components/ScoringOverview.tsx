"use client";

import { useEffect, useId, useState } from "react";
import {
	BodyShort,
	Box,
	Heading,
	HGrid,
	ProgressBar,
	Table,
} from "@navikt/ds-react";
import { Apies } from "@/app/shared/hooks/Apies";
import {
	LeaderboardEntry,
	ParticipantSeasonScore,
} from "@/app/utils/Variables";
import "../../../style/home/ScoringOverview.css";

const levelThresholds = [
	{ level: "Novice", points: 0 },
	{ level: "Apprentice", points: 100 },
	{ level: "Adept", points: 250 },
	{ level: "Expert", points: 500 },
] as const;

function ScoringProgress({ score }: { score: ParticipantSeasonScore }) {
	const progressLabelId = useId();
	const levelIndex = levelThresholds.findIndex(
		(threshold) => threshold.level === score.level,
	);
	const currentLevel = levelThresholds[levelIndex];
	const nextLevel = levelThresholds[levelIndex + 1];

	if (!currentLevel) return null;

	const currentProgress = nextLevel
		? Math.min(
				Math.max(score.points - currentLevel.points, 0),
				nextLevel.points - currentLevel.points,
			)
		: 0;
	const pointsToNextLevel = nextLevel
		? nextLevel.points - score.points
		: null;

	return (
		<Box
			as="section"
			className="sc-scoring-overview__section"
			background="default"
			borderColor="neutral-subtle"
			borderWidth="1"
			borderRadius="8"
			padding="space-16"
		>
			<Heading level="2" size="medium">
				Your season
			</Heading>
			<HGrid columns={{ xs: 1, sm: 3 }} gap="space-16">
				<div>
					<BodyShort>Your points</BodyShort>
					<Heading level="3" size="large">
						{score.points}
					</Heading>
				</div>
				<div>
					<BodyShort>Level</BodyShort>
					<Heading level="3" size="large">
						{score.level}
					</Heading>
				</div>
				<div>
					<BodyShort>Your current-season rank</BodyShort>
					<Heading level="3" size="large">
						{score.rank === null ? "Not ranked yet" : `#${score.rank}`}
					</Heading>
				</div>
			</HGrid>
			{nextLevel ? (
				<div className="sc-scoring-overview__progress">
					<BodyShort id={progressLabelId}>
						{pointsToNextLevel}{" "}
						{pointsToNextLevel === 1 ? "point" : "points"} to {nextLevel.level}
					</BodyShort>
					<ProgressBar
						aria-labelledby={progressLabelId}
						value={currentProgress}
						valueMax={nextLevel.points - currentLevel.points}
					/>
				</div>
			) : (
				<BodyShort>Expert is the highest level this season.</BodyShort>
			)}
		</Box>
	);
}

function Leaderboard({
	entries,
}: {
	entries: LeaderboardEntry[];
}) {
	return (
		<Box
			as="section"
			className="sc-scoring-overview__section"
			background="default"
			borderColor="neutral-subtle"
			borderWidth="1"
			borderRadius="8"
			padding="space-16"
		>
			<Heading level="2" size="medium">
				Full current-season leaderboard
			</Heading>
			{entries.length === 0 ? (
				<BodyShort>No participants have earned points this season yet.</BodyShort>
			) : (
				<div className="sc-scoring-overview__table">
					<Table size="small">
						<Table.Header>
							<Table.Row>
								<Table.HeaderCell scope="col">Rank</Table.HeaderCell>
								<Table.HeaderCell scope="col">Participant</Table.HeaderCell>
								<Table.HeaderCell scope="col">Points</Table.HeaderCell>
								<Table.HeaderCell scope="col">Level</Table.HeaderCell>
							</Table.Row>
						</Table.Header>
						<Table.Body>
							{entries.map((entry, index) => (
								<Table.Row
									key={`${entry.rank}-${entry.fullName}-${index}`}
								>
									<Table.DataCell>{entry.rank}</Table.DataCell>
									<Table.HeaderCell scope="row">
										{entry.fullName}
									</Table.HeaderCell>
									<Table.DataCell>{entry.points}</Table.DataCell>
									<Table.DataCell>{entry.level}</Table.DataCell>
								</Table.Row>
							))}
						</Table.Body>
					</Table>
				</div>
			)}
		</Box>
	);
}

export function ScoringOverview({
	showPersonalProgress,
	showLeaderboard,
}: {
	showPersonalProgress: boolean;
	showLeaderboard: boolean;
}) {
	const [score, setScore] = useState<ParticipantSeasonScore | null>(null);
	const [entries, setEntries] = useState<LeaderboardEntry[] | null>(null);
	const [scoreLoading, setScoreLoading] = useState(showPersonalProgress);
	const [leaderboardLoading, setLeaderboardLoading] = useState(showLeaderboard);
	const [scoreFailed, setScoreFailed] = useState(false);
	const [leaderboardFailed, setLeaderboardFailed] = useState(false);

	useEffect(() => {
		if (!showPersonalProgress) return;
		let isCurrent = true;
		Apies.getParticipantSeasonScore()
			.then((result) => {
				if (!isCurrent) return;
				setScore(result);
				setScoreFailed(result === null);
			})
			.catch(() => {
				if (isCurrent) setScoreFailed(true);
			})
			.finally(() => {
				if (isCurrent) setScoreLoading(false);
			});
		return () => {
			isCurrent = false;
		};
	}, [showPersonalProgress]);

	useEffect(() => {
		if (!showLeaderboard) return;
		let isCurrent = true;
		Apies.getLeaderboard()
			.then((result) => {
				if (!isCurrent) return;
				setEntries(result);
				setLeaderboardFailed(result === null);
			})
			.catch(() => {
				if (isCurrent) setLeaderboardFailed(true);
			})
			.finally(() => {
				if (isCurrent) setLeaderboardLoading(false);
			});
		return () => {
			isCurrent = false;
		};
	}, [showLeaderboard]);

	if (!showPersonalProgress && !showLeaderboard) return null;

	return (
		<div className="sc-scoring-overview">
			{showPersonalProgress &&
				(scoreLoading ? (
					<BodyShort>Loading your season score…</BodyShort>
				) : scoreFailed || !score ? (
					<BodyShort role="alert">
						We couldn't load your season score. Try again later.
					</BodyShort>
				) : (
					<ScoringProgress score={score} />
				))}
			{showLeaderboard &&
				(leaderboardLoading ? (
					<BodyShort>Loading the leaderboard…</BodyShort>
				) : leaderboardFailed || !entries ? (
					<BodyShort role="alert">
						We couldn't load the leaderboard. Try again later.
					</BodyShort>
				) : (
					<Leaderboard entries={entries} />
				))}
		</div>
	);
}

"use client";

import { useEffect, useId, useState } from "react";
import {
	BodyShort,
	Box,
	Heading,
	HGrid,
	ProgressBar,
	Tag,
} from "@navikt/ds-react";
import { Apies } from "@/app/shared/hooks/Apies";
import {
	LeaderboardEntry,
	ParticipantSeasonScore,
} from "@/app/utils/Variables";
import { scoringProgress } from "@/app/utils/scoringUtils";
import "../../../style/home/ScoringOverview.css";

function ScoringProgress({ score }: { score: ParticipantSeasonScore }) {
	const progressLabelId = useId();
	const progress = scoringProgress(score);
	if (!progress) return null;

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
			{progress.nextLevel ? (
				<div className="sc-scoring-overview__progress">
					<BodyShort id={progressLabelId}>
						{progress.pointsToNextLevel}{" "}
						{progress.pointsToNextLevel === 1 ? "point" : "points"} to {progress.nextLevel.level}
					</BodyShort>
					<ProgressBar
						aria-labelledby={progressLabelId}
						value={progress.value}
						valueMax={progress.valueMax}
					/>
				</div>
			) : (
				<BodyShort>Top level reached.</BodyShort>
			)}
		</Box>
	);
}

function Leaderboard({
	entries,
}: {
	entries: LeaderboardEntry[];
}) {
	const sorted = [...entries].sort((a, b) => a.rank - b.rank);
	const top = sorted.slice(0, 10);
	const self = sorted.find((entry) => entry.isCurrentUser);
	const selfOutsideTop = self && !top.includes(self) ? self : undefined;
	const visible = selfOutsideTop ? [...top, selfOutsideTop] : top;
	const leaderPoints = Math.max(sorted[0]?.points ?? 0, 1);

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
				Season leaderboard
			</Heading>
			{visible.length === 0 ? (
				<BodyShort>No participants have earned points this season yet.</BodyShort>
			) : (
				<ol className="sc-scoring-overview__leaderboard">
					{visible.map((entry, index) => {
						const isCurrentUser = entry === self;
						return (
							<li
								className={[
									"sc-scoring-overview__leaderboardRow",
									isCurrentUser ? "sc-scoring-overview__leaderboardRow--self" : "",
									selfOutsideTop && index === top.length ? "sc-scoring-overview__leaderboardRow--separated" : "",
								].filter(Boolean).join(" ")}
								key={`${entry.rank}-${entry.fullName}-${index}`}
							>
								<span className="sc-scoring-overview__rank">{entry.rank}</span>
								<div className="sc-scoring-overview__person">
									<span className="sc-scoring-overview__name">
										{entry.fullName}
										{isCurrentUser && <Tag size="xsmall" variant="moderate" data-color="success">You</Tag>}
									</span>
									<span
										className="sc-scoring-overview__bar"
										aria-hidden="true"
									>
										<span style={{ width: `${Math.max(0, Math.min(100, entry.points / leaderPoints * 100))}%` }} />
									</span>
								</div>
								<span className="sc-scoring-overview__score">
									<strong>{entry.points}</strong>
									<span>{entry.level}</span>
								</span>
							</li>
						);
					})}
				</ol>
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

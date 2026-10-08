"use client";

import { Apies } from "@/app/shared/hooks/Apies";
import { creditTypeLabel } from "@/app/utils/scoringUtils";
import type {
	ActivityCredit,
	AdminParticipantScore,
	ParticipantScoringHistory,
	ScoringHistoryEntry,
} from "@/app/utils/Variables";
import {
	BodyShort,
	Button,
	Heading,
	Modal,
	Select,
	Table,
	VStack,
} from "@navikt/ds-react";
import { useEffect, useState } from "react";

const ACTIVITY_TYPES: ActivityCredit["creditType"][] = [
	"SLACK_WEEK",
	"DELTA_REGISTRATION",
	"GITHUB_COMMIT",
	"GITHUB_PULL_REQUEST",
	"SECURITY_EVENT_CONTRIBUTION",
];
const PAGE_SIZE = 50;
const dateTime = new Intl.DateTimeFormat("en-GB", {
	dateStyle: "medium",
	timeStyle: "short",
	timeZone: "Europe/Oslo",
});

function signedPoints(points: number): string {
	return points > 0 ? `+${points}` : String(points);
}

function entryTitle(entry: ScoringHistoryEntry): string {
	if (entry.type === "SCORING_RULE_CHANGE") return "Scoring-rule change";
	if (entry.type === "ADJUSTMENT") return "Point adjustment";
	return entry.creditType
		? creditTypeLabel(entry.creditType)
		: "Activity credit";
}

export function ParticipantScoreHistoryModal({
	participant,
	onClose,
}: {
	participant: AdminParticipantScore;
	onClose: () => void;
}) {
	const [history, setHistory] = useState<ParticipantScoringHistory | null>(
		null,
	);
	const [loading, setLoading] = useState(true);
	const [failed, setFailed] = useState(false);
	const [attempt, setAttempt] = useState(0);
	const [selectedSeason, setSelectedSeason] = useState("all");
	const [visibleCount, setVisibleCount] = useState(PAGE_SIZE);

	useEffect(() => {
		let current = true;
		setLoading(true);
		setFailed(false);
		Apies.getParticipantScoringHistory(participant.participantId)
			.then((result) => {
				if (current) setHistory(result);
			})
			.catch((error) => {
				console.error("Failed to load participant scoring history:", error);
				if (current) setFailed(true);
			})
			.finally(() => {
				if (current) setLoading(false);
			});
		return () => {
			current = false;
		};
	}, [participant.participantId, attempt]);

	const seasons =
		history?.seasons.filter(
			(season) => selectedSeason === "all" || season.id === selectedSeason,
		) ?? [];
	const entries =
		history?.entries.filter(
			(entry) => selectedSeason === "all" || entry.seasonId === selectedSeason,
		) ?? [];
	const currentSeason = history?.seasons.find(
		(season) => season.id === history.currentSeasonId,
	);

	return (
		<Modal
			open
			onClose={onClose}
			header={{
				heading: `Score history for ${participant.fullName || participant.email}`,
			}}
			width="medium"
		>
			<Modal.Body>
				<VStack gap="space-24">
					<BodyShort>{participant.email}</BodyShort>
					{loading && (
						<BodyShort role="status">Loading score history...</BodyShort>
					)}
					{failed && (
						<VStack gap="space-8" align="start">
							<BodyShort role="alert">
								We couldn't load score history. Try again.
							</BodyShort>
							<Button
								type="button"
								variant="secondary"
								onClick={() => setAttempt(attempt + 1)}
							>
								Retry
							</Button>
						</VStack>
					)}
					{history && !loading && !failed && (
						<>
							<BodyShort>
								Current-season points: <strong>{currentSeason?.points}</strong>
							</BodyShort>
							<Select
								label="Season"
								value={selectedSeason}
								onChange={(event) => {
									setSelectedSeason(event.target.value);
									setVisibleCount(PAGE_SIZE);
								}}
							>
								<option value="all">All seasons</option>
								{history.seasons.map((season) => (
									<option key={season.id} value={season.id}>
										{season.startsOn} to {season.endsOn ?? "ongoing"}
										{season.id === history.currentSeasonId ? " (current)" : ""}
									</option>
								))}
							</Select>
							<section aria-labelledby="score-breakdown-heading">
								<Heading level="3" size="small" id="score-breakdown-heading">
									Score breakdown
								</Heading>
								<Table size="small">
									<caption>
										{selectedSeason === "all"
											? "All seasons"
											: "Selected season"}
										:{" "}
										{seasons.reduce(
											(total, season) => total + season.points,
											0,
										)}{" "}
										points
									</caption>
									<Table.Header>
										<Table.Row>
											<Table.HeaderCell scope="col">Source</Table.HeaderCell>
											<Table.HeaderCell scope="col" align="right">
												Points
											</Table.HeaderCell>
										</Table.Row>
									</Table.Header>
									<Table.Body>
										{ACTIVITY_TYPES.map((type) => (
											<Table.Row key={type}>
												<Table.HeaderCell scope="row">
													{creditTypeLabel(type)}
												</Table.HeaderCell>
												<Table.DataCell align="right">
													{seasons.reduce(
														(total, season) =>
															total + season.creditPoints[type],
														0,
													)}
												</Table.DataCell>
											</Table.Row>
										))}
										<Table.Row>
											<Table.HeaderCell scope="row">
												Point adjustments (including revocations)
											</Table.HeaderCell>
											<Table.DataCell align="right">
												{signedPoints(
													seasons.reduce(
														(total, season) => total + season.adjustmentPoints,
														0,
													),
												)}
											</Table.DataCell>
										</Table.Row>
										<Table.Row>
											<Table.HeaderCell scope="row">
												Scoring-rule changes
											</Table.HeaderCell>
											<Table.DataCell align="right">
												{signedPoints(
													seasons.reduce(
														(total, season) => total + season.scoringRulePoints,
														0,
													),
												)}
											</Table.DataCell>
										</Table.Row>
									</Table.Body>
								</Table>
								<BodyShort size="small">
									Original activity points plus all adjustments make up the
									total. Revoked credits remain visible with their correcting
									adjustments.
								</BodyShort>
							</section>
							<section aria-labelledby="score-entries-heading">
								<VStack gap="space-16">
									<Heading level="3" size="small" id="score-entries-heading">
										Scoring history
									</Heading>
									<BodyShort size="small">
										Newest recorded first. Dates use Oslo time; corrections
										belong to the season they affect.
									</BodyShort>
									{entries.length === 0 ? (
										<BodyShort>
											No scoring history for this selection.
										</BodyShort>
									) : (
										<VStack
											as="ol"
											gap="space-24"
											aria-label="Scoring history entries"
										>
											{entries.slice(0, visibleCount).map((entry) => (
												<VStack as="li" key={entry.id} gap="space-4">
													<BodyShort>
														<strong>
															{entryTitle(entry)}: {signedPoints(entry.points)}{" "}
															points
														</strong>
													</BodyShort>
													<BodyShort size="small">
														Recorded:{" "}
														<time dateTime={entry.recordedAt}>
															{dateTime.format(new Date(entry.recordedAt))}
														</time>
													</BodyShort>
													<BodyShort size="small">
														Season: {entry.seasonStartsOn} to{" "}
														{entry.seasonEndsOn ?? "ongoing"}
													</BodyShort>
													{entry.activityAt && (
														<BodyShort size="small">
															Activity date:{" "}
															<time dateTime={entry.activityAt}>
																{dateTime.format(new Date(entry.activityAt))}
															</time>
														</BodyShort>
													)}
													{entry.sourceReference && (
														<BodyShort
															size="small"
															style={{ overflowWrap: "anywhere" }}
														>
															{entry.sourceCreditId
																? "Linked activity"
																: "Source"}
															:{" "}
															{entry.sourceCreditId && entry.creditType
																? `${creditTypeLabel(entry.creditType)} - `
																: ""}
															{entry.sourceReference}
														</BodyShort>
													)}
													<BodyShort
														size="small"
														style={{ overflowWrap: "anywhere" }}
													>
														{entry.type === "CREDIT"
															? `Credit ID: ${entry.id}`
															: entry.sourceCreditId
																? `Linked credit ID: ${entry.sourceCreditId}`
																: "No linked activity"}
													</BodyShort>
													{entry.reason && (
														<BodyShort size="small">
															Reason: {entry.reason}
														</BodyShort>
													)}
													{entry.actorNavNoEmail && (
														<BodyShort size="small">
															Administrator: {entry.actorNavNoEmail}
														</BodyShort>
													)}
													{entry.revokedAt && (
														<BodyShort size="small">
															Revoked:{" "}
															<time dateTime={entry.revokedAt}>
																{dateTime.format(new Date(entry.revokedAt))}
															</time>
														</BodyShort>
													)}
												</VStack>
											))}
										</VStack>
									)}
									<BodyShort role="status">
										Showing {Math.min(visibleCount, entries.length)} of{" "}
										{entries.length} entries.
									</BodyShort>
									{visibleCount < entries.length && (
										<Button
											type="button"
											variant="secondary"
											onClick={() => setVisibleCount(visibleCount + PAGE_SIZE)}
										>
											Show more
										</Button>
									)}
								</VStack>
							</section>
						</>
					)}
				</VStack>
			</Modal.Body>
			<Modal.Footer>
				<Button type="button" variant="tertiary" onClick={onClose}>
					Close
				</Button>
			</Modal.Footer>
		</Modal>
	);
}

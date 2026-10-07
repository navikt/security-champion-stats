import Link from "next/link";
import {
	BodyShort,
	Box,
	Button,
	Heading,
	HGrid,
	HStack,
	ProgressBar,
	Tag,
	VStack,
} from "@navikt/ds-react";
import type {
	HistoryEntry,
	ParticipantSeasonScore,
	ProgramParticipant,
} from "@/app/utils/Variables";
import { scoringProgress } from "@/app/utils/scoringUtils";

function signedPoints(value: number): string {
	return `${value > 0 ? "+" : value < 0 ? "−" : ""}${Math.abs(value)}`;
}

function activityTitle(entry: HistoryEntry): string {
	if (entry.action === "CREDIT_AWARDED") return "Credit awarded";
	if (entry.action === "POINTS_ADJUSTED") return "Points adjusted";
	return entry.action.replaceAll("_", " ").toLowerCase().replace(/^\w/, (letter) => letter.toUpperCase());
}

function activityType(entry: HistoryEntry): string {
	const creditType = entry.details.creditType;
	if (typeof creditType === "string") {
		return creditType
			.replaceAll("_", " ")
			.toLowerCase()
			.replace(/^\w/, (letter) => letter.toUpperCase());
	}
	if (typeof entry.details.reason === "string") return entry.details.reason;
	return entry.action.replaceAll("_", " ").toLowerCase();
}

function relativeDay(value: string): string {
	return new Date(value).toLocaleDateString(undefined, {
		day: "numeric",
		month: "short",
	});
}

export function OverviewParticipationCard({
	participant,
	score,
	scoreLoading,
	activities,
	activitiesLoading,
	activitiesFailed,
	confirmingLeave,
	pending,
	actionError,
	onRequestLeave,
	onCancelLeave,
	onConfirmLeave,
	onRejoin,
}: {
	participant: ProgramParticipant;
	score: ParticipantSeasonScore | null;
	scoreLoading: boolean;
	activities: HistoryEntry[];
	activitiesLoading: boolean;
	activitiesFailed: boolean;
	confirmingLeave: boolean;
	pending: boolean;
	actionError: string | null;
	onRequestLeave: () => void;
	onCancelLeave: () => void;
	onConfirmLeave: () => void;
	onRejoin: () => void;
}) {
	const progress = score ? scoringProgress(score) : null;

	return (
		<section className="overviewParticipation hubRedesign__card">
			<div className="overviewParticipation__main">
				<HStack gap="space-12" align="center" wrap>
					<Tag
						size="small"
						variant="moderate"
						data-color={participant.active ? "success" : "neutral"}
					>
						{participant.active ? "Active participant" : participant.status === "LEFT" ? "Left program" : "Deactivated"}
					</Tag>
					<BodyShort className="hubRedesign__muted">
						since {new Date(participant.joinedAt).toLocaleDateString(undefined, {
							day: "numeric",
							month: "short",
							year: "numeric",
						})}
					</BodyShort>
				</HStack>

				{scoreLoading ? (
					<BodyShort role="status">Loading your season score…</BodyShort>
				) : score ? (
					<>
						<HGrid className="overviewParticipation__stats" columns={{ xs: 1, sm: 3 }} gap="space-16">
							<Summary label="Season points" value={String(score.points)} />
							<Summary label="Level" value={score.level} />
							<Summary label="Season rank" value={score.rank === null ? "Not ranked" : `#${score.rank}`} />
						</HGrid>
						{progress && (
							<div className="overviewParticipation__progress">
								<div className="overviewParticipation__progressCaption">
									<BodyShort>
										{score.level} · {score.points} / {progress.nextLevel?.points ?? progress.currentLevel.points}
									</BodyShort>
									<BodyShort>
										{progress.nextLevel
											? `${progress.pointsToNextLevel} ${progress.pointsToNextLevel === 1 ? "point" : "points"} to ${progress.nextLevel.level}`
											: "Top level reached"}
									</BodyShort>
								</div>
								<ProgressBar
									value={progress.value}
									valueMax={progress.valueMax}
									aria-label={`Progress to ${progress.nextLevel?.level ?? "top level"}`}
									data-color="success"
								/>
							</div>
						)}
					</>
				) : participant.active ? (
					<BodyShort role="alert">We couldn't load your season score. Try again later.</BodyShort>
				) : null}

				<div className="overviewParticipation__actions">
					{participant.active && !confirmingLeave && (
						<Button
							variant="tertiary"
							data-color="neutral"
							onClick={onRequestLeave}
						>
							Leave program…
						</Button>
					)}
					{confirmingLeave && (
						<>
							<BodyShort>Leave the program? Your history is kept.</BodyShort>
							<Button variant="secondary" onClick={onCancelLeave} disabled={pending}>
								Cancel
							</Button>
							<Button
								variant="secondary"
								data-color="danger"
								onClick={onConfirmLeave}
								loading={pending}
								disabled={pending}
							>
								Leave program
							</Button>
						</>
					)}
					{participant.status === "LEFT" && (
						<Button onClick={onRejoin} loading={pending} disabled={pending}>
							Rejoin program
						</Button>
					)}
					{actionError && <BodyShort role="alert">{actionError}</BodyShort>}
				</div>
			</div>

			<div className="overviewParticipation__activity">
				<div className="hubRedesign__cardHeader">
					<Heading level="2" size="small">Recent activity</Heading>
					<Link className="hubRedesign__buttonLink" href="/history">
						View full history →
					</Link>
				</div>
				{activitiesLoading ? (
					<BodyShort role="status">Loading recent activity…</BodyShort>
				) : activitiesFailed ? (
					<BodyShort role="alert">We couldn't load recent activity. Try again later.</BodyShort>
				) : activities.length === 0 ? (
					<BodyShort className="hubRedesign__muted">
						No activity yet — register for an event to earn your first points.
					</BodyShort>
				) : (
					<ul className="hubRedesign__dividerList overviewParticipation__activityList">
						{activities.slice(0, 3).map((activity) => {
							const points = activity.details.points;
							return (
								<li className="overviewParticipation__activityItem" key={activity.id}>
									<VStack gap="space-2">
										<BodyShort>{activityTitle(activity)}</BodyShort>
										<BodyShort className="hubRedesign__muted overviewParticipation__activityMeta">
											{activityType(activity)} · {relativeDay(activity.recordedAt)}
										</BodyShort>
									</VStack>
									{typeof points === "number" && (
										<BodyShort className="overviewParticipation__activityPoints">
											{signedPoints(points)}
										</BodyShort>
									)}
								</li>
							);
						})}
					</ul>
				)}
			</div>
		</section>
	);
}

function Summary({ label, value }: { label: string; value: string }) {
	return (
		<Box className="hubRedesign__stat">
			<BodyShort className="hubRedesign__statLabel">{label}</BodyShort>
			<Heading level="3" size="large" className="hubRedesign__statValue">{value}</Heading>
		</Box>
	);
}

"use client";

import { Apies } from "@/app/shared/hooks/Apies";
import type {
	ActivityCredit,
	ScoringConfiguration,
	ScoringConfigurationPreview,
	ScoringConfigurationRequest,
	ScoringTier,
} from "@/app/utils/Variables";
import {
	BodyShort,
	Button,
	Checkbox,
	Heading,
	Modal,
	Table,
	TextField,
	VStack,
} from "@navikt/ds-react";
import { useState } from "react";

const activityLabels: Record<ActivityCredit["creditType"], string> = {
	SLACK_WEEK: "Weekly Slack participation",
	DELTA_REGISTRATION: "Delta event registration",
	GITHUB_COMMIT: "Standalone playbook commit",
	GITHUB_PULL_REQUEST: "Merged playbook pull request",
	SECURITY_EVENT_CONTRIBUTION: "Security-event contribution",
};

function wholeNumber(value: string): number | null {
	const number = Number(value);
	return /^\d+$/.test(value.trim()) &&
		Number.isSafeInteger(number) &&
		number <= 2147483647
		? number
		: null;
}

export function ScoringConfigurationView({
	configuration,
	onRefresh,
	onSaved,
}: {
	configuration: ScoringConfiguration;
	onRefresh: () => Promise<void>;
	onSaved: () => void;
}) {
	const [tiers, setTiers] = useState(() =>
		configuration.tiers.map((tier, index) => ({
			id: index,
			name: tier.name,
			points: String(tier.points),
		})),
	);
	const [nextId, setNextId] = useState(tiers.length);
	const [activities, setActivities] = useState(() =>
		configuration.activities.map((activity) => ({
			creditType: activity.creditType,
			points: String(activity.points),
		})),
	);
	const [retroactive, setRetroactive] = useState(false);
	const [reason, setReason] = useState("");
	const [error, setError] = useState<string | null>(null);
	const [busy, setBusy] = useState(false);
	const [review, setReview] = useState<{
		request: ScoringConfigurationRequest;
		preview: ScoringConfigurationPreview;
	} | null>(null);

	const previewChanges = async () => {
		if (busy) return;
		setError(null);
		const parsedTiers = tiers
			.map((tier) => ({
				name: tier.name.trim(),
				points: wholeNumber(tier.points),
			}))
			.sort((a, b) => (a.points ?? -1) - (b.points ?? -1));
		if (
			parsedTiers.length === 0 ||
			parsedTiers.length > 50 ||
			!parsedTiers.every((tier): tier is ScoringTier => tier.points !== null) ||
			parsedTiers[0].points !== 0 ||
			parsedTiers.some((tier) => !tier.name || tier.name.length > 80) ||
			new Set(parsedTiers.map((tier) => tier.name.toLowerCase())).size !==
				parsedTiers.length ||
			new Set(parsedTiers.map((tier) => tier.points)).size !==
				parsedTiers.length
		) {
			setError(
				"Use 1-50 unique tier names and distinct whole-number thresholds starting at zero.",
			);
			return;
		}
		const parsedActivities = activities.map((activity) => ({
			creditType: activity.creditType,
			points: wholeNumber(activity.points),
		}));
		if (
			!parsedActivities.every(
				(activity): activity is ScoringConfiguration["activities"][number] =>
					activity.points !== null,
			)
		) {
			setError("Enter a non-negative whole number for every activity.");
			return;
		}
		if (!reason.trim() || reason.length > 1000) {
			setError("Enter a reason of up to 1000 characters.");
			return;
		}
		const request: ScoringConfigurationRequest = {
			expectedVersion: configuration.version,
			tiers: parsedTiers.map((tier) => ({
				name: tier.name,
				points: tier.points,
			})),
			activities: parsedActivities.map((activity) => ({
				creditType: activity.creditType,
				points: activity.points,
			})),
			applyRetroactively: retroactive,
			reason: reason.trim(),
		};
		setBusy(true);
		try {
			const preview = await Apies.previewScoringConfiguration(request);
			setReview({ request, preview });
		} catch (cause) {
			setError(
				cause instanceof Error
					? cause.message
					: "We couldn't preview the scoring changes. Try again.",
			);
		} finally {
			setBusy(false);
		}
	};

	const saveChanges = async () => {
		if (!review || busy) return;
		setBusy(true);
		setError(null);
		try {
			const status = await Apies.saveScoringConfiguration({
				...review.request,
				previewToken: review.preview.token,
			});
			if (status !== 200) {
				setError(
					status === 409
						? "Scoring changed. Reload the configuration and preview again."
						: "We couldn't save the scoring changes. Try again.",
				);
				setReview(null);
				return;
			}
			setReview(null);
			onSaved();
			try {
				await onRefresh();
			} catch {
				setError(
					"Scoring was saved, but we couldn't refresh the dashboard. Reload configuration.",
				);
			}
		} catch {
			setError(
				"We couldn't save the scoring changes. Reload the configuration before retrying.",
			);
			setReview(null);
		} finally {
			setBusy(false);
		}
	};

	return (
		<section aria-labelledby="scoring-configuration-heading">
			<VStack gap="space-24">
				<Heading level="2" size="large" id="scoring-configuration-heading">
					Scoring rules and tiers
				</Heading>
				<BodyShort>
					Tiers are shared across the program and update immediately. Activity
					rules change point values, not which activities qualify. A zero-point
					activity is still recorded.
				</BodyShort>
				<form
					onSubmit={(event) => {
						event.preventDefault();
						void previewChanges();
					}}
				>
					<VStack gap="space-24">
						<Heading level="3" size="medium">
							Named tiers
						</Heading>
						<BodyShort>
							Enter each tier's minimum points. The first threshold must be
							zero.
						</BodyShort>
						{tiers.map((tier, index) => (
							<VStack gap="space-8" key={tier.id}>
								<TextField
									label={`Tier ${index + 1} name`}
									value={tier.name}
									maxLength={80}
									onChange={(event) =>
										setTiers(
											tiers.map((item) =>
												item.id === tier.id
													? { ...item, name: event.target.value }
													: item,
											),
										)
									}
								/>
								<TextField
									label={`Tier ${index + 1} minimum points`}
									inputMode="numeric"
									value={tier.points}
									onChange={(event) =>
										setTiers(
											tiers.map((item) =>
												item.id === tier.id
													? { ...item, points: event.target.value }
													: item,
											),
										)
									}
								/>
								<Button
									type="button"
									variant="secondary"
									size="small"
									onClick={() =>
										setTiers(tiers.filter((item) => item.id !== tier.id))
									}
								>
									Remove tier {index + 1}
								</Button>
							</VStack>
						))}
						<Button
							type="button"
							variant="secondary"
							onClick={() => {
								setTiers([...tiers, { id: nextId, name: "", points: "" }]);
								setNextId(nextId + 1);
							}}
						>
							Add tier
						</Button>
						<Heading level="3" size="medium">
							Activity points
						</Heading>
						{activities.map((activity) => (
							<TextField
								key={activity.creditType}
								label={`${activityLabels[activity.creditType]} points`}
								inputMode="numeric"
								value={activity.points}
								onChange={(event) =>
									setActivities(
										activities.map((item) =>
											item.creditType === activity.creditType
												? { ...item, points: event.target.value }
												: item,
										),
									)
								}
							/>
						))}
						<Checkbox
							checked={retroactive}
							onChange={(event) => setRetroactive(event.target.checked)}
						>
							Also apply activity point values to current-season credits
						</Checkbox>
						<BodyShort>
							Without this option, existing credits keep their points. With it,
							differences are recorded as adjustments, including credits held by
							inactive participants. Manual corrections and closed seasons are
							unchanged.
						</BodyShort>
						<TextField
							label="Reason for scoring changes"
							value={reason}
							maxLength={1000}
							onChange={(event) => setReason(event.target.value)}
						/>
						{error && <BodyShort role="alert">{error}</BodyShort>}
						<Button type="submit" loading={busy}>
							Preview scoring changes
						</Button>
						<Button
							type="button"
							variant="secondary"
							loading={busy}
							onClick={async () => {
								if (busy) return;
								setBusy(true);
								try {
									await onRefresh();
								} catch {
									setError("We couldn't reload scoring. Try again.");
								} finally {
									setBusy(false);
								}
							}}
						>
							Reload configuration
						</Button>
					</VStack>
				</form>
			</VStack>
			<Modal
				open={review !== null}
				onClose={() => {
					if (!busy) setReview(null);
				}}
				header={{ heading: "Confirm scoring changes" }}
				width="medium"
			>
				<Modal.Body>
					{review && (
						<VStack gap="space-16">
							<BodyShort>
								Current season started on {review.preview.season.startsOn}.
							</BodyShort>
							<BodyShort>
								{review.preview.affectedCredits} credits will receive
								adjustments. Total point change: {review.preview.pointsDelta}.
							</BodyShort>
							<BodyShort>
								{review.preview.participants.length} participants will have
								changed points or tiers.
							</BodyShort>
							<BodyShort>
								{review.request.applyRetroactively
									? "Current-season credits will use the new activity point values."
									: "Activity point changes apply only to newly awarded credits."}{" "}
								Manual corrections and closed seasons remain unchanged.
							</BodyShort>
							<BodyShort>Reason: {review.request.reason}</BodyShort>
							{review.preview.participants.length > 0 && (
								<div style={{ overflowX: "auto" }}>
									<Table size="small">
										<Table.Header>
											<Table.Row>
												<Table.HeaderCell scope="col">
													Participant
												</Table.HeaderCell>
												<Table.HeaderCell scope="col">
													Points before / after
												</Table.HeaderCell>
												<Table.HeaderCell scope="col">
													Tier before / after
												</Table.HeaderCell>
											</Table.Row>
										</Table.Header>
										<Table.Body>
											{review.preview.participants.map((participant) => (
												<Table.Row key={participant.participantId}>
													<Table.HeaderCell scope="row">
														{participant.fullName}
													</Table.HeaderCell>
													<Table.DataCell>
														{participant.pointsBefore} /{" "}
														{participant.pointsAfter}
													</Table.DataCell>
													<Table.DataCell>
														{participant.levelBefore} / {participant.levelAfter}
													</Table.DataCell>
												</Table.Row>
											))}
										</Table.Body>
									</Table>
								</div>
							)}
						</VStack>
					)}
				</Modal.Body>
				<Modal.Footer>
					<Button type="button" loading={busy} onClick={saveChanges}>
						Confirm and save scoring
					</Button>
					<Button
						type="button"
						variant="secondary"
						disabled={busy}
						onClick={() => setReview(null)}
					>
						Cancel
					</Button>
				</Modal.Footer>
			</Modal>
		</section>
	);
}

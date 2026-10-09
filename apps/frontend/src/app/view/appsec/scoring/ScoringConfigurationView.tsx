"use client";

import {
	BodyShort,
	Box,
	Button,
	Checkbox,
	Heading,
	Table,
	TextField,
} from "@navikt/ds-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { Apies } from "@/app/shared/hooks/Apies";
import type {
	AdminParticipantScore,
	ScoringConfiguration,
	ScoringConfigurationPreview,
	ScoringConfigurationRequest,
} from "@/app/utils/Variables";

const ACTIVITY_TYPES = [
	{
		creditType: "SLACK_WEEK",
		label: "Weekly Slack participation",
		color: "slack",
	},
	{
		creditType: "DELTA_REGISTRATION",
		label: "Delta event registration",
		color: "event",
	},
	{
		creditType: "GITHUB_COMMIT",
		label: "Standalone playbook commit",
		color: "commit",
	},
	{
		creditType: "GITHUB_PULL_REQUEST",
		label: "Merged playbook pull request",
		color: "pullRequest",
	},
	{
		creditType: "SECURITY_EVENT_CONTRIBUTION",
		label: "Security-event contribution",
		color: "securityEvent",
	},
] as const;

type DraftTier = { id: number; name: string; points: string };
type DraftActivity = {
	creditType: ScoringConfiguration["activities"][number]["creditType"];
	points: string;
};
type RuleChange = { label: string; oldValue: string; newValue: string };

function wholeNumber(value: string): number | null {
	const number = Number(value);
	return /^\d+$/.test(value.trim()) &&
		Number.isSafeInteger(number) &&
		number <= 2147483647
		? number
		: null;
}

function draftTiers(configuration: ScoringConfiguration): DraftTier[] {
	return configuration.tiers.map((tier, index) => ({
		id: index,
		name: tier.name,
		points: String(tier.points),
	}));
}

function draftActivities(configuration: ScoringConfiguration): DraftActivity[] {
	return ACTIVITY_TYPES.map(({ creditType }) => ({
		creditType,
		points: String(
			configuration.activities.find(
				(activity) => activity.creditType === creditType,
			)?.points ?? 0,
		),
	}));
}

function tierValidationError(tiers: DraftTier[]): string | null {
	if (tiers.length === 0 || tiers.length > 50) {
		return "Use between 1 and 50 tiers.";
	}
	if (tiers.some((tier) => !tier.name.trim())) {
		return "Every tier needs a name.";
	}
	if (tiers.some((tier) => tier.name.trim().length > 80)) {
		return "Tier names must be 80 characters or fewer.";
	}
	if (
		new Set(tiers.map((tier) => tier.name.trim().toLocaleLowerCase())).size !==
		tiers.length
	) {
		return "Tier names must be unique.";
	}
	const minimums = tiers.map((tier) => wholeNumber(tier.points));
	if (minimums.some((minimum) => minimum === null)) {
		return "Enter a non-negative whole number for every tier minimum.";
	}
	if (minimums[0] !== 0) {
		return "The first tier must start at zero points.";
	}
	if (
		minimums.some((minimum, index) => {
			const previous = minimums[index - 1];
			return (
				index > 0 &&
				minimum !== null &&
				previous !== null &&
				previous !== undefined &&
				minimum <= previous
			);
		})
	) {
		return "Tier minimums must increase in order.";
	}
	return null;
}

function buildRuleChanges(
	saved: ScoringConfiguration,
	tiers: DraftTier[],
	activities: DraftActivity[],
): RuleChange[] {
	const changes: RuleChange[] = [];
	const savedById = new Map(saved.tiers.map((tier, index) => [index, tier]));
	const draftById = new Map(tiers.map((tier) => [tier.id, tier]));

	for (const [id, before] of savedById) {
		const after = draftById.get(id);
		if (!after) {
			changes.push({
				label: `Tier "${before.name}"`,
				oldValue: `from ${before.points}`,
				newValue: "removed",
			});
			continue;
		}
		if (before.name !== after.name.trim()) {
			changes.push({
				label: `Tier ${after.name.trim() || before.name} name`,
				oldValue: before.name,
				newValue: after.name.trim(),
			});
		}
		const points = wholeNumber(after.points);
		if (points === null || before.points !== points) {
			changes.push({
				label: `Tier ${after.name.trim() || before.name} minimum`,
				oldValue: String(before.points),
				newValue: after.points,
			});
		}
	}

	for (const tier of tiers) {
		if (savedById.has(tier.id)) continue;
		changes.push({
			label: `New tier "${tier.name.trim() || "unnamed"}"`,
			oldValue: "—",
			newValue: `from ${tier.points}`,
		});
	}

	for (const activity of activities) {
		const before =
			saved.activities.find((item) => item.creditType === activity.creditType)
				?.points ?? 0;
		const points = wholeNumber(activity.points);
		if (points === null || before !== points) {
			const label =
				ACTIVITY_TYPES.find((item) => item.creditType === activity.creditType)
					?.label ?? activity.creditType;
			changes.push({
				label,
				oldValue: String(before),
				newValue: activity.points,
			});
		}
	}
	return changes;
}

export function ScoringConfigurationView({
	configuration,
	participants,
	onRefresh,
	onSaved,
}: {
	configuration: ScoringConfiguration;
	participants: AdminParticipantScore[];
	onRefresh: () => Promise<{ configuration: ScoringConfiguration } | null>;
	onSaved: () => void;
}) {
	const [saved, setSaved] = useState(configuration);
	const [tiers, setTiers] = useState(() => draftTiers(configuration));
	const [activities, setActivities] = useState(() =>
		draftActivities(configuration),
	);
	const [nextId, setNextId] = useState(configuration.tiers.length);
	const [reason, setReason] = useState("");
	const [retroactive, setRetroactive] = useState(false);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [preview, setPreview] = useState<{
		request: ScoringConfigurationRequest;
		result: ScoringConfigurationPreview;
	} | null>(null);
	const [lastAppliedAt, setLastAppliedAt] = useState<Date | null>(null);
	const newTierNameRef = useRef<HTMLInputElement>(null);
	const previewBackRef = useRef<HTMLButtonElement>(null);
	const previousVersion = useRef(configuration.version);

	const changes = useMemo(
		() => buildRuleChanges(saved, tiers, activities),
		[saved, tiers, activities],
	);
	const dirty = changes.length > 0;

	useEffect(() => {
		if (configuration.version === previousVersion.current) return;
		previousVersion.current = configuration.version;
		setSaved(configuration);
		setTiers(draftTiers(configuration));
		setActivities(draftActivities(configuration));
		setNextId(configuration.tiers.length);
		setReason("");
		setRetroactive(false);
		setPreview(null);
		setError(null);
	}, [configuration]);

	useEffect(() => {
		if (!dirty) return;
		const warnBeforeUnload = (event: BeforeUnloadEvent) => {
			event.preventDefault();
			event.returnValue = "";
		};
		const confirmRouteChange = (event: MouseEvent) => {
			if (
				event.defaultPrevented ||
				event.button !== 0 ||
				event.metaKey ||
				event.ctrlKey ||
				event.shiftKey ||
				event.altKey
			) {
				return;
			}
			const target = event.target;
			if (!(target instanceof Element)) return;
			const link = target.closest("a[href]");
			if (
				!(link instanceof HTMLAnchorElement) ||
				link.target ||
				link.hasAttribute("download")
			) {
				return;
			}
			const destination = new URL(link.href, window.location.href);
			if (
				destination.origin !== window.location.origin ||
				(destination.pathname === window.location.pathname &&
					destination.search === window.location.search &&
					destination.hash === window.location.hash)
			) {
				return;
			}
			if (
				!window.confirm(
					"You have unsaved scoring changes. Leave this page without saving?",
				)
			) {
				event.preventDefault();
				event.stopImmediatePropagation();
			}
		};
		window.addEventListener("beforeunload", warnBeforeUnload);
		document.addEventListener("click", confirmRouteChange, true);
		return () => {
			window.removeEventListener("beforeunload", warnBeforeUnload);
			document.removeEventListener("click", confirmRouteChange, true);
		};
	}, [dirty]);

	const currentTierError = tierValidationError(tiers);
	const activityErrors = activities.map((activity) =>
		wholeNumber(activity.points) === null
			? "Enter a non-negative whole number."
			: null,
	);
	const valid =
		currentTierError === null && activityErrors.every((item) => !item);
	const normalizedTiers = tiers.map((tier) => ({
		name: tier.name.trim(),
		points: wholeNumber(tier.points),
	}));
	const normalizedActivities = activities.map((activity) => ({
		creditType: activity.creditType,
		points: wholeNumber(activity.points),
	}));
	const tierNameDuplicates = new Set<string>();
	const seenNames = new Set<string>();
	for (const tier of tiers) {
		const name = tier.name.trim().toLocaleLowerCase();
		if (seenNames.has(name)) tierNameDuplicates.add(name);
		seenNames.add(name);
	}
	const hasValidThresholds =
		currentTierError === null &&
		normalizedTiers.every(
			(tier): tier is { name: string; points: number } => tier.points !== null,
		);

	const membersInTier = (index: number) => {
		if (!hasValidThresholds) return "—";
		const from = normalizedTiers[index].points;
		const next = normalizedTiers[index + 1]?.points ?? Infinity;
		if (from === null || next === null) return "—";
		return String(
			participants.filter(
				(participant) =>
					participant.active &&
					participant.points >= from &&
					participant.points < next,
			).length,
		);
	};

	const closePreview = () => setPreview(null);

	const updateTier = (id: number, change: Partial<DraftTier>) => {
		setTiers((current) =>
			current.map((tier) => (tier.id === id ? { ...tier, ...change } : tier)),
		);
		setPreview(null);
		setError(null);
	};

	const previewChanges = async () => {
		if (busy || !dirty || !valid || !reason.trim()) return;
		const request: ScoringConfigurationRequest = {
			expectedVersion: saved.version,
			tiers: normalizedTiers.map((tier) => ({
				name: tier.name,
				points: tier.points ?? 0,
			})),
			activities: normalizedActivities.map((activity) => ({
				creditType: activity.creditType,
				points: activity.points ?? 0,
			})),
			applyRetroactively: retroactive,
			reason: reason.trim(),
		};
		setBusy(true);
		setError(null);
		try {
			const result = await Apies.previewScoringConfiguration(request);
			setPreview({ request, result });
			requestAnimationFrame(() => previewBackRef.current?.focus());
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

	const discardChanges = () => {
		setTiers(draftTiers(saved));
		setActivities(draftActivities(saved));
		setReason("");
		setRetroactive(false);
		setPreview(null);
		setError(null);
	};

	const reloadConfiguration = async () => {
		if (busy) return;
		setBusy(true);
		setError(null);
		try {
			const overview = await onRefresh();
			if (!overview) {
				setError("We couldn't reload scoring. Try again.");
				return;
			}
			const current = overview.configuration;
			previousVersion.current = current.version;
			setSaved(current);
			setTiers(draftTiers(current));
			setActivities(draftActivities(current));
			setNextId(current.tiers.length);
			setReason("");
			setRetroactive(false);
			setPreview(null);
		} catch {
			setError("We couldn't reload scoring. Try again.");
		} finally {
			setBusy(false);
		}
	};

	const applyChanges = async () => {
		if (!preview || busy) return;
		setBusy(true);
		setError(null);
		try {
			const status = await Apies.saveScoringConfiguration({
				...preview.request,
				previewToken: preview.result.token,
			});
			if (status !== 200) {
				setError(
					status === 409
						? "Scoring changed. Reload the configuration and preview again."
						: "We couldn't save the scoring changes. Try again.",
				);
				return;
			}
			const applied: ScoringConfiguration = {
				version: saved.version + 1,
				tiers: preview.request.tiers,
				activities: preview.request.activities,
			};
			setSaved(applied);
			setTiers(draftTiers(applied));
			setActivities(draftActivities(applied));
			setReason("");
			setRetroactive(false);
			setPreview(null);
			setLastAppliedAt(new Date());
			onSaved();
			const overview = await onRefresh();
			if (!overview) {
				setError(
					"Scoring was saved, but we couldn't refresh the dashboard. Reload configuration.",
				);
			}
		} catch {
			setError("We couldn't save the scoring changes. Try again.");
		} finally {
			setBusy(false);
		}
	};

	return (
		<Box
			as="section"
			aria-labelledby="scoring-configuration-heading"
			className="hubRedesign__card scoringView__rules"
			background="default"
			borderColor="neutral-subtle"
			borderWidth="1"
			borderRadius="12"
		>
			<header className="scoringView__rulesHeader">
				<Heading level="2" size="medium" id="scoring-configuration-heading">
					Scoring rules
				</Heading>
				<BodyShort className="hubRedesign__muted">
					Rules change point values, not which activities qualify. A zero-point
					activity is still recorded.
				</BodyShort>
			</header>

			<div className="scoringView__rulesPanes">
				<section
					className="scoringView__pane"
					aria-labelledby="scoring-tiers-heading"
				>
					<div className="scoringView__paneHeader">
						<Heading level="3" size="small" id="scoring-tiers-heading">
							Tiers
						</Heading>
						<BodyShort size="small" className="hubRedesign__muted">
							Minimum points · first tier starts at 0
						</BodyShort>
					</div>
					<Table
						size="small"
						className="scoringView__tierTable"
						aria-label="Scoring tiers"
					>
						<Table.Header>
							<Table.Row className="scoringView__tierRow scoringView__tierRow--header">
								<Table.HeaderCell scope="col">#</Table.HeaderCell>
								<Table.HeaderCell scope="col">Name</Table.HeaderCell>
								<Table.HeaderCell scope="col">From</Table.HeaderCell>
								<Table.HeaderCell scope="col" align="right">
									Members
								</Table.HeaderCell>
								<Table.HeaderCell scope="col" aria-label="Actions" />
							</Table.Row>
						</Table.Header>
						<Table.Body>
							{tiers.map((tier, index) => {
								const duplicate = tierNameDuplicates.has(
									tier.name.trim().toLocaleLowerCase(),
								);
								const minimum = wholeNumber(tier.points);
								const previousMinimum =
									index > 0 ? wholeNumber(tiers[index - 1].points) : null;
								const minimumError =
									minimum === null
										? "Enter a non-negative whole number."
										: previousMinimum !== null && minimum <= previousMinimum
											? "Must be greater than the previous tier."
											: undefined;
								return (
									<Table.Row className="scoringView__tierRow" key={tier.id}>
										<Table.HeaderCell scope="row" className="hubRedesign__dim">
											{index + 1}
										</Table.HeaderCell>
										<Table.DataCell>
											<TextField
												ref={
													tier.id === nextId - 1 && index === tiers.length - 1
														? newTierNameRef
														: undefined
												}
												className="scoringView__compactField"
												label={`Tier ${index + 1} name`}
												hideLabel
												value={tier.name}
												maxLength={80}
												error={
													!tier.name.trim()
														? "Enter a tier name."
														: duplicate
															? "Tier names must be unique."
															: undefined
												}
												aria-invalid={!tier.name.trim() || duplicate}
												onChange={(event) =>
													updateTier(tier.id, { name: event.target.value })
												}
											/>
										</Table.DataCell>
										<Table.DataCell>
											<div
												className="scoringView__numberInput"
												data-invalid={minimumError ? "true" : undefined}
											>
												<TextField
													className="scoringView__compactField"
													label={`Tier ${index + 1} minimum points`}
													hideLabel
													type="number"
													inputMode="numeric"
													min="0"
													step="1"
													readOnly={index === 0}
													value={index === 0 ? "0" : tier.points}
													error={minimumError}
													aria-invalid={Boolean(minimumError)}
													onChange={(event) =>
														updateTier(tier.id, {
															points: event.target.value,
														})
													}
												/>
												<span aria-hidden="true">pts</span>
											</div>
										</Table.DataCell>
										<Table.DataCell
											align="right"
											className="scoringView__tabular"
										>
											{membersInTier(index)}
										</Table.DataCell>
										<Table.DataCell>
											<Button
												type="button"
												size="xsmall"
												variant="tertiary"
												data-color="neutral"
												disabled={index === 0}
												aria-label={`Remove tier ${tier.name || index + 1}`}
												onClick={() => {
													setTiers((current) =>
														current.filter((item) => item.id !== tier.id),
													);
													setPreview(null);
													setError(null);
												}}
											>
												×
											</Button>
										</Table.DataCell>
									</Table.Row>
								);
							})}
						</Table.Body>
					</Table>
					{currentTierError && (
						<BodyShort className="scoringView__validation" role="alert">
							{currentTierError}
						</BodyShort>
					)}
					<Button
						type="button"
						size="small"
						variant="tertiary"
						className="scoringView__addTier"
						disabled={tiers.length >= 50}
						onClick={() => {
							const lastMin = wholeNumber(tiers.at(-1)?.points ?? "0") ?? 0;
							setTiers((current) => [
								...current,
								{
									id: nextId,
									name: "",
									points: String(lastMin + 100),
								},
							]);
							setNextId((value) => value + 1);
							setPreview(null);
							setError(null);
							requestAnimationFrame(() => newTierNameRef.current?.focus());
						}}
					>
						+ Add tier
					</Button>
				</section>

				<section
					className="scoringView__pane"
					aria-labelledby="scoring-activities-heading"
				>
					<div className="scoringView__paneHeader">
						<Heading level="3" size="small" id="scoring-activities-heading">
							Activity points
						</Heading>
						<BodyShort size="small" className="hubRedesign__muted">
							Per qualifying activity
						</BodyShort>
					</div>
					<div className="scoringView__activities">
						{activities.map((activity, index) => {
							const details = ACTIVITY_TYPES.find(
								(item) => item.creditType === activity.creditType,
							);
							if (!details) return null;
							const original =
								saved.activities.find(
									(item) => item.creditType === activity.creditType,
								)?.points ?? 0;
							const changed = wholeNumber(activity.points) !== original;
							return (
								<div
									className="scoringView__activityRow"
									key={activity.creditType}
								>
									<span
										className={`scoringView__activityDot scoringView__activityDot--${details.color}`}
										aria-hidden="true"
									/>
									<div className="scoringView__activityName">
										<span>{details.label}</span>
										{changed && (
											<BodyShort size="small" className="scoringView__wasValue">
												was {original}
											</BodyShort>
										)}
									</div>
									<div
										className="scoringView__numberInput"
										data-warning={changed ? "true" : undefined}
									>
										<TextField
											className="scoringView__compactField"
											label={`${details.label} points`}
											hideLabel
											value={activity.points}
											type="number"
											inputMode="numeric"
											min="0"
											step="1"
											aria-invalid={Boolean(activityErrors[index])}
											error={activityErrors[index] ?? undefined}
											onChange={(event) => {
												setActivities((current) =>
													current.map((item) =>
														item.creditType === activity.creditType
															? { ...item, points: event.target.value }
															: item,
													),
												);
												setPreview(null);
												setError(null);
											}}
										/>
										<span aria-hidden="true">pts</span>
									</div>
								</div>
							);
						})}
					</div>
				</section>
			</div>

			<footer className="scoringView__changeBar">
				{dirty ? (
					<>
						<div className="scoringView__changeRow">
							<BodyShort
								className="scoringView__unsaved"
								role="status"
								aria-live="polite"
							>
								{changes.length} unsaved{" "}
								{changes.length === 1 ? "change" : "changes"}
							</BodyShort>
							<TextField
								className="scoringView__reasonField"
								label="Reason for change (required)"
								hideLabel
								value={reason}
								maxLength={1000}
								placeholder="Reason for change (required)"
								error={
									reason.length > 1000
										? "Reason must be 1000 characters or fewer."
										: undefined
								}
								onChange={(event) => {
									setReason(event.target.value);
									setPreview(null);
									setError(null);
								}}
							/>
							<Button
								type="button"
								variant="secondary"
								data-color="neutral"
								disabled={busy}
								onClick={discardChanges}
							>
								Discard
							</Button>
							<Button
								type="button"
								disabled={busy || !valid || !reason.trim()}
								loading={busy && !preview}
								onClick={() => void previewChanges()}
							>
								Preview changes
							</Button>
						</div>
						<Checkbox
							checked={retroactive}
							onChange={(event) => {
								setRetroactive(event.target.checked);
								setPreview(null);
							}}
						>
							Also re-value current-season credits
						</Checkbox>
						<BodyShort size="small" className="scoringView__retroactiveHelp">
							Off: existing credits keep their points. On: differences are
							recorded as adjustments, including for inactive participants.
							Manual corrections and closed seasons are never changed.
						</BodyShort>
					</>
				) : (
					<div className="scoringView__changeRow">
						<BodyShort
							className="hubRedesign__muted"
							role="status"
							aria-live="polite"
						>
							{lastAppliedAt
								? "Changes applied just now."
								: "All rules are saved."}
						</BodyShort>
						<Button
							type="button"
							variant="tertiary"
							disabled={busy}
							loading={busy}
							onClick={() => void reloadConfiguration()}
						>
							Reload configuration
						</Button>
					</div>
				)}

				{error && (
					<div className="scoringView__errorBlock">
						<BodyShort className="scoringView__error" role="alert">
							{error}
						</BodyShort>
						{error.includes("Scoring changed") && (
							<Button
								type="button"
								size="small"
								variant="tertiary"
								disabled={busy}
								loading={busy}
								onClick={() => void reloadConfiguration()}
							>
								Reload configuration
							</Button>
						)}
					</div>
				)}

				{preview && (
					<Box
						className="scoringView__preview"
						background="default"
						borderColor="neutral-subtle"
						borderWidth="1"
						borderRadius="8"
						padding="space-16"
					>
						<div className="scoringView__previewGrid">
							<section aria-labelledby="scoring-preview-rules-heading">
								<Heading
									level="4"
									size="xsmall"
									id="scoring-preview-rules-heading"
								>
									Rule changes
								</Heading>
								{changes.length === 0 ? (
									<BodyShort className="hubRedesign__muted">
										No rule changes.
									</BodyShort>
								) : (
									<ul className="scoringView__previewList">
										{changes.map((change) => (
											<li key={`${change.label}-${change.oldValue}`}>
												<span>{change.label}</span>
												<span className="scoringView__previewValue">
													{change.oldValue} → <strong>{change.newValue}</strong>
												</span>
											</li>
										))}
									</ul>
								)}
							</section>
							<section aria-labelledby="scoring-preview-participants-heading">
								<Heading
									level="4"
									size="xsmall"
									id="scoring-preview-participants-heading"
								>
									Tier changes for participants
								</Heading>
								{preview.result.participants.length === 0 ? (
									<BodyShort className="hubRedesign__muted">
										No one changes tier.
									</BodyShort>
								) : (
									<ul className="scoringView__previewList">
										{preview.result.participants.map((participant) => (
											<li key={participant.participantId}>
												<span>{participant.fullName}</span>
												<span className="scoringView__previewValue">
													{participant.levelBefore === participant.levelAfter
														? "No tier change"
														: `${participant.levelBefore} → ${participant.levelAfter}`}
													{participant.pointsBefore !==
														participant.pointsAfter &&
														` · ${participant.pointsBefore} → ${participant.pointsAfter} (${participant.pointsAfter > participant.pointsBefore ? "+" : ""}${participant.pointsAfter - participant.pointsBefore})`}
												</span>
											</li>
										))}
									</ul>
								)}
								<BodyShort size="small" className="scoringView__previewNote">
									{preview.request.applyRetroactively
										? `${preview.result.affectedCredits} current-season credits will be re-valued; differences are recorded as adjustments.`
										: "Existing credits keep their points; new values apply to future activity."}
								</BodyShort>
								{preview.request.applyRetroactively && (
									<BodyShort size="small" className="hubRedesign__muted">
										Total point change:{" "}
										{preview.result.pointsDelta > 0 ? "+" : ""}
										{preview.result.pointsDelta}
									</BodyShort>
								)}
							</section>
						</div>
						<div className="scoringView__previewActions">
							<Button
								ref={previewBackRef}
								type="button"
								variant="secondary"
								data-color="neutral"
								disabled={busy}
								onClick={closePreview}
							>
								Back to editing
							</Button>
							<Button
								type="button"
								data-color="accent"
								loading={busy}
								onClick={() => void applyChanges()}
							>
								Apply changes
							</Button>
						</div>
					</Box>
				)}
			</footer>
		</Box>
	);
}

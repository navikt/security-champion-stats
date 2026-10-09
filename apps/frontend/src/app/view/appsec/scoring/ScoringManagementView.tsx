"use client";

import {
	BodyShort,
	Box,
	Button,
	DatePicker,
	Heading,
	Search,
	Select,
	Table,
	Tag,
	TextField,
	useDatepicker,
} from "@navikt/ds-react";
import { Fragment, useEffect, useMemo, useRef, useState } from "react";
import { Apies } from "@/app/shared/hooks/Apies";
import { creditTypeLabel } from "@/app/utils/scoringUtils";
import type {
	ActivityCredit,
	AdminParticipantScore,
	AdminScoringOverview,
} from "@/app/utils/Variables";
import { ScoreHistory } from "@/app/view/history/ScoreHistory";
import { ScoringConfigurationView } from "./ScoringConfigurationView";

const dateFormatter = new Intl.DateTimeFormat("en-GB", {
	day: "numeric",
	month: "short",
	year: "numeric",
	timeZone: "Europe/Oslo",
});

function parseLocalDate(value: string): Date {
	const [year, month, day] = value.split("-").map(Number);
	return new Date(year, month - 1, day);
}

function toDateString(date: Date): string {
	const year = date.getFullYear();
	const month = String(date.getMonth() + 1).padStart(2, "0");
	const day = String(date.getDate()).padStart(2, "0");
	return `${year}-${month}-${day}`;
}

function formatDate(value: string): string {
	return dateFormatter.format(parseLocalDate(value));
}

function dayOfSeason(startedOn: string, today: string): number {
	const start = parseLocalDate(startedOn);
	const current = parseLocalDate(today);
	return Math.max(
		1,
		Math.floor(
			(Date.UTC(current.getFullYear(), current.getMonth(), current.getDate()) -
				Date.UTC(start.getFullYear(), start.getMonth(), start.getDate())) /
				86_400_000,
		) + 1,
	);
}

function getTier(
	points: number,
	tiers: AdminScoringOverview["configuration"]["tiers"],
) {
	return (
		[...tiers]
			.sort((a, b) => a.points - b.points)
			.filter((tier) => tier.points <= points)
			.at(-1)?.name ?? "—"
	);
}

function creditLabel(credit: ActivityCredit): string {
	return `${creditTypeLabel(credit.creditType)} · ${credit.sourceReference} · ${credit.points} pts · season ${formatDate(credit.seasonStartsOn)}`;
}

function participantName(participant: AdminParticipantScore): string {
	return participant.fullName || participant.email || "Name unavailable";
}

export function ScoringManagementView({
	overview,
	onRefresh,
}: {
	overview: AdminScoringOverview;
	onRefresh: () => Promise<AdminScoringOverview | null>;
}) {
	const [nextResetDate, setNextResetDate] = useState(
		overview.season.nextResetDate,
	);
	const [selectedParticipant, setSelectedParticipant] =
		useState<AdminParticipantScore | null>(null);
	const [historyParticipant, setHistoryParticipant] =
		useState<AdminParticipantScore | null>(null);
	const historyTriggerRefs = useRef(new Map<string, HTMLButtonElement>());
	const [credits, setCredits] = useState<ActivityCredit[]>([]);
	const [creditsLoading, setCreditsLoading] = useState(false);
	const [creditsFailed, setCreditsFailed] = useState(false);
	const [adjustmentPoints, setAdjustmentPoints] = useState("");
	const [adjustmentReason, setAdjustmentReason] = useState("");
	const [sourceCreditId, setSourceCreditId] = useState("");
	const [adjustmentError, setAdjustmentError] = useState<string | null>(null);
	const [adjustmentValidationError, setAdjustmentValidationError] = useState<
		string | null
	>(null);
	const [confirmingNewSeason, setConfirmingNewSeason] = useState(false);
	const [resetReason, setResetReason] = useState("");
	const [resetError, setResetError] = useState<string | null>(null);
	const [resetReasonError, setResetReasonError] = useState(false);
	const [dateError, setDateError] = useState<string | null>(null);
	const [dateSaved, setDateSaved] = useState(false);
	const [notice, setNotice] = useState<string | null>(null);
	const [busy, setBusy] = useState(false);
	const [filter, setFilter] = useState("");
	const [activeFilter, setActiveFilter] = useState<"active" | "all">("all");
	const [highlightedParticipantId, setHighlightedParticipantId] = useState<
		string | null
	>(null);
	const adjustTriggerRefs = useRef(new Map<string, HTMLButtonElement>());
	const dateSavedTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
	const highlightTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
	const resetTriggerRef = useRef<HTMLButtonElement>(null);
	const resetCancelRef = useRef<HTMLButtonElement>(null);
	const adjustmentInputRef = useRef<HTMLInputElement>(null);
	const creditRequestParticipantId = useRef<string | null>(null);

	const { datepickerProps, inputProps, setSelected } = useDatepicker({
		defaultSelected: parseLocalDate(overview.season.nextResetDate),
		fromDate: parseLocalDate(overview.today),
		onDateChange: (date) => {
			setNextResetDate(date ? toDateString(date) : "");
			setDateError(null);
			setDateSaved(false);
		},
	});
	const setSelectedRef = useRef(setSelected);
	setSelectedRef.current = setSelected;

	useEffect(() => {
		if (confirmingNewSeason) {
			requestAnimationFrame(() => resetCancelRef.current?.focus());
		}
	}, [confirmingNewSeason]);

	useEffect(() => {
		setNextResetDate(overview.season.nextResetDate);
		setSelectedRef.current(parseLocalDate(overview.season.nextResetDate));
		setConfirmingNewSeason(false);
		setResetReason("");
	}, [overview.season.nextResetDate]);

	useEffect(
		() => () => {
			if (dateSavedTimeout.current) clearTimeout(dateSavedTimeout.current);
			if (highlightTimeout.current) clearTimeout(highlightTimeout.current);
		},
		[],
	);

	useEffect(() => {
		const syncHistoryFromUrl = () => {
			const participantId = new URLSearchParams(window.location.search).get(
				"history",
			);
			setHistoryParticipant(
				participantId
					? (overview.participants.find(
							(participant) => participant.participantId === participantId,
						) ?? null)
					: null,
			);
		};
		syncHistoryFromUrl();
		window.addEventListener("popstate", syncHistoryFromUrl);
		return () => window.removeEventListener("popstate", syncHistoryFromUrl);
	}, [overview.participants]);

	const sortedParticipants = useMemo(() => {
		const ordered = [...overview.participants].sort(
			(a, b) =>
				b.points - a.points ||
				participantName(a).localeCompare(participantName(b)),
		);
		let rank = 0;
		let previousPoints: number | null = null;
		return ordered.map((participant) => {
			if (participant.points !== previousPoints) {
				rank += 1;
				previousPoints = participant.points;
			}
			return { participant, rank };
		});
	}, [overview.participants]);

	const activeCount = overview.participants.filter(
		(participant) => participant.active,
	).length;
	const shownParticipants = sortedParticipants.filter(({ participant }) => {
		if (activeFilter === "active" && !participant.active) return false;
		const query = filter.trim().toLocaleLowerCase();
		return (
			!query ||
			participantName(participant).toLocaleLowerCase().includes(query) ||
			participant.email.toLocaleLowerCase().includes(query)
		);
	});

	const saveResetDate = async () => {
		if (busy) return;
		if (
			!nextResetDate ||
			nextResetDate <= overview.season.startsOn ||
			nextResetDate <= overview.today
		) {
			setDateError(
				"Choose a date after today and after the current season started.",
			);
			return;
		}
		setBusy(true);
		setDateError(null);
		setNotice(null);
		try {
			const status = await Apies.updateNextResetDate(nextResetDate);
			if (status !== 200) {
				setDateError(
					"We couldn't save the date. Choose a valid date and try again.",
				);
				return;
			}
			setDateSaved(true);
			if (dateSavedTimeout.current) clearTimeout(dateSavedTimeout.current);
			dateSavedTimeout.current = setTimeout(() => setDateSaved(false), 2000);
			await onRefresh();
		} catch {
			setDateError(
				"We couldn't save the date. Choose a valid date and try again.",
			);
		} finally {
			setBusy(false);
		}
	};

	const closeAdjustment = (restoreFocus = false) => {
		const participantId = selectedParticipant?.participantId;
		creditRequestParticipantId.current = null;
		setSelectedParticipant(null);
		setAdjustmentError(null);
		setAdjustmentValidationError(null);
		if (restoreFocus && participantId) {
			requestAnimationFrame(() =>
				adjustTriggerRefs.current.get(participantId)?.focus(),
			);
		}
	};

	const openAdjustment = async (participant: AdminParticipantScore) => {
		if (selectedParticipant?.participantId === participant.participantId) {
			closeAdjustment(true);
			return;
		}
		creditRequestParticipantId.current = participant.participantId;
		setSelectedParticipant(participant);
		setCredits([]);
		setCreditsLoading(true);
		setCreditsFailed(false);
		setAdjustmentPoints("");
		setAdjustmentReason("");
		setSourceCreditId("");
		setAdjustmentError(null);
		setAdjustmentValidationError(null);
		try {
			const result = await Apies.getParticipantCredits(
				participant.participantId,
			);
			if (creditRequestParticipantId.current !== participant.participantId) {
				return;
			}
			if (result === null) {
				setAdjustmentError(
					"We couldn't load the activities. Close this row and try again.",
				);
				setCreditsFailed(true);
			} else {
				setCredits(result);
			}
		} catch {
			if (creditRequestParticipantId.current !== participant.participantId) {
				return;
			}
			setAdjustmentError(
				"We couldn't load the activities. Close this row and try again.",
			);
			setCreditsFailed(true);
		} finally {
			if (creditRequestParticipantId.current === participant.participantId) {
				setCreditsLoading(false);
				requestAnimationFrame(() => adjustmentInputRef.current?.focus());
			}
		}
	};

	const openScoreHistory = (participant: AdminParticipantScore) => {
		const url = new URL(window.location.href);
		url.searchParams.set("history", participant.participantId);
		window.history.pushState(window.history.state, "", url);
		setHistoryParticipant(participant);
	};

	const closeScoreHistory = () => {
		const url = new URL(window.location.href);
		url.searchParams.delete("history");
		window.history.replaceState(window.history.state, "", url);
		setHistoryParticipant(null);
	};

	const addAdjustment = async () => {
		if (!selectedParticipant || busy || creditsLoading || creditsFailed) return;
		const pointsDelta = Number(adjustmentPoints);
		if (
			!/^[-+]?\d+$/.test(adjustmentPoints.trim()) ||
			!Number.isSafeInteger(pointsDelta) ||
			pointsDelta === 0
		) {
			setAdjustmentValidationError("Enter a non-zero whole number.");
			return;
		}
		if (!adjustmentReason.trim()) {
			setAdjustmentValidationError("Enter a reason.");
			return;
		}

		const selectedCredit = credits.find(
			(credit) => credit.id === sourceCreditId,
		);
		const targetsCurrentSeason =
			!selectedCredit ||
			selectedCredit.seasonStartsOn === overview.season.startsOn;
		const effectiveDelta = targetsCurrentSeason
			? Math.max(pointsDelta, -selectedParticipant.points)
			: pointsDelta;
		if (effectiveDelta === 0) {
			setAdjustmentValidationError("The participant already has zero points.");
			return;
		}

		setBusy(true);
		setAdjustmentError(null);
		setAdjustmentValidationError(null);
		setNotice(null);
		try {
			const status = await Apies.addPointAdjustment(
				selectedParticipant.participantId,
				effectiveDelta,
				adjustmentReason.trim(),
				sourceCreditId || undefined,
			);
			if (status !== 201) {
				setAdjustmentError(
					"We couldn't save the adjustment. Check the details and try again.",
				);
				return;
			}
			const participantId = selectedParticipant.participantId;
			closeAdjustment();
			setNotice("The point adjustment was saved.");
			setHighlightedParticipantId(participantId);
			if (highlightTimeout.current) clearTimeout(highlightTimeout.current);
			highlightTimeout.current = setTimeout(
				() => setHighlightedParticipantId(null),
				1500,
			);
			await onRefresh();
		} catch {
			setAdjustmentError(
				"We couldn't save the adjustment. Check the details and try again.",
			);
		} finally {
			setBusy(false);
		}
	};

	const resetSeason = async () => {
		if (busy) return;
		if (!resetReason.trim()) {
			setResetReasonError(true);
			return;
		}
		setBusy(true);
		setResetError(null);
		setNotice(null);
		try {
			const status = await Apies.resetSeason(resetReason.trim());
			if (status !== 200) {
				setResetError("We couldn't start a new season. Try again.");
				return;
			}
			setConfirmingNewSeason(false);
			setResetReason("");
			setNotice("The new season has started.");
			await onRefresh();
		} catch {
			setResetError("We couldn't start a new season. Try again.");
		} finally {
			setBusy(false);
		}
	};

	const cancelNewSeason = () => {
		setConfirmingNewSeason(false);
		setResetError(null);
		setResetReasonError(false);
		requestAnimationFrame(() => resetTriggerRef.current?.focus());
	};

	const currentSeasonYear = parseLocalDate(
		overview.season.startsOn,
	).getFullYear();
	const nextSeasonYear = currentSeasonYear + 1;

	return (
		<main className="hubRedesign scoringView">
			<header className="hubRedesign__header scoringView__header">
				<BodyShort size="small" className="hubRedesign__eyebrow">
					Admin only
				</BodyShort>
				<Heading level="1" size="xlarge">
					Manage scoring
				</Heading>
				<BodyShort className="hubRedesign__muted">
					Configure activity points and tiers, correct scores, and manage
					seasons.
				</BodyShort>
			</header>

			{notice && (
				<BodyShort
					className="scoringView__notice"
					role="status"
					aria-live="polite"
				>
					{notice}
				</BodyShort>
			)}

			<Box
				as="section"
				aria-label="Season settings"
				className="hubRedesign__card scoringView__season"
				background="default"
				borderColor="neutral-subtle"
				borderWidth="1"
				borderRadius="12"
				padding="space-16"
			>
				<div className="scoringView__seasonInfo">
					<div className="scoringView__seasonTitle">
						<Heading level="2" size="small">
							Season {currentSeasonYear}
						</Heading>
						<Tag
							size="xsmall"
							variant="moderate"
							data-color={overview.season.endsOn ? "warning" : "success"}
						>
							<span className="scoringView__statusDot" aria-hidden="true" />
							{overview.season.endsOn
								? `Ends ${formatDate(overview.season.endsOn)}`
								: "Ongoing"}
						</Tag>
					</div>
					<BodyShort size="small" className="hubRedesign__muted">
						Started {formatDate(overview.season.startsOn)} · day{" "}
						{dayOfSeason(overview.season.startsOn, overview.today)}
					</BodyShort>
				</div>

				<div className="scoringView__nextSeason">
					<DatePicker {...datepickerProps}>
						<DatePicker.Input
							{...inputProps}
							label="Next season starts"
							description="Local time, Oslo"
							className="scoringView__dateField"
						/>
					</DatePicker>
					{nextResetDate !== overview.season.nextResetDate && (
						<Button
							type="button"
							ref={resetCancelRef}
							size="small"
							variant="secondary"
							data-color="info"
							loading={busy}
							onClick={() => void saveResetDate()}
						>
							Save date
						</Button>
					)}
					{dateSaved && (
						<BodyShort
							size="small"
							className="scoringView__saved"
							role="status"
							aria-live="polite"
						>
							Saved
						</BodyShort>
					)}
					{dateError && (
						<BodyShort className="scoringView__error" role="alert">
							{dateError}
						</BodyShort>
					)}
				</div>

				<div className="scoringView__reset">
					{!confirmingNewSeason ? (
						<Button
							ref={resetTriggerRef}
							type="button"
							size="small"
							variant="secondary"
							data-color="danger"
							disabled={busy}
							onClick={() => {
								setResetReason("");
								setResetReasonError(false);
								setResetError(null);
								setConfirmingNewSeason(true);
							}}
						>
							Start new season...
						</Button>
					) : (
						<div className="scoringView__resetConfirm">
							<BodyShort className="scoringView__warning">
								Ends Season {currentSeasonYear} now and resets everyone's season
								points. This can't be undone.
							</BodyShort>
							<TextField
								label="Reason for starting the new season"
								hideLabel
								className="scoringView__resetReason"
								placeholder="Reason (required)"
								value={resetReason}
								error={
									resetReasonError
										? "Enter a reason for the season reset."
										: undefined
								}
								onChange={(event) => {
									setResetReason(event.target.value);
									setResetReasonError(false);
								}}
							/>
							<Button
								type="button"
								size="small"
								variant="secondary"
								data-color="neutral"
								disabled={busy}
								onClick={cancelNewSeason}
							>
								Cancel
							</Button>
							<Button
								type="button"
								size="small"
								variant="primary"
								data-color="danger"
								loading={busy}
								onClick={() => void resetSeason()}
							>
								Start Season {nextSeasonYear}
							</Button>
						</div>
					)}
					{resetError && (
						<BodyShort className="scoringView__error" role="alert">
							{resetError}
						</BodyShort>
					)}
				</div>
			</Box>

			<ScoringConfigurationView
				configuration={overview.configuration}
				participants={overview.participants}
				onRefresh={async () => onRefresh()}
				onSaved={() => setNotice("Scoring configuration saved.")}
			/>

			<Box
				as="section"
				aria-labelledby="scoring-participants-heading"
				className="hubRedesign__card scoringView__participants"
				background="default"
				borderColor="neutral-subtle"
				borderWidth="1"
				borderRadius="12"
			>
				<header className="scoringView__participantsHeader">
					<div>
						<Heading level="2" size="medium" id="scoring-participants-heading">
							Participants and points
						</Heading>
						<BodyShort size="small" className="hubRedesign__muted">
							{activeCount} active · ranked by season points
						</BodyShort>
					</div>
					<div className="scoringView__participantTools">
						<fieldset className="scoringView__activeFilter">
							<legend className="scoringView__filterLegend">
								Participant status
							</legend>
							<Button
								type="button"
								size="xsmall"
								variant={activeFilter === "active" ? "secondary" : "tertiary"}
								aria-pressed={activeFilter === "active"}
								onClick={() => setActiveFilter("active")}
							>
								Active
							</Button>
							<Button
								type="button"
								size="xsmall"
								variant={activeFilter === "all" ? "secondary" : "tertiary"}
								aria-pressed={activeFilter === "all"}
								onClick={() => setActiveFilter("all")}
							>
								All
							</Button>
						</fieldset>
						<Search
							className="scoringView__search"
							label="Filter by name or email"
							variant="simple"
							onChange={setFilter}
						/>
					</div>
				</header>

				<div className="scoringView__participantTable">
					<Table size="small">
						<Table.Header>
							<Table.Row>
								<Table.HeaderCell scope="col">#</Table.HeaderCell>
								<Table.HeaderCell scope="col">Participant</Table.HeaderCell>
								<Table.HeaderCell scope="col">Status</Table.HeaderCell>
								<Table.HeaderCell scope="col" align="right">
									Points
								</Table.HeaderCell>
								<Table.HeaderCell scope="col">Tier</Table.HeaderCell>
								<Table.HeaderCell scope="col">Actions</Table.HeaderCell>
							</Table.Row>
						</Table.Header>
						<Table.Body>
							{shownParticipants.map(({ participant, rank }) => (
								<Fragment key={participant.participantId}>
									<Table.Row
										className={[
											"scoringView__participantRow",
											!participant.active
												? "scoringView__participantRow--inactive"
												: "",
											highlightedParticipantId === participant.participantId
												? "scoringView__participantRow--highlighted"
												: "",
										]
											.filter(Boolean)
											.join(" ")}
									>
										<Table.DataCell className="hubRedesign__dim scoringView__tabular">
											{rank}
										</Table.DataCell>
										<Table.HeaderCell scope="row">
											<div className="scoringView__participantIdentity">
												<strong>{participantName(participant)}</strong>
												<span>{participant.email}</span>
											</div>
										</Table.HeaderCell>
										<Table.DataCell>
											<span className="scoringView__participantStatus">
												<span
													className={`scoringView__statusDot${participant.active ? "" : " scoringView__statusDot--inactive"}`}
													aria-hidden="true"
												/>
												{participant.active ? "Active" : "Deactivated"}
											</span>
										</Table.DataCell>
										<Table.DataCell
											align="right"
											className="scoringView__points scoringView__tabular"
										>
											{participant.points}
										</Table.DataCell>
										<Table.DataCell>
											<Tag
												size="xsmall"
												variant="moderate"
												data-color="neutral"
											>
												{getTier(
													participant.points,
													overview.configuration.tiers,
												)}
											</Tag>
										</Table.DataCell>
										<Table.DataCell>
											<div className="scoringView__participantActions">
												<Button
													ref={(element) => {
														if (element) {
															historyTriggerRefs.current.set(
																participant.participantId,
																element,
															);
														} else {
															historyTriggerRefs.current.delete(
																participant.participantId,
															);
														}
													}}
													type="button"
													size="xsmall"
													variant="tertiary"
													data-color="info"
													aria-label={`View score history for ${participantName(participant)}`}
													onClick={() => openScoreHistory(participant)}
												>
													History
												</Button>
												<Button
													ref={(element) => {
														if (element) {
															adjustTriggerRefs.current.set(
																participant.participantId,
																element,
															);
														}
													}}
													type="button"
													size="xsmall"
													variant="secondary"
													data-color="neutral"
													aria-expanded={
														selectedParticipant?.participantId ===
														participant.participantId
													}
													aria-controls={`adjust-${participant.participantId}`}
													onClick={() => void openAdjustment(participant)}
												>
													Adjust
												</Button>
											</div>
										</Table.DataCell>
									</Table.Row>
									{selectedParticipant?.participantId ===
										participant.participantId && (
										<Table.Row>
											<Table.DataCell
												colSpan={6}
												className="scoringView__adjustCell"
											>
												<form
													id={`adjust-${participant.participantId}`}
													className="scoringView__adjustRow"
													onSubmit={(event) => {
														event.preventDefault();
														void addAdjustment();
													}}
													onKeyDown={(event) => {
														if (event.key !== "Escape") return;
														event.preventDefault();
														closeAdjustment(true);
													}}
												>
													<BodyShort
														size="small"
														className="scoringView__adjustLabel"
													>
														Adjust by
													</BodyShort>
													<TextField
														ref={adjustmentInputRef}
														className="scoringView__adjustDelta"
														label={`Adjust points for ${participantName(participant)}`}
														hideLabel
														type="number"
														inputMode="numeric"
														step="1"
														placeholder="+5 / −5"
														value={adjustmentPoints}
														onChange={(event) =>
															setAdjustmentPoints(event.target.value)
														}
													/>
													<TextField
														className="scoringView__adjustReason"
														label={`Reason for adjusting ${participantName(participant)}'s points`}
														hideLabel
														placeholder="Reason (required, shown in their history)"
														value={adjustmentReason}
														maxLength={1000}
														onChange={(event) =>
															setAdjustmentReason(event.target.value)
														}
													/>
													<AdjustmentPreview
														participant={participant}
														delta={adjustmentPoints}
														sourceCredit={credits.find(
															(credit) => credit.id === sourceCreditId,
														)}
														currentSeasonStartsOn={overview.season.startsOn}
													/>
													<Button
														type="button"
														size="small"
														variant="secondary"
														data-color="neutral"
														disabled={busy}
														onClick={() => closeAdjustment(true)}
													>
														Cancel
													</Button>
													<Button
														type="submit"
														size="small"
														data-color="accent"
														loading={busy}
														disabled={
															creditsLoading ||
															creditsFailed ||
															!adjustmentReason.trim() ||
															!/^[-+]?\d+$/.test(adjustmentPoints.trim()) ||
															!Number.isSafeInteger(Number(adjustmentPoints)) ||
															Number(adjustmentPoints) === 0
														}
													>
														Apply
													</Button>

													{creditsLoading && (
														<BodyShort role="status">
															Loading activities...
														</BodyShort>
													)}
													{creditsFailed && (
														<BodyShort
															className="scoringView__error"
															role="alert"
														>
															{adjustmentError}
														</BodyShort>
													)}
													{adjustmentValidationError && (
														<BodyShort
															className="scoringView__error"
															role="alert"
														>
															{adjustmentValidationError}
														</BodyShort>
													)}
													{adjustmentError && !creditsFailed && (
														<BodyShort
															className="scoringView__error"
															role="alert"
														>
															{adjustmentError}
														</BodyShort>
													)}
													<Select
														className="scoringView__sourceCredit"
														label="Activity to correct"
														hideLabel
														value={sourceCreditId}
														onChange={(event) =>
															setSourceCreditId(event.target.value)
														}
													>
														<option value="">
															Current season (no linked activity)
														</option>
														{credits.map((credit) => (
															<option key={credit.id} value={credit.id}>
																{creditLabel(credit)}
															</option>
														))}
													</Select>
												</form>
											</Table.DataCell>
										</Table.Row>
									)}
								</Fragment>
							))}
							{shownParticipants.length === 0 && (
								<Table.Row>
									<Table.DataCell colSpan={6}>
										{filter || activeFilter === "active"
											? "No participants match this filter."
											: "No active participants."}
									</Table.DataCell>
								</Table.Row>
							)}
						</Table.Body>
					</Table>
				</div>
			</Box>

			{historyParticipant && (
				<ScoreHistory
					key={historyParticipant.participantId}
					variant="admin"
					participantId={historyParticipant.participantId}
					participantName={participantName(historyParticipant)}
					participantEmail={historyParticipant.email}
					returnFocusTo={() =>
						historyTriggerRefs.current.get(historyParticipant.participantId) ??
						null
					}
					onClose={closeScoreHistory}
					onAdjust={() => void openAdjustment(historyParticipant)}
				/>
			)}
		</main>
	);
}

function AdjustmentPreview({
	participant,
	delta,
	sourceCredit,
	currentSeasonStartsOn,
}: {
	participant: AdminParticipantScore;
	delta: string;
	sourceCredit: ActivityCredit | undefined;
	currentSeasonStartsOn: string;
}) {
	const parsed = /^[-+]?\d+$/.test(delta.trim()) ? Number(delta) : 0;
	const targetsCurrentSeason =
		!sourceCredit || sourceCredit.seasonStartsOn === currentSeasonStartsOn;
	const effectiveDelta = targetsCurrentSeason
		? Math.max(parsed, -participant.points)
		: parsed;
	if (sourceCredit && !targetsCurrentSeason) {
		return (
			<BodyShort className="scoringView__adjustPreview" aria-live="polite">
				{`Linked season ${formatDate(sourceCredit.seasonStartsOn)} · ${parsed > 0 ? "+" : ""}${parsed} pts`}
			</BodyShort>
		);
	}
	return (
		<BodyShort className="scoringView__adjustPreview" aria-live="polite">
			{participant.points} → {Math.max(0, participant.points + effectiveDelta)}{" "}
			pts
			{effectiveDelta !== parsed && " (clamped at 0)"}
		</BodyShort>
	);
}

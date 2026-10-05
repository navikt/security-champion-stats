"use client";

import { Apies } from "@/app/shared/hooks/Apies";
import {
	ActivityCredit,
	AdminParticipantScore,
	AdminScoringOverview,
} from "@/app/utils/Variables";
import {
	BodyShort,
	Button,
	DatePicker,
	Heading,
	Modal,
	Select,
	Table,
	TextField,
	VStack,
	useDatepicker,
} from "@navikt/ds-react";
import { useMemo, useState } from "react";

export function ScoringManagementView({
	overview,
	onRefresh,
}: {
	overview: AdminScoringOverview;
	onRefresh: () => Promise<void>;
}) {
	const [nextResetDate, setNextResetDate] = useState(
		overview.season.nextResetDate,
	);
	const [selectedParticipant, setSelectedParticipant] =
		useState<AdminParticipantScore | null>(null);
	const [credits, setCredits] = useState<ActivityCredit[]>([]);
	const [creditsLoading, setCreditsLoading] = useState(false);
	const [creditsFailed, setCreditsFailed] = useState(false);
	const [adjustmentPoints, setAdjustmentPoints] = useState("");
	const [adjustmentReason, setAdjustmentReason] = useState("");
	const [sourceCreditId, setSourceCreditId] = useState("");
	const [adjustmentError, setAdjustmentError] = useState<string | null>(null);
	const [adjustmentValidationError, setAdjustmentValidationError] =
		useState<string | null>(null);
	const [resetDialogOpen, setResetDialogOpen] = useState(false);
	const [resetReason, setResetReason] = useState("");
	const [resetError, setResetError] = useState<string | null>(null);
	const [resetReasonError, setResetReasonError] = useState(false);
	const [dateError, setDateError] = useState<string | null>(null);
	const [notice, setNotice] = useState<string | null>(null);
	const [busy, setBusy] = useState(false);

	const { datepickerProps, inputProps } = useDatepicker({
		defaultSelected: parseLocalDate(overview.season.nextResetDate),
		fromDate: parseLocalDate(overview.today),
		onDateChange: (date) => setNextResetDate(date ? toDateString(date) : ""),
	});

	const sortedParticipants = useMemo(
		() =>
			[...overview.participants].sort(
				(a, b) =>
					b.points - a.points || a.fullName.localeCompare(b.fullName),
			),
		[overview.participants],
	);

	const saveResetDate = async () => {
		if (!nextResetDate || busy) {
			setDateError("Choose the next season start date.");
			return;
		}
		setBusy(true);
		setDateError(null);
		setNotice(null);
		try {
			const status = await Apies.updateNextResetDate(nextResetDate);
			if (status !== 200) {
				setDateError(
					"We couldn't save the date. Choose a date after today and try again.",
				);
				return;
			}
			setNotice("The next season start date was saved.");
			await onRefresh();
		} catch {
			setDateError(
				"We couldn't save the date. Choose a date after today and try again.",
			);
		} finally {
			setBusy(false);
		}
	};

	const openAdjustment = async (participant: AdminParticipantScore) => {
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
			const result = await Apies.getParticipantCredits(participant.participantId);
			if (result === null) {
				setAdjustmentError(
					"We couldn't load the activities. Close this dialog and try again.",
				);
				setCreditsFailed(true);
			} else {
				setCredits(result);
			}
		} catch {
			setAdjustmentError(
				"We couldn't load the activities. Close this dialog and try again.",
			);
			setCreditsFailed(true);
		} finally {
			setCreditsLoading(false);
		}
	};

	const closeAdjustment = () => {
		setSelectedParticipant(null);
		setAdjustmentError(null);
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

		setBusy(true);
		setAdjustmentError(null);
		setAdjustmentValidationError(null);
		setNotice(null);
		try {
			const status = await Apies.addPointAdjustment(
				selectedParticipant.participantId,
				pointsDelta,
				adjustmentReason.trim(),
				sourceCreditId || undefined,
			);
			if (status !== 201) {
				setAdjustmentError(
					"We couldn't save the adjustment. Check the details and try again.",
				);
				return;
			}
			setSelectedParticipant(null);
			setNotice("The point adjustment was saved.");
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
			setResetDialogOpen(false);
			setResetReason("");
			setNotice("The new season has started.");
			await onRefresh();
		} catch {
			setResetError("We couldn't start a new season. Try again.");
		} finally {
			setBusy(false);
		}
	};

	const today = overview.today;

	return (
		<VStack gap="space-24">
			<VStack gap="space-4">
				<Heading level="1" size="xlarge">
					Manage scoring
				</Heading>
				<BodyShort>
					View current-season points, correct scores, and manage season starts.
				</BodyShort>
			</VStack>

			{notice && <BodyShort role="status">{notice}</BodyShort>}

			<section aria-labelledby="season-heading">
				<VStack gap="space-16">
					<Heading level="2" size="large" id="season-heading">
						Current season
					</Heading>
					<BodyShort>
						The season started on {overview.season.startsOn}.
					</BodyShort>
					<BodyShort>
						{overview.season.endsOn
							? `The season ends on ${overview.season.endsOn}.`
							: "The season is ongoing."}
					</BodyShort>
					<DatePicker {...datepickerProps}>
						<DatePicker.Input
							{...inputProps}
							label="Next season start"
							description="The date uses local time in Oslo."
						/>
					</DatePicker>
					{dateError && <BodyShort role="alert">{dateError}</BodyShort>}
					<Button
						type="button"
						variant="secondary"
						loading={busy}
						onClick={saveResetDate}
					>
						Save date
					</Button>
					<Button
						type="button"
						data-color="danger"
						variant="secondary"
						onClick={() => {
							setResetReason("");
							setResetReasonError(false);
							setResetError(null);
							setResetDialogOpen(true);
						}}
					>
						Start a new season
					</Button>
				</VStack>
			</section>

			<section aria-labelledby="scores-heading">
				<VStack gap="space-16">
					<Heading level="2" size="large" id="scores-heading">
						Participants and points
					</Heading>
					<Table size="small">
						<Table.Header>
							<Table.Row>
								<Table.HeaderCell scope="col">Name</Table.HeaderCell>
								<Table.HeaderCell scope="col">Email</Table.HeaderCell>
								<Table.HeaderCell scope="col">Status</Table.HeaderCell>
								<Table.HeaderCell scope="col">Points</Table.HeaderCell>
								<Table.HeaderCell scope="col">Level</Table.HeaderCell>
								<Table.HeaderCell scope="col">Actions</Table.HeaderCell>
							</Table.Row>
						</Table.Header>
						<Table.Body>
							{sortedParticipants.map((participant) => (
								<Table.Row key={participant.participantId}>
									<Table.HeaderCell scope="row">
										{participant.fullName || "Name unavailable"}
									</Table.HeaderCell>
									<Table.DataCell>{participant.email}</Table.DataCell>
									<Table.DataCell>
										{participant.active ? "Active" : "Deactivated"}
									</Table.DataCell>
									<Table.DataCell>{participant.points}</Table.DataCell>
									<Table.DataCell>{participant.level}</Table.DataCell>
									<Table.DataCell>
										<Button
											size="small"
											data-color="neutral"
											variant="secondary"
											onClick={() => openAdjustment(participant)}
										>
											Adjust points
										</Button>
									</Table.DataCell>
								</Table.Row>
							))}
							{sortedParticipants.length === 0 && (
								<Table.Row>
									<Table.DataCell colSpan={6}>
										No participants.
									</Table.DataCell>
								</Table.Row>
							)}
						</Table.Body>
					</Table>
				</VStack>
			</section>

			<Modal
				open={selectedParticipant !== null}
				onClose={closeAdjustment}
				header={{ heading: "Adjust points" }}
			>
				<Modal.Body>
					<VStack gap="space-16">
						{selectedParticipant && (
							<BodyShort>
								{`Adjust points for ${selectedParticipant.fullName}. Selecting an activity assigns the adjustment to that activity's season.`}
							</BodyShort>
						)}
						<TextField
							label="Point change"
							description="Use a positive number to add points and a negative number to subtract them."
							inputMode="numeric"
							value={adjustmentPoints}
							onChange={(event) => setAdjustmentPoints(event.target.value)}
						/>
						<Select
							label="Activity to correct"
							value={sourceCreditId}
							onChange={(event) => setSourceCreditId(event.target.value)}
						>
							<option value="">No activity, use the current season</option>
							{credits.map((credit) => (
								<option key={credit.id} value={credit.id}>
									{creditLabel(credit, creditTypeLabel(credit.creditType))}
								</option>
							))}
						</Select>
						{creditsLoading && (
							<BodyShort role="status">Loading activities...</BodyShort>
						)}
						<TextField
							label="Reason"
							value={adjustmentReason}
							onChange={(event) => setAdjustmentReason(event.target.value)}
						/>
						{adjustmentValidationError && (
							<BodyShort role="alert">{adjustmentValidationError}</BodyShort>
						)}
						{adjustmentError && (
							<BodyShort role="alert">{adjustmentError}</BodyShort>
						)}
					</VStack>
				</Modal.Body>
				<Modal.Footer>
					<Button type="button" variant="tertiary" onClick={closeAdjustment}>
						Cancel
					</Button>
					<Button type="button" loading={busy} onClick={addAdjustment}>
						Save adjustment
					</Button>
				</Modal.Footer>
			</Modal>

			<Modal
				open={resetDialogOpen}
				onClose={() => setResetDialogOpen(false)}
				header={{ heading: "Start a new season?" }}
			>
				<Modal.Body>
					<VStack gap="space-16">
						<BodyShort>
							{`The season that started on ${overview.season.startsOn} ends on ${toDateString(addDays(parseLocalDate(today), -1))}. The new season starts on ${today}.`}
						</BodyShort>
						<TextField
							label="Reason"
							value={resetReason}
							error={resetReasonError ? "Enter a reason." : undefined}
							onChange={(event) => {
								setResetReason(event.target.value);
								setResetReasonError(false);
							}}
						/>
						{resetError && <BodyShort role="alert">{resetError}</BodyShort>}
					</VStack>
				</Modal.Body>
				<Modal.Footer>
					<Button
						type="button"
						variant="tertiary"
						onClick={() => setResetDialogOpen(false)}
					>
						Cancel
					</Button>
					<Button
						type="button"
						data-color="danger"
						loading={busy}
						onClick={resetSeason}
					>
						Start new season
					</Button>
				</Modal.Footer>
			</Modal>
		</VStack>
	);
}

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

function addDays(date: Date, days: number): Date {
	const result = new Date(date);
	result.setDate(result.getDate() + days);
	return result;
}

function creditLabel(
	credit: ActivityCredit,
	typeLabel: string,
): string {
	return `${typeLabel} · ${credit.sourceReference} · ${credit.points} · ${credit.seasonStartsOn}`;
}

function creditTypeLabel(creditType: ActivityCredit["creditType"]): string {
	switch (creditType) {
		case "SLACK_WEEK":
			return "Slack participation";
		case "DELTA_REGISTRATION":
			return "Delta registration";
		case "GITHUB_COMMIT":
			return "GitHub commit";
		case "GITHUB_PULL_REQUEST":
			return "GitHub pull request";
		case "SECURITY_EVENT_CONTRIBUTION":
			return "Security event contribution";
	}
}

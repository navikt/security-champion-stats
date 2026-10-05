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
import { useTranslations } from "next-intl";
import { useMemo, useState } from "react";

export function ScoringManagementView({
	overview,
	onRefresh,
}: {
	overview: AdminScoringOverview;
	onRefresh: () => Promise<void>;
}) {
	const t = useTranslations("appsec.scoring");
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
			setDateError(t("dateRequired"));
			return;
		}
		setBusy(true);
		setDateError(null);
		setNotice(null);
		try {
			const status = await Apies.updateNextResetDate(nextResetDate);
			if (status !== 200) {
				setDateError(t("resetDateError"));
				return;
			}
			setNotice(t("resetDateSaved"));
			await onRefresh();
		} catch {
			setDateError(t("resetDateError"));
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
				setAdjustmentError(t("creditsError"));
				setCreditsFailed(true);
			} else {
				setCredits(result);
			}
		} catch {
			setAdjustmentError(t("creditsError"));
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
			setAdjustmentValidationError(t("invalidPoints"));
			return;
		}
		if (!adjustmentReason.trim()) {
			setAdjustmentValidationError(t("reasonRequired"));
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
				setAdjustmentError(t("adjustmentError"));
				return;
			}
			setSelectedParticipant(null);
			setNotice(t("adjustmentSaved"));
			await onRefresh();
		} catch {
			setAdjustmentError(t("adjustmentError"));
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
				setResetError(t("resetError"));
				return;
			}
			setResetDialogOpen(false);
			setResetReason("");
			setNotice(t("resetSaved"));
			await onRefresh();
		} catch {
			setResetError(t("resetError"));
		} finally {
			setBusy(false);
		}
	};

	const today = overview.today;

	return (
		<VStack gap="space-24">
			<VStack gap="space-4">
				<Heading level="1" size="xlarge">
					{t("title")}
				</Heading>
				<BodyShort>{t("description")}</BodyShort>
			</VStack>

			{notice && <BodyShort role="status">{notice}</BodyShort>}

			<section aria-labelledby="season-heading">
				<VStack gap="space-16">
					<Heading level="2" size="large" id="season-heading">
						{t("seasonTitle")}
					</Heading>
					<BodyShort>
						{t("seasonStart", { date: overview.season.startsOn })}
					</BodyShort>
					<BodyShort>
						{overview.season.endsOn
							? t("seasonEnd", { date: overview.season.endsOn })
							: t("seasonOngoing")}
					</BodyShort>
					<DatePicker {...datepickerProps}>
						<DatePicker.Input
							{...inputProps}
							label={t("nextResetDate")}
							description={t("nextResetDescription")}
						/>
					</DatePicker>
					{dateError && <BodyShort role="alert">{dateError}</BodyShort>}
					<Button
						type="button"
						variant="secondary"
						loading={busy}
						onClick={saveResetDate}
					>
						{t("saveResetDate")}
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
						{t("startSeason")}
					</Button>
				</VStack>
			</section>

			<section aria-labelledby="scores-heading">
				<VStack gap="space-16">
					<Heading level="2" size="large" id="scores-heading">
						{t("participantsTitle")}
					</Heading>
					<Table size="small">
						<Table.Header>
							<Table.Row>
								<Table.HeaderCell scope="col">{t("name")}</Table.HeaderCell>
								<Table.HeaderCell scope="col">{t("email")}</Table.HeaderCell>
								<Table.HeaderCell scope="col">{t("status")}</Table.HeaderCell>
								<Table.HeaderCell scope="col">{t("points")}</Table.HeaderCell>
								<Table.HeaderCell scope="col">{t("level")}</Table.HeaderCell>
								<Table.HeaderCell scope="col">{t("actions")}</Table.HeaderCell>
							</Table.Row>
						</Table.Header>
						<Table.Body>
							{sortedParticipants.map((participant) => (
								<Table.Row key={participant.participantId}>
									<Table.HeaderCell scope="row">
										{participant.fullName || t("nameUnavailable")}
									</Table.HeaderCell>
									<Table.DataCell>{participant.email}</Table.DataCell>
									<Table.DataCell>
										{participant.active ? t("active") : t("deactivated")}
									</Table.DataCell>
									<Table.DataCell>{participant.points}</Table.DataCell>
									<Table.DataCell>{t(`levels.${participant.level}`)}</Table.DataCell>
									<Table.DataCell>
										<Button
											size="small"
											data-color="neutral"
											variant="secondary"
											onClick={() => openAdjustment(participant)}
										>
											{t("adjust")}
										</Button>
									</Table.DataCell>
								</Table.Row>
							))}
							{sortedParticipants.length === 0 && (
								<Table.Row>
									<Table.DataCell colSpan={6}>
										{t("noParticipants")}
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
				header={{ heading: t("adjustmentTitle") }}
			>
				<Modal.Body>
					<VStack gap="space-16">
						{selectedParticipant && (
							<BodyShort>
								{t("adjustmentDescription", {
									name: selectedParticipant.fullName,
								})}
							</BodyShort>
						)}
						<TextField
							label={t("pointsDelta")}
							description={t("pointsDeltaDescription")}
							inputMode="numeric"
							value={adjustmentPoints}
							onChange={(event) => setAdjustmentPoints(event.target.value)}
						/>
						<Select
							label={t("sourceCredit")}
							value={sourceCreditId}
							onChange={(event) => setSourceCreditId(event.target.value)}
						>
							<option value="">{t("currentSeasonCredit")}</option>
							{credits.map((credit) => (
								<option key={credit.id} value={credit.id}>
									{creditLabel(credit, t(`creditTypes.${credit.creditType}`))}
								</option>
							))}
						</Select>
						{creditsLoading && (
							<BodyShort role="status">{t("creditsLoading")}</BodyShort>
						)}
						<TextField
							label={t("reason")}
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
						{t("cancel")}
					</Button>
					<Button type="button" loading={busy} onClick={addAdjustment}>
						{t("saveAdjustment")}
					</Button>
				</Modal.Footer>
			</Modal>

			<Modal
				open={resetDialogOpen}
				onClose={() => setResetDialogOpen(false)}
				header={{ heading: t("confirmResetTitle") }}
			>
				<Modal.Body>
					<VStack gap="space-16">
						<BodyShort>
							{t("resetPreview", {
								start: overview.season.startsOn,
								end: toDateString(addDays(parseLocalDate(today), -1)),
								nextStart: today,
							})}
						</BodyShort>
						<TextField
							label={t("reason")}
							value={resetReason}
							error={resetReasonError ? t("reasonRequired") : undefined}
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
						{t("cancel")}
					</Button>
					<Button
						type="button"
						data-color="danger"
						loading={busy}
						onClick={resetSeason}
					>
						{t("confirmReset")}
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

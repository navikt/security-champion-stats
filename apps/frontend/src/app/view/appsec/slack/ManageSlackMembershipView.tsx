"use client";

import { Apies } from "@/app/shared/hooks/Apies";
import type {
	AdminProgramParticipant,
	SlackMembershipAnnouncement,
	SlackMembershipConfiguration,
	SlackMembershipPreview,
} from "@/app/utils/Variables";
import {
	BodyShort,
	Button,
	Heading,
	Table,
	TextField,
	VStack,
} from "@navikt/ds-react";
import { useCallback, useEffect, useRef, useState } from "react";

export function ManageSlackMembershipView({
	participants,
	onRefresh,
}: {
	participants: AdminProgramParticipant[];
	onRefresh: () => Promise<void>;
}) {
	const [configuration, setConfiguration] =
		useState<SlackMembershipConfiguration | null>(null);
	const [announcements, setAnnouncements] = useState<
		SlackMembershipAnnouncement[] | null
	>(null);
	const [preview, setPreview] = useState<SlackMembershipPreview | null>(null);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [notice, setNotice] = useState<string | null>(null);
	const [confirmation, setConfirmation] = useState<
		{ kind: "sync" } | { kind: "delivery"; id: string; retry: boolean } | null
	>(null);
	const [mappingInputs, setMappingInputs] = useState<Record<string, string>>(
		{},
	);
	const confirmationRegion = useRef<HTMLDivElement>(null);

	const refresh = useCallback(async () => {
		setBusy(true);
		setError(null);
		setConfirmation(null);
		setPreview(null);
		setConfiguration(null);
		setAnnouncements(null);
		try {
			const [settings, deliveries] = await Promise.all([
				Apies.getSlackMembershipConfiguration(),
				Apies.getSlackMembershipAnnouncements(),
			]);
			setConfiguration(settings);
			setAnnouncements(deliveries);
		} catch {
			setError("We couldn't load Slack membership operations. Try again.");
		} finally {
			setBusy(false);
		}
	}, []);

	useEffect(() => {
		void refresh();
	}, [refresh]);
	useEffect(() => {
		setPreview(null);
		setConfirmation(null);
	}, [participants]);
	useEffect(() => {
		if (confirmation) confirmationRegion.current?.focus();
	}, [confirmation]);

	const loadPreview = async () => {
		if (busy) return;
		setBusy(true);
		setError(null);
		setNotice(null);
		setConfirmation(null);
		setPreview(null);
		try {
			setPreview(await Apies.getSlackMembershipPreview());
		} catch {
			setError(
				"We couldn't preview membership changes. Check the configuration and audit trail.",
			);
		} finally {
			setBusy(false);
		}
	};

	const triggerSync = async () => {
		if (busy || !configuration?.enabled) return;
		if (!configuration.dryRun && !preview?.version) return;
		setBusy(true);
		setError(null);
		setNotice(null);
		setConfirmation(null);
		try {
			const status = await Apies.triggerSlackMembershipSync(
				configuration.dryRun ? undefined : preview?.version,
			);
			if (status !== 202) {
				setPreview(null);
				setError(
					status === 409
						? "Membership sync is disabled or already running, or the preview changed. Refresh operations and preview again before retrying."
						: "We couldn't queue membership sync. Try again.",
				);
				return;
			}
			setPreview(null);
			setNotice(
				"Membership sync was queued. Check the audit trail for its outcome, then refresh operations.",
			);
		} catch {
			setPreview(null);
			setError("We couldn't queue membership sync. Try again.");
		} finally {
			setBusy(false);
		}
	};

	const resolve = async (id: string, retry: boolean) => {
		if (busy) return;
		setBusy(true);
		setError(null);
		setNotice(null);
		setConfirmation(null);
		try {
			const status = await Apies.resolveSlackMembershipDelivery(id, retry);
			if (status !== 204) {
				setError(
					status === 409
						? "Delivery changed or a sync is running. Refresh operations before trying again."
						: "We couldn't resolve announcement delivery. Try again.",
				);
				return;
			}
			setNotice(
				retry
					? "Retry authorized for the next membership sync. No message has been sent by this action."
					: "Announcement suppressed. It will not be retried.",
			);
			setAnnouncements(null);
			setAnnouncements(await Apies.getSlackMembershipAnnouncements());
		} catch {
			setAnnouncements(null);
			setError(
				"We couldn't confirm delivery resolution or refresh announcements. Refresh operations before trying again.",
			);
		} finally {
			setBusy(false);
		}
	};

	const mapParticipant = async (participantId: string) => {
		if (busy) return;
		const userId = mappingInputs[participantId]?.trim();
		setError(null);
		setNotice(null);
		if (!userId) {
			setError("Enter a verified Slack account ID before saving the mapping.");
			return;
		}
		setBusy(true);
		setConfirmation(null);
		try {
			const status = await Apies.addSlackMapping(userId, participantId);
			if (status !== 201) {
				setError(
					status === 409
						? "This Slack account already has a mapping. Review approved mappings before trying again."
						: "We couldn't save the Slack mapping. Try again.",
				);
				return;
			}
			setPreview(null);
			setNotice(
				"Slack mapping saved. Preview membership again to verify identity resolution.",
			);
			await onRefresh();
		} catch {
			setError(
				"We couldn't confirm the mapping or refresh participants. Refresh the page before trying again.",
			);
		} finally {
			setBusy(false);
		}
	};

	const canWrite =
		Boolean(preview?.version) && preview?.unresolvedParticipantIds.length === 0;
	const participantLabel = (id: string) => {
		const participant = participants.find((item) => item.id === id);
		return participant
			? `${participant.fullname || "Name unavailable"} (${participant.email})`
			: id;
	};

	return (
		<section aria-labelledby="slack-membership-heading">
			<VStack gap="space-16">
				<Heading level="2" size="large" id="slack-membership-heading">
					Slack membership
				</Heading>
				<BodyShort>
					Application enrollment controls group membership. Teamkatalogen roles
					only filter announcements.
				</BodyShort>
				{configuration && (
					<VStack gap="space-8">
						<BodyShort>
							{configuration.enabled
								? "Membership sync is enabled."
								: "Membership sync is disabled."}
						</BodyShort>
						<BodyShort>
							{configuration.dryRun
								? "Dry-run mode: sync will not change Slack membership or delivery state."
								: "Write mode: sync can replace the entire Slack group and send announcements."}
						</BodyShort>
						{!configuration.dryRun && (
							<BodyShort>
								Preview changes before syncing. Stop the old bot before enabling
								writes. The first write-enabled run establishes a silent
								baseline.
							</BodyShort>
						)}
						<Button
							variant="secondary"
							disabled={
								busy ||
								!configuration.enabled ||
								(!configuration.dryRun && !canWrite)
							}
							onClick={() =>
								configuration.dryRun
									? void triggerSync()
									: setConfirmation({ kind: "sync" })
							}
						>
							{configuration.dryRun
								? "Run membership dry run"
								: "Sync membership"}
						</Button>
					</VStack>
				)}
				{notice && !busy && <BodyShort role="status">{notice}</BodyShort>}
				{error && <BodyShort role="alert">{error}</BodyShort>}
				<Button variant="secondary" disabled={busy} onClick={loadPreview}>
					Preview membership changes
				</Button>
				<Button
					variant="tertiary"
					disabled={busy}
					onClick={() => void refresh()}
				>
					Refresh operations
				</Button>
				{busy && (
					<BodyShort role="status">Loading membership operations...</BodyShort>
				)}
				{confirmation && (
					<div
						ref={confirmationRegion}
						tabIndex={-1}
						role="region"
						aria-label="Confirm membership action"
					>
						<VStack gap="space-8">
							<BodyShort role="alert">
								{confirmation.kind === "sync"
									? "This sync can replace group membership and send announcements. Confirm the preview and that the old bot is stopped."
									: confirmation.retry
										? `Retrying announcement ${confirmation.id} may send a duplicate. Check Slack before authorizing another attempt.`
										: `Suppress announcement ${confirmation.id} after confirming it was delivered or is no longer wanted.`}
							</BodyShort>
							<Button
								disabled={busy}
								onClick={() =>
									confirmation.kind === "sync"
										? void triggerSync()
										: void resolve(confirmation.id, confirmation.retry)
								}
							>
								{confirmation.kind === "sync"
									? "Confirm membership sync"
									: confirmation.retry
										? "Confirm retry"
										: "Confirm suppression"}
							</Button>
							<Button
								variant="tertiary"
								disabled={busy}
								onClick={() => setConfirmation(null)}
							>
								Cancel
							</Button>
						</VStack>
					</div>
				)}
				{preview && (
					<VStack gap="space-8">
						<BodyShort>
							{preview.activeParticipants} active participants
						</BodyShort>
						<Heading level="3" size="small">
							Additions ({preview.addedUserIds.length})
						</Heading>
						<ul>
							{preview.addedUserIds.map((id) => (
								<li key={id}>{id}</li>
							))}
						</ul>
						<Heading level="3" size="small">
							Removals ({preview.removedUserIds.length})
						</Heading>
						<ul>
							{preview.removedUserIds.map((id) => (
								<li key={id}>{id}</li>
							))}
						</ul>
						<Heading level="3" size="small">
							Unresolved participants ({preview.unresolvedParticipantIds.length}
							)
						</Heading>
						{preview.unresolvedParticipantIds.length > 0 && (
							<BodyShort role="alert">
								Group replacement is blocked until every participant has one
								eligible Slack account. Review existing mappings before adding a
								verified account. Bots, guests and inactive accounts are
								ineligible.
							</BodyShort>
						)}
						{preview.unresolvedParticipantIds.map((id) => (
							<VStack gap="space-8" key={id}>
								<BodyShort>{participantLabel(id)}</BodyShort>
								<TextField
									label={`Verified Slack account ID for ${participantLabel(id)}`}
									size="small"
									value={mappingInputs[id] ?? ""}
									onChange={(event) =>
										setMappingInputs((current) => ({
											...current,
											[id]: event.target.value,
										}))
									}
								/>
								<Button
									size="small"
									variant="secondary"
									disabled={busy}
									onClick={() => void mapParticipant(id)}
								>
									Save mapping for {participantLabel(id)}
								</Button>
							</VStack>
						))}
					</VStack>
				)}
				{announcements && (
					<VStack gap="space-8">
						<Heading level="3" size="small">
							Outstanding announcements ({announcements.length})
						</Heading>
						<BodyShort>
							Pending announcements wait for role verification or a later sync.
							Uncertain deliveries require a Slack check before retrying or
							suppressing.
						</BodyShort>
						<div
							role="region"
							aria-label="Outstanding membership announcements"
							tabIndex={0}
							style={{ overflowX: "auto" }}
						>
							<Table size="small">
								<Table.Header>
									<Table.Row>
										<Table.HeaderCell scope="col">
											Participant / Slack account
										</Table.HeaderCell>
										<Table.HeaderCell scope="col">
											Announcement / delivery ID
										</Table.HeaderCell>
										<Table.HeaderCell scope="col">Status</Table.HeaderCell>
										<Table.HeaderCell scope="col">Action</Table.HeaderCell>
									</Table.Row>
								</Table.Header>
								<Table.Body>
									{announcements.map((announcement) => (
										<Table.Row key={announcement.id}>
											<Table.HeaderCell scope="row">
												{participantLabel(announcement.participantId)}
												<br />
												{announcement.slackUserId}
											</Table.HeaderCell>
											<Table.DataCell>
												{announcement.kind}
												<br />
												{announcement.id}
											</Table.DataCell>
											<Table.DataCell>{announcement.status}</Table.DataCell>
											<Table.DataCell>
												{announcement.status === "UNCERTAIN" ? (
													<VStack gap="space-8">
														<Button
															size="small"
															variant="secondary"
															disabled={busy}
															aria-label={`Retry announcement ${announcement.id}`}
															onClick={() =>
																setConfirmation({
																	kind: "delivery",
																	id: announcement.id,
																	retry: true,
																})
															}
														>
															Retry
														</Button>
														<Button
															size="small"
															variant="secondary"
															disabled={busy}
															aria-label={`Suppress announcement ${announcement.id}`}
															onClick={() =>
																setConfirmation({
																	kind: "delivery",
																	id: announcement.id,
																	retry: false,
																})
															}
														>
															Suppress
														</Button>
													</VStack>
												) : (
													"No manual action available"
												)}
											</Table.DataCell>
										</Table.Row>
									))}
									{announcements.length === 0 && (
										<Table.Row>
											<Table.DataCell colSpan={4}>
												No outstanding announcements.
											</Table.DataCell>
										</Table.Row>
									)}
								</Table.Body>
							</Table>
						</div>
					</VStack>
				)}
			</VStack>
		</section>
	);
}

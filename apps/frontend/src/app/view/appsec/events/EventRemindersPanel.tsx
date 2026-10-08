"use client";

import { useRef, useState } from "react";
import { BodyShort, Button, Checkbox, Heading, VStack } from "@navikt/ds-react";
import type { SecurityEvent } from "@/app/utils/Variables";
import {
	type EventReminderPreview,
	type ReminderRecipientStatus,
	previewEventReminders,
	sendEventReminders,
} from "./EventRemindersApi";

const statuses: Record<ReminderRecipientStatus, string> = {
	READY: "Will receive a Slack DM",
	UNRESOLVED: "Skipped: Slack identity unresolved",
	ALREADY_SENT: "Skipped: reminder already sent",
	DELIVERY_UNCERTAIN:
		"Skipped: delivery unconfirmed; inspect Slack and the audit trail",
	RETRY_LATER: "Skipped: retry delay has not elapsed",
};

export function EventRemindersPanel({ event }: { event: SecurityEvent }) {
	const [preview, setPreview] = useState<EventReminderPreview | null>(null);
	const [confirmed, setConfirmed] = useState(false);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [notice, setNotice] = useState<string | null>(null);
	const inFlight = useRef(false);
	const ready =
		preview?.recipients.filter((recipient) => recipient.status === "READY")
			.length ?? 0;

	async function run(send: boolean) {
		if (inFlight.current || (send && (!confirmed || !preview || ready === 0)))
			return;
		inFlight.current = true;
		setBusy(true);
		setError(null);
		setNotice(null);
		setConfirmed(false);
		const reviewed = preview;
		setPreview(null);
		try {
			if (send && reviewed) {
				await sendEventReminders(event.id, reviewed.version);
				setNotice(
					"Reminder batch queued, not yet delivered. Check the audit trail, then refresh the recipient preview for delivery outcomes.",
				);
			} else {
				setPreview(await previewEventReminders(event.id));
			}
		} catch (error) {
			console.error("Event reminder request failed:", error);
			setError(
				error instanceof Error &&
					!(error instanceof TypeError) &&
					!(error instanceof SyntaxError)
					? error.message
					: "Reminder request failed. Refresh the preview and check the audit trail before retrying.",
			);
		} finally {
			inFlight.current = false;
			setBusy(false);
		}
	}

	return (
		<VStack gap="space-12">
			<Heading level="3" size="small">
				{event.name}
			</Heading>
			<Button
				variant="secondary"
				size="small"
				loading={busy}
				onClick={() => void run(false)}
			>
				Preview Slack reminders
			</Button>
			{error && <BodyShort role="alert">{error}</BodyShort>}
			{notice && <BodyShort role="status">{notice}</BodyShort>}
			{preview && (
				<VStack gap="space-8">
					<BodyShort>
						{preview.signedUpParticipants} active participants signed up or
						hosting. {ready} will receive a reminder.
					</BodyShort>
					<BodyShort size="small">
						Checked in Delta: {new Date(preview.checkedAt).toLocaleString()}.
						Signed-up participants and hosts are excluded. Unresolved Slack
						accounts are skipped.
					</BodyShort>
					<Heading level="4" size="xsmall">
						Message
					</Heading>
					<BodyShort>{preview.message}</BodyShort>
					{preview.recipients.length > 0 ? (
						<ul>
							{preview.recipients.map((recipient) => (
								<li key={recipient.participantId}>
									{recipient.name}: {statuses[recipient.status]}
								</li>
							))}
						</ul>
					) : (
						<BodyShort>No active participants need a reminder.</BodyShort>
					)}
					{ready > 0 && (
						<>
							<Checkbox
								checked={confirmed}
								onChange={(event) => setConfirmed(event.target.checked)}
							>
								I confirm sending this reminder to the {ready} listed recipients
							</Checkbox>
							<Button
								disabled={!confirmed || busy}
								onClick={() => void run(true)}
							>
								Send Slack reminders
							</Button>
						</>
					)}
				</VStack>
			)}
		</VStack>
	);
}

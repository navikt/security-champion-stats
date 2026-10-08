export type ReminderRecipientStatus =
	| "READY"
	| "UNRESOLVED"
	| "ALREADY_SENT"
	| "DELIVERY_UNCERTAIN"
	| "RETRY_LATER";

export type EventReminderPreview = {
	version: string;
	checkedAt: string;
	message: string;
	signedUpParticipants: number;
	recipients: {
		participantId: string;
		name: string;
		slackUserId: string | null;
		status: ReminderRecipientStatus;
	}[];
};

function path(eventId: string) {
	return `/api/admin/events/${encodeURIComponent(eventId)}/reminders`;
}

function failure(status: number): Error {
	return new Error(
		status === 409
			? "The event, recipients or delivery state changed, or a batch is running. Preview again before sending."
			: status === 503
				? "Signup information or reminder delivery is unavailable. No new batch was started."
				: "We couldn't complete the reminder request. Refresh the preview and check the audit trail before retrying.",
	);
}

export async function previewEventReminders(
	eventId: string,
): Promise<EventReminderPreview> {
	const response = await fetch(path(eventId), { cache: "no-store" });
	if (!response.ok) throw failure(response.status);
	return response.json();
}

export async function sendEventReminders(
	eventId: string,
	version: string,
): Promise<void> {
	const response = await fetch(path(eventId), {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ expectedVersion: version, confirmed: true }),
	});
	if (response.status !== 202) throw failure(response.status);
}

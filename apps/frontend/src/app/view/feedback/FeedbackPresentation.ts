import type { Feedback, FeedbackAction, FeedbackStatus } from "./FeedbackApi";

export const statusLabels: Record<FeedbackStatus, string> = {
	OPEN: "Open",
	IN_PROGRESS: "In progress",
	CLOSED: "Resolved",
};
export const scoreLabels = {
	PENDING: "Not assessed",
	QUALIFIED: "Points awarded",
	NOT_QUALIFIED: "No points",
};
const actionLabels: Record<FeedbackAction, string> = {
	CLAIM: "Took ownership",
	TAKE_OVER: "Took over",
	RELEASE: "Released ownership",
	NOTE: "Added internal note",
	INTERNAL_DISCUSSION: "Recorded internal discussion",
	EXTERNAL_TICKET: "Linked ticket",
	CONTACT_SUBMITTER: "Recorded contact with submitter",
	CLOSE: "Status → Resolved",
	REOPEN: "Status → Open",
	STATUS: "Changed status",
	QUALIFY: "Scoring → Points awarded",
	DECLINE: "Scoring → No points",
};
export function historyLabel(entry: Feedback["history"][number]) {
	if (entry.action === "TAKE_OVER" && entry.previousOwnerName)
		return `Took over from ${entry.previousOwnerName}`;
	if (entry.action === "STATUS" && entry.status)
		return `Status → ${statusLabels[entry.status]}`;
	return actionLabels[entry.action];
}
export const dateTime = new Intl.DateTimeFormat("en-GB", {
	dateStyle: "medium",
	timeStyle: "short",
	timeZone: "Europe/Oslo",
});
const day = new Intl.DateTimeFormat("en-CA", {
	timeZone: "Europe/Oslo",
	year: "numeric",
	month: "2-digit",
	day: "2-digit",
});
const time = new Intl.DateTimeFormat("en-GB", {
	timeZone: "Europe/Oslo",
	hour: "2-digit",
	minute: "2-digit",
});
const shortDate = new Intl.DateTimeFormat("en-GB", {
	timeZone: "Europe/Oslo",
	day: "numeric",
	month: "short",
});
export function receivedLabel(value: string) {
	const date = new Date(value);
	const now = new Date();
	if (day.format(date) === day.format(now)) return time.format(date);
	// Compare Oslo calendar days rather than elapsed hours across DST changes.
	const yesterday = new Date(`${day.format(now)}T12:00:00Z`);
	yesterday.setUTCDate(yesterday.getUTCDate() - 1);
	if (day.format(date) === day.format(yesterday)) return "Yesterday";
	return shortDate.format(date);
}
export function owns(feedback: Feedback, actor: string) {
	return feedback.ownerEmail?.toLowerCase() === actor.toLowerCase();
}
export function ownerLabel(feedback: Feedback, actor: string) {
	return owns(feedback, actor)
		? "You"
		: (feedback.ownerName ?? feedback.ownerEmail ?? "Unassigned");
}
export function validTicketUrl(value: string) {
	try {
		const url = new URL(value);
		return (
			value.length <= 1000 &&
			/^https?:\/\/\S+$/.test(value) &&
			!url.username &&
			!url.password &&
			Boolean(url.hostname)
		);
	} catch {
		return false;
	}
}

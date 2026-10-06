import { SecurityEvent } from "@/app/utils/Variables";

export function sortEventsByDate(events: SecurityEvent[]): SecurityEvent[] {
	return [...events].sort((a, b) => compareEvents(a, b));
}

export function getUpcomingEvents(
	events: SecurityEvent[],
	now = new Date(),
): SecurityEvent[] {
	const today = osloDate(now);
	return sortEventsByDate(events).filter((event) =>
		event.allDay
			? event.endDate >= today
			: new Date(event.startDate).getTime() > now.getTime(),
	);
}

export function getPastEvents(
	events: SecurityEvent[],
	now = new Date(),
): SecurityEvent[] {
	const today = osloDate(now);
	return events
		.filter((event) =>
			event.allDay
				? event.endDate < today
				: new Date(event.endDate ?? event.startDate).getTime() < now.getTime(),
		)
		.sort((a, b) => compareEvents(a, b, -1));
}

export function getNextEvent(
	events: SecurityEvent[],
	now = new Date(),
): SecurityEvent | undefined {
	return getUpcomingEvents(events, now)[0];
}

export function formatEventDate(event: SecurityEvent): string {
	const format = (value: string) =>
		new Date(value).toLocaleDateString("nb-NO", {
			timeZone: event.allDay ? "UTC" : "Europe/Oslo",
		});
	const start = format(event.startDate);
	return event.allDay && event.endDate !== event.startDate
		? `${start} - ${format(event.endDate)}`
		: start;
}

function osloDate(now: Date): string {
	return new Intl.DateTimeFormat("sv-SE", {
		timeZone: "Europe/Oslo",
		year: "numeric",
		month: "2-digit",
		day: "2-digit",
	}).format(now);
}

function compareEvents(
	a: SecurityEvent,
	b: SecurityEvent,
	direction = 1,
): number {
	const aDate = a.allDay ? a.startDate : osloDate(new Date(a.startDate));
	const bDate = b.allDay ? b.startDate : osloDate(new Date(b.startDate));
	return (
		direction * aDate.localeCompare(bDate) ||
		Number(Boolean(a.allDay)) - Number(Boolean(b.allDay)) ||
		direction *
			(new Date(a.startDate).getTime() - new Date(b.startDate).getTime())
	);
}

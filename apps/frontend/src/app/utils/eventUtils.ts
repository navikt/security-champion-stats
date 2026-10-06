import { SecurityEvent } from "@/app/utils/Variables";

export function sortEventsByDate(events: SecurityEvent[]): SecurityEvent[] {
	return [...events].sort(
		(a, b) => new Date(a.startDate).getTime() - new Date(b.startDate).getTime(),
	);
}

export function getUpcomingEvents(
	events: SecurityEvent[],
	now = new Date(),
): SecurityEvent[] {
	return sortEventsByDate(events).filter(
		(event) => new Date(event.startDate).getTime() > now.getTime(),
	);
}

export function getPastEvents(
	events: SecurityEvent[],
	now = new Date(),
): SecurityEvent[] {
	return sortEventsByDate(events)
		.filter(
			(event) =>
				new Date(event.endDate ?? event.startDate).getTime() < now.getTime(),
		)
		.reverse();
}

export function getNextEvent(
	events: SecurityEvent[],
	now = new Date(),
): SecurityEvent | undefined {
	return getUpcomingEvents(events, now)[0];
}

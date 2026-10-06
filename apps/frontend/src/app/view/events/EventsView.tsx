import { SecurityEvent } from "@/app/utils/Variables";
import {
	getNextEvent,
	getPastEvents,
	getUpcomingEvents,
} from "@/app/utils/eventUtils";
import { BodyShort, Heading } from "@navikt/ds-react";
import { NextEventCard } from "@/app/view/events/NextEventCard";
import { EventList } from "@/app/view/events/EventList";
import "../../style/events/EventsView.css";

interface EventsViewProps {
	events: SecurityEvent[];
	compact?: boolean;
	limit?: number;
	scrollAfter?: number;
}

export function EventsView({
	events,
	compact = false,
	limit,
	scrollAfter,
}: EventsViewProps) {
	const nextEvent = getNextEvent(events);

	const upcomingEvents: SecurityEvent[] = getUpcomingEvents(events).filter(
		(event) => compact || event.id !== nextEvent?.id,
	);

	const pastEvents: SecurityEvent[] = getPastEvents(events);

	return (
		<main
			className={["eventsView", compact ? "eventsView--compact" : ""]
				.filter(Boolean)
				.join(" ")}
		>
			{!compact && (
				<header className={"eventsView__header"}>
					<Heading level={"1"} size={"xlarge"}>
						Events
					</Heading>

					<BodyShort className={"eventsView__subtitle"}>
						Meetings, workshops and other Security Champion activities.
					</BodyShort>
				</header>
			)}

			{!compact && nextEvent && <NextEventCard event={nextEvent} />}
			<EventList
				title="Upcoming events"
				events={upcomingEvents}
				emptyMessage="No other upcoming events."
				limit={compact ? limit : undefined}
				scrollAfter={scrollAfter}
			/>
			<EventList
				title="Past events"
				events={pastEvents}
				emptyMessage="No previous events."
				limit={compact ? limit : undefined}
				scrollAfter={scrollAfter}
			/>
		</main>
	);
}

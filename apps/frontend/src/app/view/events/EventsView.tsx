import type { SecurityEvent } from "@/app/utils/Variables";
import {
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
	const now = new Date();
	const upcomingEvents = getUpcomingEvents(events, now, "endOfDay");
	const pastEvents = getPastEvents(events, now, "endOfDay");
	const nextEvent = upcomingEvents[0];

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
				emptyMessage="No upcoming events."
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

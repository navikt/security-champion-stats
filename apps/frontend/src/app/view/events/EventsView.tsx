import {SecurityEvent} from "@/app/utils/Variables";
import {getNextEvent, getUpcomingEvents} from "@/app/utils/eventUtils";

interface EventsViewProps {
    events: SecurityEvent[]
}

export function EventsView(props: EventsViewProps) {
    const nextEvent = getNextEvent(props.events)

    const upcomingEvents = getUpcomingEvents(props.events)
        .filter(event => event.id !== nextEvent?.id)
}
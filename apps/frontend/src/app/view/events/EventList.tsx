import "../../style/events/EventsView.css"
import {SecurityEvent} from "@/app/utils/Variables";
import {Heading} from "@navikt/ds-react";
import {EventListItem} from "@/app/view/events/EventListItem";

interface EventListProps {
    title: string
    events: SecurityEvent[]
    emptyMessage?: string
    muted?: boolean
}

export function EventList({
    title,
    events,
    emptyMessage,
    muted = false
}: EventListProps) {
    return (
        <section className={"eventSection"}>
            <div className={"eventSection__header"}>
                <Heading
                    level={"2"}
                    size={"medium"}
                >
                    {title}
                </Heading>

                {events.length > 0 && (
                    <span className={"eventSection__count"}>{events.length}</span>
                )}
            </div>
            {events.length === 0 ? (
                <div className={"eventSection__empty"}>
                    {emptyMessage}
                </div>
            ): (
                <div className={"eventList"}>
                    {events.map(event => (
                        <EventListItem event={event} muted={muted} key={event.id}/>
                    ))}
                </div>
            )}
        </section>
    )
}
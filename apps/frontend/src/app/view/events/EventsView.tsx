import {SecurityEvent} from "@/app/utils/Variables";
import {getNextEvent, getPastEvents, getUpcomingEvents} from "@/app/utils/eventUtils";
import {BodyShort, Heading} from "@navikt/ds-react";
import {useTranslations} from "next-intl";
import {NextEventCard} from "@/app/view/events/NextEventCard";
import {EventList} from "@/app/view/events/EventList";
import "../../style/events/EventsView.css"

interface EventsViewProps {
    events: SecurityEvent[]
}

export function EventsView(props: EventsViewProps) {
    const nextEvent = getNextEvent(props.events)

    const t = useTranslations("events")

    const upcomingEvents: SecurityEvent[] = getUpcomingEvents(props.events)
        .filter(event => event.id !== nextEvent?.id)

    const pastEvents: SecurityEvent[] = getPastEvents(props.events)

    return (
        <main className={"eventsView"}>
            <header className={"eventsView__header"}>
                <Heading level={"1"} size={"xlarge"}>
                    {t("title")}
                </Heading>

                <BodyShort className={"eventsView__subtitle"}>
                    {t("description")}
                </BodyShort>
            </header>

            {
                nextEvent && (
                    <NextEventCard event={nextEvent} />
                )
            }
            <EventList
                title={t("upcomingEvents")}
                events={upcomingEvents}
                emptyMessage={t("noUpcomingEvents")}
            />
            <EventList
                title={t("pastEvents")}
                events={pastEvents}
                emptyMessage={t("noPastEvents")}
            />
        </main>
    )
}
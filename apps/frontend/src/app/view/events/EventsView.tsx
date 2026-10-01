import {SecurityEvent} from "@/app/utils/Variables";
import {getNextEvent, getPastEvents, getUpcomingEvents} from "@/app/utils/eventUtils";
import {BodyShort, Heading} from "@navikt/ds-react";
import {useTranslations} from "next-intl";
import {NextEventCard} from "@/app/view/events/NextEventCard";
import {EventList} from "@/app/view/events/EventList";
import "../../style/events/EventsView.css"

interface EventsViewProps {
    events: SecurityEvent[]
    compact?: boolean
    limit?: number
    scrollAfter?: number
}

export function EventsView({events, compact = false, limit, scrollAfter}: EventsViewProps) {
    const nextEvent = getNextEvent(events)

    const t = useTranslations("events")

    const upcomingEvents: SecurityEvent[] = getUpcomingEvents(events)
        .filter(event => event.id !== nextEvent?.id)

    const pastEvents: SecurityEvent[] = getPastEvents(events)

    return (
        <main className={["eventsView", compact ? "eventsView--compact" : ""].filter(Boolean).join(" ")}>
            {!compact && (
                <header className={"eventsView__header"}>
                    <Heading level={"1"} size={"xlarge"}>
                        {t("title")}
                    </Heading>

                    <BodyShort className={"eventsView__subtitle"}>
                        {t("description")}
                    </BodyShort>
                </header>
            )}

            {
                !compact && nextEvent && (
                    <NextEventCard event={nextEvent} />
                )
            }
            <EventList
                title={t("upcomingEvents")}
                events={upcomingEvents}
                emptyMessage={t("noUpcomingEvents")}
                limit={compact ? limit : undefined}
                scrollAfter={scrollAfter}
            />
            <EventList
                title={t("pastEvents")}
                events={pastEvents}
                emptyMessage={t("noPastEvents")}
                limit={compact ? limit : undefined}
                scrollAfter={scrollAfter}
            />
        </main>
    )
}
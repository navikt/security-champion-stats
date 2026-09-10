import {SecurityEvent} from "@/app/utils/Variables";
import {getNextEvent, getPastEvents, getUpcomingEvents} from "@/app/utils/eventUtils";
import {BodyShort, Heading} from "@navikt/ds-react";
import {useTranslations} from "next-intl";

interface EventsViewProps {
    events: SecurityEvent[]
}

export function EventsView(props: EventsViewProps) {
    const nextEvent = getNextEvent(props.events)

    const t = useTranslations("events")

    const upcomingEvents = getUpcomingEvents(props.events)
        .filter(event => event.id !== nextEvent?.id)

    const past = getPastEvents(props.events)

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
        </main>
    )
}
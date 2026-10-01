"use client"

import {SecurityEvent} from "@/app/utils/Variables";
import {useState} from "react";
import {useTranslations} from "next-intl";
import {BodyShort, Button, Heading, VStack} from "@navikt/ds-react";
import {EventList} from "@/app/view/events/EventList";
import {AddEventModal} from "@/app/view/appsec/events/AddEventModal";
import {Apies} from "@/app/shared/hooks/Apies";

interface ManageEventsViewProps {
    events: SecurityEvent[]
}

export function ManageEventsView({events}: ManageEventsViewProps) {
    const [eventsList, setEventsList] = useState<SecurityEvent[]>(events)
    const [mods, setMods] = useState(false)
    const [create, setCreate] = useState(false)
    const t = useTranslations("appsec.events")

    const handleCreate = async (event: Omit<SecurityEvent, "id">) => {
        setCreate(true)
        try {
            const created: SecurityEvent = {...event, id: crypto.randomUUID(), amountOfPeopleJoined: 0}
            const status = await Apies.createEvent(created)

            if (status === 200 || status === 201) {
                setEventsList((current) => [created, ...current])
                setMods(false)
            }
        } finally {
            setCreate(false)
        }
    }

    return (
        <VStack gap={"space-24"}>
            <VStack gap={"space-4"}>
                <Heading level={"1"} size={"xlarge"}>
                    {t("title")}
                </Heading>
                <BodyShort>
                    {t("description")}
                </BodyShort>
            </VStack>

            <Button onClick={() => setMods(true)} style={{alignSelf: "flex-start"}}>
                {t("addEvent")}
            </Button>

            <EventList
                title={t("allEvents")}
                events={eventsList}
                emptyMessage={t("noEvents")}
            />

            <AddEventModal
                open={mods}
                onClose={() => setMods(false)}
                onCreate={handleCreate}
                loading={create}
            />
        </VStack>
    )
}
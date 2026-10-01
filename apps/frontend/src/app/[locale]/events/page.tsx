"use client";

import {useEffect, useState} from "react";
import {SecurityEvent} from "@/app/utils/Variables";
import {Apies} from "@/app/shared/hooks/Apies";
import {EventsView} from "@/app/view/events/EventsView";
import Loading from "@/app/view/Loading";

export default function EventsPage() {
    const [events, updateEvents] = useState<SecurityEvent[] | null>(null)

    useEffect(() => {
        Apies.fetchEvents().then(response =>
            updateEvents(response)
        )
    }, []);

    if (events == null) return <Loading />

    return <EventsView events={events} scrollAfter={7} />
}
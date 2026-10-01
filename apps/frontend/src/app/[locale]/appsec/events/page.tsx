"use client";

import { useMe } from "@/app/shared/hooks/UseMe";
import { MainView } from "@/app/view/HomeView";
import Loading from "@/app/view/Loading";
import { useEffect, useState } from "react";
import { SecurityEvent } from "@/app/utils/Variables";
import { Apies } from "@/app/shared/hooks/Apies";
import { ManageEventsView } from "@/app/view/appsec/events/ManageEventsView";

export default function Page() {
	const { me, loading } = useMe();
	const [events, updateEvents] = useState<SecurityEvent[] | null>(null);

	useEffect(() => {
		Apies.fetchEvents().then((response) => updateEvents(response));
	}, []);

	if (loading || events == null) return <Loading />;

	if (!me.isAdmin) {
		return <MainView info={me} />;
	}

	return <ManageEventsView events={events} />;
}

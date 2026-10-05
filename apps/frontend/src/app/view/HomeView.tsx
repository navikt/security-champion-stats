"use client";

import { useEffect, useState } from "react";
import { Me, SecurityEvent } from "../utils/Variables";
import { MembershipView } from "./member/components/MembershipView";
import "../style/home/HomeView.css";
import { BodyShort, Heading } from "@navikt/ds-react";
import { EventsView } from "@/app/view/events/EventsView";
import { Apies } from "@/app/shared/hooks/Apies";

function View({ me }: { me: Me }) {
	const [userData, _] = useState(me);
	const [events, updateEvents] = useState<SecurityEvent[]>([]);

	useEffect(() => {
		Apies.fetchEvents().then((res) => updateEvents(res));
	}, []);

	return (
		<main className={"homeView"}>
			<header className={"homeView__header"}>
				<Heading level="1" size={"xlarge"}>
					Security Champion Hub
				</Heading>
				<BodyShort className={"homeView__subtitle"}>
					Here's what's happening in the Security Champion program.
				</BodyShort>
			</header>

			<div className={"homeView__body"}>
				<section className={"homeView__primary"}>
					<MembershipView me={userData} />
				</section>
				<section className={"homeView__secondary"}>
					<EventsView events={events} compact limit={4} />
				</section>
			</div>
		</main>
	);
}

export function MainView({ info }: { info: Me }) {
	return <View me={info} />;
}

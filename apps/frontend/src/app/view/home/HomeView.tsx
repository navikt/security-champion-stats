"use client";

import { useEffect, useRef, useState } from "react";
import { Me, Member, SecurityEvent } from "../../utils/Variables";
import { Apies } from "../../shared/hooks/Apies";
import { MembershipView } from "./components/MembershipView";
import { Leaderboard } from "./components/Leaderboard";
import { useTranslations } from "next-intl";
import "../../style/home/HomeView.css"
import MembersTable from "./components/MembersTable";
import { BodyShort, Button, Heading, Modal, TextField } from "@navikt/ds-react";

interface HomeViewProps {}

function View({ canEdit, me }: { canEdit: boolean; me: Me }) {
	const [userData, _] = useState(me);
	const [refreshKey, setRefreshKey] = useState(0);
	const t = useTranslations("home");


	return (
		<main className={"homeView"}>
			<header className={"homeView__header"}>
				<Heading level="1" size={"xlarge"}>
					{t("title")}
				</Heading>
				<BodyShort className={"homeView__subtitle"}>
					{t("description")}
				</BodyShort>
			</header>

			<section className={"homeView__primary"}>
				<MembershipView me={userData} onMembershipChange={() => setRefreshKey((k) => k + 1)} />
				<Leaderboard refreshKey={refreshKey} />
			</section>
		</main>
	);
}

export function MainView({ info }: { info: Me }) {
	return <View canEdit={info.isAdmin} me={info} />;
}

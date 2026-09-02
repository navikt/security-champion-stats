"use client";

import { useEffect, useRef, useState } from "react";
import { Me, Member, SecurityEvent } from "../../utils/Variables";
import { Apies } from "../../shared/hooks/Apies";
import { MembershipView } from "./components/MembershipView";
import { useTranslations } from "next-intl";
import "../../style/home/HomeView.css"
import MembersTable from "./components/MembersTable";
import { BodyShort, Button, Heading, Modal, TextField } from "@navikt/ds-react";

interface HomeViewProps {}

function View({ canEdit, me }: { canEdit: boolean; me: Me }) {
	const [userData, _] = useState(me);
	const t = useTranslations("home");


	return (
		<main className={"homeView"}>
			<header className={"homeView__header"}>
				<Heading level="1" size={"xlarge"}>
					{t("welcome",) + userData.username}
				</Heading>
				<BodyShort className={"homeView__subtitle"}>
					{t("description")}
				</BodyShort>
			</header>

			<section className={"homeView__primary"}>
				<MembershipView me={userData} />
			</section>
		</main>
	);
}

export function MainView({ info }: { info: Me }) {
	return <View canEdit={info.isAdmin} me={info} />;
}

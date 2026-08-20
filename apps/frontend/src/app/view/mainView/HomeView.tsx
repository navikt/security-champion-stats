"use client";

import { useEffect, useRef, useState } from "react";
import { Me, Member, SecurityEvent } from "../../utils/Variables";
import { Apies } from "../../shared/hooks/Apies";
import { MembershipView } from "./components/MembershipView";
import { useTranslations } from "next-intl";
import "../../style/HomeView.css"
import MembersTable from "./components/MembersTable";
import { BodyShort, Button, Heading, Modal, TextField } from "@navikt/ds-react";

interface HomeViewProps {}

function View({ canEdit, me }: { canEdit: boolean; me: Me }) {
	const [userData, setMe] = useState(me);
	const [currentEvent, setCurrentEvent] = useState<SecurityEvent | null>(null);
	const [members, setMembers] = useState<Member[]>([]);
	const [active, setActive] = useState(userData.isSecChamp);
	const modalRef = useRef<HTMLDialogElement>(null);
	const memberEmailRef = useRef<HTMLInputElement>(null);
	const t = useTranslations();
	const safeMembers = members ?? [];
	const totalPoints = safeMembers.reduce(
		(sum, member) => sum + member.points,
		0,
	);
	const topMember = safeMembers.toSorted((a, b) => b.points - a.points)[0];
	const statusKey = active ? "main.statuses.active" : "main.statuses.idle";
	const subtitleKey = active ? "main.subtitles.active" : "main.subtitles.idle";
	const reload = async () => {
		const ms = await Apies.getMembers();
		setMembers(ms);
	};
	const reloadMe = async () => {
		setMe(await Apies.validatePerson());
	};

	useEffect(() => {
		void reload();
	}, []);

	const addMember = async (email?: string) => {
		if (!email) {
			return;
		}
		await Apies.addMember(email);
		memberEmailRef.current = null;
		await reload();
	};

	const addPoints = async (email?: string, points?: number) => {
		if (!email || !points) {
			return;
		}
		await Apies.addPoints(email, points);
		await reload();
	};

	const joinProgram = async () => {
		await Apies.joinProgram();
		await reload();
		await reloadMe();
		if (me.isSecChamp) setActive(true);
		else setActive(false);
		window.location.reload();
	};

	const deleteMember = async (id: string) => {
		await Apies.deleteMember(id);
		await reload();
	};

	const leaveProgram = async () => {
		await Apies.leaveProgram();
		await reload();
		await reloadMe();
		if (me.isSecChamp) setActive(true);
		else setActive(false);
		window.location.reload();
	};

	return (
		<main className={"homeView"}>
			<header className={"homeView__header"}>
				<Heading level="1" size={"xlarge"}>
					Temp Security Champion Program
				</Heading>
				<BodyShort className={"homeView__subtitle"}>
					Temp Build security knowledge, participate in activities and connect
					with other Security Champion
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

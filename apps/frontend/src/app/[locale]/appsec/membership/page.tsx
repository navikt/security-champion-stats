"use client";

import { Apies } from "@/app/shared/hooks/Apies";
import { useMe } from "@/app/shared/hooks/UseMe";
import { BodyShort } from "@navikt/ds-react";
import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";
import Loading from "@/app/view/Loading";
import { MainView } from "@/app/view/HomeView";
import { ManageParticipantsView } from "@/app/view/appsec/membership/ManageParticipantsView";
import { AdminProgramParticipant } from "@/app/utils/Variables";

export default function Page() {
	const { me, loading } = useMe();
	const t = useTranslations("appsec.membership");
	const [participants, setParticipants] = useState<AdminProgramParticipant[] | null>(null);
	const [failed, setFailed] = useState(false);

	useEffect(() => {
		if (loading || !me.isAdmin) return;
		let mounted = true;
		Apies.getAdminParticipants()
			.then((result) => {
				if (!mounted) return;
				setParticipants(result);
				setFailed(result === null);
			})
			.catch(() => {
				if (!mounted) return;
				setFailed(true);
			});
		return () => {
			mounted = false;
		};
	}, [loading, me.isAdmin]);

	if (loading || (me.isAdmin && participants === null && !failed)) {
		return <Loading />;
	}
	if (!me.isAdmin) return <MainView info={me} />;
	if (failed || participants === null) {
		return <BodyShort role="alert">{t("loadError")}</BodyShort>;
	}
	return <ManageParticipantsView participants={participants} />;
}

"use client";

import { Apies } from "@/app/shared/hooks/Apies";
import { useMe } from "@/app/shared/hooks/UseMe";
import { AdminScoringOverview } from "@/app/utils/Variables";
import { MainView } from "@/app/view/HomeView";
import Loading from "@/app/view/Loading";
import { ScoringManagementView } from "@/app/view/appsec/scoring/ScoringManagementView";
import { BodyShort } from "@navikt/ds-react";
import { useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";

export default function Page() {
	const { me, loading } = useMe();
	const t = useTranslations("appsec.scoring");
	const [overview, setOverview] = useState<AdminScoringOverview | null>(null);
	const [failed, setFailed] = useState(false);

	const refresh = useCallback(async () => {
		try {
			const result = await Apies.getScoringOverview();
			setOverview(result);
			setFailed(result === null);
		} catch {
			setFailed(true);
		}
	}, []);

	useEffect(() => {
		if (loading || !me.isAdmin) return;
		void refresh();
	}, [loading, me.isAdmin, refresh]);

	if (loading || (me.isAdmin && overview === null && !failed)) {
		return <Loading />;
	}
	if (!me.isAdmin) return <MainView info={me} />;
	if (failed || overview === null) {
		return <BodyShort role="alert">{t("loadError")}</BodyShort>;
	}
	return (
		<ScoringManagementView
			key={`${overview.season.id}-${overview.season.nextResetDate}`}
			overview={overview}
			onRefresh={refresh}
		/>
	);
}

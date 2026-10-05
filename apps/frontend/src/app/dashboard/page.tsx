"use client";

import { BodyShort } from "@navikt/ds-react";
import { useEffect, useState } from "react";
import { Apies } from "@/app/shared/hooks/Apies";
import { useMe } from "@/app/shared/hooks/UseMe";
import type { AdminDashboardOverview } from "@/app/utils/Variables";
import { AdminDashboardView } from "@/app/view/appsec/dashboard/AdminDashboardView";
import { MainView } from "@/app/view/HomeView";
import Loading from "@/app/view/Loading";

export default function Page() {
	const { me, loading } = useMe();
	const [overview, setOverview] = useState<AdminDashboardOverview | null>(null);
	const [failed, setFailed] = useState(false);

	useEffect(() => {
		if (loading || !me.isAdmin) return;
		let mounted = true;
		Apies.getAdminDashboard()
			.then((result) => {
				if (!mounted) return;
				setOverview(result);
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

	if (loading || (me.isAdmin && overview === null && !failed)) {
		return <Loading />;
	}
	if (!me.isAdmin) return <MainView info={me} />;
	if (failed || overview === null) {
		return (
			<BodyShort role="alert">
				We couldn't fetch the program dashboard. Try again later.
			</BodyShort>
		);
	}
	return <AdminDashboardView overview={overview} />;
}

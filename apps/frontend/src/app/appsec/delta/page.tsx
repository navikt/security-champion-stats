"use client";

import { Apies } from "@/app/shared/hooks/Apies";
import { useMe } from "@/app/shared/hooks/UseMe";
import { DeltaEventMapping } from "@/app/utils/Variables";
import { MainView } from "@/app/view/HomeView";
import Loading from "@/app/view/Loading";
import { ManageDeltaEventMappingsView } from "@/app/view/appsec/delta/ManageDeltaEventMappingsView";
import { BodyShort } from "@navikt/ds-react";
import { useCallback, useEffect, useState } from "react";

export default function Page() {
	const { me, loading } = useMe();
	const [mappings, setMappings] = useState<DeltaEventMapping[] | null>(null);
	const [failed, setFailed] = useState(false);

	const refresh = useCallback(async () => {
		try {
			const result = await Apies.getDeltaEventMappings();
			setMappings(result);
			setFailed(result === null);
		} catch {
			setFailed(true);
		}
	}, []);

	useEffect(() => {
		if (loading || !me.isAdmin) return;
		void refresh();
	}, [loading, me.isAdmin, refresh]);

	if (loading || (me.isAdmin && mappings === null && !failed)) return <Loading />;
	if (!me.isAdmin) return <MainView info={me} />;
	if (failed || mappings === null) {
		return (
			<BodyShort role="alert">
				We couldn't fetch Delta event mappings. Try again later.
			</BodyShort>
		);
	}
	return <ManageDeltaEventMappingsView mappings={mappings} onRefresh={refresh} />;
}

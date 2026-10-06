"use client";

import { Apies } from "@/app/shared/hooks/Apies";
import { useMe } from "@/app/shared/hooks/UseMe";
import { DeltaCategory, DeltaEligibleCategory, DeltaEventMapping } from "@/app/utils/Variables";
import { MainView } from "@/app/view/HomeView";
import Loading from "@/app/view/Loading";
import { ManageDeltaEventMappingsView } from "@/app/view/appsec/delta/ManageDeltaEventMappingsView";
import { BodyShort } from "@navikt/ds-react";
import { useCallback, useEffect, useState } from "react";

export default function Page() {
	const { me, loading } = useMe();
	const [mappings, setMappings] = useState<DeltaEventMapping[] | null>(null);
	const [categories, setCategories] = useState<DeltaCategory[] | null>(null);
	const [eligibleCategories, setEligibleCategories] = useState<DeltaEligibleCategory[] | null>(null);
	const [failed, setFailed] = useState(false);

	const refresh = useCallback(async () => {
		try {
			const [mappingResult, categoryResult, eligibleResult] = await Promise.all([
				Apies.getDeltaEventMappings(),
				Apies.getDeltaCategories(),
				Apies.getDeltaEligibleCategories(),
			]);
			setMappings(mappingResult);
			setCategories(categoryResult);
			setEligibleCategories(eligibleResult);
			setFailed(mappingResult === null || categoryResult === null || eligibleResult === null);
		} catch {
			setFailed(true);
		}
	}, []);

	useEffect(() => {
		if (loading || !me.isAdmin) return;
		void refresh();
	}, [loading, me.isAdmin, refresh]);

	if (loading || (me.isAdmin && (mappings === null || categories === null || eligibleCategories === null) && !failed)) return <Loading />;
	if (!me.isAdmin) return <MainView info={me} />;
	if (failed || mappings === null || categories === null || eligibleCategories === null) {
		return (
			<BodyShort role="alert">
				We couldn't fetch Delta scoring settings. Try again later.
			</BodyShort>
		);
	}
	return (
		<ManageDeltaEventMappingsView
			mappings={mappings}
			categories={categories}
			eligibleCategories={eligibleCategories}
			onRefresh={refresh}
		/>
	);
}

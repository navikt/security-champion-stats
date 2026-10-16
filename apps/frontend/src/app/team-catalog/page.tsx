"use client";

import { useMe } from "@/app/shared/hooks/UseMe";
import Loading from "@/app/view/Loading";
import { TeamCatalogRolesView } from "@/app/view/member/TeamCatalogRolesView";
import { BodyShort } from "@navikt/ds-react";

export default function Page() {
	const { me, loading } = useMe();
	if (loading) return <Loading />;
	if (!me.isActive) {
		return <BodyShort>An active program membership is required to manage Teamkatalogen roles.</BodyShort>;
	}
	return <TeamCatalogRolesView />;
}

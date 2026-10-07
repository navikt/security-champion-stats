"use client";

import { BodyShort } from "@navikt/ds-react";
import { useMe } from "@/app/shared/hooks/UseMe";
import Loading from "@/app/view/Loading";
import { AdminAuditView } from "@/app/view/history/AdminAuditView";

export default function Page() {
	const { me, loading } = useMe();
	if (loading) return <Loading />;
	if (!me.isAdmin) return <BodyShort>Administrator access is required.</BodyShort>;
	return <AdminAuditView />;
}

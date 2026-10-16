"use client";

import { useMe } from "@/app/shared/hooks/UseMe";
import Loading from "@/app/view/Loading";
import { MainView } from "@/app/view/HomeView";
import { TeamCatalogRoleStatusView } from "@/app/view/appsec/TeamCatalogRoleStatusView";

export default function Page() {
	const { me, loading } = useMe();
	if (loading) return <Loading />;
	if (!me.isAdmin) return <MainView info={me} />;
	return <TeamCatalogRoleStatusView />;
}

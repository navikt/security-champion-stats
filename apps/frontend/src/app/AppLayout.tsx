"use client";

import { Page } from "@navikt/ds-react";
import "@/app/style/TopLayout.css";
import { useMe } from "./shared/hooks/UseMe";
import { SideNavigation } from "@/app/shared/navigation/SideNavigation";

export default function AppLayout({ children }: { children: React.ReactNode }) {
	const { me, loading } = useMe();
	if (loading) return null;

	return (
		<Page className={"appLayout"}>
			<div className="appBody">
				<SideNavigation me={me} />
				<Page.Block as="main" gutters className="appMain">
					{children}
				</Page.Block>
			</div>
		</Page>
	);
}

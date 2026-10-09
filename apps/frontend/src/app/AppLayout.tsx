"use client";

import { Page } from "@navikt/ds-react";
import "@/app/style/TopLayout.css";
import { SideNavigation } from "@/app/shared/navigation/SideNavigation";
import { HackerBackdrop } from "@/app/shared/theme/ThemeProvider";
import { useMe } from "./shared/hooks/UseMe";

export default function AppLayout({ children }: { children: React.ReactNode }) {
	const { me, loading } = useMe();
	if (loading) return null;

	return (
		<>
			<HackerBackdrop />
			<Page className={"appLayout"} contentBlockPadding="none">
				<div className="appBody">
					<SideNavigation me={me} />
					<Page.Block as="main" gutters className="appMain">
						{children}
					</Page.Block>
				</div>
			</Page>
		</>
	);
}

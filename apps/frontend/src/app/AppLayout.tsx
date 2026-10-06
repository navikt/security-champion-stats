"use client";

import { Page, Theme } from "@navikt/ds-react";
import "@/app/style/TopLayout.css";
import { useTheme } from "next-themes";
import { SideNavigation } from "@/app/shared/navigation/SideNavigation";
import { HackerBackdrop } from "@/app/shared/theme/ThemeProvider";
import { useMe } from "./shared/hooks/UseMe";

export default function AppLayout({ children }: { children: React.ReactNode }) {
	const { me, loading } = useMe();
	const { theme } = useTheme();
	if (loading) return null;

	const page = (
		<Page className={"appLayout"}>
			<div className="appBody">
				<SideNavigation me={me} />
				<Page.Block as="main" gutters className="appMain">
					{children}
				</Page.Block>
			</div>
		</Page>
	);

	return (
		<>
			<HackerBackdrop />
			{theme === "hacker" ? <Theme theme="dark">{page}</Theme> : page}
		</>
	);
}

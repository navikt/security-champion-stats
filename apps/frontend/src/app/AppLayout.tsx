"use client";

import {
	ActionMenu,
	InternalHeader,
	Page,
	Spacer,
} from "@navikt/ds-react";
import { MenuGridIcon } from "@navikt/aksel-icons";
import "@/app/style/TopLayout.css";
import { ThemeToggle } from "@/app/shared/theme/ThemeProvider";
import { useMe } from "./shared/hooks/UseMe";
import { SideNavigation } from "@/app/shared/navigation/SideNavigation";

export default function AppLayout({ children }: { children: React.ReactNode }) {
	const { me, loading } = useMe();
	if (loading) return null;

	return (
		<Page className={"appLayout"}>
			<InternalHeader>
				<InternalHeader.Title as="h2" href="/">
					Sec Hub
				</InternalHeader.Title>

				{me.isAdmin && (
					<ActionMenu>
						<ActionMenu.Trigger>
							<InternalHeader.Button>
								<MenuGridIcon style={{ fontSize: "1.5rem" }} />
							</InternalHeader.Button>
						</ActionMenu.Trigger>

						<ActionMenu.Content align="end">
							<ActionMenu.Group label="Menu">
								<ActionMenu.Item as="a" href="/dashboard">
									Program dashboard
								</ActionMenu.Item>
								<ActionMenu.Item as="a" href="/appsec/events">
									Manage events
								</ActionMenu.Item>
								<ActionMenu.Item as="a" href="/appsec/membership">
									Manage participants
								</ActionMenu.Item>
								<ActionMenu.Item as="a" href="/appsec/scoring">
									Manage scoring
								</ActionMenu.Item>
								<ActionMenu.Item as="a" href="/appsec/slack">
									Manage Slack mappings
								</ActionMenu.Item>
								<ActionMenu.Item as="a" href="/appsec/delta">
									Manage Delta mappings
								</ActionMenu.Item>
							</ActionMenu.Group>
						</ActionMenu.Content>
					</ActionMenu>
				)}

				<Spacer />

				<ThemeToggle />

				<div
					style={{
						display: "flex",
						alignItems: "center",
						paddingLeft: "1rem",
					}}
				>
					<InternalHeader.User name={me.username} description="" />
				</div>
			</InternalHeader>

			<div className="appBody">
				<SideNavigation />
				<Page.Block as="main" gutters className="appMain">
					{children}
				</Page.Block>
			</div>
		</Page>
	);
}

"use client";
import { useLocale, useTranslations } from "next-intl";
import { ActionMenu, InternalHeader, Page, Spacer } from "@navikt/ds-react";

import "@/app/style/TopLayout.css"
import SettingsMenu from "../view/mainView/components/SettingsMenu";
import { useMe } from "../shared/hooks/UseMe";
import { MenuGridIcon } from "@navikt/aksel-icons";
import { SideNavigation } from "@/app/shared/navigation/SideNavigation";

export default function LocaleLayout({
	children,
}: {
	children: React.ReactNode;
}) {
	const { me, loading } = useMe();
	const t = useTranslations();
	const locale = useLocale();
	if (loading) return null;

	return (
		<Page className={"appLayout"}>
			<InternalHeader>
				<InternalHeader.Title as="h2" href={`/${locale}`}>
					{t("common.appTitle")}
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
								<ActionMenu.Item as="a" href={`/${locale}/dashboard`}>
									{t("menu.dashboard")}
								</ActionMenu.Item>
							</ActionMenu.Group>
						</ActionMenu.Content>
					</ActionMenu>
				)}

				<Spacer />

				<SettingsMenu locale={locale} />

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
                <SideNavigation locale={locale} />
                <Page.Block
                    as="main"
                    gutters
                    className="appMain"
                >
                    {children}
                </Page.Block>
            </div>
		</Page>
	);
}

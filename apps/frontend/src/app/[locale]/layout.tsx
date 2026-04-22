"use client"
import {useLocale, useTranslations} from "next-intl";
import {ActionMenu, InternalHeader, Page, Spacer} from "@navikt/ds-react";

import SettingsMenu from "../shared/components/SettingsMenu";
import {useMe} from "../shared/hooks/UseMe";
import {MenuGridIcon} from "@navikt/aksel-icons";

export default function LocaleLayout(
    { children }: { children: React.ReactNode }
) {
    const { me, loading } = useMe();
    const t = useTranslations()
    const locale = useLocale()
    if (loading) return null;

    return (
        <Page >
            <div style={{ minHeight: "10rem" }}>
                <InternalHeader>
                    <InternalHeader.Title as={"h2"} href={`/${locale}`}>
                        {t("common.appTitle")}
                    </InternalHeader.Title>
                    {me.isAdmin &&
                        <ActionMenu>
                            <ActionMenu.Trigger>
                                <InternalHeader.Button>
                                    <MenuGridIcon style={{ fontSize: "1.5rem" }} />
                                </InternalHeader.Button>
                            </ActionMenu.Trigger>
                            <ActionMenu.Content align={"end"}>
                                <ActionMenu.Group label={"Menu"}>
                                    <ActionMenu.Item as={"a"} href={`/${locale}/dashboard`}>
                                        {t("menu.dashboard")}
                                    </ActionMenu.Item>
                                </ActionMenu.Group>
                            </ActionMenu.Content>
                        </ActionMenu>
                    }
                    <Spacer />
                    <SettingsMenu locale={locale}/>
                    {(
                        <div style={{ display: "flex", alignItems: "center", paddingLeft: "1rem" }}>
                            <InternalHeader.User name={me.username} description="" />
                        </div>
                    )}
                </InternalHeader>
            </div>
            <Page.Block as={"main"} width={"xl"} gutters>
                { children }
            </Page.Block>
        </Page>
    )
}
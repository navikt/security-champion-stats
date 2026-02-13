"use client"
import {useAuth} from "../shared/hooks/useAuth";
import {useLocale, useTranslations} from "next-intl";
import {InternalHeader, Page, Spacer} from "@navikt/ds-react";

import SettingsMenu from "../shared/components/SettingsMenu";

export default function LocaleLayout(
    { children }: { children: React.ReactNode }
) {
    const { me, loading } = useAuth()
    const t = useTranslations()
    const locale = useLocale()

    return (
        <Page >
            <div style={{ minHeight: "10rem" }}>
                <InternalHeader>
                    <InternalHeader.Title as={"h2"} href={`/${locale}`}>
                        {t("common.appTitle")}
                    </InternalHeader.Title>
                    <Spacer />
                    <SettingsMenu locale={locale}/>
                    {!loading && me && (
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


function Footer() {
    return <div id="decorator-footer" />;
}

function Header() {
    return <div id="decorator-header" />;
}
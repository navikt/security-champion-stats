"use client"
import {useLocale, useTranslations} from "next-intl";
import {InternalHeader, Page, Spacer} from "@navikt/ds-react";

import SettingsMenu from "../shared/components/SettingsMenu";
import {useMe} from "../shared/hooks/useMe";

export default function LocaleLayout(
    { children }: { children: React.ReactNode }
) {
    const { me, loading } = useMe();
    const t = useTranslations()
    const locale = useLocale()
    if (loading) return null;

    console.log("LocaleLayout rendered with locale:", locale, "and me:", me)
    console.log("Loading state:", loading)
    return (
        <Page >
            <div style={{ minHeight: "10rem" }}>
                <InternalHeader>
                    <InternalHeader.Title as={"h2"} href={`/${locale}`}>
                        {t("common.appTitle")}
                    </InternalHeader.Title>
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
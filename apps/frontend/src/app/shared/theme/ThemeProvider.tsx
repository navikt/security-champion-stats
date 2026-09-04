"use client";

import { ThemeProvider as NextThemeProvider, useTheme } from "next-themes";
import { useTranslations } from "next-intl";
import {useEffect, useState} from "react";
import {Select} from "@navikt/ds-react";

export function ThemeProvider({ children }: { children: React.ReactNode }) {
    return (
        <NextThemeProvider
            attribute="class"
            enableSystem
            storageKey="scs-theme"
        >
            {children}
        </NextThemeProvider>
    );
}

export function ThemeToggle() {
    const { theme, setTheme } = useTheme();
    const t = useTranslations("settings")
    const [mounted, setMounted] = useState(false)

    useEffect(() => {
        setMounted(true)
    }, [])

    if (!mounted) {
        return (
            <Select
                label={t("themeOptions.title")}
                size={"small"}
                value={"system"}
                onChange={() => {}}
                disabled
            >
                <option value={"system"}>{t("themeOptions.title")}</option>
            </Select>
        )
    }

    const themes = [
        { value: "light", label: t("themeOptions.light") },
        { value: "dark", label: t("themeOptions.dark") },
        { value: "system", label: t("themeOptions.system") }
    ]

    return (
        <Select
            label={t("themeOptions.title")}
            size={"small"}
            value={theme}
            onChange={(e) => setTheme(e.target.value)}
        >
            {themes.map((themeOption) => (
                <option key={themeOption.value} value={themeOption.value}>
                    {themeOption.label}
                </option>
            ))}
        </Select>
    )
}

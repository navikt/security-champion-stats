"use client"

import {useLocale, useTranslations} from "next-intl";
import {usePathname, useRouter} from "next/navigation";
import { useTransition } from "react";
import {Select} from "@navikt/ds-react";

export default function LanguageSwitcher() {
    const t = useTranslations()
    const locale = useLocale()
    const router = useRouter()
    const pathname = usePathname()
    const [isPending, startTransition] = useTransition()

    const languages = [
        { code: "nb", flag: "🇳🇴", name: "Norsk" },
        { code: "en", flag: "🇬🇧", name: "English" }
    ]

    const handleChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
        const newLocale = e.target.value as "nb" | "en"
        if (newLocale === locale) return;

        startTransition(() => {
            document.cookie = `NEXT_LOCALE=${newLocale}; path=/; max-age=31536000; SameSite=Lax`;

            const pathWithoutLocale = pathname.replace(/^\/(nb|en)/, '') || '/'
            const newPath = `/${newLocale}${pathWithoutLocale}`

            router.push(newPath)
            router.refresh()
        })
    }

    return (
        <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", paddingRight: "1.5rem" }}>
            <Select
                label={t("settings.language")}
                value={locale}
                onChange={handleChange}
                size={"small"}
                disabled={isPending}
                style={{ minWidth: "150px" }}
            >
                {languages.map((lang) => (
                    <option key={lang.code} value={lang.code}>
                        {lang.flag}{lang.name}
                    </option>
                ))}
            </Select>
        </div>
    )
}
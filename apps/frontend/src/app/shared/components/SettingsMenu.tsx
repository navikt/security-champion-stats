"use client"

import {BodyShort, Button, Popover, VStack} from "@navikt/ds-react";
import {useTranslations} from "next-intl";
import {CogIcon} from "@navikt/aksel-icons";
import {useAuth} from "../hooks/useAuth";
import {useRouter} from "next/navigation";
import {ThemeToggle} from "../theme/ThemeProvider";
import {useId, useRef, useState} from "react";
import LanguageSwitcher from "./LanguageSwitcher";

export default function SettingsMenu({ locale }: {locale: string}) {
    const anchorRef= useRef<HTMLButtonElement>(null);
    const [open, setOpen] = useState(false);
    const t = useTranslations()
    const { me } = useAuth();
    const router = useRouter();
    const popoverId = useId()


    return (
        <div style={{ display: "flex", alignItems: "center", gap: "0.5rem"}}>
            <Button
                ref={anchorRef}
                onClick={() => setOpen(!open)}
                variant={"tertiary"}
                icon={<CogIcon aria-hidden />}
                size={"small"}
                aria-label={t("settings.title")}
            >
                <BodyShort size={"small"}>{t("settings.title")}</BodyShort>
            </Button>
            <Popover
                anchorEl={anchorRef.current}
                open={open}
                onClose={() => setOpen(false)}
                id={popoverId}
                placement={"bottom-end"}
            >
                <Popover.Content>
                    <VStack gap={"space-4"}>
                        <ThemeToggle />
                        <LanguageSwitcher />
                        {me?.isAdmin && (
                            <div className={"settingsSection"}>
                                <div className={"settingsLabel"}>{t("settings.admin")}</div>
                                <Button
                                    size={"small"}
                                    variant={"secondary"}
                                    onClick={() => {
                                        setOpen(false)
                                        router.push(`/${locale}/admin`)
                                    }}>
                                    {t("settings.goToAdmin")}
                                </Button>
                            </div>
                        )}
                    </VStack>
                </Popover.Content>
            </Popover>
        </div>
    )
}
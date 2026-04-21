"use client"

import {BodyShort, Button, Popover, VStack} from "@navikt/ds-react";
import {useTranslations} from "next-intl";
import {CogIcon} from "@navikt/aksel-icons";
import {useRouter} from "next/navigation";
import {ThemeToggle} from "../theme/ThemeProvider";
import {useRef, useState, useId} from "react";
import LanguageSwitcher from "./LanguageSwitcher";
import {useMe} from "../hooks/UseMe";

export default function SettingsMenu({ locale }: {locale: string}) {
    const anchorRef= useRef<HTMLButtonElement>(null);
    const [open, setOpen] = useState(false);
    const t = useTranslations()
    const router = useRouter();
    const popoverId = useId()
    const { me, loading } = useMe();
    if (loading) return null;
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
                    </VStack>
                </Popover.Content>
            </Popover>
        </div>
    )
}
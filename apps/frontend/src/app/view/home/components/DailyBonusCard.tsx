"use client";

import { useState } from "react";
import { Box, BodyShort, Button, Heading } from "@navikt/ds-react";
import { Apies } from "@/app/shared/hooks/Apies";

export function DailyBonusCard({ onClaimed }: { onClaimed: () => Promise<void> }) {
    const [status, setStatus] = useState<string | null>(null)

    const handleClaim = async () => {
        const result = await Apies.claimDailyBonus()
        setStatus(result.notice ?? result.status)
        await onClaimed()
    }

    return (
        <Box
            background="neutral-soft"
            borderColor="brand-blue"
            padding="space-16"
            borderWidth="2"
            borderRadius="12"
            className={"sc-daily-card"}
        >
            <Heading size={"small"} level={"2"}>Daily bonus</Heading>
            <BodyShort className={"sc-daily-card__description"}>
                Claim your one-time daily login bonus.
            </BodyShort>

            <div className={"sc-daily-card__section"}>
                <Button size={"small"} onClick={handleClaim}>Claim bonus</Button>
            </div>

            {status && <BodyShort>{status}</BodyShort>}
        </Box>
    )
}

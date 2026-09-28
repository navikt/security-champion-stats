"use client";

import { useRef, useState } from "react";
import { Box, BodyShort, Button, Heading, TextField } from "@navikt/ds-react";
import { Apies } from "@/app/shared/hooks/Apies";

export function BoosterCard({ onRedeemed }: { onRedeemed: () => Promise<void> }) {
    const [boosterToken, setBoosterToken] = useState<string | null>(null)
    const [status, setStatus] = useState<string | null>(null)
    const redeemTokenRef = useRef<HTMLInputElement>(null)

    const handleGetBooster = async () => {
        const result = await Apies.getBoosterToken()
        setBoosterToken(result?.token ?? null)
        setStatus(result ? null : "Failed to issue booster token")
    }

    const handleRedeemBooster = async () => {
        const token = redeemTokenRef.current?.value ?? ""
        const result = await Apies.redeemBoosterToken(token)
        setStatus(result.notice ?? result.status)
        await onRedeemed()
    }

    return (
        <Box
            background="neutral-soft"
            borderColor="brand-blue"
            padding="space-16"
            borderWidth="2"
            borderRadius="12"
            className={"sc-booster-card"}
        >
            <Heading size={"small"} level={"2"}>Daily booster</Heading>
            <BodyShort className={"sc-booster-card__description"}>
                Claim a short-lived booster code for a small bonus, then redeem it before it expires.
            </BodyShort>

            <div className={"sc-booster-card__section"}>
                <Button size={"small"} onClick={handleGetBooster}>Get my booster code</Button>
                {boosterToken && (
                    <TextField
                        label="Your booster code"
                        size={"small"}
                        value={boosterToken}
                        readOnly
                    />
                )}
            </div>

            <div className={"sc-booster-card__section"}>
                <TextField label="Redeem booster code" size={"small"} ref={redeemTokenRef} />
                <Button size={"small"} onClick={handleRedeemBooster}>Redeem</Button>
            </div>

            {status && <BodyShort>{status}</BodyShort>}
        </Box>
    )
}

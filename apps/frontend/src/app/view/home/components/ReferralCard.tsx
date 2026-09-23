"use client";

import { useRef, useState } from "react";
import { Box, BodyShort, Button, Heading, TextField } from "@navikt/ds-react";
import { Apies } from "@/app/shared/hooks/Apies";

export function ReferralCard({ onClaimed }: { onClaimed: () => Promise<void> }) {
    const [certData, setCertData] = useState<string | null>(null)
    const [certSignature, setCertSignature] = useState<string | null>(null)
    const [claimsUsed, setClaimsUsed] = useState<number | null>(null)
    const [maxClaims, setMaxClaims] = useState<number | null>(null)
    const [status, setStatus] = useState<string | null>(null)
    const claimDataRef = useRef<HTMLInputElement>(null)
    const claimSignatureRef = useRef<HTMLInputElement>(null)

    const handleGetCertificate = async () => {
        const result = await Apies.getReferralCertificate()
        setCertData(result?.data ?? null)
        setCertSignature(result?.signature ?? null)
        setClaimsUsed(result?.claimsUsed ?? null)
        setMaxClaims(result?.maxClaims ?? null)
        setStatus(result ? null : "Failed to issue referral certificate")
    }

    const handleClaim = async () => {
        const data = claimDataRef.current?.value ?? ""
        const signature = claimSignatureRef.current?.value ?? ""
        const result = await Apies.claimReferralBonus(data, signature)
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
            className={"sc-referral-card"}
        >
            <Heading size={"small"} level={"2"}>Referral bonus</Heading>
            <BodyShort className={"sc-referral-card__description"}>
                Refer a colleague to get a signed referral certificate, then submit it to claim your bonus.
            </BodyShort>
            {claimsUsed !== null && maxClaims !== null && (
                <BodyShort className={"sc-referral-card__description"}>
                    Claims used: {claimsUsed} / {maxClaims}
                </BodyShort>
            )}

            <div className={"sc-referral-card__section"}>
                <Button size={"small"} onClick={handleGetCertificate}>Get my referral certificate</Button>
                {certData && (
                    <TextField label="Certificate data (base64)" size={"small"} value={certData} readOnly />
                )}
                {certSignature && (
                    <TextField label="Certificate signature" size={"small"} value={certSignature} readOnly />
                )}
            </div>

            <div className={"sc-referral-card__section"}>
                <TextField label="Certificate data (base64)" size={"small"} ref={claimDataRef} />
                <TextField label="Certificate signature" size={"small"} ref={claimSignatureRef} />
                <Button size={"small"} onClick={handleClaim}>Claim bonus</Button>
            </div>

            {status && <BodyShort>{status}</BodyShort>}
        </Box>
    )
}

"use client";

import { useEffect, useState } from "react";
import { Member } from "@/app/utils/Variables";
import { Apies } from "@/app/shared/hooks/Apies";
import { Heading, BodyShort } from "@navikt/ds-react";
import "../../../style/home/Leaderboard.css";

const VISIBLE_ROWS = 5;

export function Leaderboard({ refreshKey }: { refreshKey?: number }) {
    const [members, setMembers] = useState<Member[]>([])

    useEffect(() => {
        let cancelled = false

        Apies.getMembers().then((data) => {
            if (!cancelled) setMembers(data)
        })

        return () => {
            cancelled = true
        }
    }, [refreshKey])

    const ranked = members
        .filter((m) => m.inGame)
        .sort((a, b) => b.points - a.points)

    return (
        <section className={"sc-leaderboard-card"}>
            <header className={"sc-leaderboard-card__header"}>
                <Heading size={"small"} level={"2"}>Leaderboard</Heading>
                <BodyShort className={"sc-leaderboard-card__subtitle"}>
                    Top security champions
                </BodyShort>
            </header>

            {ranked.length === 0 ? (
                <BodyShort className={"sc-leaderboard-card__empty"}>
                    No members in the program yet.
                </BodyShort>
            ) : (
                <ol className={"sc-leaderboard-card__list"}>
                    {ranked.map((m, i) => (
                        <li key={m.id} className={"sc-leaderboard-card__row"}>
                            <span className={"sc-leaderboard-card__rank"}>{i + 1}</span>
                            <span
                                className={"sc-leaderboard-card__name"}
                                dangerouslySetInnerHTML={{ __html: m.fullname }}
                            />
                            <span className={"sc-leaderboard-card__points"}>
                                {m.points.toLocaleString()}
                            </span>
                        </li>
                    ))}
                </ol>
            )}

            {ranked.length > VISIBLE_ROWS && (
                <BodyShort className={"sc-leaderboard-card__hint"}>
                    Scroll to see all {ranked.length} champions
                </BodyShort>
            )}
        </section>
    )
}

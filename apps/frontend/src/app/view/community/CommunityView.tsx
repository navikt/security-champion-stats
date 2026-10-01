"use client"

import {useMemo, useState} from "react";
import {Member} from "@/app/utils/Variables";
import {BodyShort, Heading, Search} from "@navikt/ds-react";
import {useTranslations} from "next-intl";
import {CommunityMemberRow} from "@/app/view/community/CommunityMemberRow";
import "../../style/community/CommunityView.css"

interface CommunityViewProps {
    members: Member[]
}

export function CommunityView({members}: CommunityViewProps) {
    const t = useTranslations("community")
    const [query, setQuery] = useState("")

    const visibleMembers = useMemo(() => {
        const sorted = [...members].sort((a, b) => a.fullname.localeCompare(b.fullname))

        const normalizedQuery = query.trim().toLowerCase()
        if (!normalizedQuery) return sorted

        return sorted.filter(member =>
            member.fullname.toLowerCase().includes(normalizedQuery) ||
            member.teams.some(team => team.toLowerCase().includes(normalizedQuery))
        )
    }, [members, query])

    return (
        <main className={"communityView"}>
            <header className={"communityView__header"}>
                <Heading level={"1"} size={"xlarge"}>
                    {t("title")}
                </Heading>

                <BodyShort className={"communityView__subtitle"}>
                    {t("description")}
                </BodyShort>
            </header>

            <div className={"communityView__search"}>
                <Search
                    label={t("searchLabel")}
                    hideLabel
                    variant={"simple"}
                    placeholder={t("searchPlaceholder")}
                    onChange={setQuery}
                />
            </div>

            <section className={"communitySection"}>
                {visibleMembers.length === 0 ? (
                    <div className={"communitySection__empty"}>
                        {t("noResults")}
                    </div>
                ) : (
                    <div className={"communityList"}>
                        {visibleMembers.map(member => (
                            <CommunityMemberRow member={member} key={member.id} />
                        ))}
                    </div>
                )}
            </section>
        </main>
    )
}

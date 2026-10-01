import {Member} from "@/app/utils/Variables";
import {Tag} from "@navikt/ds-react";
import {useTranslations} from "next-intl";
import "../../style/community/CommunityView.css"

interface CommunityMemberRowProps {
    member: Member
}

function getInitials(fullname: string): string {
    const parts = fullname.trim().split(/\s+/).filter(Boolean)
    const first = parts[0]?.[0] ?? ""
    const last = parts.length > 1 ? parts[parts.length - 1][0] : ""
    return (first + last).toUpperCase()
}

export function CommunityMemberRow({member}: CommunityMemberRowProps) {
    const t = useTranslations("community")

    return (
        <article className={"communityMemberRow"}>
            <div className={"communityMemberRow__avatar"} aria-hidden>
                {getInitials(member.fullname)}
            </div>

            <div className={"communityMemberRow__content"}>
                <strong className={"communityMemberRow__name"}>
                    {member.fullname}
                </strong>

                <div className={"communityMemberRow__teams"}>
                    {member.teams.length > 0 ? (
                        member.teams.map(team => (
                            <Tag
                                key={team}
                                size={"small"}
                                variant={"neutral-moderate"}
                            >
                                {team}
                            </Tag>
                        ))
                    ) : (
                        <span className={"communityMemberRow__noTeam"}>
                            {t("noTeam")}
                        </span>
                    )}
                </div>
            </div>
        </article>
    )
}

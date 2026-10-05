import { ProgramParticipantSummary } from "@/app/utils/Variables";
import { Tag } from "@navikt/ds-react";
import "../../style/community/CommunityView.css";

interface CommunityMemberRowProps {
	member: ProgramParticipantSummary;
}

function getInitials(fullname: string): string {
	const parts = fullname.trim().split(/\s+/).filter(Boolean);
	const first = parts[0]?.[0] ?? "";
	const last = parts.length > 1 ? parts[parts.length - 1][0] : "";
	return (first + last).toUpperCase();
}

export function CommunityMemberRow({ member }: CommunityMemberRowProps) {
	const fullname = member.fullname || "Name unavailable";

	return (
		<article className={"communityMemberRow"}>
			<div className={"communityMemberRow__avatar"} aria-hidden>
				{getInitials(fullname)}
			</div>

			<div className={"communityMemberRow__content"}>
				<strong className={"communityMemberRow__name"}>
					{fullname}
				</strong>

				<div className={"communityMemberRow__teams"}>
					{member.teams.length > 0 ? (
						member.teams.map((team) => (
							<Tag key={team} size={"small"} variant={"neutral-moderate"}>
								{team}
							</Tag>
						))
					) : (
						<span className={"communityMemberRow__noTeam"}>No team</span>
					)}
				</div>
			</div>
		</article>
	);
}

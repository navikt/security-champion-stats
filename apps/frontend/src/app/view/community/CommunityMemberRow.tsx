import { ProgramParticipantSummary } from "@/app/utils/Variables";
import { Detail, Tag } from "@navikt/ds-react";
import { getInitials } from "@/app/utils/GetInitials";
import "../../style/community/CommunityView.css";

interface CommunityMemberRowProps {
	member: ProgramParticipantSummary;
}

export function CommunityMemberRow({ member }: CommunityMemberRowProps) {
	const fullname = member.fullname || "Name unavailable";

	return (
		<article className={"communityMemberRow"}>
			<div className={"communityMemberRow__avatar"} aria-hidden>
				{getInitials(fullname)}
			</div>

			<div className={"communityMemberRow__content"}>
				<strong className={"communityMemberRow__name"}>{fullname}</strong>

				<div className={"communityMemberRow__teams"}>
					{member.teams.length > 0 ? (
						member.teams.map((team) => (
							<Tag key={team} size={"small"} variant={"neutral-moderate"}>
								{team}
							</Tag>
						))
					) : (
						<Detail className={"communityMemberRow__noTeam"}>No team</Detail>
					)}
				</div>
			</div>
		</article>
	);
}

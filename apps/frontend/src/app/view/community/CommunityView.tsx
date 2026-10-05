"use client";

import { useMemo, useState } from "react";
import { ProgramParticipantSummary } from "@/app/utils/Variables";
import { BodyShort, Heading, Search } from "@navikt/ds-react";
import { CommunityMemberRow } from "@/app/view/community/CommunityMemberRow";
import "../../style/community/CommunityView.css";

interface CommunityViewProps {
	members: ProgramParticipantSummary[];
}

export function CommunityView({ members }: CommunityViewProps) {
	const [query, setQuery] = useState("");

	const visibleMembers = useMemo(() => {
		const sorted = [...members].sort((a, b) =>
			a.fullname.localeCompare(b.fullname),
		);

		const normalizedQuery = query.trim().toLowerCase();
		if (!normalizedQuery) return sorted;

		return sorted.filter(
			(member) =>
				member.fullname.toLowerCase().includes(normalizedQuery) ||
				member.teams.some((team) =>
					team.toLowerCase().includes(normalizedQuery),
				),
		);
	}, [members, query]);

	return (
		<main className={"communityView"}>
			<header className={"communityView__header"}>
				<Heading level={"1"} size={"xlarge"}>
					Community
				</Heading>

				<BodyShort className={"communityView__subtitle"}>
					Program participants and their teams.
				</BodyShort>
			</header>

			<div className={"communityView__search"}>
				<Search
					label="Search by name or team"
					hideLabel
					variant={"simple"}
					placeholder="Search by name or team"
					onChange={setQuery}
				/>
			</div>

			<section className={"communitySection"}>
				{visibleMembers.length === 0 ? (
					<div className={"communitySection__empty"}>
						No participants match your search.
					</div>
				) : (
					<div className={"communityList"}>
						{visibleMembers.map((member) => (
							<CommunityMemberRow member={member} key={member.id} />
						))}
					</div>
				)}
			</section>
		</main>
	);
}

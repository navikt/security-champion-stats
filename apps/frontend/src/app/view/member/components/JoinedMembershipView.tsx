import { BodyShort, Heading } from "@navikt/ds-react";
import { ProgramParticipant } from "@/app/utils/Variables";

export function JoinedMembershipView({
	participant,
}: {
	participant: ProgramParticipant;
}) {
	return (
		<section className="sc-membership-card sc-membership-card--joined">
			<div className="sc-membership-card__content">
				<span
					className={
						participant.active
							? "sc-membership-card__status__active"
							: "sc-membership-card__status__inactive"
					}
				>
					{participant.active ? "Active participant" : "Deactivated"}
				</span>
				<Heading
					size="large"
					level="2"
					className="sc-membership-card__description"
				>
					Program participation
				</Heading>
				<BodyShort className="sc-membership-card__description">
					{participant.active
						? "You are an active participant in the Security Champion program."
						: "A program administrator has deactivated your participation."}
				</BodyShort>
				<dl className="sc-membership-card__facts">
					<dt>Participant since</dt>
					<dd>{new Date(participant.joinedAt).toLocaleDateString("en")}</dd>
				</dl>
			</div>
		</section>
	);
}

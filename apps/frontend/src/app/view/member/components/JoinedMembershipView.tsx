import { BodyShort, Heading } from "@navikt/ds-react";
import { useTranslations } from "next-intl";
import { ProgramParticipant } from "@/app/utils/Variables";

export function JoinedMembershipView({
	participant,
}: {
	participant: ProgramParticipant;
}) {
	const t = useTranslations("home.membership.member");

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
					{participant.active ? t("active") : t("inactive")}
				</span>
				<Heading
					size="large"
					level="2"
					className="sc-membership-card__description"
				>
					{t("securityChampion")}
				</Heading>
				<BodyShort className="sc-membership-card__description">
					{participant.active ? t("description") : t("inactiveDescription")}
				</BodyShort>
				<dl className="sc-membership-card__facts">
					<dt>{t("joinedProgram")}</dt>
					<dd>{new Date(participant.joinedAt).toLocaleDateString()}</dd>
				</dl>
			</div>
		</section>
	);
}

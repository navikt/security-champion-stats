import { BodyShort, Button, Heading } from "@navikt/ds-react";
import Link from "next/link";
import { ProgramParticipant } from "@/app/utils/Variables";

export function JoinedMembershipView({
	participant,
	onLeave,
	onRejoin,
	pending = false,
	error,
}: {
	participant: ProgramParticipant;
	onLeave: () => void;
	onRejoin: () => void;
	pending?: boolean;
	error?: string | null;
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
					{participant.active ? "Active participant" : participant.status === "LEFT" ? "Left program" : "Deactivated"}
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
						: participant.status === "LEFT"
							? "You have left the program. Your history and existing credits are retained."
							: "A program administrator has deactivated your participation."}
				</BodyShort>
				<dl className="sc-membership-card__facts">
					<dt>Participant since</dt>
					<dd>
						{new Date(participant.joinedAt).toLocaleDateString("nb-NO", {
							timeZone: "Europe/Oslo",
						})}
					</dd>
				</dl>
				{error && <BodyShort role="alert">{error}</BodyShort>}
				<div className="sc-membership-card__actions">
					{participant.active && (
						<Button variant="secondary" data-color="danger" onClick={onLeave}>
							Leave program
						</Button>
					)}
					{participant.status === "LEFT" && (
						<Button onClick={onRejoin} loading={pending} disabled={pending}>
							Rejoin program
						</Button>
					)}
					<Link href="/history">My history</Link>
				</div>
			</div>
		</section>
	);
}

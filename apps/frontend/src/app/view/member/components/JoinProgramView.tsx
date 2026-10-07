import { Button, Heading, List } from "@navikt/ds-react";
import Image from "next/image";
import { useState } from "react";

export function JoinProgramView({
	onEnroll,
}: {
	onEnroll: () => Promise<boolean>;
}) {
	const [pending, setPending] = useState(false);
	const [failed, setFailed] = useState(false);

	const handleJoin = async () => {
		if (pending) return;
		setPending(true);
		setFailed(false);
		try {
			setFailed(!(await onEnroll()));
		} catch {
			setFailed(true);
		} finally {
			setPending(false);
		}
	};

	return (
		<section className="sc-membership-card sc-membership-card--guest">
			<div className="sc-membership-card__content">
				<Heading level="2" size="large" className="sc-membership-card__title">
					Join the Security Champion program
				</Heading>
				<Heading level="3" size="small">
					What to expect
				</Heading>
				<List size="small">
					<List.Item>Attend events and workshops</List.Item>
					<List.Item>Make an impact in your team</List.Item>
					<List.Item>Earn XP and unlock levels</List.Item>
				</List>
				{failed && <p role="alert">We couldn't enroll you. Try again.</p>}
				<div className="sc-membership-card__actions sc-membership-card__actions--join">
					<Button variant="primary" onClick={handleJoin}>
						{pending ? "Enrolling" : "Enroll"}
					</Button>
					<Button
						as="a"
						href="https://sikkerhet.nav.no"
						target="_blank"
						rel="noreferrer"
						variant="secondary"
					>
						Learn more
					</Button>
				</div>
			</div>
			<div className="sc-membership-card__visual">
				<Image
					src="/icon.svg"
					alt=""
					width={251}
					height={296}
					className="sc-membership-card__logo"
				/>
			</div>
		</section>
	);
}

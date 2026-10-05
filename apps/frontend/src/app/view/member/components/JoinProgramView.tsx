import { BodyShort, Button, Heading } from "@navikt/ds-react";
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
				<BodyShort className="sc-membership-card__description">
					Enroll to take part in the program.
				</BodyShort>
				{failed && <p role="alert">We couldn't enroll you. Try again.</p>}
				<div className="sc-membership-card__actions">
					<Button variant="primary" onClick={handleJoin}>
						{pending ? "Enrolling" : "Enroll"}
					</Button>
				</div>
			</div>
		</section>
	);
}

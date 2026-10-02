import { BodyShort, Button, Heading } from "@navikt/ds-react";
import { useTranslations } from "next-intl";
import { useState } from "react";

export function JoinProgramView({
	onEnroll,
}: {
	onEnroll: () => Promise<boolean>;
}) {
	const t = useTranslations("home.membership.nonmember");
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
					{t("title")}
				</Heading>
				<BodyShort className="sc-membership-card__description">
					{t("description")}
				</BodyShort>
				{failed && <p role="alert">{t("enrollError")}</p>}
				<div className="sc-membership-card__actions">
					<Button variant="primary" onClick={handleJoin}>
						{pending ? t("enrolling") : t("enroll")}
					</Button>
				</div>
			</div>
		</section>
	);
}

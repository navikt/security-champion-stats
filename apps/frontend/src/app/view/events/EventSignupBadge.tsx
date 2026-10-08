import { Tag } from "@navikt/ds-react";
import type { SecurityEvent } from "@/app/utils/Variables";

const labels = {
	SIGNED_UP: "Signed up",
	NOT_SIGNED_UP: "Not signed up",
	HOST: "Hosting",
	UNAVAILABLE: "Signup status unavailable",
};

export function signupStatusLabel(event: SecurityEvent): string | null {
	return event.signupStatus ? labels[event.signupStatus] : null;
}

export function EventSignupBadge({ event }: { event: SecurityEvent }) {
	if (!event.signupStatus) return null;
	return (
		<Tag
			size="small"
			variant="moderate"
			data-color={event.signupStatus === "SIGNED_UP" ? "success" : "neutral"}
			title={
				event.signupCheckedAt
					? `Checked in Delta: ${new Date(event.signupCheckedAt).toLocaleString()}`
					: undefined
			}
		>
			{signupStatusLabel(event)}
		</Tag>
	);
}

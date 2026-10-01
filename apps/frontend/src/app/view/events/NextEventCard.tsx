import { BodyShort, Button, Heading, Tag } from "@navikt/ds-react";
import { SecurityEvent } from "@/app/utils/Variables";
import "../../style/events/EventsView.css";
import { useTranslations } from "next-intl";
import { CalendarIcon, ClockIcon, LocationPinIcon } from "@navikt/aksel-icons";

interface NextEventCardProps {
	event: SecurityEvent;
}

export function NextEventCard({ event }: NextEventCardProps) {
	const start = new Date(event.startDate);

	const end = event.endDate ? new Date(event.endDate) : undefined;

	const t = useTranslations("events");

	return (
		<article className={"nextEventCard"}>
			<div className={"nextEventCard__eyebrow"}>{t("nextEvent")}</div>

			<div className={"nextEventCard__heading"}>
				<div>
					<Heading size={"large"} level={"2"}>
						{event.name}
					</Heading>

					{event.description && (
						<BodyShort className={"nextEventCard__description"}>
							{event.description}
						</BodyShort>
					)}
				</div>

				{event.type && (
					<Tag
						variant={"neutral-moderate"}
						size={"small"}
						className={"nextEventCard__type"}
					>
						{event.type}
					</Tag>
				)}
			</div>

			<div className={"nextEventCard__metadata"}>
				<div className={"eventMeta"}>
					<CalendarIcon aria-hidden />
					<span>{start.toLocaleDateString("no-NO")}</span>
				</div>

				<div className={"eventMeta"}>
					<ClockIcon aria-hidden />
					<span>{formatEventTime(start, end)}</span>
				</div>

				{event.location && (
					<div className={"eventMeta"}>
						<LocationPinIcon aria-hidden />
						<span>{event.location}</span>
					</div>
				)}
			</div>

			<div className={"nextEventCard__actions"}>
				<Button variant={"secondary"}>{t("details")}</Button>
			</div>
		</article>
	);
}

function formatEventTime(start: Date, end?: Date): string {
	const format = (date: Date) => {
		return date.toLocaleDateString([], {
			hour: "2-digit",
			minute: "2-digit",
		});
	};

	if (!end) {
		return format(start);
	}

	return `${format(start)} - ${format(end)}`;
}

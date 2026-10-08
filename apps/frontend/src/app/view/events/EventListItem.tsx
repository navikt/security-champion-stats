import { SecurityEvent } from "@/app/utils/Variables";
import { formatEventDate } from "@/app/utils/eventUtils";
import { CalendarIcon, ChevronRightIcon } from "@navikt/aksel-icons";
import { Detail, Link, Tag } from "@navikt/ds-react";
import "../../style/events/EventsView.css";
import { EventSignupBadge } from "./EventSignupBadge";

interface EventListItemProps {
	event: SecurityEvent;
	muted?: boolean;
}

export function EventListItem({ event, muted = false }: EventListItemProps) {
	const start = new Date(event.startDate);

	return (
		<article
			className={["eventListItem", muted ? "eventListItem--muted" : ""]
				.filter(Boolean)
				.join(" ")}
		>
			<div className={"eventListItem__icon"}>
				<CalendarIcon aria-hidden />
			</div>

			<div className={"eventListItem__content"}>
				<strong className={"eventListItem__title"}>
					{event.link ? (
						<Link href={event.link}>{event.name}</Link>
					) : (
						event.name
					)}
				</strong>

				<Detail className={"eventListItem__date"}>
					{formatEventDate(event)}
					{!event.allDay && (
						<>
							{" · "}
							{start.toLocaleTimeString("nb-NO", {
								hour: "2-digit",
								minute: "2-digit",
								timeZone: "Europe/Oslo",
							})}
						</>
					)}
				</Detail>
				{!muted && <EventSignupBadge event={event} />}
			</div>

			{event.type && (
				<Tag
					size={"small"}
					variant={"neutral-moderate"}
					className={"eventListItem__type"}
				>
					{event.type}
				</Tag>
			)}

			<ChevronRightIcon aria-hidden className={"eventListItem__chevron"} />
		</article>
	);
}

import { SecurityEvent } from "@/app/utils/Variables";
import { CalendarIcon, ChevronRightIcon } from "@navikt/aksel-icons";
import { Tag } from "@navikt/ds-react";
import "../../style/events/EventsView.css";

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
				<strong className={"eventListItem__title"}>{event.name}</strong>

				<span className={"eventListItem__date"}>
					{start.toLocaleDateString("nb-NO", { timeZone: "Europe/Oslo" })} ·{" "}
					{start.toLocaleTimeString("nb-NO", {
						hour: "2-digit",
						minute: "2-digit",
						timeZone: "Europe/Oslo",
					})}
				</span>
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

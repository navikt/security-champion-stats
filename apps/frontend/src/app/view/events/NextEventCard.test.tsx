import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { SecurityEvent } from "@/app/utils/Variables";
import { NextEventCard } from "./NextEventCard";

afterEach(cleanup);

const event: SecurityEvent = {
	id: "external:conference",
	name: "Conference",
	description: "Alle",
	startDate: "2026-10-20",
	endDate: "2026-10-22",
	location: "",
	type: "event",
	externalEvent: true,
	deltaEvent: false,
	allDay: true,
	link: "https://example.org",
};

describe("NextEventCard", () => {
	it("shows date-only ranges and a destination-neutral link", () => {
		render(<NextEventCard event={event} />);
		expect(screen.getByText("20.10.2026 - 22.10.2026")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "View event" })).toHaveAttribute(
			"href",
			event.link,
		);
		expect(screen.queryByText(/00:00|02:00/)).not.toBeInTheDocument();
	});

	it("preserves the time and Delta label for Delta events", () => {
		render(
			<NextEventCard
				event={{
					...event,
					allDay: false,
					deltaEvent: true,
					startDate: "2026-10-20T08:00:00Z",
					endDate: "2026-10-20T09:00:00Z",
					link: "https://delta.nav.no/event/test",
				}}
			/>,
		);
		expect(screen.getByText("10:00 - 11:00")).toBeInTheDocument();
		expect(
			screen.getByRole("button", { name: "View in Delta" }),
		).toBeInTheDocument();
	});
});

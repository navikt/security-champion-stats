import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { SecurityEvent } from "@/app/utils/Variables";
import { EventListItem } from "./EventListItem";

afterEach(() => {
	cleanup();
});

const event: SecurityEvent = {
	id: "1",
	name: "Security meetup",
	description: "",
	startDate: "2026-10-03T08:00:00Z",
	endDate: "2026-10-03T09:00:00Z",
	location: "Oslo",
	type: "meetup",
	externalEvent: false,
	deltaEvent: true,
};

describe("EventListItem", () => {
	it("links the event name to Delta when a link exists", () => {
		render(<EventListItem event={{ ...event, link: "https://delta.nav.no/event/1" }} />);

		expect(screen.getByRole("link", { name: "Security meetup" })).toHaveAttribute(
			"href",
			"https://delta.nav.no/event/1",
		);
	});

	it("renders the event name without a link when none exists", () => {
		render(<EventListItem event={event} />);

		expect(screen.getByText("Security meetup")).toBeInTheDocument();
		expect(screen.queryByRole("link")).not.toBeInTheDocument();
	});
});

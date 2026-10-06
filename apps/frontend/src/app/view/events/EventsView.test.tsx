import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SecurityEvent } from "@/app/utils/Variables";
import { EventsView } from "./EventsView";

const nextEvent: SecurityEvent = {
	id: "next",
	name: "Security meetup",
	description: "",
	startDate: "2026-10-20T08:00:00Z",
	endDate: "2026-10-20T09:00:00Z",
	location: "Oslo",
	type: "meetup",
	externalEvent: false,
	deltaEvent: true,
};

const laterEvent: SecurityEvent = {
	...nextEvent,
	id: "later",
	name: "Later meetup",
	startDate: "2026-11-03T08:00:00Z",
	endDate: "2026-11-03T09:00:00Z",
};

const olderEvent: SecurityEvent = {
	...nextEvent,
	id: "older",
	name: "Older meetup",
	startDate: "2026-09-01T08:00:00Z",
	endDate: "2026-09-01T09:00:00Z",
};

const recentEvent: SecurityEvent = {
	...nextEvent,
	id: "recent",
	name: "Recent meetup",
	startDate: "2026-10-03T08:00:00Z",
	endDate: "2026-10-03T09:00:00Z",
};

function eventSection(title: string): HTMLElement {
	const section = screen.getByRole("heading", { name: title }).closest("section");
	if (!section) throw new Error(`Missing event section: ${title}`);
	return section;
}

describe("EventsView", () => {
	beforeEach(() => {
		vi.useFakeTimers();
		vi.setSystemTime(new Date("2026-10-06T10:00:00Z"));
	});

	afterEach(() => {
		cleanup();
		vi.useRealTimers();
	});

	it("shows a single upcoming event in the compact homepage list", () => {
		render(<EventsView events={[nextEvent]} compact limit={4} />);

		expect(
			within(eventSection("Upcoming events")).getByText(nextEvent.name),
		).toBeInTheDocument();
		expect(screen.queryByText("No other upcoming events.")).not.toBeInTheDocument();
	});

	it("keeps upcoming events nearest-first before applying the compact limit", () => {
		render(<EventsView events={[laterEvent, nextEvent]} compact limit={1} />);

		expect(
			within(eventSection("Upcoming events")).getByText(nextEvent.name),
		).toBeInTheDocument();
		expect(screen.queryByText(laterEvent.name)).not.toBeInTheDocument();
	});

	it("shows the next event only in its featured card on the full events page", () => {
		render(<EventsView events={[laterEvent, nextEvent]} />);

		expect(screen.getAllByText(nextEvent.name)).toHaveLength(1);
		expect(screen.getByRole("heading", { name: nextEvent.name })).toBeInTheDocument();
		const upcoming = within(eventSection("Upcoming events"));
		expect(upcoming.queryByText(nextEvent.name)).not.toBeInTheDocument();
		expect(upcoming.getByText(laterEvent.name)).toBeInTheDocument();
	});

	it("shows past events most recent first on the full events page", () => {
		render(<EventsView events={[olderEvent, laterEvent, recentEvent]} />);

		const titles = within(eventSection("Past events"))
			.getAllByRole("article")
			.map((article) => article.querySelector("strong")?.textContent);
		expect(titles).toEqual([recentEvent.name, olderEvent.name]);
	});

	it("keeps the most recent past event when applying the compact limit", () => {
		render(<EventsView events={[olderEvent, recentEvent]} compact limit={1} />);

		expect(
			within(eventSection("Past events")).getByText(recentEvent.name),
		).toBeInTheDocument();
		expect(screen.queryByText(olderEvent.name)).not.toBeInTheDocument();
	});
});

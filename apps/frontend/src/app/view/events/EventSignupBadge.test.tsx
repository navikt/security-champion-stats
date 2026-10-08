import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import type { SecurityEvent } from "@/app/utils/Variables";
import { EventSignupBadge } from "./EventSignupBadge";
import { NextEventCard } from "./NextEventCard";
import { EventListItem } from "./EventListItem";
import { OverviewEvents } from "./OverviewEvents";

afterEach(cleanup);

const event: SecurityEvent = {
	id: "synthetic-delta",
	name: "Synthetic meetup",
	description: "",
	startDate: "2099-10-20T08:00:00Z",
	endDate: "2099-10-20T09:00:00Z",
	location: "",
	type: "meetup",
	externalEvent: false,
	deltaEvent: true,
	signupSupported: true,
	signupStatus: "SIGNED_UP",
	signupCheckedAt: "2026-10-08T08:00:00Z",
};

describe("Event signup status", () => {
	it.each([
		["SIGNED_UP", "Signed up"],
		["NOT_SIGNED_UP", "Not signed up"],
		["HOST", "Hosting"],
		["UNAVAILABLE", "Signup status unavailable"],
	] as const)(
		"shows a text label for %s without relying on color",
		(status, label) => {
			render(<EventSignupBadge event={{ ...event, signupStatus: status }} />);
			expect(screen.getByText(label)).toBeInTheDocument();
			if (status === "SIGNED_UP") {
				expect(screen.getByText(label)).toHaveAttribute(
					"data-color",
					"success",
				);
			}
		},
	);

	it("shows when Delta was checked and no status for non Delta or unpersonalized events", () => {
		const { rerender } = render(<EventSignupBadge event={event} />);
		expect(screen.getByText("Signed up")).toHaveAttribute(
			"title",
			expect.stringContaining("Checked in Delta:"),
		);
		rerender(
			<EventSignupBadge
				event={{ ...event, deltaEvent: false, signupStatus: undefined }}
			/>,
		);
		expect(screen.queryByText(/signed up/i)).not.toBeInTheDocument();
	});

	it.each(["featured", "list", "overview"])(
		"wires signup status into the %s event view",
		(view) => {
			if (view === "featured") render(<NextEventCard event={event} />);
			if (view === "list") render(<EventListItem event={event} />);
			if (view === "overview")
				render(
					<OverviewEvents
						events={[event]}
						seasonStartsOn={null}
						loading={false}
					/>,
				);
			expect(screen.getByText("Signed up")).toBeInTheDocument();
		},
	);
});

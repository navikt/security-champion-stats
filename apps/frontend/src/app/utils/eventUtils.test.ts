import { describe, expect, it } from "vitest";
import { SecurityEvent } from "./Variables";
import {
	formatEventDate,
	getNextEvent,
	getPastEvents,
	getUpcomingEvents,
} from "./eventUtils";

const event: SecurityEvent = {
	id: "playbook:course",
	name: "Course",
	description: "Alle",
	startDate: "2026-10-20",
	endDate: "2026-10-22",
	location: "",
	type: "event",
	externalEvent: false,
	deltaEvent: false,
	allDay: true,
};

describe("date-only events", () => {
	it("keeps multi-day events upcoming throughout the inclusive final Oslo date", () => {
		const now = new Date("2026-10-22T21:59:59Z");
		expect(getUpcomingEvents([event], now)).toEqual([event]);
		expect(getPastEvents([event], now)).toEqual([]);
		expect(getNextEvent([event], now)).toEqual(event);
	});

	it("moves events to the past at midnight in Oslo rather than UTC", () => {
		const now = new Date("2026-10-22T22:00:00Z");
		expect(getUpcomingEvents([event], now)).toEqual([]);
		expect(getPastEvents([event], now)).toEqual([event]);
	});

	it("handles single-day events and winter Oslo offsets", () => {
		const winter = { ...event, startDate: "2026-11-25", endDate: "2026-11-25" };
		expect(
			getUpcomingEvents([winter], new Date("2026-11-25T22:59:59Z")),
		).toEqual([winter]);
		expect(getPastEvents([winter], new Date("2026-11-25T23:00:00Z"))).toEqual([
			winter,
		]);
		expect(formatEventDate(winter)).toBe("25.11.2026");
	});

	it("preserves timed event classification", () => {
		const timed = {
			...event,
			allDay: false,
			startDate: "2026-10-20T08:00:00Z",
			endDate: "2026-10-20T09:00:00Z",
		};
		expect(
			getUpcomingEvents([timed], new Date("2026-10-20T07:00:00Z")),
		).toEqual([timed]);
		expect(getPastEvents([timed], new Date("2026-10-20T10:00:00Z"))).toEqual([
			timed,
		]);
	});

	it("features own events ahead of date-only feed entries starting on the same Oslo date", () => {
		const own = {
			...event,
			id: "own",
			allDay: false,
			deltaEvent: true,
			startDate: "2026-10-19T22:30:00Z",
			endDate: "2026-10-20T01:00:00Z",
		};
		expect(
			getUpcomingEvents([event, own], new Date("2026-10-19T20:00:00Z")),
		).toEqual([own, event]);
		expect(
			getNextEvent([event, own], new Date("2026-10-19T20:00:00Z")),
		).toEqual(own);
		expect(
			getPastEvents([event, own], new Date("2026-10-23T12:00:00Z")),
		).toEqual([own, event]);
	});
});

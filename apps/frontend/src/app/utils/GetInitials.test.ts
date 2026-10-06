import { describe, expect, it } from "vitest";
import { getInitials } from "./GetInitials";

describe("getInitials", () => {
	it.each([
		["Ada Lovelace", "AL"],
		["  Ada   Maria Lovelace  ", "AL"],
		["Lovelace, Ada", "AL"],
		["  Lovelace ,  Ada Maria  ", "AL"],
		["van Lovelace, Ada Maria", "AL"],
		["Ada", "A"],
		["", ""],
		["   ", ""],
	])("returns initials for %j", (name, initials) => {
		expect(getInitials(name)).toBe(initials);
	});
});

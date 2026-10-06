import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { Apies } from "./Apies";
import { notifyMembershipChanged, useMe } from "./UseMe";

afterEach(() => vi.restoreAllMocks());

it("updates independent layout and page consumers without reloading", async () => {
	const employee = {
		username: "user@nav.no", displayName: null, isAdmin: false,
		isParticipant: false, isActive: false,
	};
	vi.spyOn(Apies, "validatePerson").mockResolvedValue(employee);
	const layout = renderHook(() => useMe());
	const page = renderHook(() => useMe());
	await waitFor(() => expect(layout.result.current.loading).toBe(false));
	await waitFor(() => expect(page.result.current.loading).toBe(false));

	act(() => notifyMembershipChanged({ ...employee, isParticipant: true, isActive: true }));
	expect(layout.result.current.me.isParticipant).toBe(true);
	expect(page.result.current.me.isActive).toBe(true);
	act(() => notifyMembershipChanged({ ...employee, isParticipant: true, isActive: false }));
	expect(layout.result.current.me.isActive).toBe(false);
});

it("does not overwrite membership changes with an older validation response", async () => {
	const employee = {
		username: "user@nav.no", displayName: null, isAdmin: false,
		isParticipant: false, isActive: false,
	};
	let resolve!: (me: typeof employee) => void;
	vi.spyOn(Apies, "validatePerson").mockReturnValue(new Promise((done) => { resolve = done; }));
	const layout = renderHook(() => useMe());
	act(() => notifyMembershipChanged({ ...employee, isParticipant: true, isActive: true }));
	await act(async () => resolve(employee));
	expect(layout.result.current.me.isParticipant).toBe(true);
});

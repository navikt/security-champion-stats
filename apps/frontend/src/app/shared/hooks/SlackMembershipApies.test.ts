import { afterEach, describe, expect, it, vi } from "vitest";
import { Apies } from "./Apies";

afterEach(() => vi.unstubAllGlobals());

describe("Slack membership API client", () => {
	it.each([
		[
			"",
			Apies.getSlackMembershipConfiguration,
			{ enabled: false, dryRun: true },
		],
		[
			"/preview",
			Apies.getSlackMembershipPreview,
			{
				activeParticipants: 128,
				addedUserIds: [],
				removedUserIds: [],
				unresolvedParticipantIds: [],
			},
		],
		["/announcements", Apies.getSlackMembershipAnnouncements, []],
	] as const)(
		"reads membership%s through the frontend API",
		async (suffix, operation, result) => {
			const fetch = vi.fn().mockResolvedValue(Response.json(result));
			vi.stubGlobal("fetch", fetch);

			expect(await operation()).toEqual(result);
			expect(fetch).toHaveBeenCalledWith(
				`/api/admin/slack/membership${suffix}`,
			);
		},
	);

	it("queues sync through the frontend without reading a bodyless acceptance response", async () => {
		const fetch = vi
			.fn()
			.mockResolvedValue(new Response(null, { status: 202 }));
		vi.stubGlobal("fetch", fetch);

		expect(await Apies.triggerSlackMembershipSync()).toBe(202);
		expect(fetch).toHaveBeenCalledWith("/api/admin/slack/membership/sync", {
			method: "POST",
		});
	});

	it("sends explicit delivery resolution as JSON", async () => {
		const fetch = vi
			.fn()
			.mockResolvedValue(new Response(null, { status: 204 }));
		vi.stubGlobal("fetch", fetch);

		expect(
			await Apies.resolveSlackMembershipDelivery("delivery/id", false),
		).toBe(204);
		expect(fetch).toHaveBeenCalledWith(
			"/api/admin/slack/membership/announcements/delivery%2Fid/resolve",
			{
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: '{"retry":false}',
			},
		);
	});

	it("failed preview throws instead of returning a successful empty snapshot", async () => {
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(new Response(null, { status: 500 })),
		);

		await expect(Apies.getSlackMembershipPreview()).rejects.toThrow(
			"couldn't preview",
		);
	});
});

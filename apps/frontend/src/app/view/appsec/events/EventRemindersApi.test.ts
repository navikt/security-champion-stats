// @vitest-environment node
import { afterEach, describe, expect, it, vi } from "vitest";
import { previewEventReminders, sendEventReminders } from "./EventRemindersApi";

afterEach(() => vi.unstubAllGlobals());

describe("Event reminder API", () => {
	it("fetches an uncached preview and sends only the reviewed version with confirmation", async () => {
		const fetch = vi
			.fn()
			.mockResolvedValueOnce(Response.json({ version: "reviewed" }))
			.mockResolvedValueOnce(new Response(null, { status: 202 }));
		vi.stubGlobal("fetch", fetch);
		expect(await previewEventReminders("external:delta")).toEqual({
			version: "reviewed",
		});
		await sendEventReminders("external:delta", "reviewed");
		expect(fetch).toHaveBeenNthCalledWith(
			1,
			"/api/admin/events/external%3Adelta/reminders",
			{ cache: "no-store" },
		);
		expect(fetch).toHaveBeenNthCalledWith(
			2,
			"/api/admin/events/external%3Adelta/reminders",
			{
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ expectedVersion: "reviewed", confirmed: true }),
			},
		);
	});

	it.each([409, 503, 500])(
		"rejects status %i without exposing response details",
		async (status) => {
			vi.stubGlobal(
				"fetch",
				vi
					.fn()
					.mockResolvedValue(
						Response.json({ detail: "Private details" }, { status }),
					),
			);
			await expect(sendEventReminders("event", "reviewed")).rejects.not.toThrow(
				"Private details",
			);
			await expect(previewEventReminders("event")).rejects.not.toThrow(
				"Private details",
			);
		},
	);
});

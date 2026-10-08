// @vitest-environment node
import { afterEach, describe, expect, it, vi } from "vitest";
import { previewEventReminders, sendEventReminders } from "./EventRemindersApi";

afterEach(() => vi.unstubAllGlobals());

describe("Event reminder API", () => {
	it("fetches an uncached preview and sends the edited message with the reviewed version and confirmation", async () => {
		const fetch = vi
			.fn()
			.mockResolvedValueOnce(Response.json({ version: "reviewed" }))
			.mockResolvedValueOnce(new Response(null, { status: 202 }));
		vi.stubGlobal("fetch", fetch);
		expect(await previewEventReminders("external:delta")).toEqual({
			version: "reviewed",
		});
		await sendEventReminders(
			"external:delta",
			"reviewed",
			"Edited reminder\nSign up!",
		);
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
				body: JSON.stringify({
					expectedVersion: "reviewed",
					message: "Edited reminder\nSign up!",
					confirmed: true,
				}),
			},
		);
	});

	it.each([400, 409, 503, 500])(
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
			await expect(
				sendEventReminders("event", "reviewed", "Reminder"),
			).rejects.not.toThrow("Private details");
			await expect(previewEventReminders("event")).rejects.not.toThrow(
				"Private details",
			);
		},
	);
});

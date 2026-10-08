// @vitest-environment node
import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { GET, POST } from "./route";

vi.mock("@/app/utils/Validation", () => ({
	getBackendToken: vi.fn().mockResolvedValue("synthetic-token"),
	getServerEnv: vi
		.fn()
		.mockReturnValue({ backendUrl: "https://backend.invalid" }),
}));

afterEach(() => {
	vi.unstubAllGlobals();
	vi.restoreAllMocks();
});

describe("Event reminder proxy", () => {
	it("forwards preview data and prevents caching participant rosters", async () => {
		const fetch = vi.fn().mockResolvedValue(
			Response.json(
				{ version: "reviewed" },
				{
					headers: { "Cache-Control": "no-store" },
				},
			),
		);
		vi.stubGlobal("fetch", fetch);
		const response = await GET(
			new NextRequest(
				"https://frontend.invalid/api/admin/events/event/reminders",
			),
			{
				params: Promise.resolve({ eventId: "external:delta" }),
			},
		);
		expect(response.headers.get("cache-control")).toBe("no-store");
		expect(await response.json()).toEqual({ version: "reviewed" });
		expect(fetch).toHaveBeenCalledWith(
			"https://backend.invalid/api/admin/events/external%3Adelta/reminders",
			expect.objectContaining({ method: "GET", cache: "no-store" }),
		);
	});

	it.each([202, 409, 503])(
		"preserves reminder status %i and the reviewed confirmation body",
		async (status) => {
			const body =
				status === 202 ? null : JSON.stringify({ detail: "Synthetic problem" });
			const fetch = vi.fn().mockResolvedValue(
				new Response(body, {
					status,
					headers:
						status === 202
							? {}
							: { "Content-Type": "application/problem+json" },
				}),
			);
			vi.stubGlobal("fetch", fetch);
			const request = new NextRequest(
				"https://frontend.invalid/api/admin/events/event/reminders",
				{
					method: "POST",
					headers: { "Content-Type": "application/json" },
					body: JSON.stringify({
						expectedVersion: "reviewed",
						confirmed: true,
					}),
				},
			);
			const response = await POST(request, {
				params: Promise.resolve({ eventId: "event" }),
			});
			expect(response.status).toBe(status);
			if (status !== 202)
				expect(response.headers.get("content-type")).toBe(
					"application/problem+json",
				);
			expect(fetch).toHaveBeenCalledWith(
				"https://backend.invalid/api/admin/events/event/reminders",
				expect.objectContaining({
					method: "POST",
					body: JSON.stringify({
						expectedVersion: "reviewed",
						confirmed: true,
					}),
				}),
			);
		},
	);
});

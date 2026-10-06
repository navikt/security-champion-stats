// @vitest-environment node
import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { POST } from "./route";

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

describe("event creation proxy", () => {
	it.each([201, 400, 409, 503])(
		"should preserve backend status %i and its JSON response",
		async (status) => {
			const body =
				status === 201
					? { id: "saved-event" }
					: { detail: "Synthetic rejection" };
			const contentType =
				status === 201 ? "application/json" : "application/problem+json";
			const fetch = vi.fn().mockResolvedValue(
				new Response(JSON.stringify(body), {
					status,
					headers: { "Content-Type": contentType },
				}),
			);
			vi.stubGlobal("fetch", fetch);
			const request = new NextRequest("https://frontend.invalid/api/events", {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ name: "Synthetic event" }),
			});

			const response = await POST(request);

			expect(response.status).toBe(status);
			expect(response.headers.get("content-type")).toBe(contentType);
			expect(await response.json()).toEqual(body);
			expect(fetch).toHaveBeenCalledWith(
				"https://backend.invalid/api/admin/events",
				expect.objectContaining({
					method: "POST",
					body: JSON.stringify({ name: "Synthetic event" }),
				}),
			);
		},
	);

	it("should not report failure after the backend successfully saves an event", async () => {
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(
				new Response("Event was added", {
					status: 200,
					headers: { "Content-Type": "text/plain" },
				}),
			),
		);
		vi.spyOn(console, "error").mockImplementation(() => {});
		const request = new NextRequest("https://frontend.invalid/api/events", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ name: "Synthetic event" }),
		});

		const response = await POST(request);

		expect(response.status).toBe(200);
		expect(await response.text()).toBe("Event was added");
	});
});

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

describe("admin member proxy", () => {
	it("transforms the frontend request and forwards the backend response", async () => {
		const fetch = vi.fn().mockResolvedValue(
			new Response("User was created", {
				status: 201,
				headers: { "Content-Type": "text/plain" },
			}),
		);
		vi.stubGlobal("fetch", fetch);
		const request = new NextRequest("https://frontend.invalid/api/admin/member", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ email: "synthetic.user@nav.no", ignored: "value" }),
		});

		const response = await POST(request);

		expect(response.status).toBe(201);
		expect(response.headers.get("content-type")).toBe("text/plain");
		expect(await response.text()).toBe("User was created");
		expect(fetch).toHaveBeenCalledWith(
			"https://backend.invalid/api/admin/member",
			expect.objectContaining({ body: JSON.stringify({ email: "synthetic.user@nav.no" }) }),
		);
	});

	it("returns Problem Details for malformed local request JSON", async () => {
		const fetch = vi.fn();
		vi.stubGlobal("fetch", fetch);
		const request = new NextRequest("https://frontend.invalid/api/admin/member", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: "{",
		});

		const response = await POST(request);

		expect(response.status).toBe(400);
		expect(response.headers.get("content-type")).toBe("application/problem+json");
		expect(await response.json()).toMatchObject({
			title: "Invalid request",
			status: 400,
			detail: "The request body must be valid JSON",
		});
		expect(fetch).not.toHaveBeenCalled();
	});
});

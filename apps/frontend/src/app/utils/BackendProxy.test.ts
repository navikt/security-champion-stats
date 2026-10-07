// @vitest-environment node
import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { proxyBackendRequest } from "./BackendProxy";
import { getBackendToken, getServerEnv } from "./Validation";
import { AUTHENTICATED_FAILED } from "./Variables";

vi.mock("./Validation", () => ({
	getBackendToken: vi.fn(),
	getServerEnv: vi.fn(),
}));

beforeEach(() => {
	vi.mocked(getBackendToken).mockResolvedValue("synthetic-token");
	vi.mocked(getServerEnv).mockReturnValue({
		backendUrl: "https://backend.invalid",
		backendScope: "synthetic-scope",
	});
});

afterEach(() => {
	vi.unstubAllGlobals();
	vi.restoreAllMocks();
});

describe("backend proxy", () => {
	it("forwards backend status, body, and content type", async () => {
		const problem = {
			type: "about:blank",
			title: "Conflict",
			status: 409,
			detail: "Synthetic conflict",
		};
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(
				Response.json(problem, {
					status: 409,
					headers: { "Content-Type": "application/problem+json" },
				}),
			),
		);

		const response = await proxyBackendRequest(
			new NextRequest("https://frontend.invalid/api/members?bypassCache=true"),
			"/api/members?bypassCache=true",
		);

		expect(response.status).toBe(409);
		expect(response.headers.get("content-type")).toBe("application/problem+json");
		expect(await response.json()).toEqual(problem);
	});

	it("returns a Problem Details response when authentication fails locally", async () => {
		vi.mocked(getBackendToken).mockResolvedValue(AUTHENTICATED_FAILED);
		const fetch = vi.fn();
		vi.stubGlobal("fetch", fetch);

		const response = await proxyBackendRequest(
			new NextRequest("https://frontend.invalid/api/membership"),
			"/api/membership",
		);

		expect(response.status).toBe(401);
		expect(response.headers.get("content-type")).toBe("application/problem+json");
		expect(await response.json()).toMatchObject({
			type: "about:blank",
			title: "Unauthorized",
			status: 401,
			instance: "/api/membership",
		});
		expect(fetch).not.toHaveBeenCalled();
	});

	it("preserves bodyless backend success responses", async () => {
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 204 })));

		const response = await proxyBackendRequest(
			new NextRequest("https://frontend.invalid/api/leave", { method: "POST" }),
			"/api/leave",
		);

		expect(response.status).toBe(204);
		expect(await response.text()).toBe("");
	});

	it("returns a sanitized Problem Details response when the backend is unreachable", async () => {
		vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("Synthetic network detail")));
		const logError = vi.spyOn(console, "error").mockImplementation(() => {});

		const response = await proxyBackendRequest(
			new NextRequest("https://frontend.invalid/api/events"),
			"/api/events/%s?bypassCache=true",
		);

		expect(logError).toHaveBeenCalledWith("Failed to proxy backend request", {
			path: "/api/events/%s",
			error: expect.any(Error),
		});
		expect(response.status).toBe(500);
		expect(response.headers.get("content-type")).toBe("application/problem+json");
		expect(await response.json()).toMatchObject({
			title: "Internal server error",
			status: 500,
			detail: "The request could not be completed",
		});
	});
});

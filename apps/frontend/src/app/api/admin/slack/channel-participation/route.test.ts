// @vitest-environment node
import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AUTHENTICATED_FAILED } from "@/app/utils/Variables";
import { getBackendToken, getServerEnv } from "@/app/utils/Validation";
import { GET as overview } from "./route";
import { POST as check } from "./check/route";

vi.mock("@/app/utils/Validation", () => ({
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
	vi.restoreAllMocks();
	vi.unstubAllGlobals();
});

const routes = [
	{ path: "", method: "GET", handler: overview },
	{ path: "/check", method: "POST", handler: check },
];

describe("channel participation proxies", () => {
	it.each(routes)(
		"forwards $method $path and preserves Problem Details",
		async ({ path, method, handler }) => {
			const problem = JSON.stringify({ title: "Conflict", status: 409 });
			const fetch = vi.fn().mockResolvedValue(
				new Response(problem, {
					status: 409,
					headers: { "Content-Type": "application/problem+json" },
				}),
			);
			vi.stubGlobal("fetch", fetch);
			const request = new NextRequest(
				`https://frontend.invalid/api/admin/slack/channel-participation${path}`,
				{ method },
			);

			const response = await handler(request);

			expect(fetch).toHaveBeenCalledWith(
				`https://backend.invalid/api/admin/slack/channel-participation${path}`,
				expect.objectContaining({ method, cache: "no-store" }),
			);
			expect(response.status).toBe(409);
			expect(response.headers.get("content-type")).toBe("application/problem+json");
			expect(await response.text()).toBe(problem);
		},
	);

	it.each(routes)(
		"rejects unauthenticated $method $path before calling the backend",
		async ({ path, method, handler }) => {
			vi.mocked(getBackendToken).mockResolvedValue(AUTHENTICATED_FAILED);
			const fetch = vi.fn();
			vi.stubGlobal("fetch", fetch);

			const response = await handler(
				new NextRequest(
					`https://frontend.invalid/api/admin/slack/channel-participation${path}`,
					{ method },
				),
			);

			expect(response.status).toBe(401);
			expect(fetch).not.toHaveBeenCalled();
		},
	);
});

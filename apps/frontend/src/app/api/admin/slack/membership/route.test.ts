// @vitest-environment node
import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AUTHENTICATED_FAILED } from "@/app/utils/Variables";
import { getBackendToken, getServerEnv } from "@/app/utils/Validation";
import { GET as configuration } from "./route";
import { GET as preview } from "./preview/route";
import { GET as announcements } from "./announcements/route";
import { POST as sync } from "./sync/route";
import { POST as resolve } from "./announcements/[id]/resolve/route";

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
	{ path: "", method: "GET", handler: configuration },
	{ path: "/preview", method: "GET", handler: preview },
	{ path: "/announcements", method: "GET", handler: announcements },
	{ path: "/sync", method: "POST", handler: sync },
	{
		path: "/announcements/delivery-id/resolve",
		method: "POST",
		handler: (request: NextRequest) =>
			resolve(request, { params: Promise.resolve({ id: "delivery-id" }) }),
	},
];

describe("authenticated membership proxies", () => {
	it.each(routes)(
		"forwards $method $path with the exchanged token and preserves Problem Details",
		async ({ path, method, handler }) => {
			const body = JSON.stringify({ retry: true });
			const problem = JSON.stringify({
				title: "Conflict",
				status: 409,
				detail: "Synthetic conflict",
			});
			const fetch = vi.fn().mockResolvedValue(
				new Response(problem, {
					status: 409,
					headers: { "Content-Type": "application/problem+json" },
				}),
			);
			vi.stubGlobal("fetch", fetch);
			const request = new NextRequest(
				`https://frontend.invalid/api/admin/slack/membership${path}`,
				{
					method,
					...(method === "POST"
						? { body, headers: { "Content-Type": "application/json" } }
						: {}),
				},
			);

			const response = await handler(request);

			expect(getBackendToken).toHaveBeenCalledWith(request);
			expect(fetch).toHaveBeenCalledWith(
				`https://backend.invalid/api/admin/slack/membership${path}`,
				expect.objectContaining({
					method,
					cache: "no-store",
					body: method === "POST" ? body : undefined,
				}),
			);
			const options = fetch.mock.calls[0][1];
			expect(options.headers.get("Authorization")).toBe(
				"Bearer synthetic-token",
			);
			expect(response.status).toBe(409);
			expect(response.headers.get("content-type")).toBe(
				"application/problem+json",
			);
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
					`https://frontend.invalid/api/admin/slack/membership${path}`,
					{ method },
				),
			);

			expect(response.status).toBe(401);
			expect(fetch).not.toHaveBeenCalled();
		},
	);

	it("preserves the bodyless response when delivery resolution succeeds", async () => {
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(new Response(null, { status: 204 })),
		);
		const response = await resolve(
			new NextRequest(
				"https://frontend.invalid/api/admin/slack/membership/announcements/id/resolve",
				{
					method: "POST",
					body: '{"retry":false}',
					headers: { "Content-Type": "application/json" },
				},
			),
			{ params: Promise.resolve({ id: "id" }) },
		);

		expect(response.status).toBe(204);
		expect(await response.text()).toBe("");
	});
});

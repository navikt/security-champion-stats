// @vitest-environment node
import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { getBackendToken } from "@/app/utils/Validation";
import { AUTHENTICATED_FAILED } from "@/app/utils/Variables";
import { GET, PUT } from "./route";
import { POST } from "./preview/route";

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

describe.each([
	{ method: "GET", path: "/api/admin/scoring/configuration", handler: GET },
	{ method: "PUT", path: "/api/admin/scoring/configuration", handler: PUT },
	{
		method: "POST",
		path: "/api/admin/scoring/configuration/preview",
		handler: POST,
	},
])("$method $path", ({ method, path, handler }) => {
	it.each([200, 400, 403, 409, 500])(
		"preserves status %i body and content type",
		async (status) => {
			const contentType =
				status === 200 ? "application/json" : "application/problem+json";
			const body = JSON.stringify({ status, detail: "Synthetic response" });
			const fetch = vi
				.fn()
				.mockResolvedValue(
					new Response(body, {
						status,
						headers: { "Content-Type": contentType },
					}),
				);
			vi.stubGlobal("fetch", fetch);
			const requestBody = JSON.stringify({
				reason: "Balance scoring",
				previewToken: "token",
			});
			const response = await handler(
				new NextRequest(`https://frontend.invalid${path}`, {
					method,
					...(method === "GET"
						? {}
						: {
								body: requestBody,
								headers: { "Content-Type": "application/json" },
							}),
				}),
			);
			expect(response.status).toBe(status);
			expect(await response.text()).toBe(body);
			expect(response.headers.get("content-type")).toBe(contentType);
			expect(fetch).toHaveBeenCalledWith(
				`https://backend.invalid${path}`,
				expect.objectContaining({
					method,
					cache: "no-store",
					body: method === "GET" ? undefined : requestBody,
				}),
			);
		},
	);

	it("does not contact the backend without authentication", async () => {
		vi.mocked(getBackendToken).mockResolvedValueOnce(AUTHENTICATED_FAILED);
		const fetch = vi.fn();
		vi.stubGlobal("fetch", fetch);
		const response = await handler(
			new NextRequest(`https://frontend.invalid${path}`, { method }),
		);
		expect(response.status).toBe(401);
		expect(fetch).not.toHaveBeenCalled();
	});
});

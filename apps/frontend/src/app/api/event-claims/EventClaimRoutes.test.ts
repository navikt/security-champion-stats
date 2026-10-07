// @vitest-environment node
import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { GET, POST } from "./route";
import { PUT } from "./[id]/route";
import { GET as adminGET } from "../admin/event-claims/route";
import { POST as reviewPOST } from "../admin/event-claims/[id]/reviews/route";

vi.mock("@/app/utils/Validation", () => ({
	getBackendToken: vi.fn().mockResolvedValue("synthetic-token"),
	getServerEnv: vi
		.fn()
		.mockReturnValue({ backendUrl: "https://backend.invalid" }),
}));
afterEach(() => vi.unstubAllGlobals());

describe.each([
	{
		path: "/api/event-claims",
		method: "GET",
		invoke: (request: NextRequest) => GET(request),
	},
	{
		path: "/api/event-claims",
		method: "POST",
		invoke: (request: NextRequest) => POST(request),
	},
	{
		path: "/api/event-claims/claim-1",
		method: "PUT",
		invoke: (request: NextRequest) =>
			PUT(request, { params: Promise.resolve({ id: "claim-1" }) }),
	},
	{
		path: "/api/admin/event-claims",
		method: "GET",
		invoke: (request: NextRequest) => adminGET(request),
	},
	{
		path: "/api/admin/event-claims/claim-1/reviews",
		method: "POST",
		invoke: (request: NextRequest) =>
			reviewPOST(request, { params: Promise.resolve({ id: "claim-1" }) }),
	},
])("$method $path", ({ path, method, invoke }) => {
	it.each([200, 403, 409, 500])(
		"preserves backend status, body and content type (%i)",
		async (status) => {
			const body = '{"detail":"Claim response"}';
			const contentType =
				status === 200 ? "application/json" : "application/problem+json";
			const fetch = vi
				.fn()
				.mockResolvedValue(
					new Response(body, {
						status,
						headers: { "Content-Type": contentType },
					}),
				);
			vi.stubGlobal("fetch", fetch);
			const response = await invoke(
				new NextRequest(`https://frontend.invalid${path}`, {
					method,
					...(method !== "GET"
						? {
								body: '{"reason":"Verified"}',
								headers: { "Content-Type": "application/json" },
							}
						: {}),
				}),
			);
			expect(response.status).toBe(status);
			expect(await response.text()).toBe(body);
			expect(response.headers.get("Content-Type")).toBe(contentType);
			expect(fetch).toHaveBeenCalledWith(
				`https://backend.invalid${path}`,
				expect.objectContaining({ method }),
			);
		},
	);
});

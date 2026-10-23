// @vitest-environment node
import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { POST as act } from "../admin/feedback/[id]/actions/route";
import { POST as markRead } from "../admin/feedback/[id]/read/route";
import { GET as detail } from "../admin/feedback/[id]/route";
import { GET as search } from "../admin/feedback/route";
import { POST } from "./route";

vi.mock("@/app/utils/Validation", () => ({
	getBackendToken: vi.fn().mockResolvedValue("synthetic-token"),
	getServerEnv: vi
		.fn()
		.mockReturnValue({ backendUrl: "https://backend.invalid" }),
}));
afterEach(() => vi.unstubAllGlobals());

describe.each([
	{
		path: "/api/feedback",
		method: "POST",
		invoke: (request: NextRequest) => POST(request),
	},
	{
		path: "/api/admin/feedback?q=menu&owner=mine&cursor=next",
		method: "GET",
		invoke: (request: NextRequest) => search(request),
	},
	{
		path: "/api/admin/feedback/id-1",
		method: "GET",
		invoke: (request: NextRequest) =>
			detail(request, { params: Promise.resolve({ id: "id-1" }) }),
	},
	{
		path: "/api/admin/feedback/id-1/actions",
		method: "POST",
		invoke: (request: NextRequest) =>
			act(request, { params: Promise.resolve({ id: "id-1" }) }),
	},
	{
		path: "/api/admin/feedback/id-1/read",
		method: "POST",
		invoke: (request: NextRequest) =>
			markRead(request, { params: Promise.resolve({ id: "id-1" }) }),
	},
])("$method $path", ({ path, method, invoke }) => {
	it.each([201, 403, 409, 500])(
		"preserves the backend response (%i)",
		async (status) => {
			const body = '{"detail":"Feedback response"}';
			const contentType =
				status === 201 ? "application/json" : "application/problem+json";
			const fetch = vi.fn().mockResolvedValue(
				new Response(body, {
					status,
					headers: { "Content-Type": contentType },
				}),
			);
			vi.stubGlobal("fetch", fetch);
			const response = await invoke(
				new NextRequest(`https://frontend.invalid${path}`, {
					method,
					...(method === "POST"
						? {
								body: '{"action":"CLAIM","expectedVersion":1}',
								headers: { "Content-Type": "application/json" },
							}
						: {}),
				}),
			);
			expect(response.status).toBe(status);
			expect(response.headers.get("Content-Type")).toBe(contentType);
			expect(await response.text()).toBe(body);
			expect(fetch).toHaveBeenCalledWith(
				`https://backend.invalid${path}`,
				expect.objectContaining({
					method,
					...(method === "POST"
						? { body: '{"action":"CLAIM","expectedVersion":1}' }
						: {}),
				}),
			);
		},
	);
});

// @vitest-environment node
import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { getBackendToken } from "@/app/utils/Validation";
import { AUTHENTICATED_FAILED } from "@/app/utils/Variables";
import { GET as getHistory } from "./route";
import { GET as getAudit } from "../admin/audit/route";
import { POST as leave } from "../leave/route";

vi.mock("@/app/utils/Validation", () => ({
	getBackendToken: vi.fn().mockResolvedValue("synthetic-token"),
	getServerEnv: vi.fn().mockReturnValue({ backendUrl: "https://backend.invalid" }),
}));

afterEach(() => {
	vi.unstubAllGlobals();
	vi.restoreAllMocks();
});

describe("History proxies", () => {
	it("preserves admin search and pagination parameters", async () => {
		const fetch = vi.fn().mockResolvedValue(Response.json({ entries: [], nextCursor: null }));
		vi.stubGlobal("fetch", fetch);

		await getAudit(new NextRequest("https://frontend.invalid/api/admin/audit?q=SLACK&page=12&size=50"));

		expect(fetch).toHaveBeenCalledWith(
			"https://backend.invalid/api/admin/audit?q=SLACK&page=12&size=50",
			expect.objectContaining({ method: "GET", cache: "no-store" }),
		);
	});

	it("does not forward participant identity or search overrides on personal history", async () => {
		const fetch = vi.fn().mockResolvedValue(Response.json({ entries: [], nextCursor: null }));
		vi.stubGlobal("fetch", fetch);

		await getHistory(
			new NextRequest("https://frontend.invalid/api/history?participantId=other&search=other&cursor=12&limit=50"),
		);

		expect(fetch).toHaveBeenCalledWith(
			"https://backend.invalid/api/history",
			expect.objectContaining({ method: "GET" }),
		);
	});

	it("does not query history when authentication fails", async () => {
		vi.mocked(getBackendToken).mockResolvedValueOnce(AUTHENTICATED_FAILED);
		const fetch = vi.fn();
		vi.stubGlobal("fetch", fetch);

		const response = await getHistory(new NextRequest("https://frontend.invalid/api/history"));

		expect(response.status).toBe(401);
		expect(fetch).not.toHaveBeenCalled();
	});

	it.each([403, 404, 500])("preserves history failure status %i", async (status) => {
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status })));

		expect((await getHistory(new NextRequest("https://frontend.invalid/api/history"))).status).toBe(status);
	});

	it("proxies voluntary departure and preserves an empty success response", async () => {
		const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
		vi.stubGlobal("fetch", fetch);

		const response = await leave(new NextRequest("https://frontend.invalid/api/leave", { method: "POST" }));

		expect(response.status).toBe(204);
		expect(fetch).toHaveBeenCalledWith(
			"https://backend.invalid/api/leave",
			expect.objectContaining({ method: "POST", cache: "no-store" }),
		);
	});
});

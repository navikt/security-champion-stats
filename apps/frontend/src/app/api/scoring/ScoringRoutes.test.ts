// @vitest-environment node
import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { getBackendToken } from "@/app/utils/Validation";
import { AUTHENTICATED_FAILED } from "@/app/utils/Variables";
import { GET as getOwnScore } from "./me/route";
import { GET as getLeaderboard } from "../leaderboard/route";

vi.mock("@/app/utils/Validation", () => ({
	getBackendToken: vi.fn().mockResolvedValue("synthetic-token"),
	getServerEnv: vi.fn().mockReturnValue({ backendUrl: "https://backend.invalid" }),
}));

afterEach(() => {
	vi.unstubAllGlobals();
	vi.restoreAllMocks();
});

describe.each([
	{
		path: "/api/scoring/me",
		get: getOwnScore,
		body: {
			season: {
				id: "season-1",
				startsOn: "2026-01-01",
				endsOn: null,
				nextResetDate: "2027-01-01",
			},
			points: 0,
			level: "Novice",
			rank: null,
		},
	},
	{ path: "/api/leaderboard", get: getLeaderboard, body: [] },
])("$path proxy", ({ path, get, body }) => {
	it("returns zero-score or empty-season data without a loading error", async () => {
		const fetch = vi.fn().mockResolvedValue(Response.json(body));
		vi.stubGlobal("fetch", fetch);

		const response = await get(new NextRequest(`https://frontend.invalid${path}`));

		expect(response.status).toBe(200);
		expect(await response.json()).toEqual(body);
		expect(fetch).toHaveBeenCalledWith(
			`https://backend.invalid${path}`,
			expect.objectContaining({ method: "GET", cache: "no-store" }),
		);
	});

	it.each([403, 404, 500])("preserves backend failure status %i", async (status) => {
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status })));

		const response = await get(new NextRequest(`https://frontend.invalid${path}`));

		expect(response.status).toBe(status);
	});

	it("does not fetch backend data when authentication fails", async () => {
		vi.mocked(getBackendToken).mockResolvedValueOnce(AUTHENTICATED_FAILED);
		const fetch = vi.fn();
		vi.stubGlobal("fetch", fetch);

		const response = await get(new NextRequest(`https://frontend.invalid${path}`));

		expect(response.status).toBe(401);
		expect(fetch).not.toHaveBeenCalled();
	});
});

// @vitest-environment node
import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { getBackendToken } from "@/app/utils/Validation";
import { AUTHENTICATED_FAILED } from "@/app/utils/Variables";
import { GET } from "./route";
import { Apies } from "@/app/shared/hooks/Apies";

vi.mock("@/app/utils/Validation", () => ({
	getBackendToken: vi.fn().mockResolvedValue("synthetic-token"),
	getServerEnv: vi
		.fn()
		.mockReturnValue({ backendUrl: "https://backend.invalid" }),
}));

const request = new NextRequest(
	"https://frontend.invalid/api/admin/scoring/participants/participant-1/history",
);
const context = { params: Promise.resolve({ id: "participant-1" }) };

afterEach(() => {
	vi.unstubAllGlobals();
	vi.restoreAllMocks();
});

describe("Participant scoring history proxy", () => {
	it("forwards the participant path and preserves the backend body", async () => {
		const body = { currentSeasonId: "season-1", seasons: [], entries: [] };
		const fetch = vi.fn().mockResolvedValue(Response.json(body));
		vi.stubGlobal("fetch", fetch);
		const response = await GET(request, context);
		expect(response.status).toBe(200);
		expect(await response.json()).toEqual(body);
		expect(response.headers.get("content-type")).toContain("application/json");
		expect(fetch).toHaveBeenCalledWith(
			"https://backend.invalid/api/admin/scoring/participants/participant-1/history",
			expect.objectContaining({ method: "GET", cache: "no-store" }),
		);
	});

	it.each([403, 404, 500])(
		"preserves backend problem details and status %i",
		async (status) => {
			const body = JSON.stringify({ status, title: "Unable to read history" });
			vi.stubGlobal(
				"fetch",
				vi.fn().mockResolvedValue(
					new Response(body, {
						status,
						headers: { "Content-Type": "application/problem+json" },
					}),
				),
			);
			const response = await GET(request, context);
			expect(response.status).toBe(status);
			expect(response.headers.get("content-type")).toBe(
				"application/problem+json",
			);
			expect(await response.text()).toBe(body);
		},
	);

	it("does not fetch participant history without authentication", async () => {
		vi.mocked(getBackendToken).mockResolvedValueOnce(AUTHENTICATED_FAILED);
		const fetch = vi.fn();
		vi.stubGlobal("fetch", fetch);
		expect((await GET(request, context)).status).toBe(401);
		expect(fetch).not.toHaveBeenCalled();
	});

	it("encodes the participant ID and surfaces API failures instead of empty history", async () => {
		const body = { currentSeasonId: "season-1", seasons: [], entries: [] };
		const fetch = vi
			.fn()
			.mockResolvedValueOnce(Response.json(body))
			.mockResolvedValueOnce(new Response(null, { status: 403 }));
		vi.stubGlobal("fetch", fetch);
		expect(await Apies.getParticipantScoringHistory("id/with spaces")).toEqual(
			body,
		);
		expect(fetch).toHaveBeenCalledWith(
			"/api/admin/scoring/participants/id%2Fwith%20spaces/history",
		);
		await expect(
			Apies.getParticipantScoringHistory("participant-1"),
		).rejects.toThrow("status: 403");
	});
});

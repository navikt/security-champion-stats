// @vitest-environment node
import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { getBackendToken } from "@/app/utils/Validation";
import { AUTHENTICATED_FAILED } from "@/app/utils/Variables";
import { GET as getMyHistory } from "./me/score-history/route";
import { GET as getMySummary } from "./me/score-summary/route";
import { GET as getParticipantHistory } from "./participants/[id]/score-history/route";
import { GET as getParticipantSummary } from "./participants/[id]/score-summary/route";

vi.mock("@/app/utils/Validation", () => ({
	getBackendToken: vi.fn().mockResolvedValue("synthetic-token"),
	getServerEnv: vi
		.fn()
		.mockReturnValue({ backendUrl: "https://backend.invalid" }),
}));

const participantContext = {
	params: Promise.resolve({ id: "id/with spaces" }),
};

afterEach(() => {
	vi.unstubAllGlobals();
	vi.restoreAllMocks();
});

describe("score history proxies", () => {
	it("forwards self-service endpoints and query parameters", async () => {
		const fetch = vi
			.fn()
			.mockImplementation(() =>
				Promise.resolve(Response.json({ entries: [] })),
			);
		vi.stubGlobal("fetch", fetch);

		await getMySummary(
			new NextRequest(
				"https://frontend.invalid/api/me/score-summary?season=2026",
			),
		);
		await getMyHistory(
			new NextRequest(
				"https://frontend.invalid/api/me/score-history?season=2026&type=credit&cursor=opaque%2Fcursor&limit=25",
			),
		);

		expect(fetch).toHaveBeenNthCalledWith(
			1,
			"https://backend.invalid/api/me/score-summary?season=2026",
			expect.objectContaining({ method: "GET", cache: "no-store" }),
		);
		expect(fetch).toHaveBeenNthCalledWith(
			2,
			"https://backend.invalid/api/me/score-history?season=2026&type=credit&cursor=opaque%2Fcursor&limit=25",
			expect.objectContaining({ method: "GET", cache: "no-store" }),
		);
	});

	it("encodes participant IDs and proxies admin summary and history paths", async () => {
		const fetch = vi
			.fn()
			.mockImplementation(() =>
				Promise.resolve(Response.json({ entries: [] })),
			);
		vi.stubGlobal("fetch", fetch);

		await getParticipantSummary(
			new NextRequest(
				"https://frontend.invalid/api/participants/id%2Fwith%20spaces/score-summary?season=all",
			),
			participantContext,
		);
		await getParticipantHistory(
			new NextRequest(
				"https://frontend.invalid/api/participants/id%2Fwith%20spaces/score-history?type=adjustment",
			),
			participantContext,
		);

		expect(fetch).toHaveBeenNthCalledWith(
			1,
			"https://backend.invalid/api/participants/id%2Fwith%20spaces/score-summary?season=all",
			expect.objectContaining({ method: "GET" }),
		);
		expect(fetch).toHaveBeenNthCalledWith(
			2,
			"https://backend.invalid/api/participants/id%2Fwith%20spaces/score-history?type=adjustment",
			expect.objectContaining({ method: "GET" }),
		);
	});

	it("does not query the backend without authentication", async () => {
		vi.mocked(getBackendToken).mockResolvedValueOnce(AUTHENTICATED_FAILED);
		const fetch = vi.fn();
		vi.stubGlobal("fetch", fetch);

		const response = await getMyHistory(
			new NextRequest("https://frontend.invalid/api/me/score-history"),
		);

		expect(response.status).toBe(401);
		expect(fetch).not.toHaveBeenCalled();
	});

	it("preserves backend error status and problem details", async () => {
		const body = JSON.stringify({ status: 403, title: "Forbidden" });
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(
				new Response(body, {
					status: 403,
					headers: { "Content-Type": "application/problem+json" },
				}),
			),
		);

		const response = await getMySummary(
			new NextRequest("https://frontend.invalid/api/me/score-summary"),
		);

		expect(response.status).toBe(403);
		expect(response.headers.get("content-type")).toBe(
			"application/problem+json",
		);
		expect(await response.text()).toBe(body);
	});
});

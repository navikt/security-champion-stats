// @vitest-environment node
import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { parseAzureUserToken } from "@navikt/oasis";
import { GET } from "./route";

vi.mock("@/app/utils/Validation", () => ({
	getBackendToken: vi.fn().mockResolvedValue("synthetic-token"),
	getServerEnv: vi.fn().mockReturnValue({
		backendUrl: "https://backend.invalid",
		backendScope: "synthetic-scope",
	}),
}));

vi.mock("@navikt/oasis", () => ({
	parseAzureUserToken: vi.fn(),
}));

const user = {
	username: "synthetic.user@nav.no",
	isAdmin: false,
	isParticipant: true,
	isActive: true,
};

beforeEach(() => {
	vi.stubEnv("APPSEC_ID", "synthetic-group");
	vi.mocked(parseAzureUserToken).mockReturnValue({
		ok: true,
		name: "Ada Lovelace",
		preferred_username: user.username,
		groups: ["synthetic-group"],
		NAVident: "S123456",
		oid: "synthetic-user",
	});
	vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json(user)));
});

afterEach(() => {
	vi.unstubAllGlobals();
	vi.unstubAllEnvs();
	vi.clearAllMocks();
});

describe("current user proxy", () => {
	it("returns the authenticated display name without changing identity or roles", async () => {
		const response = await GET(new NextRequest("https://frontend.invalid/api/validate"));

		expect(response.status).toBe(200);
		expect(await response.json()).toEqual({ ...user, displayName: "Ada Lovelace" });
	});

	it("reports an unavailable display name explicitly", async () => {
		vi.mocked(parseAzureUserToken).mockReturnValue({
			ok: true,
			name: " ",
			preferred_username: user.username,
			groups: ["synthetic-group"],
			NAVident: "S123456",
			oid: "synthetic-user",
		});

		const response = await GET(new NextRequest("https://frontend.invalid/api/validate"));

		expect(await response.json()).toEqual({ ...user, displayName: null });
	});

	it("still rejects a mismatched backend identity", async () => {
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({
			...user,
			username: "different.user@nav.no",
		})));

		const response = await GET(new NextRequest("https://frontend.invalid/api/validate"));

		expect(response.status).toBe(401);
	});
});

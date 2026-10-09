import type { NextRequest } from "next/server";
import { proxyBackendRequest } from "@/app/utils/BackendProxy";

export async function GET(
	request: NextRequest,
	ctx: RouteContext<"/api/participants/[id]/score-summary">,
) {
	const { id } = await ctx.params;
	return proxyBackendRequest(
		request,
		`/api/participants/${encodeURIComponent(id)}/score-summary${request.nextUrl.search}`,
	);
}

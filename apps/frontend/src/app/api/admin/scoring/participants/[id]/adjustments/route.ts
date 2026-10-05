import { NextRequest } from "next/server";
import { proxyBackendRequest } from "@/app/utils/BackendProxy";

export async function POST(
	request: NextRequest,
	ctx: RouteContext<"/api/admin/scoring/participants/[id]/adjustments">,
) {
	const { id } = await ctx.params;
	return proxyBackendRequest(
		request,
		`/api/admin/scoring/participants/${encodeURIComponent(id)}/adjustments`,
	);
}

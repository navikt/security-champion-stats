import { NextRequest } from "next/server";
import { proxyBackendRequest } from "@/app/utils/BackendProxy";

export async function GET(
	request: NextRequest,
	ctx: RouteContext<"/api/admin/scoring/participants/[id]/credits">,
) {
	const { id } = await ctx.params;
	return proxyBackendRequest(
		request,
		`/api/admin/scoring/participants/${encodeURIComponent(id)}/credits`,
	);
}

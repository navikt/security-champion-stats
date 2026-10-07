import { NextRequest } from "next/server";
import { proxyBackendRequest } from "@/app/utils/BackendProxy";

export async function PUT(
	request: NextRequest,
	ctx: RouteContext<"/api/admin/participants/[id]/status">,
) {
	const { id } = await ctx.params;
	return proxyBackendRequest(request, `/api/admin/participants/${encodeURIComponent(id)}/status`);
}

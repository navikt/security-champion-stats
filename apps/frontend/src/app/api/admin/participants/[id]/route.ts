import { NextRequest } from "next/server";
import { proxyBackendRequest } from "@/app/utils/BackendProxy";

export async function DELETE(
	request: NextRequest,
	ctx: RouteContext<"/api/admin/participants/[id]">,
) {
	const { id } = await ctx.params;
	return proxyBackendRequest(request, `/api/admin/participants/${encodeURIComponent(id)}`);
}

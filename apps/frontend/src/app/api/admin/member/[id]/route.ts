import { NextRequest } from "next/server";
import { proxyBackendRequest } from "@/app/utils/BackendProxy";

export async function DELETE(
	request: NextRequest,
	ctx: RouteContext<"/api/admin/member/[id]">,
) {
	const { id } = await ctx.params;
	return proxyBackendRequest(request, `/api/admin/member/${encodeURIComponent(id)}`);
}

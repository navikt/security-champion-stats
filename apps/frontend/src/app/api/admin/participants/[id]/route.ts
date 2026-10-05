import { getBackendToken, getServerEnv } from "@/app/utils/Validation";
import { AUTHENTICATED_FAILED, FAILED_FETCH, INTERNAL_ERROR } from "@/app/utils/Variables";
import { NextRequest, NextResponse } from "next/server";

export async function DELETE(
	request: NextRequest,
	ctx: RouteContext<"/api/admin/participants/[id]">,
) {
	const { id } = await ctx.params;
	try {
		const { backendUrl } = getServerEnv();
		const backendToken = await getBackendToken(request);

		if (backendToken === AUTHENTICATED_FAILED) {
			return NextResponse.json({ error: AUTHENTICATED_FAILED }, { status: 401 });
		}

		const response = await fetch(
			`${backendUrl}/api/admin/participants/${encodeURIComponent(id)}`,
			{
				method: "DELETE",
				headers: {
					Authorization: `Bearer ${backendToken}`,
					"Content-Type": "application/json",
				},
				body: JSON.stringify(await request.json()),
			},
		);
		if (!response.ok) {
			return NextResponse.json({ error: FAILED_FETCH }, { status: response.status });
		}
		return new NextResponse(null, { status: response.status });
	} catch (error) {
		console.error("Failed to delete program participant:", error);
		return NextResponse.json({ error: INTERNAL_ERROR }, { status: 500 });
	}
}

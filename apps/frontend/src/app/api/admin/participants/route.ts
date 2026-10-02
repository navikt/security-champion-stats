import { getBackendToken, getServerEnv } from "@/app/utils/Validation";
import { AUTHENTICATED_FAILED, FAILED_FETCH, INTERNAL_ERROR } from "@/app/utils/Variables";
import { NextRequest, NextResponse } from "next/server";

export async function GET(request: NextRequest) {
	try {
		const { backendUrl } = getServerEnv();
		const backendToken = await getBackendToken(request);
		if (backendToken === AUTHENTICATED_FAILED) {
			return NextResponse.json({ error: AUTHENTICATED_FAILED }, { status: 401 });
		}

		const response = await fetch(`${backendUrl}/api/admin/participants`, {
			headers: { Authorization: `Bearer ${backendToken}` },
		});
		if (!response.ok) {
			return NextResponse.json({ error: FAILED_FETCH }, { status: response.status });
		}
		return NextResponse.json(await response.json());
	} catch (error) {
		console.error("Failed to fetch program participants:", error);
		return NextResponse.json({ error: INTERNAL_ERROR }, { status: 500 });
	}
}

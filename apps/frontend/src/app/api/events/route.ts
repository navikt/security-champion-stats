import { NextRequest, NextResponse } from "next/server";
import { getBackendToken, getServerEnv } from "@/app/utils/Validation";
import { AUTHENTICATED_FAILED, INTERNAL_ERROR } from "@/app/utils/Variables";
import { proxyBackendRequest } from "@/app/utils/BackendProxy";

export async function GET(request: NextRequest) {
	try {
		const { backendUrl } = getServerEnv();
		const backendToken = await getBackendToken(request);

		if (backendToken === AUTHENTICATED_FAILED) {
			console.error("Authentication failed when trying to fetch events");
			return NextResponse.json("Authentication failed", { status: 401 });
		}

		const url = `${backendUrl}/api/events`;
		const response = await fetch(url, {
			method: "GET",
			headers: {
				Authorization: `Bearer ${backendToken}`,
				"Content-Type": "application/json",
			},
		});

		if (!response.ok) {
			console.error("Failed to fetch events");
			return NextResponse.json(
				{ error: "Failed fetch data" },
				{ status: response.status },
			);
		}

		const data = await response.json();
		return NextResponse.json(data);
	} catch (error) {
		console.error("Internal server error in events fetching: ", error);
		return NextResponse.json({ error: INTERNAL_ERROR }, { status: 500 });
	}
}

export async function POST(request: NextRequest) {
	return proxyBackendRequest(request, "/api/admin/events");
}

import { NextRequest } from "next/server";
import { problemResponse, proxyBackendRequest } from "@/app/utils/BackendProxy";

export async function POST(request: NextRequest) {
	let body: { email?: string };
	try {
		body = await request.json();
	} catch {
		return problemResponse(
			400,
			"Invalid request",
			"The request body must be valid JSON",
			new URL(request.url).pathname,
		);
	}

	return proxyBackendRequest(request, "/api/admin/member", {
		body: JSON.stringify({ email: body.email }),
	});
}

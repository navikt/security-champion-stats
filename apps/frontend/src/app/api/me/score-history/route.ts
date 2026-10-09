import type { NextRequest } from "next/server";
import { proxyBackendRequest } from "@/app/utils/BackendProxy";

export async function GET(request: NextRequest) {
	return proxyBackendRequest(
		request,
		`/api/me/score-history${request.nextUrl.search}`,
	);
}

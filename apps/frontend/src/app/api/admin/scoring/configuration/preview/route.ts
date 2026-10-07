import { NextRequest } from "next/server";
import { proxyBackendRequest } from "@/app/utils/BackendProxy";

export async function POST(request: NextRequest) {
	return proxyBackendRequest(
		request,
		"/api/admin/scoring/configuration/preview",
	);
}

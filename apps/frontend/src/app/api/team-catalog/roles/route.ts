import type { NextRequest } from "next/server";
import { proxyBackendRequest } from "@/app/utils/BackendProxy";

export async function GET(request: NextRequest) {
	return proxyBackendRequest(request, "/api/team-catalog/roles");
}

export async function PUT(request: NextRequest) {
	return proxyBackendRequest(request, "/api/team-catalog/roles");
}

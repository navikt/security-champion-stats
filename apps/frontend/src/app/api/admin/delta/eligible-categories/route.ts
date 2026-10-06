import { NextRequest } from "next/server";
import { proxyBackendRequest } from "@/app/utils/BackendProxy";

export async function GET(request: NextRequest) {
	return proxyBackendRequest(request, "/api/admin/delta/eligible-categories");
}

export async function POST(request: NextRequest) {
	return proxyBackendRequest(request, "/api/admin/delta/eligible-categories");
}

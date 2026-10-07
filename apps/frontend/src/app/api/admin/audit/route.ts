import { NextRequest } from "next/server";
import { proxyBackendRequest } from "@/app/utils/BackendProxy";

export async function GET(request: NextRequest) {
	const query = new URLSearchParams();
	for (const name of ["q", "category", "page", "size"]) {
		const value = request.nextUrl.searchParams.get(name);
		if (value !== null) query.set(name, value);
	}
	return proxyBackendRequest(request, `/api/admin/audit?${query}`);
}

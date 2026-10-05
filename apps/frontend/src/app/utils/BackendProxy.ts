import { NextRequest, NextResponse } from "next/server";
import { getBackendToken, getServerEnv } from "@/app/utils/Validation";
import {
	AUTHENTICATED_FAILED,
	INTERNAL_ERROR,
} from "@/app/utils/Variables";

export async function proxyBackendRequest(
	request: NextRequest,
	backendPath: string,
): Promise<NextResponse> {
	try {
		const backendToken = await getBackendToken(request);
		if (backendToken === AUTHENTICATED_FAILED) {
			return NextResponse.json(
				{ error: AUTHENTICATED_FAILED },
				{ status: 401 },
			);
		}

		const { backendUrl } = getServerEnv();
		const headers = new Headers({
			Authorization: `Bearer ${backendToken}`,
		});
		const body =
			request.method === "GET" || request.method === "HEAD"
				? undefined
				: await request.text();
		const contentType = request.headers.get("content-type");
		if (body !== undefined && contentType) {
			headers.set("Content-Type", contentType);
		}

		const response = await fetch(`${backendUrl}${backendPath}`, {
			method: request.method,
			headers,
			body,
			cache: "no-store",
		});
		const responseBody = await response.text();
		const responseHeaders = new Headers();
		const responseContentType = response.headers.get("content-type");
		if (responseContentType) {
			responseHeaders.set("Content-Type", responseContentType);
		}
		return new NextResponse(responseBody || null, {
			status: response.status,
			headers: responseHeaders,
		});
	} catch (error) {
		console.error(`Failed to proxy ${backendPath}:`, error);
		return NextResponse.json(
			{ error: INTERNAL_ERROR },
			{ status: 500 },
		);
	}
}

import { NextRequest, NextResponse } from "next/server";
import { getBackendToken, getServerEnv } from "@/app/utils/Validation";
import { AUTHENTICATED_FAILED } from "@/app/utils/Variables";

export type BackendProxyOptions = {
	body?: BodyInit | null;
};

export function problemResponse(
	status: number,
	title: string,
	detail: string,
	instance: string,
): NextResponse {
	return new NextResponse(
		JSON.stringify({
			type: "about:blank",
			title,
			status,
			detail,
			instance,
		}),
		{
			status,
			headers: { "Content-Type": "application/problem+json" },
		},
	);
}

export async function forwardBackendResponse(response: Response): Promise<NextResponse> {
	const responseBody = await response.text();
	const headers = new Headers();
	const contentType = response.headers.get("content-type");
	if (contentType) {
		headers.set("Content-Type", contentType);
	}
	return new NextResponse(responseBody || null, {
		status: response.status,
		headers,
	});
}

export async function proxyBackendRequest(
	request: NextRequest,
	backendPath: string,
	options: BackendProxyOptions = {},
): Promise<NextResponse> {
	try {
		const backendToken = await getBackendToken(request);
		if (backendToken === AUTHENTICATED_FAILED) {
			return problemResponse(
				401,
				"Unauthorized",
				"Authentication is required",
				new URL(request.url).pathname,
			);
		}

		const { backendUrl } = getServerEnv();
		const headers = new Headers({
			Authorization: `Bearer ${backendToken}`,
		});
		const body =
			request.method === "GET" || request.method === "HEAD"
				? undefined
				: options.body === undefined
					? await request.text()
					: options.body;
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
		return forwardBackendResponse(response);
	} catch (error) {
		console.error(`Failed to proxy ${backendPath.split("?")[0]}:`, error);
		return problemResponse(
			500,
			"Internal server error",
			"The request could not be completed",
			new URL(request.url).pathname,
		);
	}
}

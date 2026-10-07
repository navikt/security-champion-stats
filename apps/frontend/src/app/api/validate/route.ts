import { NextRequest, NextResponse } from "next/server";
import { getBackendToken, getServerEnv } from "../../utils/Validation";
import { AUTHENTICATED_FAILED, INTERNAL_ERROR, MISSING_GROUP } from "../../utils/Variables";
import { parseAzureUserToken } from "@navikt/oasis";
import { createLocalParserResult } from "@/app/utils/LocalDevAuth";
import { forwardBackendResponse, problemResponse } from "@/app/utils/BackendProxy";

export async function GET(request: NextRequest) {
	try {
		const token = await getBackendToken(request);
		const id = process.env.APPSEC_ID;
		if (!id) {
			throw new Error("Missing environment variable APPSEC_ID");
		}

		if (token === AUTHENTICATED_FAILED) {
			return problemResponse(
				401,
				"Unauthorized",
				"Authentication is required",
				new URL(request.url).pathname,
			);
		}
		const { backendUrl, backendScope } = getServerEnv();
		const parsedToken = backendScope === "LOCAL" ? null : parseAzureUserToken(token);
		if (parsedToken && !parsedToken.ok) {
			return problemResponse(
				401,
				"Unauthorized",
				"The user token is invalid",
				new URL(request.url).pathname,
			);
		}
		const parse = parsedToken?.ok ? parsedToken : createLocalParserResult();

		const response = await fetch(`${backendUrl}/api/validate`, {
			method: "GET",
			headers: {
				Authorization: `Bearer ${token}`,
				"Content-Type": "application/json",
			},
		});

		if (!response.ok) {
			return forwardBackendResponse(response);
		}

		const backendResponse: { username: string } = await response.json();
		const groups = parse.groups;

		if (!groups) {
			return problemResponse(
				403,
				"Forbidden",
				MISSING_GROUP,
				new URL(request.url).pathname,
			);
		}

		if (backendResponse.username !== parse.preferred_username) {
			return problemResponse(
				401,
				"Unauthorized",
				AUTHENTICATED_FAILED,
				new URL(request.url).pathname,
			);
		}
		return NextResponse.json({
			...backendResponse,
			displayName: parse.name?.trim() || null,
		});
	} catch (error) {
		console.error("Validation error, then validating user," + error);
		return problemResponse(
			500,
			"Internal server error",
			INTERNAL_ERROR,
			new URL(request.url).pathname,
		);
	}
}

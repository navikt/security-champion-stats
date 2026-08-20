import {getBackendToken, getServerEnv} from "@/app/utils/Validation";
import {AUTHENTICATED_FAILED, FAILED_FETCH} from "@/app/utils/Variables";
import {NextRequest, NextResponse} from "next/server";

export async function GET(request: NextRequest) {
    try {
        const { backendUrl } = getServerEnv()
        const backendToken = await getBackendToken(request)

        if (backendToken === AUTHENTICATED_FAILED) {
            return NextResponse.json(
                { error: "Authentication failed, failed to fetch obo-token or token" },
                { status: 401 }
            )
        }

        const url = `${backendUrl}/api/membership`
        const response = await fetch(url, {
            method: 'GET',
            headers: {
                "Content-Type": "application/json",
                Authorization: `Bearer ${backendToken}`,
            }
        })

        if (!response.ok) {
            return NextResponse.json(
                { error: FAILED_FETCH },
                { status: response.status }
            )
        }

        return NextResponse.json(await response.json())

    } catch (error) {
        console.error("Error in /api/membership: ", error)
        return NextResponse.json(
            { error: "Failed to fetch membership, due to an internal error" },
            { status: 500 },
        )
    }
}
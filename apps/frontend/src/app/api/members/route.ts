import { NextRequest, NextResponse } from "next/server";
import {activeMock, getBackendToken, getServerEnv} from "../../shared/utils/validation";
import {AUTHENTICATED_FAILED, FAILED_FETCH, INTERNAL_ERROR} from "../../shared/utils/variable";
import {mockMembers} from "../../mocks/mockPayloads";


export async function GET(request: NextRequest) {
    if (activeMock()) {
        return NextResponse.json(mockMembers)
    }
    try {
        const { backendUrl } = getServerEnv()
        const { searchParams } = new URL(request.url)
        const bypassCache = searchParams.get("bypassCache") === "true"

        const backendToken = await getBackendToken(request)

        if (backendToken === AUTHENTICATED_FAILED) {
            return NextResponse.json(
                { error: AUTHENTICATED_FAILED },
                { status: 401 }
            )
        }

        const url = bypassCache
            ? `${backendUrl}/api/members?bypassCache=true`
            : `${backendUrl}/api/members`

        const response = await fetch(url, {
            method: 'GET',
            headers: {
                Authorization: `Bearer ${backendToken}`,
                "Content-Type": "application/json"
            }
        })

        if (!response.ok) {
            return NextResponse.json(
                { error: FAILED_FETCH },
                { status: response.status }
            )
        }

        const data = await response.json()
        return NextResponse.json(data)
    } catch (error) {
        console.error("Internal server error: ", error)
        return NextResponse.json(
            { error: INTERNAL_ERROR },
            { status: 500 }
        )
    }
}
import {NextRequest, NextResponse} from "next/server";
import {getBackendToken, getServerEnv} from "../../../shared/utils/Validation";
import {AUTHENTICATED_FAILED, FAILED_FETCH, INTERNAL_ERROR} from "../../../shared/utils/Variables";

export async function POST(request: NextRequest) {
    const body = await request.json()
    const { email } = body
    try {
        const { backendUrl } = getServerEnv()
        const backendToken = await getBackendToken(request)

        if (backendToken === AUTHENTICATED_FAILED) {
            return NextResponse.json(
                { error: AUTHENTICATED_FAILED },
                { status: 401 }
            )
        }

        const url =`${backendUrl}/api/admin/member`
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                Authorization: `Bearer ${backendToken}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify({"email": email})
        })

        if (!response.ok) {
            return NextResponse.json(
                { error: FAILED_FETCH },
                { status: response.status }
            )
        }
        return NextResponse.json(response)
    } catch (error) {
        console.error("Internal server error: ", error)
        return NextResponse.json(
            { error: INTERNAL_ERROR },
            { status: 500 }
        )
    }
}
import {NextRequest, NextResponse} from "next/server";
import {activeMock, getBackendToken, getServerEnv} from "../../../shared/utils/Validation";
import {AUTHENTICATED_FAILED, FAILED_FETCH, INTERNAL_ERROR} from "../../../shared/utils/Variables";
import {mockMembers} from "../../../mocks/MockPayloads";

export async function POST(
    request: NextRequest
) {
    const body = await request.json()
    const { email, amount } = body
    if (activeMock()) {
        return NextResponse.json(mockMembers)
    } try {
        const { backendUrl } = getServerEnv()
        const backendToken = await getBackendToken(request)

        if (backendToken === AUTHENTICATED_FAILED) {
            return NextResponse.json(
                { error: AUTHENTICATED_FAILED },
                { status: 401 }
            )
        }

        const url = `${backendUrl}/api/admin/points`
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                Authorization: `Bearer ${backendUrl}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify({"email": email, "points": amount})
        })

        if (!response.ok) {
            return NextResponse.json(
                { error: FAILED_FETCH },
                { status: response.status }
            )
        }
        return NextResponse.json({ status: "success" })
    } catch (error) {
        console.error("Internal server error: ", error)
        return NextResponse.json(
            { error: INTERNAL_ERROR },
            { status: 500 }
        )
    }
}
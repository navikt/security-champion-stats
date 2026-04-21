import {NextRequest, NextResponse} from "next/server";
import {activeMock, getBackendToken, getServerEnv} from "@/app/shared/utils/Validation";
import {mockSCData} from "@/app/mocks/MockPayloads";
import {AUTHENTICATED_FAILED, FAILED_FETCH, INTERNAL_ERROR} from "@/app/shared/utils/Variables";

export async function GET(request: NextRequest) {
    if (activeMock()) {
       return NextResponse.json(mockSCData)
    } try {
        const { backendUrl } = getServerEnv()
        const backendToken = await getBackendToken(request)

        if (backendToken === AUTHENTICATED_FAILED) {
            return NextResponse.json(
                { error: AUTHENTICATED_FAILED },
                { status: 401 }
            )
        }

        const url = `${backendUrl}/api/admin/dashboard/members`

        const response = await fetch(url, {
            method: 'GET',
            headers: {
                Authorization: `Bearer ${backendUrl}`,
                "Content-Type": "application/json"
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
        console.error("Internal server error: ", error)
        return NextResponse.json(
            { error: INTERNAL_ERROR },
            { status: 500 }
        )
    }
}
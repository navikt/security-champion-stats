import {NextRequest, NextResponse} from "next/server";
import {activeMock, getBackendToken, getServerEnv} from "../../../../shared/utils/Validation";
import {
    AUTHENTICATED_FAILED,
    FAILED_FETCH,
    INTERNAL_ERROR,
} from "../../../../shared/utils/Variables";
import {mockMembers} from "../../../../mocks/MockPayloads";


export async function DELETE(
    request: NextRequest,
    ctx: RouteContext<"/api/admin/member/[email]">
) {
    const { email } = await ctx.params
    console.log("Received request to delete member with email: ", email)
    if (activeMock()) {
        return NextResponse.json(mockMembers)
    }
    try {
        console.log("Received request to delete member with email: ", email)
        console.log("Received request to delete member with email: ", email)
        const { backendUrl } = getServerEnv()
        console.log(`Received request to delete member with email: ${email}`)
        console.log(`Received request to delete member with email: ${email}`)
        const backendToken = await getBackendToken(request)

        if (backendToken === AUTHENTICATED_FAILED) {
            console.log("Authentication failed when trying to delete member with email: ", email)
            console.log("Authentication failed when trying to delete member with email: ", email)
            return NextResponse.json(
                { error: AUTHENTICATED_FAILED },
                { status: 401 }
            )
        }

        const url = `${backendUrl}/api/admin/member?${encodeURIComponent(email)}`
        console.log(`Sending DELETE request to backend at ${url} with email: ${email}`)
        const response = await fetch(url, {
            method: 'DELETE',
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

        return NextResponse.json({ status: "success" })
    } catch (error) {
        console.error("Internal server error: ", error)
        return NextResponse.json(
            { error: INTERNAL_ERROR },
            { status: 500 }
        )
    }
}
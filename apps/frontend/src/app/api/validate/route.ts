import {NextRequest, NextResponse} from "next/server";
import {activeMock, getBackendToken, getServerEnv} from "../../shared/utils/validation";
import {AUTHENTICATED_FAILED, INTERNAL_ERROR, Me, MISSING_GROUP} from "../../shared/utils/variable";
import {parseAzureUserToken} from "@navikt/oasis";

export async function GET(
    request: NextRequest
) {
    if (activeMock()) {
        const email = process.env.LOCAL_DEV_EMAIL ?? "lokal.utvikler@nav.no"
        return NextResponse.json({
            username: email,
            isAdmin: true,
            inProgram: false
        })
    } try {
        console.log("Starting user validation in API route, next frackend")
        const token = await getBackendToken(request)
        const id = process.env.APPSEC_ID
        if (!id) {
            throw new Error("Missing environment variable APPSEC_ID")
        }
        console.log("APPSEC_ID: ", id)

        if (token === AUTHENTICATED_FAILED) {
            return NextResponse.json(
                { error: AUTHENTICATED_FAILED },
                { status: 401 }
            )
        }

        const parse = parseAzureUserToken(token)
        if (!parse.ok) {
            return NextResponse.json(
                { error: AUTHENTICATED_FAILED },
                { status: 401 }
            )
        }
        const backendUrl = getServerEnv()
        const url = `${backendUrl.backendUrl}/api/validate`
        console.log("Validating user with backend at: " + url)
        const response = await fetch(url, {
            method: 'GET',
            headers: {
                Authorization: `Bearer ${token}`,
                "Content-Type": "application/json"
            }
        })

        console.log("Backend response status: ", response.status)

        if (!response.ok) {
            return NextResponse.json(
                { error: AUTHENTICATED_FAILED, backendStatus: response.status, backendHeaders: Object.fromEntries(response.headers.entries()), backendBody: response.text() },
                { status: response.status }
            )
        }

        const backendResponse: Me = await response.json()
        const groups = parse.groups
        console.log("Backend response: ", backendResponse)
        if (!groups) {
            return NextResponse.json(
                { error: MISSING_GROUP },
                { status: 403 }
            )
        }

        if (backendResponse.username !== parse.preferred_username) {
            return NextResponse.json(
                { error: AUTHENTICATED_FAILED },
                { status: 401 }
            )
        }
        console.log("User validated successfully, returning response")
        return NextResponse.json(
            {
                username: parse.preferred_username,
                isAdmin: parse.groups?.includes(id),
                inProgram: backendResponse.inProgram
            }
        )
    } catch (error) {
        console.error("Validation error, then validating user," + error)
        return NextResponse.json(
            { error: INTERNAL_ERROR },
            { status: 500 }
        )
    }
}
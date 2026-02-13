import {NextRequest} from "next/server";
import {createLocalDevToken} from "../../utils/localDevAuth";
import {getToken, requestOboToken} from "@navikt/oasis";
import {AUTHENTICATED_FAILED} from "./variable";

export function getServerEnv() {
    const backendUrl = process.env.BACKEND_URL
    const backendScope = process.env.BACKEND_SCOPE

    if (!backendUrl) {
        throw new Error("Backend url is not set, set env variable BACKEND_URL")
    }

    if (!backendScope) {
        throw new Error("Backend scope is not set, set env variable BACKEND_SCOPE")
    }

    return { backendUrl, backendScope }
}

export function isLocaDev(): boolean {
    return process.env.LOCAL_DEV === "true"
}

export function activeMock(): boolean {
    return process.env.MOCKS_ENABLED === "true"
}

export async function getBackendToken(request: NextRequest): Promise<string> {
    if (isLocaDev()) {
        return createLocalDevToken()
    } else {
        const accessToken = getToken(request)
        if (!accessToken) {
            return AUTHENTICATED_FAILED
        }

        const { backendScope } = getServerEnv()
        const oboResult = await requestOboToken(accessToken, backendScope)

        if (!oboResult.ok) {
            return AUTHENTICATED_FAILED
        }
        return oboResult.token
    }
}

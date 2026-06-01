import { describe, it, expect, vi, beforeEach } from "vitest"

vi.mock("next/server", () => ({}))
vi.mock("@navikt/oasis", () => ({
    getToken: vi.fn(),
    requestOboToken: vi.fn(),
}))
vi.mock("../../utils/LocalDevAuth", () => ({
    createLocalDevToken: vi.fn(() => "local-dev-token"),
}))

import { isLocaDev, activeMock, getServerEnv } from "./Validation"

beforeEach(() => {
    vi.unstubAllEnvs()
})

describe("isLocaDev", () => {
    it("should return true when LOCAL_DEV is set to true", () => {
        vi.stubEnv("LOCAL_DEV", "true")
        expect(isLocaDev()).toBe(true)
    })

    it("should return false when LOCAL_DEV is not set", () => {
        expect(isLocaDev()).toBe(false)
    })
})

describe("activeMock", () => {
    it("should return true when MOCKS_ENABLED is set to true", () => {
        vi.stubEnv("MOCKS_ENABLED", "true")
        expect(activeMock()).toBe(true)
    })

    it("should return false when MOCKS_ENABLED is not set", () => {
        expect(activeMock()).toBe(false)
    })
})

describe("getServerEnv", () => {
    it("should return backendUrl and backendScope when both env vars are set", () => {
        vi.stubEnv("BACKEND_URL", "http://localhost:8080")
        vi.stubEnv("BACKEND_SCOPE", "api://test/.default")
        const { backendUrl, backendScope } = getServerEnv()
        expect(backendUrl).toBe("http://localhost:8080")
        expect(backendScope).toBe("api://test/.default")
    })

    it("should throw when BACKEND_URL is missing", () => {
        vi.stubEnv("BACKEND_SCOPE", "api://test/.default")
        expect(() => getServerEnv()).toThrow("Backend url is not set")
    })

    it("should throw when BACKEND_SCOPE is missing", () => {
        vi.stubEnv("BACKEND_URL", "http://localhost:8080")
        expect(() => getServerEnv()).toThrow("Backend scope is not set")
    })
})

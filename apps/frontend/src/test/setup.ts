import "@testing-library/jest-dom"
import { vi } from "vitest"

vi.mock("next-intl", () => ({
    useTranslations: () => (key: string) => key,
}))

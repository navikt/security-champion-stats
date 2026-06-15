import { describe, it, expect, vi } from "vitest"
import { render, screen } from "@testing-library/react"
import MembersTable from "./MembersTable"
import { Member } from "../utils/Variables"

const members: Member[] = [
    { id: "1", email: "alice@example.com", fullname: "Alice", points: 100, level: "1"},
    { id: "2", email: "bob@example.com", fullname: "Bob", points: 50, level: "2" },
]

describe("MembersTable", () => {
    it("should render member rows", async () => {
        render(
            <MembersTable
                members={members}
                onDelete={vi.fn()}
                onAddPoints={vi.fn()}
                canEdit={false}
            />
        )
        expect(await screen.findByText("Alice")).toBeInTheDocument()
        expect(await screen.findByText("Bob")).toBeInTheDocument()
    })

    it("should show empty state when no members", async () => {
        render(
            <MembersTable
                members={[]}
                onDelete={vi.fn()}
                onAddPoints={vi.fn()}
                canEdit={false}
            />
        )
        expect(await screen.findByText("main.table.noMembers")).toBeInTheDocument()
    })

    it("should render admin action buttons when canEdit is true", async () => {
        render(
            <MembersTable
                members={members}
                onDelete={vi.fn()}
                onAddPoints={vi.fn()}
                canEdit={true}
            />
        )
        const addPointsButtons = await screen.findAllByText("main.table.buttons.admin.addPoints")
        expect(addPointsButtons).toHaveLength(members.length)
    })

    it("should not render admin action buttons when canEdit is false", async () => {
        render(
            <MembersTable
                members={members}
                onDelete={vi.fn()}
                onAddPoints={vi.fn()}
                canEdit={false}
            />
        )
        expect(await screen.findByText("Alice")).toBeInTheDocument()
        expect(screen.queryByText("main.table.buttons.admin.addPoints")).not.toBeInTheDocument()
    })
})

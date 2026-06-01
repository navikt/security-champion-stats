import { describe, it, expect, vi } from "vitest"
import { render, screen } from "@testing-library/react"
import MembersTable from "./MembersTable"
import { Member } from "../utils/Variables"

const members: Member[] = [
    { id: "1", email: "alice@example.com", fullname: "Alice", points: 100 },
    { id: "2", email: "bob@example.com", fullname: "Bob", points: 50 },
]

describe("MembersTable", () => {
    it("should render member rows", () => {
        render(
            <MembersTable
                members={members}
                onDelete={vi.fn()}
                onAddPoints={vi.fn()}
                canEdit={false}
            />
        )
        expect(screen.getByText("Alice")).toBeInTheDocument()
        expect(screen.getByText("Bob")).toBeInTheDocument()
    })

    it("should show empty state when no members", () => {
        render(
            <MembersTable
                members={[]}
                onDelete={vi.fn()}
                onAddPoints={vi.fn()}
                canEdit={false}
            />
        )
        expect(screen.getByText("main.table.noMembers")).toBeInTheDocument()
    })

    it("should render admin action buttons when canEdit is true", () => {
        render(
            <MembersTable
                members={members}
                onDelete={vi.fn()}
                onAddPoints={vi.fn()}
                canEdit={true}
            />
        )
        const addPointsButtons = screen.getAllByText("main.table.buttons.admin.addPoints")
        expect(addPointsButtons).toHaveLength(members.length)
    })

    it("should not render admin action buttons when canEdit is false", () => {
        render(
            <MembersTable
                members={members}
                onDelete={vi.fn()}
                onAddPoints={vi.fn()}
                canEdit={false}
            />
        )
        expect(screen.queryByText("main.table.buttons.admin.addPoints")).not.toBeInTheDocument()
    })
})

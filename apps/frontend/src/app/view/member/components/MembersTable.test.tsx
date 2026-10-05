import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import MembersTable from "./MembersTable";
import { Member } from "@/app/utils/Variables";

const members: Member[] = [
	{
		id: "1",
		email: "alice@example.com",
		fullname: "Alice",
		points: 100,
		level: "1",
		inGame: true,
		joinedAt: "somedate",
		teams: [],
	},
	{
		id: "2",
		email: "bob@example.com",
		fullname: "Bob",
		points: 50,
		level: "2",
		inGame: false,
		joinedAt: "somedate",
		teams: [],
	},
];

describe("MembersTable", () => {
	it("should render member rows", async () => {
		render(
			<MembersTable
				members={members}
				onDelete={vi.fn()}
				canEdit={false}
			/>,
		);
		expect(await screen.findByText("Alice")).toBeInTheDocument();
		expect(await screen.findByText("Bob")).toBeInTheDocument();
		expect(screen.queryByText("100")).not.toBeInTheDocument();
	});

	it("should show empty state when no members", async () => {
		render(
			<MembersTable
				members={[]}
				onDelete={vi.fn()}
				canEdit={false}
			/>,
		);
		expect(await screen.findByText("No members found.")).toBeInTheDocument();
	});

	it("should show scores and admin actions when canEdit is true", async () => {
		render(
			<MembersTable
				members={members}
				onDelete={vi.fn()}
				canEdit={true}
			/>,
		);
		expect(await screen.findByText("100")).toBeInTheDocument();
		expect(screen.getAllByText("Delete member")).toHaveLength(
			members.length,
		);
	});

	it("should allow a participant to view their leaderboard scores without admin actions", async () => {
		render(
			<MembersTable
				members={members}
				onDelete={vi.fn()}
				canEdit={false}
				canViewScores
			/>,
		);
		expect(await screen.findByText("100")).toBeInTheDocument();
		expect(screen.queryByText("Actions")).not.toBeInTheDocument();
	});
});

import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { JoinProgramView } from "./JoinProgramView";

describe("JoinProgramView", () => {
	it("describes what employees can expect from the program", () => {
		render(<JoinProgramView onEnroll={vi.fn()} />);

		expect(
			screen.queryByText("Enroll to take part in the program."),
		).not.toBeInTheDocument();
		expect(screen.getByRole("heading", { name: "What to expect" })).toBeInTheDocument();
		expect(screen.getByRole("list").children).toHaveLength(3);
		expect(screen.getByText("Attend events and workshops")).toBeInTheDocument();
		expect(screen.getByText("Make an impact in your team")).toBeInTheDocument();
		expect(screen.getByText("Earn XP and unlock levels")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Learn more" })).toHaveAttribute(
			"href",
			"https://sikkerhet.nav.no",
		);
		expect(screen.getByAltText("")).toHaveAttribute("src", "/icon.svg");
	});

	it("should enroll the current employee when requested", async () => {
		const onEnroll = vi.fn().mockResolvedValue(true);
		render(<JoinProgramView onEnroll={onEnroll} />);

		fireEvent.click(screen.getByRole("button", { name: "Enroll" }));

		expect(onEnroll).toHaveBeenCalledOnce();
		await waitFor(() =>
			expect(screen.getByRole("button", { name: "Enroll" })).toBeEnabled(),
		);
	});

	it("should report when enrollment fails", async () => {
		const onEnroll = vi.fn().mockResolvedValue(false);
		render(<JoinProgramView onEnroll={onEnroll} />);

		fireEvent.click(screen.getByRole("button", { name: "Enroll" }));

		expect(await screen.findByRole("alert")).toHaveTextContent(
			"We couldn't enroll you. Try again.",
		);
	});
});

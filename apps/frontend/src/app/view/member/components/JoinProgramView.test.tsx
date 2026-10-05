import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { JoinProgramView } from "./JoinProgramView";

describe("JoinProgramView", () => {
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

import { InternalHeader } from "@navikt/ds-react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ThemeToggle } from "./ThemeProvider";

const { setTheme } = vi.hoisted(() => ({ setTheme: vi.fn() }));

vi.mock("next-themes", () => ({
	useTheme: () => ({ theme: "system", resolvedTheme: "light", setTheme }),
}));

describe("ThemeToggle", () => {
	it("shows an icon button instead of a labeled select and retains all theme options", async () => {
		const user = userEvent.setup();
		render(
			<InternalHeader>
				<ThemeToggle />
			</InternalHeader>,
		);

		expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Choose theme" }));
		expect(screen.getByRole("menuitemradio", { name: "System" })).toHaveAttribute(
			"aria-checked",
			"true",
		);
		expect(screen.getByRole("menuitemradio", { name: "Light" })).toBeInTheDocument();
		await user.click(screen.getByRole("menuitemradio", { name: "Dark" }));
		expect(setTheme).toHaveBeenCalledWith("dark");
	});

	it("opens the theme menu with the keyboard and restores focus on Escape", async () => {
		const user = userEvent.setup();
		render(
			<InternalHeader>
				<ThemeToggle />
			</InternalHeader>,
		);

		const button = screen.getByRole("button", { name: "Choose theme" });
		button.focus();
		await user.keyboard("{Enter}");
		await waitFor(() => expect(screen.getByRole("menu")).toHaveFocus());
		await user.keyboard("{ArrowDown}");
		await waitFor(() =>
			expect(screen.getByRole("menuitemradio", { name: "Light" })).toHaveFocus(),
		);
		await user.keyboard("{Escape}");
		await waitFor(() => expect(button).toHaveFocus());
	});
});

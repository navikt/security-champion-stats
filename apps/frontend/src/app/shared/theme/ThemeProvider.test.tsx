import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { HackerPreferencesProvider, ThemeToggle } from "./ThemeProvider";

const themeState = vi.hoisted(() => ({
	value: "system",
	setTheme: vi.fn(),
}));

vi.mock("next-themes", () => ({
	useTheme: () => ({
		theme: themeState.value,
		resolvedTheme: "light",
		setTheme: themeState.setTheme,
	}),
}));

describe("ThemeToggle", () => {
	const renderThemeToggle = () =>
		render(
			<HackerPreferencesProvider>
				<ThemeToggle />
			</HackerPreferencesProvider>,
		);

	it("shows a sidebar button and retains all theme options", async () => {
		themeState.value = "system";
		const user = userEvent.setup();
		renderThemeToggle();

		expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Choose theme" }));
		expect(
			screen.getByRole("menuitemradio", { name: "System" }),
		).toHaveAttribute("aria-checked", "true");
		expect(
			screen.getByRole("menuitemradio", { name: "Light" }),
		).toBeInTheDocument();
		expect(
			screen.getByRole("menuitemradio", { name: "Hacker" }),
		).toBeInTheDocument();
		expect(screen.queryByLabelText("Matrix rain")).not.toBeInTheDocument();
		await user.click(screen.getByRole("menuitemradio", { name: "Dark" }));
		expect(themeState.setTheme).toHaveBeenCalledWith("dark");
	});

	it("can select the Hacker theme", async () => {
		themeState.value = "system";
		const user = userEvent.setup();
		renderThemeToggle();

		await user.click(screen.getByRole("button", { name: "Choose theme" }));
		await user.click(screen.getByRole("menuitemradio", { name: "Hacker" }));
		expect(themeState.setTheme).toHaveBeenCalledWith("hacker");
	});

	it("shows matrix and CRT controls only for the Hacker theme", async () => {
		themeState.value = "hacker";
		const user = userEvent.setup();
		renderThemeToggle();

		await user.click(screen.getByRole("button", { name: "Choose theme" }));
		expect(screen.getByLabelText("Matrix rain")).toBeInTheDocument();
		expect(screen.getByLabelText("CRT scanlines")).toBeInTheDocument();
		expect(screen.getByLabelText("Phosphor")).toBeInTheDocument();
	});

	it("opens the theme menu with the keyboard and restores focus on Escape", async () => {
		themeState.value = "system";
		const user = userEvent.setup();
		renderThemeToggle();

		const button = screen.getByRole("button", { name: "Choose theme" });
		button.focus();
		await user.keyboard("{Enter}");
		await waitFor(() => expect(screen.getByRole("menu")).toHaveFocus());
		await user.keyboard("{ArrowDown}");
		await waitFor(() =>
			expect(
				screen.getByRole("menuitemradio", { name: "Light" }),
			).toHaveFocus(),
		);
		await user.keyboard("{Escape}");
		await waitFor(() => expect(button).toHaveFocus());
	});
});

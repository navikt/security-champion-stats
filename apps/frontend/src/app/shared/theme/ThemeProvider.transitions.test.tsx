import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useTheme } from "next-themes";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { HackerBackdrop, ThemeProvider, ThemeToggle } from "./ThemeProvider";

function CurrentTheme() {
	const { theme, resolvedTheme } = useTheme();
	return (
		<output aria-label="Current theme">
			{theme}:{resolvedTheme}
		</output>
	);
}

beforeEach(() => {
	const stored = new Map<string, string>();
	vi.stubGlobal("localStorage", {
		getItem: (key: string) => stored.get(key) ?? null,
		setItem: (key: string, value: string) => stored.set(key, value),
		removeItem: (key: string) => stored.delete(key),
		clear: () => stored.clear(),
		key: (index: number) => [...stored.keys()][index] ?? null,
		get length() {
			return stored.size;
		},
	} satisfies Storage);
	document.documentElement.className = "unrelated";
});

afterEach(() => {
	cleanup();
	vi.unstubAllGlobals();
	document.documentElement.className = "";
	document.documentElement.style.removeProperty("color-scheme");
	delete document.documentElement.dataset.phosphor;
});

describe("theme transitions", () => {
	it.each([
		{ target: "Light", mode: "light", systemDark: false },
		{ target: "Dark", mode: "dark", systemDark: false },
		{ target: "System", mode: "light", systemDark: false },
		{ target: "System", mode: "dark", systemDark: true },
	])(
		"removes Hacker styling when switching to $target ($mode)",
		async ({ target, mode, systemDark }) => {
			vi.stubGlobal(
				"matchMedia",
				(query: string) =>
					({
						matches:
							query === "(prefers-color-scheme: dark)" ? systemDark : true,
						media: query,
						onchange: null,
						addListener: vi.fn(),
						removeListener: vi.fn(),
						addEventListener: vi.fn(),
						removeEventListener: vi.fn(),
						dispatchEvent: vi.fn(),
					}) satisfies MediaQueryList,
			);
			localStorage.setItem("scs-theme", "hacker");
			const user = userEvent.setup();
			const { container } = render(
				<ThemeProvider>
					<ThemeToggle />
					<HackerBackdrop />
					<CurrentTheme />
				</ThemeProvider>,
			);

			await waitFor(() =>
				expect(document.documentElement).toHaveClass("hacker", "unrelated"),
			);
			expect(container.querySelector(".hackerCrt")).toBeInTheDocument();

			await user.click(screen.getByRole("button", { name: "Choose theme" }));
			await user.click(screen.getByRole("menuitemradio", { name: target }));

			await waitFor(() =>
				expect(screen.getByLabelText("Current theme")).toHaveTextContent(
					`${target.toLowerCase()}:${mode}`,
				),
			);
			expect(document.documentElement).toHaveClass(mode, "unrelated");
			expect(document.documentElement).not.toHaveClass("hacker");
			expect(document.documentElement).not.toHaveClass(
				mode === "light" ? "dark" : "light",
			);
			expect(container.querySelector(".hackerCrt")).not.toBeInTheDocument();
			expect(container.querySelector(".hackerRain")).not.toBeInTheDocument();
		},
	);
});

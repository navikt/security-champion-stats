import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AppLayout from "./AppLayout";
import type { Me } from "./utils/Variables";

const state = vi.hoisted(() => ({
	pathName: "/",
	loading: false,
	me: {
		username: "synthetic.user@nav.no",
		displayName: "Ada Lovelace",
		isAdmin: false,
		isParticipant: true,
		isActive: true,
	} as Me,
}));

vi.mock("next/navigation", () => ({
	usePathname: () => state.pathName,
}));

vi.mock("./shared/hooks/UseMe", () => ({
	useMe: () => ({ me: state.me, loading: state.loading }),
}));

vi.mock("next-themes", () => ({
	useTheme: () => ({ theme: "system", resolvedTheme: "light", setTheme: vi.fn() }),
}));

beforeEach(() => {
	state.pathName = "/";
	state.loading = false;
	state.me.isAdmin = false;
	state.me.displayName = "Ada Lovelace";
});

describe("sidebar layout", () => {
	it("shows shared navigation and bottom controls without a top bar or visible full name", () => {
		render(<AppLayout><p>Page content</p></AppLayout>);

		const sidebar = screen.getByRole("complementary");
		expect(sidebar).toContainElement(screen.getByRole("link", { name: "Sec Hub" }));
		for (const label of ["Overview", "Events", "Community"]) {
			expect(sidebar).toContainElement(screen.getByRole("link", { name: label }));
		}
		expect(screen.queryByRole("banner")).not.toBeInTheDocument();
		expect(screen.queryByText("Administration")).not.toBeInTheDocument();
		expect(screen.queryByText("Program dashboard")).not.toBeInTheDocument();
		expect(screen.queryByText(/Manage/)).not.toBeInTheDocument();
		const footer = sidebar.querySelector(".sideNavigation__footer");
		expect(footer).toContainElement(screen.getByRole("button", { name: "Choose theme" }));
		const avatar = screen.getByRole("img", { name: "Signed in as Ada Lovelace" });
		expect(footer).toContainElement(avatar);
		expect(avatar).toHaveTextContent("AL");
		expect(avatar).toHaveAttribute("title", "Signed in as Ada Lovelace");
		expect(screen.queryByText("Ada Lovelace")).not.toBeInTheDocument();
		expect(screen.getByRole("main")).toHaveTextContent("Page content");
	});

	it("shows first-name-first initials for a surname-first Entra display name", () => {
		state.me.displayName = "Lovelace, Ada";
		render(<AppLayout><p>Page content</p></AppLayout>);

		expect(screen.getByRole("img", { name: "Signed in as Lovelace, Ada" }))
			.toHaveTextContent("AL");
	});

	it("lets admins expand Administration with the keyboard and reach every existing admin page", async () => {
		state.me.isAdmin = true;
		const user = userEvent.setup();
		render(<AppLayout><p>Page content</p></AppLayout>);
		const trigger = screen.getByRole("button", { name: "Administration" });
		expect(trigger).toHaveAttribute("aria-expanded", "false");

		trigger.focus();
		await user.keyboard("{Enter}");

		expect(trigger).toHaveAttribute("aria-expanded", "true");
		expect(screen.getByText("Admin only")).toBeVisible();
		for (const [name, href] of [
			["Program dashboard", "/dashboard"],
			["Manage events", "/appsec/events"],
			["Manage participants", "/appsec/membership"],
			["Manage scoring", "/appsec/scoring"],
			["Manage Slack mappings", "/appsec/slack"],
			["Manage Delta mappings", "/appsec/delta"],
		]) {
			expect(screen.getByRole("link", { name })).toHaveAttribute("href", href);
		}

		await user.keyboard("{Enter}");
		expect(trigger).toHaveAttribute("aria-expanded", "false");
	});

	it.each(["/dashboard", "/appsec/scoring"])(
		"automatically expands Administration and marks the current admin page at %s",
		(pathName) => {
			state.me.isAdmin = true;
			state.pathName = pathName;
			render(<AppLayout><p>Page content</p></AppLayout>);

			expect(screen.getByRole("button", { name: "Administration" }))
				.toHaveAttribute("aria-expanded", "true");
			expect(screen.getByRole("link", { current: "page" })).toHaveAttribute("href", pathName);
		},
	);

	it("keeps shared navigation active for nested routes", () => {
		state.pathName = "/events/synthetic-event";
		render(<AppLayout><p>Page content</p></AppLayout>);

		expect(screen.getByRole("link", { name: "Events" })).toHaveAttribute("aria-current", "page");
	});

	it("does not expose Administration to regular users on an admin URL", () => {
		state.pathName = "/appsec/events";
		render(<AppLayout><p>Page content</p></AppLayout>);

		expect(screen.queryByText("Administration")).not.toBeInTheDocument();
		expect(screen.queryByText("Manage events")).not.toBeInTheDocument();
	});

	it("shows an explicit placeholder when the display name is unavailable", () => {
		state.me.displayName = null;
		render(<AppLayout><p>Page content</p></AppLayout>);

		expect(screen.getByRole("img", {
			name: "Signed in as synthetic.user@nav.no",
		})).toHaveTextContent("?");
	});

	it("waits for the current user before rendering the layout", () => {
		state.loading = true;
		const { container } = render(<AppLayout><p>Page content</p></AppLayout>);

		expect(container).toBeEmptyDOMElement();
	});
});

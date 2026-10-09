import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import { CreditSource } from "./CreditSource";

it("describes the specific GitHub change instead of exposing its internal source key", () => {
	render(
		<CreditSource
			creditType="GITHUB_PULL_REQUEST"
			displayName="navikt/security-playbook #42: Improve threat modeling"
			sourceUrl="https://github.com/navikt/security-playbook/pull/42"
			sourceOccurredAt="2026-10-03T08:00:00Z"
		/>,
	);
	expect(
		screen.getByRole("link", { name: /#42: Improve threat modeling/ }),
	).toHaveAttribute(
		"href",
		"https://github.com/navikt/security-playbook/pull/42",
	);
	expect(screen.getByText(/Activity:/)).toHaveTextContent(
		"Activity: 3 Oct 2026, 10:00",
	);
});

it.each(["javascript:alert(1)", "https://user:password@example.org/event"])(
	"does not render an unsafe source link: %s",
	(sourceUrl) => {
		render(
			<CreditSource displayName="Security workshop" sourceUrl={sourceUrl} />,
		);
		expect(screen.getByText("Security workshop")).toBeInTheDocument();
		expect(screen.queryByRole("link")).not.toBeInTheDocument();
	},
);

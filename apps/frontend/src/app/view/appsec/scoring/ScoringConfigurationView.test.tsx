import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import { defaultScoringConfiguration } from "@/app/utils/scoringFixtures.test-support";
import type {
	AdminScoringOverview,
	ScoringConfigurationPreview,
} from "@/app/utils/Variables";
import { ScoringConfigurationView } from "./ScoringConfigurationView";

const preview: ScoringConfigurationPreview = {
	token: "preview-token",
	season: {
		id: "season-1",
		startsOn: "2026-01-01",
		endsOn: null,
		nextResetDate: "2027-01-01",
	},
	affectedCredits: 2,
	pointsDelta: 6,
	participants: [
		{
			participantId: "participant-1",
			fullName: "Example Person",
			pointsBefore: 2,
			pointsAfter: 8,
			levelBefore: "Novice",
			levelAfter: "Champion",
		},
	],
};

const overview: AdminScoringOverview = {
	configuration: defaultScoringConfiguration,
	season: {
		id: "season-1",
		startsOn: "2026-01-01",
		endsOn: null,
		nextResetDate: "2027-01-01",
	},
	today: "2026-10-05",
	participants: [
		{
			participantId: "participant-1",
			fullName: "Example Person",
			email: "person@nav.no",
			active: true,
			points: 8,
			level: "Novice",
		},
	],
};

function renderConfiguration() {
	const refresh = vi.fn().mockResolvedValue(overview);
	const saved = vi.fn();
	render(
		<ScoringConfigurationView
			configuration={defaultScoringConfiguration}
			participants={overview.participants}
			onRefresh={refresh}
			onSaved={saved}
		/>,
	);
	return { refresh, saved };
}

function change(label: string, value: string) {
	fireEvent.change(screen.getByLabelText(label), { target: { value } });
}

describe("ScoringConfigurationView", () => {
	afterEach(() => {
		vi.restoreAllMocks();
		vi.unstubAllGlobals();
	});

	it("previews every changed rule inline and applies only after explicit confirmation", async () => {
		const previewChanges = vi
			.spyOn(Apies, "previewScoringConfiguration")
			.mockResolvedValue(preview);
		const saveChanges = vi
			.spyOn(Apies, "saveScoringConfiguration")
			.mockResolvedValue(200);
		const { refresh, saved } = renderConfiguration();
		change("Tier 2 name", "Engineer");
		change("Merged playbook pull request points", "5");
		change("Reason for change (required)", "Balance scoring");
		fireEvent.click(screen.getByRole("button", { name: "Preview changes" }));

		expect(
			await screen.findByRole("heading", { name: "Rule changes" }),
		).toBeInTheDocument();
		expect(screen.getByText("Tier Engineer name")).toBeInTheDocument();
		expect(
			screen.getAllByText("Merged playbook pull request").length,
		).toBeGreaterThanOrEqual(2);
		expect(screen.getByText("Example Person")).toBeInTheDocument();
		expect(screen.getByText(/Novice → Champion/)).toBeInTheDocument();
		expect(saveChanges).not.toHaveBeenCalled();
		expect(previewChanges).toHaveBeenCalledWith({
			expectedVersion: 1,
			tiers: defaultScoringConfiguration.tiers.map((tier, index) =>
				index === 1 ? { ...tier, name: "Engineer" } : tier,
			),
			activities: defaultScoringConfiguration.activities.map((activity) =>
				activity.creditType === "GITHUB_PULL_REQUEST"
					? { ...activity, points: 5 }
					: activity,
			),
			applyRetroactively: false,
			reason: "Balance scoring",
		});

		fireEvent.click(screen.getByRole("button", { name: "Apply changes" }));
		await waitFor(() =>
			expect(saveChanges).toHaveBeenCalledWith({
				expectedVersion: 1,
				tiers: defaultScoringConfiguration.tiers.map((tier, index) =>
					index === 1 ? { ...tier, name: "Engineer" } : tier,
				),
				activities: defaultScoringConfiguration.activities.map((activity) =>
					activity.creditType === "GITHUB_PULL_REQUEST"
						? { ...activity, points: 5 }
						: activity,
				),
				applyRetroactively: false,
				reason: "Balance scoring",
				previewToken: "preview-token",
			}),
		);
		await waitFor(() => expect(refresh).toHaveBeenCalledOnce());
		expect(saved).toHaveBeenCalledOnce();
		expect(screen.getByText("Changes applied just now.")).toBeInTheDocument();
	});

	it("locks the first minimum, shows draft member counts, and disables preview until valid", () => {
		const previewChanges = vi.spyOn(Apies, "previewScoringConfiguration");
		renderConfiguration();
		expect(screen.getByLabelText("Tier 1 minimum points")).toHaveAttribute(
			"readonly",
		);
		expect(screen.getAllByText("1")).toHaveLength(2);
		change("Tier 3 minimum points", "100");
		change("Reason for change (required)", "Change tiers");
		expect(screen.getByRole("alert")).toHaveTextContent(
			"Tier minimums must increase in order.",
		);
		expect(
			screen.getByRole("button", { name: "Preview changes" }),
		).toBeDisabled();
		expect(previewChanges).not.toHaveBeenCalled();
	});

	it("discards draft values, reason, and revaluation selection", () => {
		renderConfiguration();
		change("Weekly Slack participation points", "4");
		change("Reason for change (required)", "Update Slack");
		fireEvent.click(
			screen.getByRole("checkbox", {
				name: "Also re-value current-season credits",
			}),
		);
		expect(screen.getByText("1 unsaved change")).toBeInTheDocument();
		fireEvent.click(screen.getByRole("button", { name: "Discard" }));
		expect(screen.getByText("All rules are saved.")).toBeInTheDocument();
		expect(
			screen.getByLabelText("Weekly Slack participation points"),
		).toHaveValue(1);
		expect(
			screen.queryByRole("checkbox", {
				name: "Also re-value current-season credits",
			}),
		).not.toBeInTheDocument();
		change("Weekly Slack participation points", "2");
		expect(
			screen.getByRole("checkbox", {
				name: "Also re-value current-season credits",
			}),
		).not.toBeChecked();
		expect(screen.getByLabelText("Reason for change (required)")).toHaveValue(
			"",
		);
	});

	it("adds a tier at the next threshold and removes it from the draft", async () => {
		renderConfiguration();
		fireEvent.click(screen.getByRole("button", { name: "Remove tier Expert" }));
		fireEvent.click(screen.getByRole("button", { name: "+ Add tier" }));
		await waitFor(() =>
			expect(screen.getByLabelText("Tier 4 name")).toHaveFocus(),
		);
		expect(screen.getByLabelText("Tier 4 minimum points")).toHaveValue(350);
		fireEvent.click(screen.getByRole("button", { name: "Remove tier 4" }));
		expect(screen.queryByLabelText("Tier 4 name")).not.toBeInTheDocument();
	});

	it("keeps the preview open on a stale save and reloads on request", async () => {
		vi.spyOn(Apies, "previewScoringConfiguration").mockResolvedValue(preview);
		const save = vi
			.spyOn(Apies, "saveScoringConfiguration")
			.mockResolvedValue(409);
		const { refresh } = renderConfiguration();
		change("Weekly Slack participation points", "4");
		change("Reason for change (required)", "Balance scoring");
		fireEvent.click(screen.getByRole("button", { name: "Preview changes" }));
		await screen.findByRole("heading", { name: "Rule changes" });
		fireEvent.click(screen.getByRole("button", { name: "Apply changes" }));
		expect(await screen.findByRole("alert")).toHaveTextContent(
			"Scoring changed",
		);
		expect(
			screen.getByRole("heading", { name: "Rule changes" }),
		).toBeInTheDocument();
		fireEvent.click(
			screen.getByRole("button", { name: "Reload configuration" }),
		);
		await waitFor(() => expect(refresh).toHaveBeenCalledOnce());
		expect(save).toHaveBeenCalledOnce();
		expect(screen.getByText("All rules are saved.")).toBeInTheDocument();
	});

	it("asks before leaving with unsaved changes", () => {
		vi.stubGlobal(
			"confirm",
			vi.fn(() => false),
		);
		renderConfiguration();
		change("Weekly Slack participation points", "4");
		const link = document.createElement("a");
		link.href = "/appsec/dashboard";
		link.textContent = "Dashboard";
		document.body.append(link);
		const event = new MouseEvent("click", {
			bubbles: true,
			cancelable: true,
			button: 0,
		});
		link.dispatchEvent(event);
		expect(window.confirm).toHaveBeenCalledOnce();
		expect(event.defaultPrevented).toBe(true);
		link.remove();
	});
});

import { Apies } from "@/app/shared/hooks/Apies";
import { defaultScoringConfiguration } from "@/app/utils/scoringFixtures.test-support";
import type { ScoringConfigurationPreview } from "@/app/utils/Variables";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
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

function renderConfiguration() {
	const refresh = vi.fn().mockResolvedValue(undefined);
	const saved = vi.fn();
	render(
		<ScoringConfigurationView
			configuration={defaultScoringConfiguration}
			onRefresh={refresh}
			onSaved={saved}
		/>,
	);
	return { refresh, saved };
}

function change(label: string, value: string) {
	fireEvent.change(screen.getByRole("textbox", { name: label }), {
		target: { value },
	});
}

describe("ScoringConfigurationView", () => {
	afterEach(() => vi.restoreAllMocks());

	it("previews named tiers and retroactive activity points and saves only after explicit confirmation", async () => {
		const previewChanges = vi
			.spyOn(Apies, "previewScoringConfiguration")
			.mockResolvedValue(preview);
		const saveChanges = vi
			.spyOn(Apies, "saveScoringConfiguration")
			.mockResolvedValue(200);
		const { refresh, saved } = renderConfiguration();
		change("Tier 1 name", "Starter");
		change("Weekly Slack participation points", "4");
		change("Reason for scoring changes", "Balance scoring");
		fireEvent.click(
			screen.getByRole("checkbox", {
				name: "Also apply activity point values to current-season credits",
			}),
		);
		fireEvent.click(
			screen.getByRole("button", { name: "Preview scoring changes" }),
		);

		expect(
			await screen.findByRole("heading", { name: "Confirm scoring changes" }),
		).toBeInTheDocument();
		expect(screen.getByText("Example Person")).toBeInTheDocument();
		expect(screen.getByText("2 / 8")).toBeInTheDocument();
		expect(saveChanges).not.toHaveBeenCalled();
		const expected = {
			expectedVersion: 1,
			tiers: [
				{ name: "Starter", points: 0 },
				...defaultScoringConfiguration.tiers.slice(1),
			],
			activities: defaultScoringConfiguration.activities.map((activity) =>
				activity.creditType === "SLACK_WEEK"
					? { ...activity, points: 4 }
					: activity,
			),
			applyRetroactively: true,
			reason: "Balance scoring",
		};
		expect(previewChanges).toHaveBeenCalledWith(expected);
		fireEvent.click(
			screen.getByRole("button", { name: "Confirm and save scoring" }),
		);
		await waitFor(() =>
			expect(saveChanges).toHaveBeenCalledWith({
				...expected,
				previewToken: "preview-token",
			}),
		);
		await waitFor(() => expect(refresh).toHaveBeenCalledOnce());
		expect(saved).toHaveBeenCalledOnce();
	});

	it("defaults to future-only activity changes and permits adding and removing tiers", async () => {
		const previewChanges = vi
			.spyOn(Apies, "previewScoringConfiguration")
			.mockResolvedValue({
				...preview,
				participants: [],
				affectedCredits: 0,
				pointsDelta: 0,
			});
		renderConfiguration();
		fireEvent.click(screen.getByRole("button", { name: "Remove tier 4" }));
		fireEvent.click(screen.getByRole("button", { name: "Add tier" }));
		change("Tier 4 name", "Champion");
		change("Tier 4 minimum points", "10");
		change("Merged playbook pull request points", "0");
		change("Reason for scoring changes", "New progression");
		fireEvent.click(
			screen.getByRole("button", { name: "Preview scoring changes" }),
		);
		await screen.findByRole("heading", { name: "Confirm scoring changes" });
		expect(previewChanges).toHaveBeenCalledWith(
			expect.objectContaining({
				applyRetroactively: false,
				tiers: [
					{ name: "Novice", points: 0 },
					{ name: "Champion", points: 10 },
					{ name: "Apprentice", points: 100 },
					{ name: "Adept", points: 250 },
				],
			}),
		);
		expect(
			screen.getByText(
				/Activity point changes apply only to newly awarded credits/,
			),
		).toBeInTheDocument();
	});

	it.each([
		["Tier 1 minimum points", "1"],
		["Tier 2 minimum points", "0"],
		["Tier 2 name", " novice "],
		["Tier 2 name", ""],
		["Weekly Slack participation points", "-1"],
		["Weekly Slack participation points", "1.5"],
		["Weekly Slack participation points", "2147483648"],
		["Reason for scoring changes", " "],
	])(
		"rejects invalid %s values before calling the backend",
		async (label, value) => {
			const previewChanges = vi.spyOn(Apies, "previewScoringConfiguration");
			renderConfiguration();
			change("Reason for scoring changes", "Balance scoring");
			change(label, value);
			fireEvent.click(
				screen.getByRole("button", { name: "Preview scoring changes" }),
			);
			expect(screen.getByRole("alert")).toBeInTheDocument();
			expect(previewChanges).not.toHaveBeenCalled();
		},
	);

	it("cancels a preview without saving", async () => {
		vi.spyOn(Apies, "previewScoringConfiguration").mockResolvedValue(preview);
		const save = vi.spyOn(Apies, "saveScoringConfiguration");
		renderConfiguration();
		change("Reason for scoring changes", "Balance scoring");
		fireEvent.click(
			screen.getByRole("button", { name: "Preview scoring changes" }),
		);
		await screen.findByRole("heading", { name: "Confirm scoring changes" });
		fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
		await waitFor(() =>
			expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
		);
		expect(save).not.toHaveBeenCalled();
	});

	it("reports stale previews and does not announce success", async () => {
		vi.spyOn(Apies, "previewScoringConfiguration").mockResolvedValue(preview);
		vi.spyOn(Apies, "saveScoringConfiguration").mockResolvedValue(409);
		const { refresh, saved } = renderConfiguration();
		change("Reason for scoring changes", "Balance scoring");
		fireEvent.click(
			screen.getByRole("button", { name: "Preview scoring changes" }),
		);
		await screen.findByRole("heading", { name: "Confirm scoring changes" });
		fireEvent.click(
			screen.getByRole("button", { name: "Confirm and save scoring" }),
		);
		expect(await screen.findByRole("alert")).toHaveTextContent(
			"Scoring changed",
		);
		expect(refresh).not.toHaveBeenCalled();
		expect(saved).not.toHaveBeenCalled();
		fireEvent.click(
			screen.getByRole("button", { name: "Reload configuration" }),
		);
		await waitFor(() => expect(refresh).toHaveBeenCalledOnce());
	});

	it("reports preview request failures", async () => {
		vi.spyOn(Apies, "previewScoringConfiguration").mockRejectedValue(
			new Error("Preview unavailable"),
		);
		renderConfiguration();
		change("Reason for scoring changes", "Balance scoring");
		fireEvent.click(
			screen.getByRole("button", { name: "Preview scoring changes" }),
		);
		expect(await screen.findByRole("alert")).toHaveTextContent(
			"Preview unavailable",
		);
	});
});

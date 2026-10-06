import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import { DeltaCategory, DeltaEligibleCategory, DeltaEventMapping } from "@/app/utils/Variables";
import { ManageDeltaEventMappingsView } from "./ManageDeltaEventMappingsView";

const mappings: DeltaEventMapping[] = [];
const categories: DeltaCategory[] = [{ id: 7, name: "Security" }, { id: 8, name: "Security Champions" }];
const eligibleCategories: DeltaEligibleCategory[] = [
	{ categoryId: 8, categoryName: "Security Champions", createdAt: "2026-10-05T10:00:00Z" },
];

function renderView(onRefresh = vi.fn().mockResolvedValue(undefined), currentMappings = mappings) {
	render(
		<ManageDeltaEventMappingsView
			mappings={currentMappings}
			categories={categories}
			eligibleCategories={eligibleCategories}
			onRefresh={onRefresh}
		/>,
	);
}

describe("ManageDeltaEventMappingsView", () => {
	it("should start a Delta event import and confirm it", async () => {
		const trigger = vi.spyOn(Apies, "triggerDeltaEventImport").mockResolvedValue(202);
		renderView();

		fireEvent.click(screen.getByRole("button", { name: "Import Delta events now" }));

		await waitFor(() => expect(trigger).toHaveBeenCalledOnce());
		expect(await screen.findByRole("status")).toHaveTextContent("Delta event import started");
	});

	it("should report when a Delta event import cannot start", async () => {
		vi.spyOn(Apies, "triggerDeltaEventImport").mockResolvedValue(409);
		renderView();

		fireEvent.click(screen.getByRole("button", { name: "Import Delta events now" }));

		expect(await screen.findByRole("alert")).toHaveTextContent("already running or is disabled");
	});
	it("should explicitly map a named program event to an owner-confirmed Delta UUID", async () => {
		const addMapping = vi.spyOn(Apies, "addDeltaEventMapping").mockResolvedValue(201);
		const onRefresh = vi.fn().mockResolvedValue(undefined);
		renderView(onRefresh);

		fireEvent.change(screen.getByLabelText("Program event name"), {
			target: { value: "Security Champion meetup" },
		});
		fireEvent.change(screen.getByLabelText("Owner-confirmed Delta event UUID"), {
			target: { value: "123e4567-e89b-12d3-a456-426614174000" },
		});
		fireEvent.click(screen.getByRole("button", { name: "Add mapping" }));

		await waitFor(() =>
			expect(addMapping).toHaveBeenCalledWith(
				"Security Champion meetup",
				"123e4567-e89b-12d3-a456-426614174000",
			),
		);
		expect(await screen.findByRole("status")).toHaveTextContent(
			"The Delta event was mapped to Security Champion meetup.",
		);
		expect(onRefresh).toHaveBeenCalled();
		addMapping.mockRestore();
	});

	it("should reject a Delta event UUID that already has a mapping", async () => {
		const addMapping = vi.spyOn(Apies, "addDeltaEventMapping").mockResolvedValue(409);
		renderView(vi.fn());

		fireEvent.change(screen.getByLabelText("Program event name"), {
			target: { value: "Security Champion meetup" },
		});
		fireEvent.change(screen.getByLabelText("Owner-confirmed Delta event UUID"), {
			target: { value: "123e4567-e89b-12d3-a456-426614174000" },
		});
		fireEvent.click(screen.getByRole("button", { name: "Add mapping" }));

		expect(await screen.findByRole("alert")).toHaveTextContent(
			"This Delta event UUID is already mapped.",
		);
		addMapping.mockRestore();
	});

	it("should add a Delta category that is not already eligible", async () => {
		const addCategory = vi.spyOn(Apies, "addDeltaEligibleCategory").mockResolvedValue(201);
		const onRefresh = vi.fn().mockResolvedValue(undefined);
		renderView(onRefresh);

		expect(screen.queryByRole("option", { name: "Security Champions" })).not.toBeInTheDocument();
		fireEvent.change(screen.getByLabelText("Delta category"), { target: { value: "7" } });
		fireEvent.click(screen.getByRole("button", { name: "Add category" }));

		await waitFor(() => expect(addCategory).toHaveBeenCalledWith(7));
		expect(await screen.findByRole("status")).toHaveTextContent("Security is now eligible for scoring.");
		expect(onRefresh).toHaveBeenCalled();
		addCategory.mockRestore();
	});

	it("should remove an eligible Delta category and keep awarded points", async () => {
		const removeCategory = vi.spyOn(Apies, "removeDeltaEligibleCategory").mockResolvedValue(204);
		renderView();

		fireEvent.click(screen.getByRole("button", { name: "Remove category" }));

		await waitFor(() => expect(removeCategory).toHaveBeenCalledWith(8));
		expect(await screen.findByRole("status")).toHaveTextContent(
			"Security Champions is no longer eligible. Points already awarded are kept.",
		);
		removeCategory.mockRestore();
	});
});

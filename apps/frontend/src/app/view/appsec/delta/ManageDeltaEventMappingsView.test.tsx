import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import { DeltaCategory, DeltaEventMapping } from "@/app/utils/Variables";
import { ManageDeltaEventMappingsView } from "./ManageDeltaEventMappingsView";

const mappings: DeltaEventMapping[] = [];
const categories: DeltaCategory[] = [{ id: 7, name: "Security" }];

describe("ManageDeltaEventMappingsView", () => {
	it("should explicitly map a named program event to an owner-confirmed Delta UUID", async () => {
		const addMapping = vi.spyOn(Apies, "addDeltaEventMapping").mockResolvedValue(201);
		const onRefresh = vi.fn().mockResolvedValue(undefined);
		render(<ManageDeltaEventMappingsView mappings={mappings} categories={categories} onRefresh={onRefresh} />);

		fireEvent.change(screen.getByLabelText("Program event name"), {
			target: { value: "Security Champion meetup" },
		});
		fireEvent.change(screen.getByLabelText("Owner-confirmed Delta event UUID"), {
			target: { value: "123e4567-e89b-12d3-a456-426614174000" },
		});
		fireEvent.change(screen.getByLabelText("Delta category"), { target: { value: "7" } });
		fireEvent.click(screen.getByRole("button", { name: "Add mapping" }));

		await waitFor(() =>
			expect(addMapping).toHaveBeenCalledWith(
				"Security Champion meetup",
				"123e4567-e89b-12d3-a456-426614174000",
				7,
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
		render(<ManageDeltaEventMappingsView mappings={mappings} categories={categories} onRefresh={vi.fn()} />);

		fireEvent.change(screen.getByLabelText("Program event name"), {
			target: { value: "Security Champion meetup" },
		});
		fireEvent.change(screen.getByLabelText("Owner-confirmed Delta event UUID"), {
			target: { value: "123e4567-e89b-12d3-a456-426614174000" },
		});
		fireEvent.change(screen.getByLabelText("Delta category"), { target: { value: "7" } });
		fireEvent.click(screen.getByRole("button", { name: "Add mapping" }));

		expect(await screen.findByRole("alert")).toHaveTextContent(
			"This Delta event UUID is already mapped.",
		);
		addMapping.mockRestore();
	});

	it("should allow assigning a category to a legacy mapping", async () => {
		const legacyMapping: DeltaEventMapping = {
			id: "mapping-id",
			programEventName: "Legacy event",
			deltaEventUuid: "123e4567-e89b-12d3-a456-426614174000",
			deltaCategoryId: null,
			createdAt: "2026-10-05T10:00:00Z",
		};
		const updateCategory = vi.spyOn(Apies, "updateDeltaEventMappingCategory").mockResolvedValue(204);
		const onRefresh = vi.fn().mockResolvedValue(undefined);
		render(
			<ManageDeltaEventMappingsView
				mappings={[legacyMapping]}
				categories={categories}
				onRefresh={onRefresh}
			/>,
		);

		fireEvent.change(screen.getByLabelText("Delta category for Legacy event"), {
			target: { value: "7" },
		});
		fireEvent.click(screen.getByRole("button", { name: "Save category" }));

		await waitFor(() => expect(updateCategory).toHaveBeenCalledWith("mapping-id", 7));
		expect(await screen.findByRole("status")).toHaveTextContent(
			"The category for Legacy event was updated.",
		);
		expect(onRefresh).toHaveBeenCalled();
		updateCategory.mockRestore();
	});
});

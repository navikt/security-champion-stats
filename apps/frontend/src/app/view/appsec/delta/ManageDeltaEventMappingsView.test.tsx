import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Apies } from "@/app/shared/hooks/Apies";
import { DeltaEventMapping } from "@/app/utils/Variables";
import { ManageDeltaEventMappingsView } from "./ManageDeltaEventMappingsView";

const mappings: DeltaEventMapping[] = [];

describe("ManageDeltaEventMappingsView", () => {
	it("should explicitly map a named program event to an owner-confirmed Delta UUID", async () => {
		const addMapping = vi.spyOn(Apies, "addDeltaEventMapping").mockResolvedValue(201);
		const onRefresh = vi.fn().mockResolvedValue(undefined);
		render(<ManageDeltaEventMappingsView mappings={mappings} onRefresh={onRefresh} />);

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
		render(<ManageDeltaEventMappingsView mappings={mappings} onRefresh={vi.fn()} />);

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
});

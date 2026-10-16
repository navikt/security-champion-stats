"use client";

import { Apies } from "@/app/shared/hooks/Apies";
import type {
	TeamCatalogRoleSaveResult,
	TeamCatalogRoleState,
	TeamCatalogRoleUpdate,
} from "@/app/utils/Variables";
import { BodyShort, Button, Checkbox, CheckboxGroup, Heading, VStack } from "@navikt/ds-react";
import { useEffect, useState } from "react";

export function TeamCatalogRolesView() {
	const [state, setState] = useState<TeamCatalogRoleState | null>(null);
	const [selectedTeamIds, setSelectedTeamIds] = useState<string[]>([]);
	const [updates, setUpdates] = useState<TeamCatalogRoleUpdate[]>([]);
	const [loading, setLoading] = useState(true);
	const [pending, setPending] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [notice, setNotice] = useState<string | null>(null);

	useEffect(() => {
		let mounted = true;
		Apies.getTeamCatalogRoles()
			.then((result) => {
				if (!mounted) return;
				setState(result);
				setSelectedTeamIds(
					result.teams.filter((team) => team.isSecurityChampion).map((team) => team.id),
				);
				setError(null);
			})
			.catch(() => {
				if (mounted) setError("We couldn't fetch your Teamkatalogen teams. Try again later.");
			})
			.finally(() => {
				if (mounted) setLoading(false);
			});
		return () => {
			mounted = false;
		};
	}, []);

	const save = async () => {
		if (!state || pending) return;
		setPending(true);
		setError(null);
		setNotice(null);
		setUpdates([]);
		try {
			const result = await Apies.saveTeamCatalogRoles(selectedTeamIds);
			const freshState: TeamCatalogRoleState = {
				teams: result.teams,
				writeEnabled: result.writeEnabled,
				dryRun: result.dryRun,
			};
			const updatedState = result.dryRun
				? freshState
				: applySuccessfulUpdates(freshState, result);
			setState(updatedState);
			const previouslyListed = new Set(state.teams.map((team) => team.id));
			setSelectedTeamIds((current) =>
				updatedState.teams
					.filter((team) => current.includes(team.id) || (!previouslyListed.has(team.id) && team.isSecurityChampion))
					.map((team) => team.id),
			);
			setUpdates(result.updates);
			if (result.dryRun) {
				setNotice("Dry run complete. No Teamkatalogen changes were written.");
			} else if (result.updates.some((update) => update.status === "FAILED")) {
				setNotice("Some teams could not be updated. Review the results and retry.");
			} else {
				setNotice("Your Teamkatalogen roles were updated.");
			}
		} catch (saveError) {
			setError(saveError instanceof Error ? saveError.message : "We couldn't save your team roles. Try again.");
		} finally {
			setPending(false);
		}
	};

	const hasChanges = state?.teams.some((team) =>
		selectedTeamIds.includes(team.id) !== team.isSecurityChampion,
	) ?? false;

	return (
		<main className="hubRedesign">
			<VStack gap="space-24">
				<VStack gap="space-8">
					<Heading level="1" size="xlarge">My Teamkatalogen roles</Heading>
					<BodyShort>
						Choose the teams where you are a Security Champion. Your selection is
						synced to your current Teamkatalogen memberships.
					</BodyShort>
				</VStack>
				{state && (
					<BodyShort role="status">
						{state.writeEnabled
							? "Write mode is enabled. Saving updates your Teamkatalogen roles."
							: "Dry-run mode is active. Saving previews the changes without updating Teamkatalogen."}
					</BodyShort>
				)}
				{loading && <BodyShort>Loading your teams…</BodyShort>}
				{error && <BodyShort role="alert">{error}</BodyShort>}
				{!loading && state?.teams.length === 0 && (
					<BodyShort>No current Teamkatalogen team memberships were found.</BodyShort>
				)}
				{state && state.teams.length > 0 && (
					<CheckboxGroup
						legend="Teams where you are a Security Champion"
						description="You can add or remove the Security Champion tag for any current team membership."
						value={selectedTeamIds}
						onChange={setSelectedTeamIds}
					>
						{state.teams.map((team) => (
							<Checkbox key={team.id} value={team.id}>
								{team.name}
							</Checkbox>
						))}
					</CheckboxGroup>
				)}
				{notice && <BodyShort role="status">{notice}</BodyShort>}
				{state && (
					<Button
						type="button"
						variant="primary"
						onClick={() => void save()}
						disabled={pending || loading || !hasChanges}
					>
						{pending
							? "Saving"
							: state.writeEnabled
								? "Save team roles"
								: "Preview team role changes"}
					</Button>
				)}
				{updates.length > 0 && (
					<VStack gap="space-16">
						<Heading level="2" size="medium">Team role update results</Heading>
						{updates.map((update) => (
							<section key={update.teamId}>
								<Heading level="3" size="small">{update.teamName}</Heading>
								<BodyShort>
									{update.operation === "ADD" ? "Add" : "Remove"} Security Champion tag: {update.status.toLowerCase()}.
								</BodyShort>
								<details>
									<summary>Redacted payload preview</summary>
									<pre>{JSON.stringify({
										id: update.payloadPreview.id,
										name: update.payloadPreview.name,
										members: `[redacted: ${update.payloadPreview.teamMemberCount} member records]`,
										currentMemberRoles: update.payloadPreview.currentMemberRoles,
									}, null, 2)}</pre>
								</details>
							</section>
						))}
					</VStack>
				)}
			</VStack>
		</main>
	);
}

function applySuccessfulUpdates(
	current: TeamCatalogRoleState,
	result: TeamCatalogRoleSaveResult,
): TeamCatalogRoleState {
	const successfulUpdates = new Map(
		result.updates
			.filter((update) => update.status === "UPDATED")
			.map((update) => [update.teamId, update.operation === "ADD"]),
	);
	return {
		...current,
		teams: current.teams.map((team) => ({
			...team,
			isSecurityChampion: successfulUpdates.get(team.id) ?? team.isSecurityChampion,
		})),
	};
}

"use client";

import { Member } from "@/app/utils/Variables";

function MembersTable({
	members,
	onDelete,
	canEdit,
	canViewScores,
}: {
	members: Member[];
	onDelete: (email: string) => void;
	canEdit: boolean;
	canViewScores?: boolean;
}) {
	const showScores = canViewScores ?? canEdit;
	const columnCount = 1 + (showScores ? 2 : 0) + (canEdit ? 1 : 0);

	const handleLevelNames = (member: Member): string => {
		switch (member.level) {
			case "1":
				return "Novice";
			case "2":
				return "Apprentice";
			case "3":
				return "Adept";
			case "4":
				return "Expert";
			default:
				return "Novice";
		}
	};

	return (
		<div className="membersTable">
			<table role={"table"} aria-label={"Members"}>
				<colgroup>
					<col className={"membersTable__col-name"} />
					{showScores && <col className={"membersTable__col-points"} />}
					{showScores && <col className={"membersTable__col-level"} />}
					{canEdit && <col className={"membersTable__col-actions"} />}
				</colgroup>
				<thead>
					<tr>
						<th>Full name</th>
						{showScores && <th>Points</th>}
						{showScores && <th>Level</th>}
						{canEdit && (
							<th className={"membersTable__actionsHeader"}>
								Actions
							</th>
						)}
					</tr>
				</thead>
				<tbody>
					{members.map((m) => (
						<tr key={m.id}>
							<td>{m.fullname}</td>
							{showScores && (
								<>
									<td className={"td-num"}>{m.points.toLocaleString("en")}</td>
									<td>{handleLevelNames(m)}</td>
								</>
							)}
							{canEdit && (
								<td className={"membersTable__actionsCell"}>
									<button
										type="button"
										className={"btn danger"}
										onClick={() => onDelete(m.id)}
									>
										Delete member
									</button>
								</td>
							)}
						</tr>
					))}
					{members.length === 0 && (
						<tr>
							<td colSpan={columnCount}>No members found.</td>
						</tr>
					)}
				</tbody>
			</table>
		</div>
	);
}

export default MembersTable;

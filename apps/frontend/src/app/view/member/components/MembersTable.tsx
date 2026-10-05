"use client";

import { Member } from "@/app/utils/Variables";
import { useTranslations } from "next-intl";

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
	const t = useTranslations();
	const showScores = canViewScores ?? canEdit;
	const columnCount = 1 + (showScores ? 2 : 0) + (canEdit ? 1 : 0);

	const handleLevelNames = (member: Member): string => {
		switch (member.level) {
			case "1":
				return t("main.table.levels.1");
			case "2":
				return t("main.table.levels.2");
			case "3":
				return t("main.table.levels.3");
			case "4":
				return t("main.table.levels.4");
			default:
				return t("main.table.levels.1");
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
						<th> {t("main.table.member.fullname")} </th>
						{showScores && <th>{t("main.table.member.points")}</th>}
						{showScores && <th>{t("main.table.member.level")}</th>}
						{canEdit && (
							<th className={"membersTable__actionsHeader"}>
								{t("main.table.adminActions")}
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
									<td className={"td-num"}>{m.points.toLocaleString()}</td>
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
										{t("main.table.buttons.admin.deleteMember")}
									</button>
								</td>
							)}
						</tr>
					))}
					{members.length === 0 && (
						<tr>
							<td colSpan={columnCount}>{t("main.table.noMembers")}</td>
						</tr>
					)}
				</tbody>
			</table>
		</div>
	);
}

export default MembersTable;

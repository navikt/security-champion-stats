"use client"

import {Member} from "../utils/Variables";
import {useTranslations} from "next-intl";
import "../../style/home.page.css"
import {useRef, useState} from "react";
import {Button, Modal, TextField} from "@navikt/ds-react";

function MembersTable({
    members,
    onDelete,
    onAddPoints,
    canEdit
}: {
    members: Member[],
    onDelete: (email: string) => void,
    onAddPoints: (email?: string, number?: number) => void,
    canEdit: boolean
}) {
    const t = useTranslations()
    const columnCount = canEdit ? 4 : 3
    const [disableButtons, setDisableButtons] = useState(false)
    const [modalOpenFor, setModalOpenFor] = useState<string | null>(null)
    const pointRef = useRef<HTMLInputElement>(null)

    return (
        <div className="membersTable">
            <table role={"table"} aria-label={"Members"}>
                <colgroup>
                    <col className={"membersTable__col-name"} />
                    <col className={"membersTable__col-points"} />
                    <col className={"membersTable__col-level"} />
                    {canEdit && <col className={"membersTable__col-actions"} />}
                </colgroup>
                <thead>
                    <tr>
                        <th> {t("main.table.member.fullname")} </th>
                        <th> {t("main.table.member.points")} </th>
                        <th> {t("main.table.member.level")} </th>
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
                        <td className={"td-num"}>{m.points.toLocaleString()}</td>
                        <td>MasterClass</td>
                        {canEdit && (
                            <td className={"membersTable__actionsCell"}>
                                <button
                                    type="button"
                                    className={"btn outline"}
                                    onClick={() => {
                                        setModalOpenFor(m.email)
                                        setDisableButtons(true)
                                    }}
                                    disabled={disableButtons}
                                >
                                    {t("main.table.buttons.admin.addPoints")}
                                </button>
                                <button
                                    type="button"
                                    className={"btn danger"}
                                    onClick={() => onDelete(m.email)}
                                >
                                    {t("main.table.buttons.admin.deleteMember")}
                                </button>
                            </td>
                        )}
                    </tr>
                ))}
                {members.length === 0 && (
                    <tr>
                        <td colSpan={columnCount}>
                            {t("main.table.noMembers")}
                        </td>
                    </tr>
                )}
                </tbody>
            </table>
            <Modal open={!!modalOpenFor} onClose={
                () => {
                    setModalOpenFor(null); setDisableButtons(false);
                }
            } header={{ heading: t("main.table.modals.addPoints.title")}}>
                <Modal.Body>
                    <TextField label={ t("main.table.modals.addPoints.pointsLabel") } size={"small"} ref={pointRef}/>
                </Modal.Body>
                <Modal.Footer>
                    <Button type={"button"} variant={"tertiary"} onClick={() => {
                        setModalOpenFor(null)
                        setDisableButtons(false)
                    }}>
                        {t("main.table.modals.buttons.close")}
                    </Button>
                    <Button type={"button"} color={"success"} onClick={() => {
                        if (modalOpenFor) {
                            onAddPoints(modalOpenFor, Number(pointRef.current?.value))
                        }
                        setDisableButtons(false)
                        setModalOpenFor(null)
                    }}>
                        {t("main.table.modals.buttons.submit")}
                    </Button>
                </Modal.Footer>
            </Modal>
        </div>
    )
}

export default MembersTable
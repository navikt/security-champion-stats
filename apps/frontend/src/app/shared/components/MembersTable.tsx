"use client"

import {Member} from "../utils/variable";
import {useTranslations} from "next-intl";
import "../../style/localStyling.css"
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
    const modalRef = useRef<HTMLDialogElement>(null)
    const [disableButtons, setDisableButtons] = useState(false)
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
                        <th> {t("member.fullname")} </th>
                        <th> {t("member.points")} </th>
                        <th> {t("member.level")} </th>
                        {canEdit && (
                            <th className={"membersTable__actionsHeader"}>
                                {t("dashboard.adminActions")}
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
                        <div className={"cardBody"}>
                            <Modal ref={modalRef} header={{ heading: t("dashboard.modals.addPoints.title")}}>
                                <Modal.Body>
                                    <TextField label={ t("dashboard.modals.addPoints.pointsLabel") } size={"small"} ref={pointRef}/>
                                </Modal.Body>
                                <Modal.Footer>
                                    <Button type={"button"} variant={"tertiary"} onClick={() => {
                                        modalRef.current?.close()
                                        setDisableButtons(false)
                                    }}>
                                        {t("dashboard.modals.buttons.cancel")}
                                    </Button>
                                    <Button type={"button"} color={"success"} onClick={() => {
                                        onAddPoints(m.email, Number(pointRef.current?.value))
                                        setDisableButtons(false)
                                        modalRef.current?.close()
                                    }}>
                                        {t("dashboard.modals.buttons.submit")}
                                    </Button>
                                </Modal.Footer>
                            </Modal>
                        </div>
                        {canEdit && (
                            <td className={"membersTable__actionsCell"}>
                                <button
                                    type="button"
                                    className={"btn outline"}
                                    onClick={() => {
                                        modalRef.current?.showModal()
                                        setDisableButtons(true)
                                    }}
                                    disabled={disableButtons}
                                >
                                    {t("dashboard.buttons.admin.addPoints")}
                                </button>
                                <button
                                    type="button"
                                    className={"btn danger"}
                                    onClick={() => onDelete(m.email)}
                                >
                                    {t("dashboard.buttons.admin.deleteMember")}
                                </button>
                            </td>
                        )}
                    </tr>
                ))}
                {members.length === 0 && (
                    <tr>
                        <td colSpan={columnCount}>
                            {t("dashboard.noMembers")}
                        </td>
                    </tr>
                )}
                </tbody>
            </table>
        </div>
    )
}

export default MembersTable
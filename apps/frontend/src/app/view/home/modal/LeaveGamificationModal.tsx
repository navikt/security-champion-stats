import {BodyLong, Button, Heading, Modal} from "@navikt/ds-react";
import {useTranslations} from "next-intl";
import "../../../style/home/LeaveModal.css"

interface LeaveGamificationModalProps {
    open: boolean
    onClose: () => void
    onConfirm: () => Promise<void> | void
    loading?: boolean
}

export function LeaveGamificationModal({
    open,
    onClose,
    onConfirm,
    loading = false
}: LeaveGamificationModalProps) {
    const t = useTranslations("home.membership.member.leaveGamificationModal")
    return (
        <Modal
        open={open}
        onClose={onClose}
        aria-labelledby={"leave-gamification-title"}
        width={"small"}
        >
            <Modal.Header>
                <Heading
                    size={"medium"}
                    id={"leave-gamification-title"}
                    level={"2"}
                >
                    {t("question")}
                </Heading>
            </Modal.Header>
            <Modal.Body>
                <div className={"leaveGameModal"}>
                    <BodyLong>
                        {t("description")}
                    </BodyLong>

                    <section className={"leaveGameModal__section"}>
                        <Heading size={"small"} level={"3"}>
                            What changes
                        </Heading>

                        <ul className={"leaveGameModal__list"}>
                            <li>Xp progression will stop</li>
                            <li>Your champion level will no longer be active</li>
                        </ul>
                    </section>

                    <section className={"leaveGameModal__section"}>
                        <Heading size={"small"} level={"3"}> What stays the same </Heading>
                        <ul className={"leaveGameModal__list"}>
                            <li>You remain a Security Champion</li>
                            <li>You can still attend meetings and workshops</li>
                            <li>You keep access to program resources</li>
                        </ul>
                    </section>
                </div>
            </Modal.Body>
            <Modal.Footer>
                <Button
                    variant={"danger"}
                    loading={loading}
                    onClick={onConfirm}
                >
                    {t("confirm")}
                </Button>
                <Button variant={"secondary"} onClick={onClose} disabled={loading}>
                    {t("cancel")}
                </Button>
            </Modal.Footer>
        </Modal>
    )
}
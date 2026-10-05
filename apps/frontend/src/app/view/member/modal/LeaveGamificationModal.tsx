import { BodyLong, Button, Heading, Modal } from "@navikt/ds-react";
import "../../../style/home/LeaveModal.css";

interface LeaveGamificationModalProps {
	open: boolean;
	onClose: () => void;
	onConfirm: () => Promise<void> | void;
	loading?: boolean;
}

export function LeaveGamificationModal({
	open,
	onClose,
	onConfirm,
	loading = false,
}: LeaveGamificationModalProps) {
	return (
		<Modal
			open={open}
			onClose={onClose}
			aria-labelledby={"leave-gamification-title"}
			width={"small"}
		>
			<Modal.Header>
				<Heading size={"medium"} id={"leave-gamification-title"} level={"2"}>
					Are you sure you want to leave gamification?
				</Heading>
			</Modal.Header>
			<Modal.Body>
				<div className={"leaveGameModal"}>
					<BodyLong>
						You will no longer be able to earn XP and unlock levels and
						achievements.
					</BodyLong>

					<section className={"leaveGameModal__section"}>
						<Heading size={"small"} level={"3"}>
							What changes
						</Heading>

						<ul className={"leaveGameModal__list"}>
							<li>XP progression will stop</li>
							<li>Your champion level will no longer be active</li>
						</ul>
					</section>

					<section className={"leaveGameModal__section"}>
						<Heading size={"small"} level={"3"}>
							{" "}
							What stays the same
						</Heading>
						<ul className={"leaveGameModal__list"}>
							<li>You remain a Security Champion</li>
							<li>You can still see all events and people in the community</li>
						</ul>
					</section>
				</div>
			</Modal.Body>
			<Modal.Footer>
				<Button variant={"danger"} loading={loading} onClick={onConfirm}>
					Confirm
				</Button>
				<Button variant={"secondary"} onClick={onClose} disabled={loading}>
					Cancel
				</Button>
			</Modal.Footer>
		</Modal>
	);
}

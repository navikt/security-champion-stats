import { BodyLong, Button, Heading, Modal } from "@navikt/ds-react";
import "../../../style/home/LeaveModal.css";

interface LeaveProgramModalProps {
	open: boolean;
	onClose: () => void;
	onConfirm: () => Promise<void> | void;
	loading?: boolean;
	error?: string | null;
}

export function LeaveProgramModal({ open, onClose, onConfirm, loading = false, error }: LeaveProgramModalProps) {
	return (
		<Modal
			open={open}
			onClose={() => {
				if (!loading) onClose();
			}}
			aria-labelledby={"leave-program-title"}
			width={"small"}
		>
			<Modal.Header>
				<Heading size={"medium"} id={"leave-program-title"} level={"2"}>
					Leave the Security Champion program?
				</Heading>
			</Modal.Header>
			<Modal.Body>
				<div className={"leaveGameModal"}>
					<BodyLong>You will no longer earn points while inactive or appear on the active leaderboard.</BodyLong>

					<section className={"leaveGameModal__section"}>
						<Heading size={"small"} level={"3"}>
							What changes
						</Heading>

						<ul className={"leaveGameModal__list"}>
							<li>Your program participation becomes inactive</li>
							<li>New credits stop while you are inactive</li>
						</ul>
					</section>

					<section className={"leaveGameModal__section"}>
						<Heading size={"small"} level={"3"}>
							What stays the same
						</Heading>
						<ul className={"leaveGameModal__list"}>
							<li>Your membership history and existing credits are retained</li>
							<li>You can still see all events and people in the community</li>
							<li>You can rejoin; activity discovered after rejoining may earn points</li>
						</ul>
					</section>
				</div>
				{error && <BodyLong role="alert">{error}</BodyLong>}
			</Modal.Body>
			<Modal.Footer>
				<Button data-color="danger" loading={loading} disabled={loading} onClick={onConfirm}>
					Leave program
				</Button>
				<Button variant={"secondary"} onClick={onClose} disabled={loading}>
					Cancel
				</Button>
			</Modal.Footer>
		</Modal>
	);
}

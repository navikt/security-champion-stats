import { useEffect, useState } from "react";
import { Me } from "../../utils/Variables";
import { Apies } from "./Apies";

export function notifyMembershipChanged(me: Me) {
	window.dispatchEvent(new CustomEvent<Me>("membership-changed", { detail: me }));
}

export function useMe() {
	const [me, setMe] = useState<Me>({
		username: "",
		displayName: null,
		isAdmin: false,
		isParticipant: false,
		isActive: false,
	});
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		let current = true;
		let membershipChanged = false;
		const onMembershipChanged = (event: Event) => {
			if (!(event instanceof CustomEvent)) return;
			membershipChanged = true;
			setMe(event.detail);
			setLoading(false);
		};
		window.addEventListener("membership-changed", onMembershipChanged);
		const validate = async () => {
			const meData = await Apies.validatePerson();
			if (current && !membershipChanged) {
				setMe(meData);
				setLoading(false);
			}
		};
		validate();
		return () => {
			current = false;
			window.removeEventListener("membership-changed", onMembershipChanged);
		};
	}, []);

	return { me, loading };
}

import { useEffect, useState } from "react";
import { Me } from "../../utils/Variables";
import { Apies } from "./Apies";

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
		const validate = async () => {
			const meData = await Apies.validatePerson();
			setMe(meData);
			setLoading(false);
		};
		validate();
	}, []);

	return { me, loading };
}

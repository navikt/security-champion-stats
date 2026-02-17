import { useEffect, useState } from "react";
import { Me } from "../utils/variable";
import { Apies } from "./Apies";

export function useMe() {
    const [me, setMe] = useState<Me>({ username: "", isAdmin: false, inProgram: false });
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const validate = async () => {
            console.log("Fetching user data with useMe hook...");
            const meData = await Apies.validatePerson();
            setMe(meData);
            setLoading(false);
        };
        validate();
    }, []);

    return { me, loading };
}


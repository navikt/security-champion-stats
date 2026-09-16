"use client";

import { useState } from "react";
import { Box, Button, Heading, Switch, TextField } from "@navikt/ds-react";
import { Apies } from "@/app/shared/hooks/Apies";
import { ChallengeMember } from "@/app/utils/Variables";

const emptyMember: ChallengeMember = {
    id: "",
    fullname: "",
    email: "",
    points: 0,
    level: "1",
    inProgram: false,
};

export default function Page() {
    const [member, setMember] = useState<ChallengeMember>(emptyMember);
    const [status, setStatus] = useState<string | null>(null);

    const handleSubmit = async () => {
        setStatus(null);
        const code = await Apies.upsertChallengeMember(member);
        setStatus(code >= 200 && code < 300 ? "Saved" : `Failed (status ${code})`);
    };

    return (
        <Box padding="6">
            <Heading level="1" size="large" spacing>
                Challenge member setup
            </Heading>

            <TextField
                label="Id"
                value={member.id}
                onChange={(e) => setMember({ ...member, id: e.target.value })}
            />
            <TextField
                label="Fullname"
                value={member.fullname}
                onChange={(e) => setMember({ ...member, fullname: e.target.value })}
            />
            <TextField
                label="Email"
                value={member.email}
                onChange={(e) => setMember({ ...member, email: e.target.value })}
            />
            <TextField
                label="Points"
                type="number"
                value={member.points}
                onChange={(e) => setMember({ ...member, points: Number(e.target.value) })}
            />
            <TextField
                label="Level"
                value={member.level}
                onChange={(e) => setMember({ ...member, level: e.target.value })}
            />
            <Switch
                checked={member.inProgram}
                onChange={(e) => setMember({ ...member, inProgram: e.target.checked })}
            >
                In program
            </Switch>

            <Button onClick={handleSubmit}>Save</Button>

            {status && <p>{status}</p>}
        </Box>
    );
}

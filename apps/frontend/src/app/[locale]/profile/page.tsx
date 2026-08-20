import {
    Heading,
    BodyShort,
} from "@navikt/ds-react";

export default function ProfilePage() {
    return (
        <main className="standardPage">
            <Heading
                level="1"
                size="xlarge"
            >
                My profile
            </Heading>

            <BodyShort>
                Your Security Champion profile
                and program information.
            </BodyShort>
        </main>
    );
}
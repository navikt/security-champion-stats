import {
    Heading,
    BodyShort,
} from "@navikt/ds-react";

export default function EventsPage() {
    return (
        <main className="standardPage">
            <Heading
                level="1"
                size="xlarge"
            >
                Events
            </Heading>

            <BodyShort>
                Meetings, workshops and
                Security Champion activities.
            </BodyShort>
        </main>
    );
}
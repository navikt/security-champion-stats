import {BodyShort, Button, Heading} from "@navikt/ds-react";

export function JoinProgramView() {
    const handleJoin = () => {

    }

    return (
        <section className="sc-membership-card sc-membership-card--guest">
            <div className="sc-membership-card__content">
                <span className="sc-membership-card__status">
                    Temp Not enrolled
                </span>

                <Heading
                    level="2"
                    size="large"
                    className="sc-membership-card__title"
                >
                    Temp Become a Security Champion
                </Heading>

                <BodyShort className="sc-membership-card__description">
                    Temp Join the program to participate in
                    workshops, access security resources
                    and connect with other champions.
                </BodyShort>
                {/*TODO: go over them and potentially remove all of them*/}
                <ul className="sc-benefit-list">
                    <li>
                        Temp Attend meetings and workshops
                    </li>

                    <li>
                        Temp Participate in optional XP and levels
                    </li>
                </ul>
                {/* TODO: Add button functionality*/ }
                <div className="sc-membership-card__actions">
                    <Button
                        variant="primary"
                        onClick={handleJoin}
                    >
                        Temp Join the program
                    </Button>

                    <Button variant="secondary">
                        Temp Learn more
                    </Button>
                </div>
            </div>

            <div
                className="sc-membership-card__visual"
                aria-hidden="true"
            >
                <div className="sc-rank-emblem">
                    +
                </div>
                {/*TODO: look over this and decide to keep it or not*/}
                <p className="sc-rank-name">
                    Temp Your journey starts here
                </p>

                <p className="sc-rank-level">
                    Temp Levels and XP are optional
                </p>
            </div>
        </section>
    )
}
import {Member} from "@/app/utils/Variables";
import {BodyShort, Button, Heading} from "@navikt/ds-react";

interface JoinedMembershipViewProps {
    member: Member
}

export function JoinedMembershipView({
    member,
}: JoinedMembershipViewProps) {
    const inGame = member.inGame

    const points = member.points
    const progress = Math.min(100, Math.max(
        0, (points / 200) * 100
    ))

    const handleLeaveGame = () => {

    }

    const handleJoinGame = () => {

    }

    const handleLeaveProgram = () => {

    }

    return (
        <section className={"sc-membership-card sc-membership-card--joined"}>
            <div className={"sc-membership-card__content"}>
                <span className={"sc-membership-card__status"}>
                    temp Active member
                </span>

                <Heading
                    size={"large"}
                    level={"2"}
                    className={"sc-membership-card__description"}
                >
                    Temp Security Champion
                </Heading>

                <BodyShort className={"sc-membership-card__description"}>
                    Temp You are an active member....
                </BodyShort>

                <dl className="sc-membership-card__facts">
                    <dt>Joined</dt>
                    <dd>
                        {new Date(
                            member.joinedAt
                        ).toLocaleDateString()}
                    </dd>

                    <dt>Temp Gamification</dt>
                    <dd>
                        {inGame
                            ? "Enabled"
                            : "Disabled"}
                    </dd>
                </dl>

                <div className="sc-membership-card__actions">
                    <Button variant="primary">
                        {/*TODO: add button functional*/}
                        Temp View my profile
                    </Button>

                    <Button variant="secondary">
                        {/*TODO: add button functional*/}
                        Temp Program settings
                    </Button>
                </div>
            </div>
            {/* TODO: ADD button to join */}
            {inGame && member.level && (
                <div className="sc-membership-card__visual">
                    <div
                        className="sc-rank-emblem"
                        aria-hidden="true"
                    >
                        Temp ★
                    </div>
                    <p className="sc-rank-name">
                        Temp level name
                    </p>

                    <p className="sc-rank-level">
                        Temp level the actual level
                    </p>

                    <div className="sc-progress">
                        <div className="sc-progress__label">
                            <span>
                                Temp {points} / {200} XP
                            </span>

                            <span>
                               Temp {Math.round(progress)}%
                            </span>
                        </div>

                        <div
                            className="sc-progress__track"
                            role="progressbar"
                            aria-valuemin={0}
                            aria-valuemax={200}
                            aria-valuenow={points}
                        >
                            <div
                                className="sc-progress__value"
                                style={
                                    {
                                        "--sc-progress-value":
                                            `${progress}%`,
                                    } as React.CSSProperties
                                }
                            />
                        </div>

                        <p className="sc-progress__meta">
                            Temp {Math.max(
                                200 - points,
                                0,
                            )}{" "}
                            XP to next level
                        </p>
                    </div>
                </div>
            )}
        </section>
    )
}
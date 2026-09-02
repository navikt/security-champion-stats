import {Member} from "@/app/utils/Variables";
import {BodyShort, Button, Heading} from "@navikt/ds-react";
import {useTranslations} from "next-intl";
import {useState} from "react";
import {LeaveGamificationModal} from "@/app/view/home/modal/LeaveGamificationModal";

interface JoinedMembershipViewProps {
    member: Member
}

export function JoinedMembershipView({
    member,
}: JoinedMembershipViewProps) {
    const inGame = member.inGame
    const t = useTranslations("home.membership.member")

    const points = member.points
    const progress = Math.min(100, Math.max(
        0, (points / 200) * 100
    ))

    const [leaveGameOpen, setLeaveGameOpen] = useState(false)

    const handleLeaveGame = () => {

    }

    const handleJoinGame = () => {

    }

    const handleLeaveProgram = () => {

    }

    const fetchGameButtonValue = () => {
        return inGame ? t("leaveGame") : t("joinGame")
    }

    const fetchLevelName = () => {
        switch (member.level) {
            case "1": return t("levelNames.level1")
            case "2": return t("levelNames.level2")
            case "3": return t("levelNames.level3")
            default: return t("levelNames.level1")
        }
    }

    const fetchLevelValue = () => {
        switch (member.level) {
            case "1": return t("level.level1")
            case "2": return t("level.level2")
            case "3": return t("level.level3")
            default: return t("level.level1")
        }
    }

    return (
        <section className={"sc-membership-card sc-membership-card--joined"}>
            <div className={"sc-membership-card__content"}>
                <span className={"sc-membership-card__status"}>
                    {t("status")}
                </span>

                <Heading
                    size={"large"}
                    level={"2"}
                    className={"sc-membership-card__description"}
                >
                    {t("securityChampion")}
                </Heading>

                <BodyShort className={"sc-membership-card__description"}>
                    {t("description")}
                </BodyShort>

                <dl className="sc-membership-card__facts">
                    <dt>{t("joined")}</dt>
                    <dd>
                        {new Date(
                            member.joinedAt
                        ).toLocaleDateString()}
                    </dd>

                    <dt>{t("gamification")}</dt>
                    <dd>
                        {inGame
                            ? t("enabled")
                            : t("disabled")}
                    </dd>
                </dl>

                <div className="sc-membership-card__actions">
                    <Button variant="secondary-neutral" onClick={() => setLeaveGameOpen(true)}>
                        {fetchGameButtonValue()}
                    </Button>

                    <Button variant="secondary">
                        {t("leave")}
                    </Button>
                </div>
            </div>
            {inGame && member.level && (
                <div className="sc-membership-card__visual">
                    <div
                        className="sc-rank-emblem"
                        aria-hidden="true"
                    >
                        Temp ★
                    </div>
                    <p className="sc-rank-name">
                        {fetchLevelName()}
                    </p>

                    <p className="sc-rank-level">
                        {fetchLevelValue()}
                    </p>

                    <div className="sc-progress">
                        <div className="sc-progress__label">
                            <span>
                                Temp {points} / {200} XP
                            </span>

                            <span>
                               {Math.round(progress)}%
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
            {!inGame && (
                <div className={"sc-membership-card__visual"}>
                    <div className={"sc-gamification-empty__icon"} aria-hidden={"true"}>

                    </div>
                    <Heading size={"medium"} level={"3"}>
                        {t("gamification")}
                    </Heading>

                    <BodyShort className={"sc-gamification-empty__status"}>
                        {t("optional")}
                    </BodyShort>

                    <BodyShort className={"sc-gamification-empty__description"}>
                        {t("information")}
                    </BodyShort>

                </div>
            )}
            <LeaveGamificationModal
                open={leaveGameOpen}
                onClose={() => setLeaveGameOpen(false)}
                onConfirm={handleLeaveGame}
            />
        </section>
    )
}
"use client"

import {useEffect, useRef, useState} from "react";
import {Me, Member} from "../utils/variable";
import {Apies} from "../hooks/Apies";
import {useTranslations} from "next-intl";
import MembersTable from "../components/MembersTable";
import "../../style/localStyling.css"
import {Button, Modal, TextField} from "@navikt/ds-react";

function View({ canEdit, me  }: { canEdit: boolean; me: Me }) {
    const [members, setMembers] = useState<Member[]>([])
    const [active, setActive] = useState(me.inProgram)
    const [disableMemberButton, setDisableMemberButton] = useState(false)
    const modalRef = useRef<HTMLDialogElement>(null);
    const memberRef = useRef<HTMLInputElement>(null)
    const t = useTranslations()
    const safeMembers = members ?? []

    const totalPoints = safeMembers.reduce((sum, member) => sum + member.points, 0)
    const topMember = safeMembers.toSorted((a, b) => b.points - a.points)[0]
    const statusKey = active ? "dashboard.statusLive" : "dashboard.statusIdle"
    const subtitleKey = active ? "dashboard.subtitleLive" : "dashboard.subtitleIdle"

    const reload = async () => {
        const ms = await Apies.getMembers()
        console.log("Fetched members:", ms)
        setMembers(ms ?? [])
    }

    useEffect(() => {
       void reload()
    }, [])

    const addMember = async (email?: string) => {
        if(!email) {
            return
        }
        await Apies.addMember(email)
        await reload()
    }

    const addPoints = async (email?: string, points?: number) => {
        if (!email || !points) {
            return
        }
        await Apies.addPoints(email, points)
        await reload()
    }

    const joinProgram = async () => {
        await Apies.joinProgram(me.username)
        await reload()
        setActive(true)
    }

    const deleteMember = async (email: string) => {
        await Apies.deleteMember(email)
        await reload()
    }

    return (
        <div className={"dashboardView"}>
            <section className={"gamePanel"}>
                <div>
                    <p className={`gameStatus ${active ? "gameStatus--live" : "gameStatus--sleep"}`}>
                        {t(statusKey)}
                    </p>
                    <h2 className={"gameTitle"}>{t("common.appTitle")}</h2>
                    <p className={"gameSubtitle"}>
                        {t(subtitleKey)}
                    </p>
                    {!active && (
                        <div className={"gameCTA"}>
                            <p>{t("dashboard.ctaHint")}</p>
                            <button className={"btn outline"} onClick={joinProgram} disabled={!active}>
                                {t("dashboard.buttons.joinProgram")}
                            </button>
                        </div>
                    )}
                </div>
                <div className={"statGrid"}>
                    <div className={"statCard"}>
                        <p className={"statLabel"}>{t("dashboard.stats.players")}</p>
                        <p className={"statValue"}>{members.length || "--"}</p>
                    </div>
                    <div className={"statCard"}>
                        <p className={"statLabel"}>{t("dashboard.stats.points")}</p>
                        <p className={"statValue"}>{totalPoints.toLocaleString()}</p>
                    </div>
                    <div className={"statCard"}>
                        <p className={"statLabel"}>{t("dashboard.stats.topAgent")}</p>
                        <p className={"statValue"}>{topMember?.fullname ?? t("dashboard.stats.topAgentFallback")}</p>
                        {topMember && (
                            <span className={"statMeta"}>
                                {t("dashboard.stats.topAgentMeta", { points: topMember.points.toLocaleString() })}
                            </span>
                        )}
                    </div>
                </div>
            </section>


            <section className={"card membersCard"}>
                <header className={"cardHeader"}>
                    <div>
                        <p className={"cardEyebrow"}>{t("dashboard.leaderboardEyebrow")}</p>
                        <h3 className={"cardTitle"}>{t("dashboard.leaderboardTitle")}</h3>
                    </div>
                    <div className={"cardMeta"}>
                        <span>{t("dashboard.cardAgentsLabel", { count: safeMembers.length })}</span>
                        <span>{t("dashboard.cardPointsLabel", { points: totalPoints.toLocaleString() })}</span>
                    </div>
                </header>
                <div className={"cardBody"}>
                    <Modal ref={modalRef} header={{ heading: t("dashboard.modals.addMember.title") }}>
                        <Modal.Body>
                            <TextField label={ t("dashboard.modals.addMember.email") } size={"small"} ref={memberRef}/>
                        </Modal.Body>
                        <Modal.Footer>
                            <Button type={"button"} variant={"tertiary"} onClick={() => {
                                modalRef.current?.close()
                                setDisableMemberButton(false)
                            }}>
                                {t("dashboard.modals.buttons.close")}
                            </Button>
                            <Button type={"button"} color={"success"} onClick={() => {
                                addMember(memberRef.current?.value)
                                modalRef.current?.close()
                                setDisableMemberButton(false)
                            }}>
                                {t("dashboard.modals.buttons.submit")}
                            </Button>
                        </Modal.Footer>
                    </Modal>
                    <MembersTable members={safeMembers} onDelete={deleteMember} onAddPoints={addPoints} canEdit={canEdit} />
                    {canEdit && (
                        <div className={"membersTable__footer"}>
                            <button className={"btn neon"} onClick={() => {
                                modalRef.current?.showModal()
                                setDisableMemberButton(true)
                            }} disabled={disableMemberButton}>
                                {t("dashboard.buttons.admin.addMember")}
                            </button>
                        </div>
                    )}
                </div>
            </section>
        </div>
    )
}

export function UserView({ info }: { info: Me }) {
    return <View canEdit={ false } me={ info } />
}

export function AdminView({ info } : { info: Me }) {
    return <View canEdit={true} me={ info }/>
}
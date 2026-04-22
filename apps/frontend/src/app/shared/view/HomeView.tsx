"use client"

import {useEffect, useRef, useState} from "react";
import {Me, Member} from "../utils/Variables";
import {Apies} from "../hooks/Apies";
import {useTranslations} from "next-intl";
import MembersTable from "../components/MembersTable";
import "../../style/home.page.css"
import {Button, Modal, TextField} from "@navikt/ds-react";

function View({ canEdit, me  }: { canEdit: boolean; me: Me }) {
    const [userData, setMe] = useState(me)
    const [members, setMembers] = useState<Member[]>([])
    const [active, setActive] = useState(userData.inProgram)
    const modalRef = useRef<HTMLDialogElement>(null)
    const memberFullnameRef = useRef<HTMLInputElement>(null)
    const memberEmailRef = useRef<HTMLInputElement>(null)
    const t = useTranslations()
    const safeMembers = members ?? []
    const totalPoints = safeMembers.reduce((sum, member) => sum + member.points, 0)
    const topMember = safeMembers.toSorted((a, b) => b.points - a.points)[0]
    const statusKey = active ? "main.statuses.active" : "main.statuses.idle"
    const subtitleKey = active ? "main.subtitles.active" : "main.subtitles.idle"
    const reload = async () => {
        const ms = await Apies.getMembers()
        setMembers(ms)
    }
    const reloadMe = async () => {
        setMe(await Apies.validatePerson())
    }

    useEffect(() => {
       void reload()
    }, [])

    const addMember = async (email?: string, fullname?: string) => {
        if(!email || !fullname) {
            return
        }
        await Apies.addMember(email, fullname)
        memberEmailRef.current = null
        memberFullnameRef.current = null
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
        await Apies.joinProgram(userData.username)
        await reload()
        await reloadMe()
        if (me.inProgram) setActive(true)
        else setActive(false)
    }

    const deleteMember = async (email: string) => {
        await Apies.deleteMember(email)
        await reload()
    }

    const leaveProgram = async() => {
        await Apies.leaveProgram(userData.username)
        await reload()
        await reloadMe()
        if (me.inProgram) setActive(true)
        else setActive(false)
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
                            <p>{t("main.cta.hintJoin")}</p>
                            <button className={"btn outline"} onClick={joinProgram} disabled={active}>
                                {t("main.table.buttons.joinProgram")}
                            </button>
                        </div>
                    )}
                    {active && (
                        <div className={"gameCTA"}>
                            <p>{t("main.cta.hintLeave")}</p>
                            <button className={"btn outline"} onClick={leaveProgram} disabled={!active}>
                                {t("main.table.buttons.leaveProgram")}
                            </button>
                        </div>
                    )}
                </div>
                <div className={"statGrid"}>
                    <div className={"statCard"}>
                        <p className={"statLabel"}>{t("main.gameStats.players")}</p>
                        <p className={"statValue"}>{members.length || "--"}</p>
                    </div>
                    <div className={"statCard"}>
                        <p className={"statLabel"}>{t("main.gameStats.points")}</p>
                        <p className={"statValue"}>{totalPoints.toLocaleString()}</p>
                    </div>
                    <div className={"statCard"}>
                        <p className={"statLabel"}>{t("main.gameStats.topAgent")}</p>
                        <p className={"statValue"}>{topMember?.fullname ?? t("main.gameStats.topAgentFallback")}</p>
                        {topMember && (
                            <span className={"statMeta"}>
                                {t("main.gameStats.topAgentMeta", { points: topMember.points.toLocaleString() })}
                            </span>
                        )}
                    </div>
                </div>
            </section>


            <section className={"card membersCard"}>
                <header className={"cardHeader"}>
                    <div>
                        <p className={"cardEyebrow"}>{t("main.table.leaderboardEyebrow")}</p>
                        <h3 className={"cardTitle"}>{t("main.table.leaderboardTitle")}</h3>
                    </div>
                    <div className={"cardMeta"}>
                        <span>{t("main.table.cardAgentsLabel", { count: safeMembers.length })}</span>
                        <span>{t("main.table.cardPointsLabel", { points: totalPoints.toLocaleString() })}</span>
                    </div>
                </header>
                <div className={"cardBody"}>
                    <Modal ref={modalRef} header={{ heading: t("main.table.modals.addMember.title") }}>
                        <Modal.Body>
                            <TextField label={ t("main.table.modals.addMember.email") } size={"small"} ref={memberEmailRef}/>
                            <TextField label={ t("main.table.modals.addMember.fullname") } size={"small"} ref={memberFullnameRef}/>
                        </Modal.Body>
                        <Modal.Footer>
                            <Button type={"button"} variant={"tertiary"} onClick={() => {
                                modalRef.current?.close()
                            }}>
                                {t("main.table.modals.buttons.close")}
                            </Button>
                            <Button type={"button"} color={"success"} onClick={() => {
                                addMember(memberEmailRef.current?.value, memberFullnameRef.current?.value)
                                modalRef.current?.close()
                            }}>
                                {t("main.table.modals.buttons.submit")}
                            </Button>
                        </Modal.Footer>
                    </Modal>
                    <MembersTable members={safeMembers} onDelete={deleteMember} onAddPoints={addPoints} canEdit={canEdit} />
                    {canEdit && (
                        <div className={"membersTable__footer"}>
                            <button className={"btn neon"} onClick={() => {
                                modalRef.current?.showModal()
                            }} disabled={modalRef.current == null}>
                                {t("main.table.buttons.admin.addMember")}
                            </button>
                        </div>
                    )}
                </div>
            </section>
        </div>
    )
}

export function MainView({ info }: { info: Me }) {
    return <View canEdit={ info.isAdmin } me={ info } />
}

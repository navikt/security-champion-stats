export const AUTHENTICATED_FAILED = "Authentication failed"
export const FAILED_FETCH = "Failed fetch data"
export const INTERNAL_ERROR = "Internal server error"
export const MISSING_VALUE = "Failed fetch, due to missing value"
export const MISSING_GROUP = "Missing group id value, failed to validate admin"
export const FAILED_TO_JOIN = "Failed to join member to program, backend error"
export const FAILED_TO_LEAVE = "Failed to leave program, backend error"

export type Me = { username: string; isAdmin: boolean, isSecChamp: boolean, inGame: boolean };
export type Member = {
    id: string,
    email: string,
    points: number,
    fullname: string,
    level: string,
    inGame: boolean,
    joinedAt: string,
}
export type SCData = { timestamp: string, amount: number }
export type Row = { year: number, count: number }
export interface SecurityEvent {
    id: string,
    title: string,
    description: string,
    type: SecurityEventType,
    startsAt: string,
    endsAt: string,
    location?: string,
    onlineMeetingUrl?: string,
}

export type SecurityEventType = "meeting" | "workshop" | "course"
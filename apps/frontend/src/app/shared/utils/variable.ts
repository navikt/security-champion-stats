export const AUTHENTICATED_FAILED = "Authentication failed"
export const FAILED_FETCH = "Failed fetch data"
export const INTERNAL_ERROR = "Internal server error"
export const MISSING_VALUE = "Failed fetch, due to missing value"
export const MISSING_GROUP = "Missing group id value, failed to validate admin"
export const FAILED_TO_JOIN = "Failed to join member to program, backend error"

export type DeleteParam = { email: string }
export type Me = { username: string; isAdmin: boolean, inProgram: boolean };
export type Member = { id: string, email: string; points: number, fullname: string }

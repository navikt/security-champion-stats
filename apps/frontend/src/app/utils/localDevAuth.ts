
export function createLocalDevToken(email: string = "local.email@nav.no"): string {
    const header = { alg: "none", type: "JWT" }
    const payload = {
        preferred_username: email,
        sub: "local-dev-user",
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600, // 1 hour
    }

    const encodedHeader = Buffer.from(JSON.stringify(header)).toString("base64url");
    const encodedPayload = Buffer.from(JSON.stringify(payload)).toString("base64url");

    return `${encodedHeader}.${encodedPayload}.local-dev-signature`;
}
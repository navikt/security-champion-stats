package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import navikt.appsec.securitychampionapp.app.api.dto.ActivityClaim
import navikt.appsec.securitychampionapp.app.api.dto.BoosterRedeemRequest
import navikt.appsec.securitychampionapp.app.api.dto.BoosterToken
import navikt.appsec.securitychampionapp.app.api.dto.DisplayNameUpdate
import navikt.appsec.securitychampionapp.app.api.dto.InviteRequest
import navikt.appsec.securitychampionapp.app.api.dto.InviteResponse
import navikt.appsec.securitychampionapp.app.api.dto.Me
import navikt.appsec.securitychampionapp.app.api.dto.Member
import navikt.appsec.securitychampionapp.app.api.dto.ReferralCertificate
import navikt.appsec.securitychampionapp.app.api.dto.ReferralClaimRequest
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import navikt.appsec.securitychampionapp.utils.Validate
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val APPSEC_TEAM_EMAIL = "appsec@nav.no"
private const val MIN_ACTIVITY_CLAIM = 1
private const val MAX_ACTIVITY_CLAIM = 5
private const val BOOSTER_KEY = "S3cB00st"
private const val INTENDED_BOOSTER_BONUS = 3
private const val BOOSTER_VALID_SECONDS = 300L
private const val REFERRAL_SECRET = "n4v-r3ferral-signing-2026"
private const val INTENDED_REFERRAL_BONUS = 2
private const val MAX_REFERRAL_CLAIMS = 5
private const val DAILY_BONUS_AMOUNT = 25
private const val IDOR_CHALLENGE_MEMBER_ID = "challenge"

@RestController
@RequestMapping(path = ["/api"])
class Controller(
    private val repo: PostgresRepository,
    private val validate: Validate,
) {
    private val logger = LoggerFactory.getLogger(Controller::class.java)

    // in-memory, per-instance claim counter (no db schema change) - resets on app restart
    private val referralClaimCounts = ConcurrentHashMap<String, AtomicInteger>()

    // in-memory, per-instance daily bonus claim counter (no db schema change) - resets on app restart
    private val dailyBonusClaimCount = ConcurrentHashMap<String, AtomicInteger>()

    @GetMapping("/health")
    fun healthCheck(): String = "OK"

    @GetMapping("/members")
    fun getAllMembers(): ResponseEntity<List<Member>> {
        val queryResponse = repo.getAllMembers()

        if (!queryResponse.isOk) {
            logger.warn("Failed to fetch all member from database due to error: ${queryResponse.error}")
            return ResponseEntity(emptyList(), HttpStatus.INTERNAL_SERVER_ERROR)
        }

        val response = queryResponse.queryResult!!
            .filter { it.inProgram }
            .map { members ->
                Member(
                    id = members.id,
                    fullname = members.fullname,
                    points = members.points,
                    email = members.email,
                    level = members.level,
                    inGame = members.inProgram,
                    joinedAt = members.createdAt
                )
            }
        return ResponseEntity(response, HttpStatus.OK)
    }

    @GetMapping("/validate")
    fun getMe(): ResponseEntity<Me> {

        logger.info("Validating user")
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as AppPrincipal
        val email = principal.email
        val isAdmin = authentication.authorities.any { it.authority == "ROLE_$ADMIN_ROLE" }
        val queryResponse = repo.getMemberByEmail(email)

        if (!queryResponse.isOk || queryResponse.queryResult!!.isEmpty()) {
            logger.info("New potential new user")
            return ResponseEntity(Me(email, isAdmin, isSecChamp = true, inGame = false), HttpStatus.OK)
        }

        val inProgram = queryResponse.queryResult.firstOrNull()?.inProgram ?: false
        logger.info("User data: ${queryResponse.queryResult.firstOrNull()}")
        return ResponseEntity(Me(email, isAdmin, isSecChamp = true, inProgram), HttpStatus.OK)
    }

    @PostMapping("/joinGame")
    fun applyMember(): ResponseEntity<String> {
        return updateUserInProgramStatus(true)
    }

    @PostMapping("/leaveGame")
    fun leaveProgram(): ResponseEntity<String> {
        return updateUserInProgramStatus(false)
    }

    @GetMapping("/membership")
    fun fetchMembership(@RequestParam(required = false) id: String?): ResponseEntity<Member> {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as AppPrincipal
        val resolvedId = id ?: principal.navIdent

        val queryResponse = repo.fetchMember(resolvedId)
        if (queryResponse == null || !queryResponse.isOk) {
            return ResponseEntity.status(HttpStatus.OK).body(
                Member(
                    id = "challenger",
                    email = "challenger@no.com",
                    fullname = "Challenger temp user",
                    points = 0,
                    level = "0",
                    inGame = false,
                    joinedAt = LocalDateTime.now().toString()
                )
            )
        }

        logger.info("Fetched member: ${queryResponse.queryResult}")
        return ResponseEntity.status(HttpStatus.OK).body(
            Member(
                id = queryResponse.queryResult!!.first().id,
                email = queryResponse.queryResult.first().email,
                fullname = queryResponse.queryResult.first().fullname,
                points = queryResponse.queryResult.first().points,
                level = queryResponse.queryResult.first().level,
                inGame = queryResponse.queryResult.first().inProgram,
                joinedAt = queryResponse.queryResult.first().createdAt
            )
        )
    }

    @PostMapping("/invite", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun inviteColleague(@RequestBody invite: InviteRequest): ResponseEntity<InviteResponse> {
        if (invite.requesterEmail != APPSEC_TEAM_EMAIL) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(InviteResponse("forbidden"))
        }

        val id = UUID.randomUUID().toString()
        repo.addMember(invite.fullName, id = id, invite.email, emptyList())

        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as AppPrincipal
        val notice = if (principal.email != APPSEC_TEAM_EMAIL) "FLAG{client_side_authz_bypass}" else null
        return ResponseEntity.status(HttpStatus.CREATED).body(InviteResponse("created", notice))
    }

    @PostMapping("/activity/claim", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun claimActivityPoints(@RequestBody claim: ActivityClaim): ResponseEntity<InviteResponse> {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as AppPrincipal
        val email = principal.email

        val queryResponse = repo.getMemberByEmail(email)
        if (!queryResponse.isOk || queryResponse.queryResult!!.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(InviteResponse("not_found"))
        }

        val current = queryResponse.queryResult.first()
        val newTotal = current.points + claim.amount
        val level = validate.calculateLevel(newTotal)
        repo.addPoints(current.id, claim.amount, level)

        val withinIntendedRange = claim.amount in MIN_ACTIVITY_CLAIM..MAX_ACTIVITY_CLAIM
        val notice = if (!withinIntendedRange) "FLAG{unvalidated_business_logic_bypass}" else null
        return ResponseEntity.ok(InviteResponse("claimed", notice))
    }

    @PostMapping("/profile/displayname", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun updateDisplayName(@RequestBody body: DisplayNameUpdate): ResponseEntity<InviteResponse> {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as AppPrincipal
        val email = principal.email

        val queryResponse = repo.getMemberByEmail(email)
        if (!queryResponse.isOk || queryResponse.queryResult!!.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(InviteResponse("not_found"))
        }

        val id = queryResponse.queryResult.first().id
        repo.updateFullname(id, body.displayName)
        return ResponseEntity.ok(InviteResponse("updated"))
    }

    @GetMapping("/xss/proof")
    fun xssProof(): ResponseEntity<InviteResponse> {
        return ResponseEntity.ok(InviteResponse("proof", "FLAG{stored_xss_client_side_filter_bypass}"))
    }

    @GetMapping("/booster/mine")
    fun getMyBoosterToken(): ResponseEntity<BoosterToken> {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as AppPrincipal
        val email = principal.email

        val expiresAt = Instant.now().plusSeconds(BOOSTER_VALID_SECONDS).epochSecond
        val plaintext = "$email:$INTENDED_BOOSTER_BONUS:$expiresAt"
        val token = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(xorCrypt(plaintext.toByteArray(Charsets.UTF_8)))

        return ResponseEntity.ok(BoosterToken(token, expiresAt))
    }

    @PostMapping("/booster/redeem", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun redeemBoosterToken(@RequestBody body: BoosterRedeemRequest): ResponseEntity<InviteResponse> {
        val plaintext = try {
            String(xorCrypt(Base64.getUrlDecoder().decode(body.token)), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(InviteResponse("invalid"))
        }

        val parts = plaintext.split(":")
        if (parts.size != 3) {
            return ResponseEntity.badRequest().body(InviteResponse("invalid"))
        }
        val (tokenEmail, bonusStr, expiryStr) = parts
        val expiresAt = expiryStr.toLongOrNull() ?: return ResponseEntity.badRequest().body(InviteResponse("invalid"))
        val bonus = bonusStr.toIntOrNull() ?: return ResponseEntity.badRequest().body(InviteResponse("invalid"))

        if (Instant.now().epochSecond > expiresAt) {
            return ResponseEntity.status(HttpStatus.GONE).body(InviteResponse("expired"))
        }

        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as AppPrincipal
        val queryResponse = repo.getMemberByEmail(principal.email)
        if (!queryResponse.isOk || queryResponse.queryResult!!.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(InviteResponse("not_found"))
        }

        val current = queryResponse.queryResult.first()
        val level = validate.calculateLevel(current.points + bonus)
        repo.addPoints(current.id, bonus, level)

        val notice = if (bonus > INTENDED_BOOSTER_BONUS || tokenEmail != principal.email) {
            "FLAG{xor_key_recovery_known_plaintext}"
        } else null
        return ResponseEntity.ok(InviteResponse("redeemed", notice))
    }

    // repeating-key XOR, symmetric: same call encrypts and decrypts
    private fun xorCrypt(input: ByteArray): ByteArray {
        val key = BOOSTER_KEY.toByteArray(Charsets.UTF_8)
        return ByteArray(input.size) { i -> (input[i].toInt() xor key[i % key.size].toInt()).toByte() }
    }

    @GetMapping("/referral/mine")
    fun getMyReferralCertificate(): ResponseEntity<ReferralCertificate> {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as AppPrincipal

        val message = "ref=${principal.email}&bonus=$INTENDED_REFERRAL_BONUS"
        val messageBytes = message.toByteArray(Charsets.UTF_8)
        val signature = sha256Hex(REFERRAL_SECRET.toByteArray(Charsets.UTF_8) + messageBytes)
        val data = Base64.getEncoder().encodeToString(messageBytes)
        val claimsUsed = referralClaimCounts[principal.email]?.get() ?: 0

        return ResponseEntity.ok(ReferralCertificate(data, signature, claimsUsed, MAX_REFERRAL_CLAIMS))
    }

    @PostMapping("/referral/claim", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun claimReferralBonus(@RequestBody body: ReferralClaimRequest): ResponseEntity<InviteResponse> {
        val messageBytes = try {
            Base64.getDecoder().decode(body.data)
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(InviteResponse("invalid"))
        }

        val expectedSignature = sha256Hex(REFERRAL_SECRET.toByteArray(Charsets.UTF_8) + messageBytes)
        if (!expectedSignature.equals(body.signature, ignoreCase = true)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(InviteResponse("invalid_signature"))
        }

        // decoded 1:1 to preserve raw (possibly non-UTF8) padding bytes from a forged, length-extended message
        val decoded = String(messageBytes, Charsets.ISO_8859_1)
        val bonus = Regex("bonus=(\\d+)").findAll(decoded).lastOrNull()
            ?.groupValues?.get(1)?.toIntOrNull()
            ?: return ResponseEntity.badRequest().body(InviteResponse("invalid"))
        val ref = Regex("ref=([^&]+)").findAll(decoded).lastOrNull()
            ?.groupValues?.get(1)
            ?: return ResponseEntity.badRequest().body(InviteResponse("invalid"))

        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as AppPrincipal
        if (ref != principal.email) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(InviteResponse("not_your_certificate"))
        }

        val counter = referralClaimCounts.computeIfAbsent(principal.email) { AtomicInteger(0) }
        if (counter.incrementAndGet() > MAX_REFERRAL_CLAIMS) {
            counter.decrementAndGet()
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(InviteResponse("referral_limit_reached"))
        }

        val queryResponse = repo.getMemberByEmail(principal.email)
        if (!queryResponse.isOk || queryResponse.queryResult!!.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(InviteResponse("not_found"))
        }

        val current = queryResponse.queryResult.first()
        val level = validate.calculateLevel(current.points + bonus)
        repo.addPoints(current.id, bonus, level)

        val notice = if (bonus > INTENDED_REFERRAL_BONUS) "FLAG{hash_length_extension_forgery}" else null
        return ResponseEntity.ok(InviteResponse("claimed", notice))
    }

    private fun sha256Hex(input: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(input).joinToString("") { "%02x".format(it) }
    }

    @PostMapping("/daily/claim")
    fun claimDailyBonus(): ResponseEntity<InviteResponse> {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as AppPrincipal
        val email = principal.email

        // check-then-act: this read is not atomic with the increment further down,
        // so concurrent requests can all observe "not claimed yet" before any of them commits
        val counter = dailyBonusClaimCount.computeIfAbsent(email) { AtomicInteger(0) }
        if (counter.get() >= 1) {
            return ResponseEntity.ok(InviteResponse("already_claimed"))
        }

        val queryResponse = repo.getMemberByEmail(email)
        if (!queryResponse.isOk || queryResponse.queryResult!!.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(InviteResponse("not_found"))
        }

        val current = queryResponse.queryResult.first()
        val level = validate.calculateLevel(current.points + DAILY_BONUS_AMOUNT)
        repo.addPoints(current.id, DAILY_BONUS_AMOUNT, level)

        val timesClaimed = counter.incrementAndGet()
        val notice = if (timesClaimed > 1) "FLAG{race_condition_toctou_double_claim}" else null
        return ResponseEntity.ok(InviteResponse("claimed", notice))
    }

    private fun updateUserInProgramStatus(status: Boolean): ResponseEntity<String> {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as AppPrincipal
        val email = principal.email
        val queryResponse = repo.getMemberByEmail(email)
        if (!queryResponse.isOk) {
            return returnInternalError("Failed to find/fetch member due to error: ${queryResponse.error}")
        }
        val id = queryResponse.queryResult!!.firstOrNull()?.id ?: ""
        val updateResponse = repo.updateInProgram(id, status)

        if (!updateResponse.isOk) {
            return returnInternalError("Failed to update member inProgram status due to error: ${updateResponse.error}")
        }

        return ResponseEntity(HttpStatus.OK)
    }


    private fun returnInternalError(error: String): ResponseEntity<String> {
        logger.error("Internal error: $error")
        return ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR)
    }
}

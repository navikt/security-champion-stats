package navikt.appsec.securitychampionstats.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException


private val logger = LoggerFactory.getLogger("ReadJsonFile")

private fun readJsonFile(filename: String): String {
    try {
        return File(filename).readText(Charsets.UTF_8)
    } catch (e: IOException) {
        logger.error("Error while reading json file: $e")
        return ""
    }
}

private fun initMapper(): ObjectMapper {
    val mapper = jacksonObjectMapper()
    mapper.registerKotlinModule()
    mapper.registerModule(JavaTimeModule())
    return mapper
}

fun readMemberJsonFile(filename: String): List<Member> {
    val file = readJsonFile(filename)

    val mapper = initMapper()

    try {
        return mapper.readValue<List<Member>>(file)
    } catch (e: IOException) {
        logger.error("Error while converting json file to member data class: $e")
        return emptyList()
    }
}

private fun writeMemberJsonFile(filename: String, members: List<Member>) {
    val mapper = initMapper()

    try {
        val json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(members)
        File(filename).writeText(json)
    } catch (e: IOException) {
        println("Error while writing json file: $e")
    }
}

fun addMemberToJsonFile(filename: String, newMember: Member) {
    val existingMembers = readMemberJsonFile(filename).toMutableList()
    existingMembers.add(newMember)
    writeMemberJsonFile(filename, existingMembers)
}

fun deleteMemberFromJsonFile(filename: String, id: String) {
    val existingMembers = readMemberJsonFile(filename).toMutableList()
    val newMembers = existingMembers.filter { it.id == id }
    writeMemberJsonFile(filename, newMembers)
}

fun addPointsForMemberToJsonFile(filename: String, id: String, points: Int) {
    val existingMembers = readMemberJsonFile(filename).toMutableList()
    val member = existingMembers.find { it.id == id }
    if (member != null) {
        val newMembers = existingMembers.filter { it.id == id }.toMutableList()
        member.addPoints(points)
        newMembers.add(member)
        writeMemberJsonFile(filename, newMembers)
    } else {
        logger.info("No existing member found with id $id")
    }
}

data class Member(val fullname: String,  val id: String, var points: Int, val email: String) {
    fun addPoints(points: Int) {
        this.points += points
    }
}

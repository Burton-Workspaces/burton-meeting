package com.burton.meeting.domain

import java.security.SecureRandom

object RoomCodes {
    const val LENGTH = 6
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val SIGNAL_PORT = 9472
    const val NSD_TYPE = "_burton-meeting._tcp."

    fun generate(random: SecureRandom = SecureRandom()): String =
        CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] }.concatToString()

    fun normalize(raw: String): String =
        raw.filter { it.isLetterOrDigit() }.uppercase()

    fun isValid(code: String): Boolean {
        val normalized = normalize(code)
        return normalized.length == LENGTH && normalized.all { it in ALPHABET }
    }
}

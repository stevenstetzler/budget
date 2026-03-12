package com.vidalabs.budget.sync

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun monthDirFromTs(ts: Long): String {
    val z = Instant.ofEpochMilli(ts).atZone(ZoneOffset.UTC)
    return "%04d-%02d".format(z.year, z.monthValue)
}

fun filenameFor(deviceId: String, ts: Long, seq: Long): String {
    val tsStr = DateTimeFormatter.ISO_INSTANT
        .format(Instant.ofEpochMilli(ts))
        .replace(":", "-")
    return "${deviceId}_${tsStr}_${seq.toString().padStart(6, '0')}.json"
}

package com.vidalabs.budget.sync

import kotlinx.serialization.json.Json

val SyncJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type" // will write {"type":"UpsertReceipt", ...}
}

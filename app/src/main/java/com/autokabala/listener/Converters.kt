package com.autokabala.listener

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromBillingType(value: BillingType): String = value.name

    @TypeConverter
    fun toBillingType(value: String): BillingType =
        BillingType.entries.firstOrNull { it.name == value } ?: BillingType.PER_SESSION
}

package com.ash.axis.domain.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

@Serializable
data class AcadYear(
    val id: String = "",
    val item: String = "",
)

@Serializable
data class ClassInfo(
    val id: String = "",
    val item: String = "",
)

data class SemesterOption(
    val yearId: String,
    val classId: String,
    val label: String,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AttendanceEntry(
    @JsonNames("SubName", "subject_name", "subjectName")
    val subname: String = "",
    @SerialName("sub_code")
    @JsonNames("subCode", "sub_shortname")
    val subCode: String = "",
    @SerialName("lec_type")
    @JsonNames("lecType", "lectType", "LectType")
    val lecType: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("AttdPre") val present: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("AttdAll") val total: Int = 0,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val percent: Double = 0.0,
)

@Serializable
data class AttendanceEndRow(
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("AttdPre") val present: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("AttdAll") val total: Int = 0,
    @Serializable(with = FlexibleDoubleSerializer::class)
    @SerialName("AttdPerc") val percentage: Double = 0.0,
)

@Serializable
data class AttendanceResponse(
    val table: Map<String, AttendanceEntry> = emptyMap(),
    val endrow: AttendanceEndRow = AttendanceEndRow(),
    @SerialName("from_date") val fromDate: String = "",
    @SerialName("to_date") val toDate: String = "",
)

@Serializable
data class DaywiseSlot(
    val title: String = "",
    @SerialName("from_time") val fromTime: String = "",
    @SerialName("to_time") val toTime: String = "",
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isPresent: Boolean? = null,
    val status: String? = null,
    @SerialName("lec_type") val lecType: String? = null,
    val subjectId: String? = null,
    @SerialName("sub_code") val subCode: String? = null,
    @SerialName("sub_shortname") val subShortname: String? = null,
)

@Serializable
data class DaywiseResponse(
    @SerialName("DateArray") val dateArray: Map<String, String> = emptyMap(),
    @SerialName("AttendanceArray") val attendanceArray: Map<String, Map<String, DaywiseSlot>> = emptyMap(),
)

object FlexibleIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val input = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val primitive = input.decodeJsonElement() as? JsonPrimitive ?: return 0
        return primitive.intOrNull ?: primitive.contentOrNull?.toDoubleOrNull()?.toInt() ?: 0
    }

    override fun serialize(
        encoder: kotlinx.serialization.encoding.Encoder,
        value: Int,
    ) {
        encoder.encodeInt(value)
    }
}

object FlexibleDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double {
        val input = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        val primitive = input.decodeJsonElement() as? JsonPrimitive ?: return 0.0
        return primitive.doubleOrNull ?: primitive.contentOrNull?.toDoubleOrNull() ?: 0.0
    }

    override fun serialize(
        encoder: kotlinx.serialization.encoding.Encoder,
        value: Double,
    ) {
        encoder.encodeDouble(value)
    }
}

object FlexibleBooleanSerializer : KSerializer<Boolean?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleBoolean", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean? {
        val input = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        val primitive = input.decodeJsonElement() as? JsonPrimitive ?: return null
        primitive.booleanOrNull?.let { return it }
        return when (primitive.contentOrNull?.trim()?.lowercase()) {
            "1", "yes", "y", "present", "p" -> true
            "0", "no", "n", "absent", "a" -> false
            else -> null
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(
        encoder: kotlinx.serialization.encoding.Encoder,
        value: Boolean?,
    ) {
        if (value == null) encoder.encodeNull() else encoder.encodeBoolean(value)
    }
}

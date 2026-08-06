@file:Suppress("MatchingDeclarationName")

package com.ash.axis.domain.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Suppress("ConstructorParameterNaming")
@Serializable
data class TimetableSlot(
    @SerialName("from_time")
    @JsonNames("fromTime", "from", "startTime", "start_time", "fromtime")
    val fromTime: String = "",
    @SerialName("to_time")
    @JsonNames("toTime", "to", "endTime", "end_time", "totime")
    val toTime: String = "",
    val day: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    val period: Int = 0,
    @JsonNames("room", "room_no")
    val roomno: String = "",
    @SerialName("lect_type")
    @JsonNames("lectType", "lec_type", "lecType", "type", "LectType")
    val lectType: String = "",
    @SerialName("subject_id")
    @JsonNames("subjectId", "sub_id", "subjectCode", "SubId")
    val subjectId: String = "",
    @SerialName("sub_code")
    @JsonNames("subCode", "subject_code")
    val subCode: String = "",
    @SerialName("class_id")
    @JsonNames("classId", "classid")
    val classId: String = "",
    @JsonNames("SubName")
    val subname: String? = null,
    @SerialName("subject_name")
    @JsonNames("subjectName", "title")
    val subjectName: String? = null,
    val sub_shortname: String? = null,
    val sub_short: String? = null,
    val subject_full: String? = null,
    @SerialName("AltChangeId")
    @JsonNames("altChangeId", "alt_change_id")
    val altChangeId: String? = null,
    @SerialName("EmpFirstName")
    @JsonNames("empFirstName", "emp_first_name")
    val empFirstName: String? = null,
    @SerialName("EmpLastName")
    @JsonNames("empLastName", "emp_last_name")
    val empLastName: String? = null,
    @SerialName("EmpFirstName_alt")
    @JsonNames("empFirstName_alt", "emp_first_name_alt", "EmpFirstNameAlt", "empFirstNameAlt", "AltEmpFirstName", "altEmpFirstName")
    val empFirstNameAlt: String? = null,
    @SerialName("EmpLastName_alt")
    @JsonNames("empLastName_alt", "emp_last_name_alt", "EmpLastNameAlt", "empLastNameAlt", "AltEmpLastName", "altEmpLastName")
    val empLastNameAlt: String? = null,
)

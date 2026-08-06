@file:Suppress("MatchingDeclarationName")

package com.ash.axis.data.repository

import com.ash.axis.domain.model.CourseMarks
import com.ash.axis.domain.model.ExamSession
import com.ash.axis.domain.model.GradesData
import com.ash.axis.domain.model.LectureMark
import com.ash.axis.domain.model.PerformanceAcadYear
import com.ash.axis.domain.model.ReportCardEntry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class SelectionOption(
    val id: String,
    val label: String = id,
)

internal fun parseSemesterNumbers(obj: JsonObject): List<String> {
    val semesters = mutableListOf<String>()
    val arr = obj["myNumsemArrApp"]
    if (arr is JsonArray) {
        arr.forEach { el ->
            val semObj = el.jsonObject
            val num = semObj["semesterNumeric"]?.jsonPrimitive?.contentOrNull
            if (!num.isNullOrBlank()) {
                semesters.add(num)
            }
        }
    }
    return semesters
}

@Suppress("NestedBlockDepth")
internal fun parseMaxSemFromClasses(obj: JsonObject): Int? {
    val romanMap =
        mapOf(
            "I" to 1, "II" to 2, "III" to 3, "IV" to 4,
            "V" to 5, "VI" to 6, "VII" to 7, "VIII" to 8,
            "IX" to 9, "X" to 10, "XI" to 11, "XII" to 12,
        )
    var maxSem: Int? = null
    val classes = obj["classes"]
    if (classes is JsonArray) {
        classes.forEach { el ->
            if (el !is JsonObject) return@forEach
            val semName = el["sem_disp_name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val numMatch = Regex("(?i)sem(?:ester)?[\\s\\-]*([IVXL]+|\\d+)").find(semName)
            if (numMatch != null) {
                val raw = numMatch.groupValues[1]
                val num = raw.toIntOrNull() ?: romanMap[raw.uppercase()]
                if (num != null && (maxSem == null || num > maxSem!!)) {
                    maxSem = num
                }
            }
        }
    }
    return maxSem
}

internal fun parseExamSessions(obj: JsonObject): List<ExamSession> {
    val sessions = mutableListOf<ExamSession>()
    val arr = obj["showSessionArray"]
    if (arr is JsonArray) {
        arr.forEach { el ->
            val sessionObj = el.jsonObject
            val id = sessionObj["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val label = sessionObj["item"]?.jsonPrimitive?.contentOrNull ?: id
            sessions.add(ExamSession(id = id, label = label))
        }
    }
    return sessions
}

@Suppress("CyclomaticComplexMethod")
internal fun parseGradesData(element: JsonElement): GradesData {
    val obj = element.jsonObject

    val isOverall = obj["isOverallPerformance"]?.jsonPrimitive?.booleanOrNull ?: false
    val isPublish = obj["isResultPublish"]?.jsonPrimitive?.booleanOrNull ?: true
    val noResultMsg = obj["noResultMsg"]?.jsonPrimitive?.contentOrNull ?: ""
    val subExamType = obj["sub_exam_typeAA"]?.jsonPrimitive?.contentOrNull ?: ""
    val acadYear = obj["acad_year"]?.jsonPrimitive?.contentOrNull ?: ""
    val classId = obj["class_id"]?.jsonPrimitive?.contentOrNull ?: ""
    val marksheetType =
        obj["marksheet_type"]?.jsonPrimitive?.contentOrNull
            ?: obj["marksheet_type"]?.jsonPrimitive?.intOrNull?.toString() ?: ""

    val reportCards = mutableListOf<ReportCardEntry>()
    val dataArr = obj["tempDataArray"]
    if (dataArr is JsonArray && dataArr.isNotEmpty()) {
        dataArr.forEach { el ->
            if (el !is JsonObject) return@forEach
            reportCards.add(parseReportCardEntry(el))
        }
    }

    return GradesData(
        reportCards = reportCards,
        isOverallPerformance = isOverall,
        noResultMsg = noResultMsg,
        isResultPublish = isPublish,
        subExamTypeAA = subExamType,
        acadYear = acadYear,
        classId = classId,
        marksheetType = marksheetType,
    )
}

internal fun parseReportCardEntry(obj: JsonObject): ReportCardEntry =
    ReportCardEntry(
        srno = obj["srno"]?.jsonPrimitive?.intOrNull ?: 0,
        id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
        examSession =
            obj["examSession"]?.jsonPrimitive?.contentOrNull
                ?: obj["exam_session"]?.jsonPrimitive?.contentOrNull ?: "",
        acadYear = obj["acad_year"]?.jsonPrimitive?.contentOrNull ?: "",
        classes = obj["classes"]?.jsonPrimitive?.contentOrNull ?: "",
        semDisplayName = obj["sem_disp_name"]?.jsonPrimitive?.contentOrNull ?: "",
        semesterNumeric = obj["semesterNumeric"]?.jsonPrimitive?.contentOrNull ?: "",
        year = obj["year"]?.jsonPrimitive?.contentOrNull ?: "",
        subExamName = obj["subexamname"]?.jsonPrimitive?.contentOrNull ?: "",
    )

internal fun parseAcademicPerformanceMarks(element: JsonElement): List<CourseMarks> {
    val arr = element as? JsonArray ?: return emptyList()
    return arr.mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val lectures = mutableListOf<LectureMark>()
        val exams = obj["exams"] as? JsonArray
        exams?.forEach { examEl ->
            val exam = examEl as? JsonObject ?: return@forEach
            val examName = exam.string("exam_name", "examName", "name")
            val components = exam["components"] as? JsonArray
            components?.forEach { compEl ->
                val component = compEl as? JsonObject ?: return@forEach
                lectures.add(
                    LectureMark(
                        lecType = component.string("component", "lec_type"),
                        examType = examName,
                        maxMarks = component.string("max_marks", "maximum_marks", "total_marks"),
                        minMarks = component.string("min_marks", "minimum_marks"),
                        obtainedMarks =
                            component.string(
                                "marks_display",
                                "obtained_marks",
                                "marks",
                                "score",
                            ),
                    ),
                )
            }
        }
        CourseMarks(
            subCode = obj.string("subject_code", "sub_code", "code"),
            subShort = obj.string("subject_short", "sub_short"),
            subName = obj.string("subject_name", "subname", "sub_name", "name"),
            lectures = lectures,
        )
    }
}

internal fun parseOptions(element: JsonElement?): List<SelectionOption> {
    if (element == null) return emptyList()
    if (element is JsonArray) {
        return element.mapIndexedNotNull { index, item ->
            val obj = item as? JsonObject
            if (obj != null) {
                val id =
                    obj.string(
                        "id",
                        "idsubexam",
                        "class_id",
                        "division",
                        "exam_types",
                        "examSession",
                        "value",
                    ).ifBlank {
                        obj[index.toString()]?.jsonPrimitive?.contentOrNull.orEmpty()
                    }
                val label =
                    obj.string(
                        "item",
                        "subexamname",
                        "class_name",
                        "classes",
                        "division",
                        "exam_types",
                        "label",
                        "name",
                    ).ifBlank { id }
                if (id.isBlank()) null else SelectionOption(id, label)
            } else {
                val id = item.jsonPrimitive.contentOrNull.orEmpty()
                if (id.isBlank()) null else SelectionOption(id)
            }
        }
    }
    return parseNamedMap(element)
}

internal fun parseNamedMap(element: JsonElement?): List<SelectionOption> {
    val obj = element as? JsonObject ?: return emptyList()
    return obj.mapNotNull { (key, value) ->
        val label = value.jsonPrimitive.contentOrNull ?: key
        if (key.isBlank()) null else SelectionOption(key, label)
    }
}

internal fun parsePerformanceClasses(element: JsonElement?): List<SelectionOption> {
    val arr = element as? JsonArray ?: return emptyList()
    val options =
        arr.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj.string("class_id", "id")
            val className = obj.string("classes", "class_name", "name")
            val sem = obj.string("sem_disp_name", "semester")
            val label =
                listOf(className, sem)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            if (id.isBlank()) null else SelectionOption(id, label.ifBlank { id })
        }
    return options.distinctBy { it.label.trim().lowercase() }
}

internal fun parsePerformanceAcadYears(obj: JsonObject): List<PerformanceAcadYear> =
    (obj["all_academic_year"] as? JsonArray)
        ?.mapNotNull { it as? JsonObject }
        ?.map {
            PerformanceAcadYear(
                academicYear = it.string("academicYear", "acadyear"),
                isCurrent = it.string("is_current") == "1",
            )
        }
        ?.filter { it.academicYear.isNotBlank() }
        ?.sortedWith(
            compareByDescending<PerformanceAcadYear> { it.isCurrent }
                .thenByDescending { it.academicYear },
        )
        ?.take(5)
        .orEmpty()

internal fun JsonObject.string(vararg keys: String): String =
    keys.firstNotNullOfOrNull { key ->
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }.orEmpty()

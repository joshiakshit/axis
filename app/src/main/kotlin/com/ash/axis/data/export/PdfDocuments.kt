package com.ash.axis.data.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

internal data class PdfTableRow(
    val cells: List<String>,
    val bold: Boolean = false,
)

internal data class PdfSection(
    val heading: String,
    val lines: List<String>,
)

// Minimal A4 PDF composer for the export feature. Two layouts: a column table (attendance) and
// headed sections (timetable). Handles page breaks; everything is black text on white.
@Suppress("TooManyFunctions")
internal object PdfDocuments {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val LINE = 16f

    private val titlePaint = paint(size = 22f, bold = true)
    private val subtitlePaint = paint(size = 11f, color = Color.rgb(90, 90, 90))
    private val headingPaint = paint(size = 13f, bold = true)
    private val bodyPaint = paint(size = 10f)
    private val boldBodyPaint = paint(size = 10f, bold = true)
    private val rulePaint = Paint().apply { color = Color.rgb(210, 210, 210) }

    fun writeTable(
        file: File,
        title: String,
        subtitle: String,
        headers: List<String>,
        weights: List<Float>,
        rows: List<PdfTableRow>,
    ) {
        val doc = PdfDocument()
        val state = newState(doc)
        drawHeader(state, title, subtitle)
        val columns = columnOffsets(weights)
        drawRow(state, headers, columns, bold = true)
        drawRule(state)
        rows.forEach { drawRow(state, it.cells, columns, it.bold) }
        finish(state, file)
    }

    fun writeSections(
        file: File,
        title: String,
        subtitle: String,
        sections: List<PdfSection>,
        emptyText: String,
    ) {
        val doc = PdfDocument()
        val state = newState(doc)
        drawHeader(state, title, subtitle)
        if (sections.isEmpty()) {
            drawLine(state, emptyText, bodyPaint)
        } else {
            sections.forEach { section ->
                state.y += LINE / 2
                drawLine(state, section.heading, headingPaint)
                section.lines.forEach { drawLine(state, it, bodyPaint) }
            }
        }
        finish(state, file)
    }

    private fun drawHeader(
        state: PageState,
        title: String,
        subtitle: String,
    ) {
        state.y += titlePaint.textSize
        state.canvas.drawText(title, MARGIN, state.y, titlePaint)
        state.y += LINE
        state.canvas.drawText(subtitle, MARGIN, state.y, subtitlePaint)
        state.y += LINE
    }

    private fun drawRow(
        state: PageState,
        cells: List<String>,
        columns: List<Float>,
        bold: Boolean,
    ) {
        ensureSpace(state)
        val p = if (bold) boldBodyPaint else bodyPaint
        cells.forEachIndexed { i, cell ->
            state.canvas.drawText(cell, columns.getOrElse(i) { MARGIN }, state.y, p)
        }
        state.y += LINE
    }

    private fun drawLine(
        state: PageState,
        text: String,
        paint: Paint,
    ) {
        ensureSpace(state)
        state.canvas.drawText(text, MARGIN, state.y, paint)
        state.y += LINE
    }

    private fun drawRule(state: PageState) {
        val ruleY = state.y - LINE + 4f
        state.canvas.drawLine(MARGIN, ruleY, PAGE_WIDTH - MARGIN, ruleY, rulePaint)
        state.y += 4f
    }

    private fun columnOffsets(weights: List<Float>): List<Float> {
        val usable = PAGE_WIDTH - 2 * MARGIN
        val total = weights.sum().takeIf { it > 0f } ?: 1f
        var x = MARGIN
        return weights.map { w ->
            val at = x
            x += usable * (w / total)
            at
        }
    }

    private fun ensureSpace(state: PageState) {
        if (state.y > PAGE_HEIGHT - MARGIN) {
            state.doc.finishPage(state.page)
            state.pageNo++
            state.page = state.doc.startPage(pageInfo(state.pageNo))
            state.y = MARGIN
        }
    }

    private fun newState(doc: PdfDocument): PageState = PageState(doc, doc.startPage(pageInfo(1))).apply { y = MARGIN }

    private fun pageInfo(pageNo: Int) = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNo).create()

    private fun finish(
        state: PageState,
        file: File,
    ) {
        state.doc.finishPage(state.page)
        FileOutputStream(file).use { state.doc.writeTo(it) }
        state.doc.close()
    }

    private fun paint(
        size: Float,
        bold: Boolean = false,
        color: Int = Color.BLACK,
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
}

// Mutable page-walk state; all drawing math lives in PdfDocuments so this stays a dumb holder.
private class PageState(
    val doc: PdfDocument,
    var page: PdfDocument.Page,
) {
    var y: Float = 0f
    var pageNo: Int = 1
    val canvas: Canvas get() = page.canvas
}

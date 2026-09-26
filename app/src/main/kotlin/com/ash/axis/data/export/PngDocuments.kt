package com.ash.axis.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File

internal data class ImageRow(val heading: String, val detail: String)

internal object PngDocuments {
    private const val WIDTH = 1080
    private const val MARGIN = 48
    private const val GAP = 24

    fun write(
        file: File,
        title: String,
        subtitle: String,
        rows: List<ImageRow>,
    ) {
        val titlePaint = paint(48f, Color.rgb(45, 75, 115), bold = true)
        val headingPaint = paint(30f, Color.BLACK, bold = true)
        val bodyPaint = paint(26f, Color.DKGRAY)
        val text =
            listOf(layout("Axis · $title", titlePaint), layout(subtitle, bodyPaint)) +
                rows.flatMap { row ->
                    listOfNotNull(layout(row.heading, headingPaint), row.detail.takeIf { it.isNotBlank() }?.let { layout(it, bodyPaint) })
                }
        val height = MARGIN * 2 + text.sumOf { it.height + GAP }
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            var top = MARGIN
            text.forEach { line ->
                canvas.save()
                canvas.translate(MARGIN.toFloat(), top.toFloat())
                line.draw(canvas)
                canvas.restore()
                top += line.height + GAP
            }
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Cannot create PNG image" } }
        } finally {
            bitmap.recycle()
        }
    }

    private fun paint(
        size: Float,
        color: Int,
        bold: Boolean = false,
    ) = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun layout(
        text: String,
        paint: TextPaint,
    ): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, WIDTH - MARGIN * 2)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(4f, 1f)
            .build()
}

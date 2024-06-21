package com.example.translateocrapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.text.TextPaint
import android.util.Log
import com.google.mlkit.vision.text.Text
import kotlin.math.atan
import kotlin.math.sqrt


object BitmapAnnotator {


    // Function to take as input a bitmap, a map of OCR results as well as their translations, and return a bitmap annotated with translated ocr results
    public fun annotateBitmap(bitmap: Bitmap, ocrResult: Map<Array<Point>, Text.TextBlock>, translatedOcrResult: Map<Array<Point>, String>, context: Context): Bitmap {

        // Create a mutable copy of the bitmap
        val annotatedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        // Get a canvas to draw on the bitmap
        val canvas = Canvas(annotatedBitmap)

        val path = Path()

        // Create a paint object to draw the rectangles
        val rectPaint = Paint()

        val rectFMap = mutableMapOf<Rect, RectF>()

        // Iterate over ocrResult and translatedOcrResult and draw the translated text on the bitmap
        for ((points, _) in ocrResult) {

            // Draw a filled rectangle on the bitmap in rect coordinates
            val r = Rect(points[0].x, points[0].y, points[2].x, points[3].y)
            val clampedRect = r.clampToBitmap(bitmap) // Helper function to clamp the rect to the bitmap
            val rectF = RectF(clampedRect)
            rectPaint.color = getAverageColor(bitmap, clampedRect)


            path.reset()
            path.moveTo(points[0].x.toFloat(), points[0].y.toFloat())
            path.lineTo(points[1].x.toFloat(), points[1].y.toFloat())
            path.lineTo(points[2].x.toFloat(), points[2].y.toFloat())
            path.lineTo(points[3].x.toFloat(), points[3].y.toFloat())
            path.close()
            canvas.drawPath(path, rectPaint)

//            blurRegion(bitmap, r, context)

            // Store the rectF in the rectFMap
            rectFMap[r] = rectF
        }

        // Create a paint object to draw the text
        val textPaint = TextPaint()
        for ((points, _) in ocrResult) {
            val r = Rect(points[0].x, points[0].y, points[2] .x, points[3].y)

            // Get the translated text from the translatedOcrResult map
            val translatedText = translatedOcrResult[points]

            // Get the rectF from the rectFMap
            val rectF = rectFMap[r]!!

            // Draw the translated text on the bitmap in rect coordinates
            // Adjust the text size to fit the rectangle
            // Draw the translated text on the bitmap in rect coordinates
            textPaint.color = getContrastingColor(rectPaint.color)
            textPaint.typeface = Typeface.DEFAULT_BOLD

            val lines = translatedText?.split("\n")

            val textWidthMax = lines?.maxBy { textPaint.measureText(it) }

            textPaint.textSize = calculateOptimalTextSize(textWidthMax?:"", rectF.width().toInt(), rectF.height().toInt(), textPaint)

            // Start from top left
            val b = Rect()
            textPaint.getTextBounds(textWidthMax, 0, textWidthMax?.length?:1, b)
            var currentY = rectF.top + b.height()


            val offsetLine = (rectF.height() - b.height()) / ((lines?.size ?:1) -1)

            // Tạo ma trận để xoay văn bản
            val matrix = Matrix()
            // Tính góc bằng hàm atan
            val angleRadian = atan((points[1].y.toDouble() - points[0].y) / (points[1].x.toDouble() - points[0].x))
            // Chuyển đổi từ radian sang độ
            val angleDegree = Math.toDegrees(angleRadian)
            matrix.postRotate(angleDegree.toFloat(), rectF.left, currentY)


            // Lưu trạng thái canvas và áp dụng ma trận xoay
            canvas.save()
            canvas.setMatrix(matrix)

            lines?.forEach {
                canvas.drawText(it, points[3].x.toFloat(), currentY, textPaint)
                currentY += offsetLine
            }
            // Khôi phục trạng thái canvas ban đầu
            canvas.restore()

        }

        return annotatedBitmap
    }

    private fun blurRegion(bitmap: Bitmap, rect: Rect, context: Context) {
        val rs = RenderScript.create(context)
        val input = Allocation.createFromBitmap(rs, bitmap)
        val output = Allocation.createTyped(rs, input.type)
        val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        script.setRadius(10f) // Set the blur radius (1-25)
        script.setInput(input)

        // Blur the entire bitmap and then copy the blurred portion to the original
        script.forEach(output)
        output.copyTo(bitmap)

        // Create a temporary bitmap for the blurred region
        val tempBitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(tempBitmap)
        tempCanvas.drawBitmap(bitmap, -rect.left.toFloat(), -rect.top.toFloat(), null)

        // Draw the blurred region back to the original bitmap
        Canvas(bitmap).drawBitmap(tempBitmap, rect.left.toFloat(), rect.top.toFloat(), null)

        // Clean up
        input.destroy()
        output.destroy()
        script.destroy()
        rs.destroy()
    }

    private fun Rect.clampToBitmap(bitmap: Bitmap): Rect {
        val clampedLeft = left.coerceIn(0, bitmap.width)
        val clampedTop = top.coerceIn(0, bitmap.height)
        val clampedRight = right.coerceIn(0, bitmap.width)
        val clampedBottom = bottom.coerceIn(0, bitmap.height)
        return Rect(clampedLeft, clampedTop, clampedRight, clampedBottom)
    }

    // Function to get the average color of a region in a bitmap
    private fun getAverageColor(bitmap: Bitmap, rect: Rect): Int {
        var redSum = 1
        var greenSum = 1
        var blueSum = 1
        var pixelCount = 1

        for (x in rect.left until rect.right) {
            for (y in rect.top until rect.bottom) {
                val pixel = bitmap.getPixel(x, y)
                redSum += Color.red(pixel)
                greenSum += Color.green(pixel)
                blueSum += Color.blue(pixel)
                pixelCount++
            }
        }

        val averageRed = redSum / pixelCount
        val averageGreen = greenSum / pixelCount
        val averageBlue = blueSum / pixelCount

        return Color.rgb(averageRed, averageGreen, averageBlue)
    }

    // Helper function to get a contrasting color for text based on the background color
    private fun getContrastingColor(backgroundColor: Int): Int {
        val luminance = (0.299 * Color.red(backgroundColor) +
                0.587 * Color.green(backgroundColor) +
                0.114 * Color.blue(backgroundColor)) / 255
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }

    private fun calculateOptimalTextSize(text: String, rectWidth: Int, rectHeight: Int, paint: Paint): Float {
        var textSize = 20f
        val bounds = Rect()

        // Tăng kích thước văn bản cho đến khi văn bản vượt quá kích thước của hình chữ nhật
        while (true) {
            paint.textSize = textSize
            paint.getTextBounds(text, 0, text.length, bounds)

            if (bounds.width() > rectWidth || bounds.height() > rectHeight) {
                break
            }
            textSize += 1f
        }

        // Giảm kích thước văn bản một chút để đảm bảo nó vừa vặn
        return textSize - 1f
    }
}
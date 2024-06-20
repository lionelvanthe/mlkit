package com.example.translateocrapp

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
import android.text.TextPaint
import android.util.Log
import com.google.mlkit.vision.text.Text
import kotlin.math.atan
import kotlin.math.sqrt


object BitmapAnnotator {

    const val offsetWidth = 30
    const val offsetHeight = 10

    // Function to take as input a bitmap, a map of OCR results as well as their translations, and return a bitmap annotated with translated ocr results
    public fun annotateBitmap(bitmap: Bitmap, ocrResult: Map<Array<Point>, Text.TextBlock>, translatedOcrResult: Map<Array<Point>, String>): Bitmap {

        // Create a mutable copy of the bitmap
        val annotatedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        // Get a canvas to draw on the bitmap
        val canvas = Canvas(annotatedBitmap)

        val path = Path()

        // Create a paint object to draw the rectangles
        val rectPaint = Paint()

        val rectFMap = mutableMapOf<Rect, RectF>()

        // Iterate over ocrResult and translatedOcrResult and draw the translated text on the bitmap
        for ((points, line) in ocrResult) {

            // Draw a filled rectangle on the bitmap in rect coordinates
            val r = Rect(points[0].x, points[0].y, points[2].x, points[3].y)
            val clampedRect = r.clampToBitmap(bitmap) // Helper function to clamp the rect to the bitmap
            val rectF = RectF(clampedRect)
            rectPaint.color = getAverageColor(bitmap, clampedRect)





            path.reset()
            path.moveTo(points[0].x.toFloat() - offsetWidth, points[0].y.toFloat() - offsetHeight)
            path.lineTo(points[1].x.toFloat() + offsetWidth, points[1].y.toFloat() - offsetHeight)
            path.lineTo(points[2].x.toFloat() + offsetWidth, points[2].y.toFloat() + offsetHeight)
            path.lineTo(points[3].x.toFloat() - offsetWidth, points[3].y.toFloat() + offsetHeight)
            path.close()
            canvas.drawPath(path, rectPaint)

//            canvas.drawRect(rectF, rectPaint)
            // Store the rectF in the rectFMap
            rectFMap[r] = rectF
        }

        // Create a paint object to draw the text
        val textPaint = TextPaint()
        for ((points, line) in ocrResult) {
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
            textPaint.textSize = getTextSizeToFitRect(rectF, translatedText ?: "") - 2

            // Get approx char per line using the width of the rectF
            // Do an integer division to get the number of chars per line
            val averageCharWidth = textPaint.measureText(translatedText) / translatedText!!.length
            val approxCharsPerLine = (rectF.width() / averageCharWidth).toInt()
            if (approxCharsPerLine <= 0) {
                continue
            }
            // Split the translated text into lines each with approxCharsPerLine chars
            val translatedTextLines = translatedText.chunked(approxCharsPerLine) ?: listOf("")

            // Draw each line of the translated text on the bitmap in rect coordinates
            // Start from top left
            var currentY = rectF.top + textPaint.textSize

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
            for (line in translatedTextLines) {
                canvas.drawText(line, rectF.left, currentY, textPaint)
                currentY += textPaint.textSize
            }
            // Khôi phục trạng thái canvas ban đầu
            canvas.restore()

        }

        return annotatedBitmap
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
        var redSum = 0
        var greenSum = 0
        var blueSum = 0
        var pixelCount = 0

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

    // Helper function to calculate the text size that fits within the given rectangle (takes into account warping of the text)
    private fun getTextSizeToFitRect(rect: RectF, text: String, tolerance: Float = 0.9f): Float {


        // Area of the rectangle
        val targetArea = (rect.width() * rect.height()) * tolerance

        // Number of characters in the text
        val textLength = text.length

        // Area of a single character
        val singleCharacterArea = targetArea / textLength

        // Get an estimate of the text size such that the area of a character is equal to singleCharacterArea
        // Assuming equal size for all characters, aspect ratio of a character is 0.5
        val estimatedTextSize = sqrt(singleCharacterArea / 0.5f)

        return estimatedTextSize
    }
}
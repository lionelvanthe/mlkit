package com.example.translateocrapp

// Import coroutines dependencies

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Point
import android.media.ExifInterface
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileChannel.MapMode
import kotlin.math.pow
import kotlin.math.sqrt


class PreviewActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_IMAGE_PATH = "extra_image_path"
        const val TRASNPARENT_IS_BLACK: Boolean = false
        const val SPACE_BREAKING_POINT: Double = 13.0 / 30.0

    }


    private lateinit var previewImageView: ImageView
    private lateinit var backButton: Button

    private lateinit var bitmap: Bitmap

    // Create an instance of the OcrHelper class
    private val ocrHelper = OcrHelper()

    // Create an instance of the LanguageRecognizer class
    private val languageRecognizer = LanguageRecognizer()

    // Create an instance of the TextTranslator class
    private val textTranslator = TextTranslator(this)

    // Create a variable to store the OCR result
    private lateinit var ocrResultMap: Map<Array<Point>, Text.TextBlock>

    // Create a variable to store the language detected
    private lateinit var languageCode: String

    // Create a variable to store the translated ocr result
    private lateinit var translatedOcrResultMap: Map<Array<Point>, String>

    // Job variable to keep track of the OCR job
    private lateinit var ocrJob: Job

    // Job variable to keep track of the language identification job
    private lateinit var languageJob: Job

    // Create a progress dialog
    private lateinit var progressBar: ProgressBar

    // AlertDialog to show download progress
    private var progressDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        previewImageView = findViewById(R.id.previewImageView)
        backButton = findViewById(R.id.backButton)

        backButton.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        Log.d("Thenv", "onCreate: $imagePath")
        // Initialize the progress bar
        progressBar = ProgressBar(this).apply {
            isIndeterminate = true
        }

        // Show the progress dialog saying that translation is in progress
        Handler(Looper.getMainLooper()).post {
            progressDialog = AlertDialog.Builder(this)
                .setTitle("Translating ...")
                .setCancelable(false)
                .setView(progressBar)
                .show()
        }

        if (imagePath != null) {

            // Get the bitmap from the image file
            bitmap = readImageFile(imagePath)

            // Display the bitmap
            displayBitmap(bitmap)

            // Create a thread to run the OCR
            // Perform OCR in a background thread
            ocrJob = CoroutineScope(Dispatchers.Default).launch {
                ocrResultMap = ocrHelper.performOcr(bitmap)

                withContext(Dispatchers.Main) {
                    // Handle the OCR result here
                    processOcrResult(ocrResultMap)
                }
            }

            // Wait for the OCR job to complete before starting the language identification job
            ocrJob.invokeOnCompletion {
                // Perform language identification in a separate background thread
                languageJob = CoroutineScope(Dispatchers.Default).launch {
                    languageCode = languageRecognizer.recognizeLanguage(ocrResultMap)

                    withContext(Dispatchers.Main) {
                        // Handle the language identification result here
                        processLanguageResult(languageCode)
                    }
                }

                languageJob.invokeOnCompletion {
                    // Perform translation in a separate background thread
                    CoroutineScope(Dispatchers.Default).launch {
                        translatedOcrResultMap = textTranslator.translateOcrResult(ocrResultMap, languageCode)

                        withContext(Dispatchers.Main) {
                            // Handle the translation result here
                            processTranslationResult(translatedOcrResultMap)
                        }
                    }
                }


            }


        } else {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    // Create a function to process the OCR result
    private fun processOcrResult(ocrResultMap: Map<Array<Point>, Text.TextBlock>) {
        // Log the OCR result with Rect and text
        for ((rect, textBlock) in ocrResultMap) {
            Log.d("OCR", "Found text ${textBlock.text} at $rect")

        }

    }

    // Create a function to process the language identification result
    private fun processLanguageResult(languageResult: String) {
        // Handle the language identification result
        Log.d("Language", "Language detected is $languageCode")

    }

    // Create a function to process the translation result
    private fun processTranslationResult(translatedText: Map<Array<Point>, String>) {
        // Handle the translation result
        for ((rect, text) in translatedText) {
            Log.d("Translation", "Translated text $text at $rect")
        }

        // Get annotated bitmap
        bitmap = BitmapAnnotator.annotateBitmap(bitmap, ocrResultMap, translatedText, this@PreviewActivity)

        // Display the annotated bitmap
        displayBitmap(bitmap)

        // Dismiss the progress dialog
        Handler(Looper.getMainLooper()).post {
            progressDialog?.dismiss()
            progressDialog = null
        }

    }

    // Create a function to read image file and return bitmap
    private fun readImageFile(imagePath: String): Bitmap {
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        val bitmap = BitmapFactory.decodeFile(imagePath, options)
        // Rotate the bitmap if required and return it

        val binarizedImage = convertToMutable(bitmap)


        // I will look at each pixel and use the function shouldBeBlack to decide
        // whether to make it black or otherwise white
        for (i in 0 until binarizedImage.width) {
            for (c in 0 until binarizedImage.height) {
                val pixel = binarizedImage.getPixel(i, c)
                if (shouldBeBlack(pixel)) binarizedImage.setPixel(i, c, Color.BLACK)
                else binarizedImage.setPixel(i, c, Color.WHITE)
            }
        }

        val b1 = RemoveNoise(binarizedImage)

//
//        val bitmapScale = Bitmap.createScaledBitmap(
//            bitmap,
//            (bitmap.width*0.887f).toInt(),
//            (bitmap.height*0.887f).toInt(),
//            true
//        )
        return rotateBitmap(imagePath, b1)
    }

    // Create a function to display the bitmap
    private fun displayBitmap(bitmap: Bitmap) {
        previewImageView.setImageBitmap(bitmap)
    }

    private fun rotateBitmap(imagePath: String, bitmap: Bitmap): Bitmap {
        val exif = ExifInterface(imagePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )

        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

        return if (rotationDegrees != 0) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    private fun shouldBeBlack(pixel: Int): Boolean {
        val alpha = Color.alpha(pixel)
        val redValue = Color.red(pixel)
        val blueValue = Color.blue(pixel)
        val greenValue = Color.green(pixel)
        if (alpha == 0x00) //if this pixel is transparent let me use TRASNPARENT_IS_BLACK
            return TRASNPARENT_IS_BLACK
        // distance from the white extreme
        val distanceFromWhite = sqrt(
            (0xff - redValue).toDouble().pow(2.0) + (0xff - blueValue).toDouble().pow(2.0) + (0xff - greenValue).toDouble().pow(2.0)
        )
        // distance from the black extreme //this should not be computed and might be as well a function of distanceFromWhite and the whole distance
        val distanceFromBlack = sqrt(
            (0x00 - redValue).toDouble().pow(2.0) + (0x00 - blueValue).toDouble().pow(2.0) + (0x00 - greenValue).toDouble().pow(2.0)
        )
        // distance between the extremes //this is a constant that should not be computed :p
        val distance = distanceFromBlack + distanceFromWhite
        // distance between the extremes
        return ((distanceFromWhite / distance) > SPACE_BREAKING_POINT)
    }

    fun convertToMutable(imgIn: Bitmap): Bitmap {
        var imgIn = imgIn
        try {
            //this is the file going to use temporally to save the bytes.
            // This file will not be a image, it will store the raw image data.
            val file = File(
                Environment.getExternalStorageDirectory().toString() + File.separator + "temp.tmp"
            )

            //Open an RandomAccessFile
            //Make sure you have added uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
            //into AndroidManifest.xml file
            val randomAccessFile = RandomAccessFile(file, "rw")

            // get the width and height of the source bitmap.
            val width = imgIn.width
            val height = imgIn.height
            val type: Bitmap.Config = imgIn.config

            //Copy the byte to the file
            //Assume source bitmap loaded using options.inPreferredConfig = Config.ARGB_8888;
            val channel = randomAccessFile.channel
            val map = channel.map(MapMode.READ_WRITE, 0, (imgIn.rowBytes * height).toLong())
            imgIn.copyPixelsToBuffer(map)
            //recycle the source bitmap, this will be no longer used.
            imgIn.recycle()
            System.gc() // try to force the bytes from the imgIn to be released

            //Create a new bitmap to load the bitmap again. Probably the memory will be available.
            imgIn = Bitmap.createBitmap(width, height, type)
            map.position(0)
            //load it back from temporary
            imgIn.copyPixelsFromBuffer(map)
            //close the temporary file and channel , then delete that also
            channel.close()
            randomAccessFile.close()

            // delete the temp file
            file.delete()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return imgIn
    }

    fun RemoveNoise(bmap: Bitmap): Bitmap {
        for (x in 0 until bmap.width) {
            for (y in 0 until bmap.height) {
                val pixel = bmap.getPixel(x, y)
                val R = Color.red(pixel)
                val G = Color.green(pixel)
                val B = Color.blue(pixel)
                if (R < 162 && G < 162 && B < 162) bmap.setPixel(x, y, Color.BLACK)
            }
        }
        for (x in 0 until bmap.width) {
            for (y in 0 until bmap.height) {
                val pixel = bmap.getPixel(x, y)
                val R = Color.red(pixel)
                val G = Color.green(pixel)
                val B = Color.blue(pixel)
                if (R > 162 && G > 162 && B > 162) bmap.setPixel(x, y, Color.WHITE)
            }
        }
        return bmap
    }

}
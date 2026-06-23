package com.ironie.einker

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import kotlin.experimental.or
import androidx.core.graphics.createBitmap
import org.opencv.core.at
import java.io.File.separator
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class ImageProcesses {

    companion object {

        var CARD_IMG_WIDTH = 480
        var CARD_IMG_HEIGHT = 800

        fun updateCardWidthHeight(width: Int, height: Int) {
            CARD_IMG_HEIGHT =height
            CARD_IMG_WIDTH = width
        }

        fun formatAndConvertBW(src: Bitmap, dither: Boolean = true, invertColors: Boolean = false): ByteArray
        {
            val bwMat = bitmapToGrayMat(src)
            if (invertColors)
                Core.bitwise_not(bwMat, bwMat)

            val cropped = cropSides(bwMat, top = 100, bottom = 100)
            val formatted = fitAndScaleImage(cropped)

            val bw = if (dither)
                dither(formatted, 2)
            else
                reduce(formatted, 2)
            return convertToBWByteArray(bw)
        }

        fun formatAndConvert4Gray(src: Bitmap, dither: Boolean = true, invertColors: Boolean = false): Pair<ByteArray, ByteArray>
        {
            val srcMat = bitmapToGrayMat(src)

            if (invertColors)
                Core.bitwise_not(srcMat, srcMat)
            val cropped = cropSides(srcMat, top = 100, bottom = 100)
            val formatted = fitAndScaleImage(cropped)

            val gray = if (dither)
                dither(formatted, 4)
            else
                reduce(formatted, 4)

            return convertTo4GrayByteArray(gray)
        }

        fun bitmapToGrayMat(src: Bitmap): Mat
        {
            val typed = src.copy(Bitmap.Config.ARGB_8888, true)
            val mat = Mat(typed.height, typed.width, CvType.CV_8UC1)
            val grayscale = Mat(typed.height, typed.width, CvType.CV_8UC1)
            Utils.bitmapToMat(typed, mat)
            Imgproc.cvtColor(mat, grayscale, Imgproc.COLOR_BGR2GRAY)
            Core.normalize(grayscale, grayscale, 0.0, 255.0, Core.NORM_MINMAX, CvType.CV_8UC1)
            return grayscale
        }

        fun convertToBW(src: Mat): Mat
        {
            val fin = Mat(src.height(), src.width(), CvType.CV_8UC1)
            Imgproc.threshold(src, fin, 128.0,
                255.0, Imgproc.THRESH_OTSU)
            return fin
        }

        fun reduce(src: Mat, pixelCount: Int): Mat
        {
            val reduced = Mat(src.height(), src.width(), CvType.CV_8UC1)
            val minMax = Core.minMaxLoc(src)

//            Imgproc.threshold(src, reduced, 128.0,
//                255.0, Imgproc.THRESH_OTSU)

            Core.subtract(src, Scalar(minMax.minVal), reduced)
            val scale = (minMax.maxVal-minMax.minVal)/(pixelCount - 1.0)
            Core.divide(reduced, Scalar(scale), reduced, 1.0, CvType.CV_8UC1)
            Core.multiply(reduced, Scalar(255.0/(pixelCount - 1.0)), reduced)
            return reduced
        }
        //uses floyd-steinberg
        fun dither(src: Mat, pixelCount: Int): Mat
        {
            val srcByteArr = ByteArray(src.total().toInt())
            src.get(0,0, srcByteArr)
            val srcArray = srcByteArr.map { it.toUByte().toDouble() }.toDoubleArray()
            val minMax = Core.minMaxLoc(src)
            val scaled = (minMax.maxVal-minMax.minVal)/(pixelCount-1)
            for (i in 0 until src.height())
            {
                for (j in 0 until src.width())
                {
                    val oldPixel = srcArray[i*src.width() + j]
                    val newPixel = ((oldPixel - minMax.minVal) / scaled).roundToInt() * (255.0/(pixelCount-1))
                    val err = oldPixel - newPixel
                    srcArray[i*src.width() + j] = newPixel

                    if (j < src.width()-1)
                        srcArray[i*src.width() + j + 1] += 7.0/16 * err
                    if (i < src.height()-1) {
                        srcArray[(i+1)*src.width() + j] += 5.0/16 * err
                        if (j > 0)
                            srcArray[(i+1)*src.width() + j - 1] += 3.0/16 * err
                        if (j < src.width()-1)
                            srcArray[(i+1)*src.width() + j + 1] += 1.0/16 * err
                    }
                }
            }

            val ditheredByteArr = srcArray.map { (it.toInt().toByte()) }.toByteArray()
            val dithered = Mat(src.rows(), src.cols(), CvType.CV_8UC1)
            dithered.put(0,0, ditheredByteArr)
            return dithered
        }

        fun cropSides(src: Mat, top: Int = 0, bottom: Int = 0, left: Int = 0, right: Int = 0): Mat
        {
            val rect = Rect(left, top, src.width()-left-right, src.height()-top-bottom)
            val cropped = src.submat(rect)
            Core.normalize(cropped, cropped, 0.0, 255.0, Core.NORM_MINMAX, CvType.CV_8UC1)
            return cropped
        }

        fun cropAndScaleImage(src: Mat, width: Int, xOffset: Int, yOffset: Int): Mat
        {
            if (width > src.width())
                Log.d("test", "error!")
            val height =  (CARD_IMG_HEIGHT.toFloat()/CARD_IMG_WIDTH.toFloat())*width
            val rect = Rect(xOffset, yOffset, width, height.toInt())
            val cropped = src.submat(rect)
            val scaled = Mat(CARD_IMG_WIDTH, CARD_IMG_HEIGHT, CvType.CV_8UC1)
            val scale = Size(CARD_IMG_WIDTH.toDouble(), CARD_IMG_HEIGHT.toDouble())
            Imgproc.resize(cropped, scaled, scale, 0.0, 0.0)
            return scaled
        }

        fun fitAndScaleImage(src: Mat, filledValue: Double = 0.0): Mat
        {
            var w: Int; var h: Int; var offsetX: Int; var offsetY: Int
            // if ratio of the width is bigger than height, means we can vary height
            if (src.width()/CARD_IMG_WIDTH > src.height()/CARD_IMG_HEIGHT)
            {
                w = src.width()
                h = ((CARD_IMG_HEIGHT.toFloat()/CARD_IMG_WIDTH.toFloat()) * w).toInt()
                offsetX = 0
                offsetY = (h-src.height())/2
            }
            else
            {
                h = src.height()
                w = ((CARD_IMG_WIDTH.toFloat()/CARD_IMG_HEIGHT.toFloat()) * h).toInt()
                offsetX = (w-src.width())/2
                offsetY = 0
            }
            val fitted = Mat(h, w, CvType.CV_8UC1, Scalar(filledValue))
            val roi = fitted.submat(Rect(offsetX,offsetY,src.width(), src.height()))
            src.copyTo(roi)

            val scaled = Mat(CARD_IMG_HEIGHT, CARD_IMG_WIDTH, CvType.CV_8UC1)
            val scale = Size(CARD_IMG_WIDTH.toDouble(), CARD_IMG_HEIGHT.toDouble())
            Imgproc.resize(fitted, scaled, scale, 0.0, 0.0)
            return scaled
        }

        fun convertToBWByteArray(src: Mat): ByteArray
        {
            val rot = Mat(CARD_IMG_HEIGHT, CARD_IMG_WIDTH, CvType.CV_8UC1)
            Core.transpose(src, rot)
            Core.flip(rot, rot, 0)

            val srcByteArray = ByteArray(rot.total().toInt())
            val arr = ByteArray(srcByteArray.size/8)
            rot.get(0,0, srcByteArray)

            var currentByte = 0
            var byteIndex = 0
            srcByteArray.forEachIndexed { index, b ->
                val bit = 7 - (index%8)
                if (b.toInt() != 0)
                    currentByte += 1 shl bit

                if (index%8 == 7)
                {
                    byteIndex++
                    arr[index/8] = currentByte.toByte()
                    currentByte = 0
                }
            }
            return arr
        }

        fun convertTo4GrayByteArray(src: Mat): Pair<ByteArray, ByteArray>
        {
            val rot = Mat(CARD_IMG_HEIGHT, CARD_IMG_WIDTH, CvType.CV_8UC1)
            Core.transpose(src, rot)
            Core.flip(rot, rot, 0)

            val srcByteArr = ByteArray(rot.total().toInt())
            val reg1 = ByteArray(rot.total().toInt()/8)
            val reg2 = ByteArray(rot.total().toInt()/8)
            rot.get(0, 0,srcByteArr)

            srcByteArr.forEachIndexed { index, b ->
                val bit = 7 - (index%8)
                //dark gray
                when (b.toUByte().toInt()) {
                    //white
                    0 -> {
                        reg1[index / 8] = reg1[index / 8] or (0b1 shl bit).toByte()
                        reg2[index / 8] = reg2[index / 8] or (0b1 shl bit).toByte()
                    }
                    //dark gray
                    85 -> {
                        reg1[index / 8] = reg1[index / 8] or (0b1 shl bit).toByte()
                    }
                    //light gray
                    170 -> {
                        reg2[index / 8] = reg2[index / 8] or (0b1 shl bit).toByte()
                    }
                }
            }

            return Pair(reg1, reg2)
        }
    }
}
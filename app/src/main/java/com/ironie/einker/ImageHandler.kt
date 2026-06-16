package com.ironie.einker

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.InputStream
import java.io.OutputStream
import kotlin.experimental.or

class ImageHandler {

    companion object {

        const val CARD_IMG_WIDTH = 480
        const val CARD_IMG_HEIGHT = 800

        fun formatAndConvert(src: Bitmap): ByteArray
        {
            val bwMat = convertToBW(src)
            val cropped = cropSides(bwMat, top = 50, bottom = 50)
            val formatted = fitAndScaleImage(cropped)
            return convertToBWByteArray(formatted)
        }

        fun bitmapToMat(src: Bitmap): Mat
        {
            val typed = src.copy(Bitmap.Config.ARGB_8888, true)
            val mat = Mat(typed.height, typed.width, CvType.CV_8UC1)
            Utils.bitmapToMat(typed, mat)
            return mat
        }

        fun convertToBW(src: Bitmap): Mat
        {
            val srcMat = bitmapToMat(src)
            val bw = Mat(srcMat.height(), srcMat.width(), CvType.CV_8UC1)
            val fin = Mat(srcMat.height(), srcMat.width(), CvType.CV_8UC1)
            Imgproc.cvtColor(srcMat, bw, Imgproc.COLOR_BGR2GRAY)
            Imgproc.threshold(bw, fin, 128.0,
                255.0, Imgproc.THRESH_OTSU)
            return fin
        }

        fun cropSides(src: Mat, top: Int = 0, bottom: Int = 0, left: Int = 0, right: Int = 0): Mat
        {
            val rect = Rect(left, top, src.width()-left-right, src.height()-top-bottom)
            val cropped = src.submat(rect)
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
            val rot = Mat(CARD_IMG_WIDTH, CARD_IMG_HEIGHT, CvType.CV_8UC1)
            Core.transpose(scaled, rot)
            Core.flip(rot, scaled, 0)
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

            val rot = Mat(CARD_IMG_HEIGHT, CARD_IMG_WIDTH, CvType.CV_8UC1)
            Core.transpose(scaled, rot)
            Core.flip(rot, scaled, 0)
            return scaled
        }

        fun convertToBWByteArray(src: Mat): ByteArray
        {
            val ba = ByteArray(src.total().toInt())
            val bb = ByteArray(ba.size/8)
            src.get(0,0, ba)

            var currentByte = 0
            var a = 0
            ba.forEachIndexed { index, b ->
                val bit = 7 - (index%8)
                if (b.toInt() != 0)
                    currentByte += 1 shl bit

                if (index%8 == 7)
                {
                    a++
                    bb[index/8] = currentByte.toByte()
                    currentByte = 0
                }
            }
            return bb
        }

        fun convertTo4GrayByteArray()
        {
        }
    }
}
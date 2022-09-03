package watermark

import java.awt.Color
import java.awt.Transparency
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

val VALID_BIT_VALUES = arrayOf(24, 32)

enum class PositionType {
    SINGLE, GRID
}

class Position(val type: PositionType, val pair: Pair<Int, Int>? = null)

class TransparencyColor(val use: Boolean, val color: Color? = null)

fun main() {
    try {
        val inputBufferedImage = getValidInputImage()

        val watermarkBufferedImage = getValidWatermarkImage()

        validateDimensions(inputBufferedImage, watermarkBufferedImage)

        val useWatermarkTransparency = getValidWatermarkTransparency(watermarkBufferedImage)

        val transparencyColor = getTransparencyColor(watermarkBufferedImage, useWatermarkTransparency)

        val weight = getValidWatermarkWeight()

        val maxDiffX = inputBufferedImage.width - watermarkBufferedImage.width
        val maxDiffY = inputBufferedImage.height - watermarkBufferedImage.height
        val position = getValidPosition(maxDiffX, maxDiffY)

        val outputFilename = getValidOutputFilename()

        val resultImage =
            blendImages(inputBufferedImage, watermarkBufferedImage, weight, position, transparencyColor)

        ImageIO.write(resultImage, outputFilename.split(".").last(), File(outputFilename))

        println("The watermarked image $outputFilename has been created.")
    } catch (ex: RuntimeException) {
        ex.printStackTrace()
        println(ex.message)
    }
}

fun getValidPosition(maxDiffX: Int, maxDiffY: Int): Position {
    println("Choose the position method (single, grid):")
    return when (readLine()!!.lowercase()) {
        "single" -> Position(PositionType.SINGLE, getValidSinglePosition(maxDiffX, maxDiffY))
        "grid" -> Position(PositionType.GRID)
        else -> throw IllegalArgumentException("The position method input is invalid.")
    }
}

fun getValidSinglePosition(maxDiffX: Int, maxDiffY: Int): Pair<Int, Int> {
    println("Input the watermark position ([x 0-$maxDiffX] [y 0-$maxDiffY]):")
    val input = readLine()!!

    val twoIntegers = "(-?\\d+) (-?\\d+)".toRegex()
    if (!input.matches(twoIntegers)) {
        throw IllegalArgumentException("The position input is invalid.")
    }

    val (x, y) = twoIntegers.matchEntire(input)!!.destructured

    if (x.toInt() !in 0..maxDiffX ||
        y.toInt() !in 0..maxDiffY) {
        throw IllegalArgumentException("The position input is out of range.")
    }

    return Pair(x.toInt(), y.toInt())
}

fun getTransparencyColor(watermark: BufferedImage, useWatermarkTransparency: Boolean): TransparencyColor {
    // Watermark uses alpha channel -> do nothing
    if (watermark.colorModel.hasAlpha()) {
        return TransparencyColor(useWatermarkTransparency)
    }

    println("Do you want to set a transparency color?")
    val input = readLine()!!
    if (input != "yes") {
        return TransparencyColor(false)
    }

    println("Input a transparency color ([Red] [Green] [Blue]):")
    val colorInput = readLine()!!

    val threeIntegers = "(\\d+) (\\d+) (\\d+)".toRegex()
    if (!threeIntegers.matches(colorInput)) {
        throw IllegalArgumentException("The transparency color input is invalid.")
    }

    val (r, g, b) = threeIntegers.matchEntire(colorInput)!!.destructured
    try {
        return TransparencyColor(useWatermarkTransparency, Color(r.toInt(), g.toInt(), b.toInt()))
    } catch (ex: IllegalArgumentException) { // color value not in 0..255
        throw IllegalArgumentException("The transparency color input is invalid.")
    }
}

fun getValidInputImage(): BufferedImage {
    println("Input the image filename:")
    val filename = readLine()!!

    val image = File(filename)
    if (!image.exists()) {
        throw RuntimeException("The file $filename doesn't exist.")
    }

    val bufferedImage = ImageIO.read(image)
    if (bufferedImage.colorModel.numColorComponents != 3) {
        throw RuntimeException("The number of image color components isn't 3.")
    } else if (bufferedImage.colorModel.pixelSize !in VALID_BIT_VALUES) {
        throw RuntimeException("The image isn't 24 or 32-bit.")
    }
    return bufferedImage
}

fun getValidWatermarkImage(): BufferedImage {
    println("Input the watermark image filename:")
    val filename = readLine()!!

    val image = File(filename)
    if (!image.exists()) {
        throw RuntimeException("The file $filename doesn't exist.")
    }

    val bufferedImage = ImageIO.read(image)
    if (bufferedImage.colorModel.numColorComponents != 3) {
        throw RuntimeException("The number of watermark color components isn't 3.")
    } else if (bufferedImage.colorModel.pixelSize !in VALID_BIT_VALUES) {
        throw RuntimeException("The watermark isn't 24 or 32-bit.")
    }


    return bufferedImage
}

fun getValidWatermarkWeight(): Int {
    println("Input the watermark transparency percentage (Integer 0-100):")
    val input = readLine()!!

    if (!input.matches(Regex("\\d+"))) {
        throw RuntimeException("The transparency percentage isn't an integer number.")
    }

    val tr = input.toInt()
    if (tr !in 0..100) {
        throw RuntimeException("The transparency percentage is out of range.")
    }
    return tr
}

fun getValidWatermarkTransparency(watermark: BufferedImage): Boolean {
    if (watermark.colorModel.transparency == Transparency.TRANSLUCENT) {
        println("Do you want to use the watermark's Alpha channel?")
        val input = readLine()!!
        if (input.lowercase() == "yes") {
            return true
        }
    }
    return false
}

fun getValidOutputFilename(): String {
    println("Input the output image filename (jpg or png extension):")
    val input = readLine()!!

    if (!input.endsWith(".jpg") && !input.endsWith(".png")) {
        throw RuntimeException("The output file extension isn't \"jpg\" or \"png\".")
    }
    return input
}

fun blendImages(
    image: BufferedImage, watermark: BufferedImage, weight: Int,
    position: Position,
    transparencyColor: TransparencyColor
): BufferedImage {
    val resultImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)

    for (x in 0 until image.width) {
        for (y in 0 until image.height) {
            val i = Color(image.getRGB(x, y))

            val w = if (position.type == PositionType.GRID) {
                Color(watermark.getRGB(x % watermark.width, y % watermark.height), transparencyColor.use)
            } else {
                if (x in position.pair!!.first until(position.pair.first + watermark.width) &&
                    y in position.pair!!.second until (position.pair.second + watermark.height)) {
                    Color(watermark.getRGB(x - position.pair.first, y - position.pair.second), transparencyColor.use)
                } else
                    Color(image.getRGB(x, y))
            }

            val watermarkColorIsTransparencyColor =
                w.red   == transparencyColor.color?.red &&
                        w.green == transparencyColor.color.green &&
                        w.blue  == transparencyColor.color.blue

            val color = if (w.alpha == 0 || watermarkColorIsTransparencyColor) {
                Color(i.red, i.green, i.blue)
            } else {
                Color(mixColors(w.red,   i.red,   weight),
                    mixColors(w.green, i.green, weight),
                    mixColors(w.blue,   i.blue, weight))
            }

            resultImage.setRGB(x, y, color.rgb)
        }
    }

    return resultImage
}

fun mixColors(watermarkColor: Int, imageColor: Int, weight: Int): Int {
    return (weight * watermarkColor + (100 - weight) * imageColor) / 100
}

fun validateDimensions(image: BufferedImage, watermark: BufferedImage) {
    if (watermark.width > image.width||
        watermark.height > image.height
    ) {
        throw RuntimeException("The watermark's dimensions are larger.")
    }
}
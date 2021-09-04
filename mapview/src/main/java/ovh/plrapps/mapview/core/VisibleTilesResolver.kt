package ovh.plrapps.mapview.core

import ovh.plrapps.mapview.util.rotateX
import ovh.plrapps.mapview.util.rotateY
import kotlin.math.*

/**
 * Resolves the visible tiles.
 * This class isn't thread-safe, and public methods should be invoked from the same thread to ensure
 * consistency.
 *
 * @param levelCount Number of levels
 * @param fullWidth Width of the map at scale 1.0f
 * @param fullHeight Height of the map at scale 1.0f
 * @param magnifyingFactor Alters the level at which tiles are picked for a given scale. By default,
 * the level immediately higher (in index) is picked, to avoid sub-sampling. This corresponds to a
 * [magnifyingFactor] of 0. The value 1 will result in picking the current level at a given scale,
 * which will be at a relative scale between 1.0 and 2.0
 *
 * @author peterLaurence on 25/05/2019
 */
internal class VisibleTilesResolver(private val levelCount: Int, private val fullWidth: Int,
                                    private val fullHeight: Int, private val tileSize: Int = 256,
                                    private val magnifyingFactor: Int = 0) {

    private var scale: Float = 1.0f
    var currentLevel = levelCount - 1
        private set
    var subSample: Int = 0
        private set

    /**
     * Last level is at scale 1.0f, others are at scale 1.0 / power_of_2
     */
    private val scaleForLevel: Map<Int, Double> = (0 until levelCount).associateWith {
        (1.0 / 2.0.pow((levelCount - it - 1)))
    }

    fun setScale(scale: Float) {
        this.scale = scale

        this.subSample = if (scale < scaleForLevel[0]?.toFloat() ?: Float.MIN_VALUE) {
            ceil(ln((scaleForLevel[0] ?: error("")).toDouble() / scale) / ln(2.0)).toInt()
        } else {
            0
        }

        /* Update current level */
        currentLevel = getLevel(scale, magnifyingFactor)
    }

    /**
     * Get the scale for a given [level] (also called zoom).
     * @return the scale or null if no such level was configured.
     */
    fun getScaleForLevel(level: Int): Float? {
        return scaleForLevel[level]?.toFloat()
    }

    /**
     * Returns the level, an entire value belonging to [0 ; [levelCount] - 1]
     */
    private fun getLevel(scale: Float, magnifyingFactor: Int = 0): Int {
        /* This value can be negative */
        val partialLevel = levelCount - 1 - magnifyingFactor +
                ln(scale.toDouble()) / ln(2.0)

        /* The level can't be greater than levelCount - 1.0 */
        val capedLevel = min(partialLevel, levelCount - 1.0)

        /* The level can't be lower than 0 */
        return ceil(max(capedLevel, 0.0)).toInt()
    }

    /**
     * Get the [VisibleTiles], given the visible area in pixels.
     *
     * @param viewport The [Viewport] which represents the visible area. Its values depend on the
     * scale.
     */
    fun getVisibleTiles(viewport: Viewport, level: Int = currentLevel): VisibleTiles {
        val scaleAtLevel = scaleForLevel[level] ?: throw AssertionError()
        val relativeScale = scale / scaleAtLevel

        /* At the current level, row and col index have maximum values */
        val maxCol = max(0.0, ceil(fullWidth * scaleAtLevel / tileSize) - 1).toInt()
        val maxRow = max(0.0, ceil(fullHeight * scaleAtLevel / tileSize) - 1).toInt()

        fun Int.lowerThan(limit: Int): Int {
            return if (this <= limit) this else limit
        }

        val scaledTileSize = tileSize.toDouble() * relativeScale

        fun makeVisibleTiles(left: Int, top: Int, right: Int, bottom: Int): VisibleTiles {
            val colLeft = floor(left / scaledTileSize).toInt().lowerThan(maxCol).coerceAtLeast(0)
            val rowTop = floor(top / scaledTileSize).toInt().lowerThan(maxRow).coerceAtLeast(0)
            val colRight = (ceil(right / scaledTileSize).toInt() - 1).lowerThan(maxCol)
            val rowBottom = (ceil(bottom / scaledTileSize).toInt() - 1).lowerThan(maxRow)

            val tileMatrix = (rowTop..rowBottom).associateWith {
                colLeft..colRight
            }
            val count = (rowBottom - rowTop + 1) * (colRight - colLeft + 1)
            return VisibleTiles(level, tileMatrix, count, subSample)
        }

        return if (viewport.angleRad == 0f) {
            makeVisibleTiles(viewport.left, viewport.top, viewport.right, viewport.bottom)
        } else {
            val xTopLeft = viewport.left
            val yTopLeft = viewport.top

            val xTopRight = viewport.right
            val yTopRight = viewport.top

            val xBotLeft = viewport.left
            val yBotLeft = viewport.bottom

            val xBotRight = viewport.right
            val yBotRight = viewport.bottom

            val xCenter = (viewport.right + viewport.left).toDouble() / 2
            val yCenter = (viewport.bottom + viewport.top).toDouble() / 2

            val xTopLeftRot = rotateX(xTopLeft - xCenter, yTopLeft - yCenter, viewport.angleRad) + xCenter
            val yTopLeftRot = rotateY(xTopLeft - xCenter, yTopLeft - yCenter, viewport.angleRad) + yCenter
            var xLeftMost = xTopLeftRot
            var yTopMost = yTopLeftRot
            var xRightMost = xTopLeftRot
            var yBotMost = yTopLeftRot

            val xTopRightRot = rotateX(xTopRight - xCenter, yTopRight - yCenter, viewport.angleRad) + xCenter
            val yTopRightRot = rotateY(xTopRight - xCenter, yTopRight - yCenter, viewport.angleRad) + yCenter
            xLeftMost = xLeftMost.coerceAtMost(xTopRightRot)
            yTopMost = yTopMost.coerceAtMost(yTopRightRot)
            xRightMost = xRightMost.coerceAtLeast(xTopRightRot)
            yBotMost = yBotMost.coerceAtLeast(yTopRightRot)

            val xBotLeftRot = rotateX(xBotLeft - xCenter, yBotLeft - yCenter, viewport.angleRad) + xCenter
            val yBotLeftRot = rotateY(xBotLeft - xCenter, yBotLeft - yCenter, viewport.angleRad) + yCenter
            xLeftMost = xLeftMost.coerceAtMost(xBotLeftRot)
            yTopMost = yTopMost.coerceAtMost(yBotLeftRot)
            xRightMost = xRightMost.coerceAtLeast(xBotLeftRot)
            yBotMost = yBotMost.coerceAtLeast(yBotLeftRot)

            val xBotRightRot = rotateX(xBotRight - xCenter, yBotRight - yCenter, viewport.angleRad) + xCenter
            val yBotRightRot = rotateY(xBotRight - xCenter, yBotRight - yCenter, viewport.angleRad) + yCenter
            xLeftMost = xLeftMost.coerceAtMost(xBotRightRot)
            yTopMost = yTopMost.coerceAtMost(yBotRightRot)
            xRightMost = xRightMost.coerceAtLeast(xBotRightRot)
            yBotMost = yBotMost.coerceAtLeast(yBotRightRot)

            makeVisibleTiles(xLeftMost.toInt(), yTopMost.toInt(), xRightMost.toInt(), yBotMost.toInt())
        }
    }
}

/**
 * Properties container for the computed visible tiles.
 * @param level 0-based level index
 * @param tileMatrix contains all (row, col) indexes, grouped by rows
 * @param count the precomputed total count
 * @param subSample the current sub-sample factor. If the current scale of the [VisibleTilesResolver]
 * is lower than the scale of the minimum level, [subSample] is greater than 0. Otherwise, [subSample]
 * equals 0.
 */
data class VisibleTiles(var level: Int, val tileMatrix: TileMatrix, val count: Int, val subSample: Int = 0)

typealias Row = Int
typealias ColRange = IntRange
typealias TileMatrix = Map<Row, ColRange>
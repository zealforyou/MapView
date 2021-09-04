package ovh.plrapps.mapview.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream

/**
 * Test the [TileCollector.collectTiles] engine. The following assertions are tested:
 * * The Bitmap flow should pick a [Bitmap] from the pool if possible
 * * If [TileSpec]s are send to the input channel, corresponding [Tile]s are received from the
 * output channel (from the [TileCollector.collectTiles] point of view).
 * * The [Bitmap] of the [Tile]s produced should be consistent with the output of the flow
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "src/main/AndroidManifest.xml", sdk = [Build.VERSION_CODES.P])
class TileCollectorTest {

    private val tileSize = 256

    companion object {
        private var assetsDir: File? = null

        init {
            try {
                val mapviewDirURL = TileCollectorTest::class.java.classLoader!!.getResource("mapview")
                assetsDir = File(mapviewDirURL.toURI())
            } catch (e: Exception) {
                println("No mapview directory found.")
            }

        }
    }

    @Test
    fun fullTest() = runBlocking {
        assertNotNull(assetsDir)
        val imageFile = File(assetsDir, "10.jpg")
        assertTrue(imageFile.exists())

        /* Setup the channels */
        val visibleTileLocationsChannel = Channel<TileSpec>(capacity = Channel.RENDEZVOUS)
        val tilesOutput = Channel<Tile>(capacity = Channel.RENDEZVOUS)

        val pool = Pool<Bitmap>()

        val mapViewTileStreamProvider = ovh.plrapps.mapview.core.TileStreamProvider { _, _, _ -> FileInputStream(imageFile) }

        val bitmapFlow: Flow<Bitmap> = flow {
            val bitmap = pool.get()
            emit(bitmap)
        }.flowOn(Dispatchers.Unconfined).map {
            it ?: Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.RGB_565)
        }

        val bitmapReference = try {
            val inputStream = FileInputStream(imageFile)
            val bitmapLoadingOptions = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeStream(inputStream, null, bitmapLoadingOptions)
        } catch (e: Exception) {
            fail()
            error("Could not decode image")
        }

        fun CoroutineScope.consumeTiles(tileChannel: ReceiveChannel<Tile>) = launch {
            for (tile in tileChannel) {
                println("received tile ${tile.zoom}-${tile.row}-${tile.col}")
                assertTrue(tile.bitmap.sameAs(bitmapReference))

                /* Add bitmap to the pool only if they are from level 0 */
                if (tile.zoom == 0) {
                    pool.put(tile.bitmap)
                }
            }
        }

        val job = launch {
            with(TileCollector(1, Bitmap.Config.RGB_565)) {
                collectTiles(visibleTileLocationsChannel, tilesOutput, mapViewTileStreamProvider, bitmapFlow)
                consumeTiles(tilesOutput)
            }
        }

        launch {
            val locations1 = listOf(
                    TileSpec(0, 0, 0),
                    TileSpec(0, 1, 1),
                    TileSpec(0, 2, 1)
            )
            for (spec in locations1) {
                visibleTileLocationsChannel.send(spec)
            }
            delay(100)
            val locations2 = listOf(
                    TileSpec(1, 0, 0),
                    TileSpec(1, 1, 1),
                    TileSpec(1, 2, 1)
            )
            /* Bitmaps inside the pool should be used */
            for (spec in locations2) {
                visibleTileLocationsChannel.send(spec)
            }

            // wait a little, then cancel the job
            delay(1000)
            job.cancel()

            // in the end, the pool should be empty since we put 3 bitmap in it, then requested 3
            // more tiles right after.
            assertEquals(0, pool.size)
        }
        Unit
    }
}
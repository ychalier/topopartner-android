package com.piweb.topopartner

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import org.osmdroid.tileprovider.modules.SqliteArchiveTileWriter
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import java.io.File


class TileCacheActivity : AppCompatActivity() {

    private lateinit var mMapView: MapView
    private lateinit var mCacheManager: CacheManager
    private lateinit var mWriter: SqliteArchiveTileWriter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tile_cache)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        createMap()
        findViewById<Button>(R.id.button_download).setOnClickListener {
            downloadTiles()
        }

    }

    private fun createMap() {
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
        mMapView = findViewById(R.id.map)
        mMapView.setTileSource(TileSourceFactory.OpenTopo)
        mMapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        mMapView.setMultiTouchControls(true)
        mMapView.controller.setCenter(GeoPoint(45.772515, 2.964141))
        mMapView.controller.setZoom(6.0)
    }

    private fun downloadTiles() {
        val boundingBox = mMapView.boundingBox
        val zoomMin = 7
        val zoomMax = 15
        Log.i(
            "TileCacheActivity",
            "Downloading area $boundingBox with zoom from $zoomMin to $zoomMax"
        )
        val callback = object : CacheManager.CacheManagerCallback {
            override fun downloadStarted() {
                Log.i("TileCache", "Download started")
                Toast.makeText(baseContext, "Started download", Toast.LENGTH_SHORT).show()
            }

            override fun updateProgress(
                progress: Int,
                currentZoomLevel: Int,
                zoomMin: Int,
                zoomMax: Int
            ) {
                Log.i("TileCache", "Download progress: $progress")
            }

            override fun onTaskFailed(errors: Int) {
                Log.e("TileCache", "Download failed (errors=$errors)")
            }

            override fun onTaskComplete() {
                Log.i("TileCache", "Download finished!")
                Toast.makeText(baseContext, "Finished downloading!", Toast.LENGTH_SHORT).show()
            }

            override fun setPossibleTilesInArea(total: Int) {
                Log.i("TileCache", "There are $total tiles to download")
                Toast.makeText(
                    baseContext,
                    "There are $total tiles to download",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        val outputName =
            this.dataDir.absolutePath + File.separator + "files" + File.separator + "osmdroid" + File.separator + "topopartner.sqlite"
        Log.i("TileCache", "Writer will store tiles into $outputName")
        mWriter = SqliteArchiveTileWriter(outputName)
        mCacheManager = CacheManager(mMapView, mWriter)
        mCacheManager.downloadAreaAsync(this, boundingBox, zoomMin, zoomMax, callback)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
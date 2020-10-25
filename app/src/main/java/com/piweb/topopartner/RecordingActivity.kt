package com.piweb.topopartner

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.room.Room
import com.android.volley.Response
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.*


const val INTENT_EXTRA_UID: String = "uid"


class LoadRecording constructor(activity: RecordingActivity) : AsyncTask<Int, Void, Recording>() {

    private val mActivity = activity

    override fun doInBackground(vararg params: Int?): Recording {
        return mActivity.mDatabase.recordingDao().getById(params[0]!!)
    }

    override fun onPostExecute(result: Recording) {
        super.onPostExecute(result)
        mActivity.loadRecording(result)
    }

}

class DeleteRecording constructor(activity: RecordingActivity) :
    AsyncTask<Recording, Void, Void>() {

    private val mActivity = activity

    override fun doInBackground(vararg params: Recording?): Void? {
        params.forEach {
            mActivity.mDatabase.recordingDao().delete(it!!)
        }
        return null
    }

    override fun onPostExecute(result: Void) {
        super.onPostExecute(result)
        mActivity.startActivity(Intent(mActivity, MainActivity::class.java))
    }

}


class UploadRecording constructor(activity: RecordingActivity, recording: Recording) :
    AsyncTask<Void, Void, Void>() {

    private val mActivity = activity
    private val mRecording = recording

    override fun doInBackground(vararg params: Void?): Void? {
        mActivity.mDatabase.recordingDao().setUploaded(mRecording.uid)
        ServerApi(mActivity).postRecording(mRecording, Response.ErrorListener {
            Log.e(
                "Recording Activity",
                "Could not post the recording: ${it.networkResponse.statusCode}"
            )
            Toast.makeText(mActivity, "Server Unreachable", Toast.LENGTH_SHORT).show()
        })
        return null
    }

    override fun onPostExecute(result: Void?) {
        super.onPostExecute(result)
        Toast.makeText(mActivity, "Recording uploaded!", Toast.LENGTH_SHORT).show()
    }

}


class RecordingActivity : AppCompatActivity() {

    lateinit var mDatabase: TopopartnerDatabase
    private lateinit var mRecording: Recording
    private lateinit var mMapView: MapView
    private lateinit var mTrack: Polyline

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recording)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        mDatabase =
            Room.databaseBuilder(applicationContext, TopopartnerDatabase::class.java, "topopartner")
                .fallbackToDestructiveMigration()
                .build()
        LoadRecording(this).execute(intent.getIntExtra(INTENT_EXTRA_UID, 0))
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    fun loadRecording(recording: Recording) {
        mRecording = recording
        val gpx = GpxXmlParser().parse(mRecording.gpx!!.byteInputStream(Charsets.UTF_8))
        val dateFormat = SimpleDateFormat("MMM dd", Locale.ENGLISH)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.ENGLISH)
        findViewById<TextView>(R.id.text_view_title).text = "Recorded on %s at %s".format(
            dateFormat.format(Date(mRecording.date!!)),
            timeFormat.format(Date(mRecording.date!!))
        )
        findViewById<TextView>(R.id.text_view_subtitle).text = "Walked %.1fkm in %s".format(
            gpx.getDistance() / 1000,
            formatDuration(gpx.getElapsed())
        )
        if (mRecording.uploaded) {
            findViewById<TextView>(R.id.text_view_uploaded).text = "Uploaded"
        } else {
            findViewById<TextView>(R.id.text_view_uploaded).text = "Not uploaded"
        }
        val self = this
        findViewById<Button>(R.id.button_upload).setOnClickListener {
            UploadRecording(self, mRecording).execute()
        }
        findViewById<Button>(R.id.button_delete).setOnClickListener {
            val alertDialog: AlertDialog? = this.let {
                val builder = AlertDialog.Builder(it)
                builder.apply {
                    setTitle("Delete recording?")
                    setMessage("This will completely remove this recording from local storage.")
                    setPositiveButton(
                        R.string.delete
                    ) { _, _ ->
                        Log.i("Recording Activity", "User wants to delete the recording")
                        DeleteRecording(self).execute(mRecording)
                    }
                    setNegativeButton(
                        getString(R.string.cancel)
                    ) { _, _ ->
                        // User cancelled the dialog
                    }
                }

                // Create the AlertDialog
                builder.create()
            }
            alertDialog?.show()

        }


        Log.i("Tracking Activity", "Entering .createMap()")
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
        mMapView = findViewById(R.id.map)
        mMapView.setUseDataConnection(false)
        mMapView.setTileSource(TileSourceFactory.OpenTopo)
        mMapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        mMapView.setMultiTouchControls(false)
        mMapView.controller.setCenter(GeoPoint(45.772515, 2.964141))
        mMapView.controller.setZoom(6.0)
        mMapView.setOnTouchListener { _, _ -> true }
        mTrack = Polyline().apply {
            this.outlinePaint.color = Color.argb(
                128,
                Color.red(getColor(R.color.colorAccent)),
                Color.green(getColor(R.color.colorAccent)),
                Color.blue(getColor(R.color.colorAccent))
            )
            this.outlinePaint.strokeCap = Paint.Cap.ROUND
        }
        gpx.getGeoPoints().forEach {
            mTrack.addPoint(it)
        }
        mMapView.overlays.add(mTrack)
        mMapView.zoomToBoundingBox(mTrack.bounds, true)
    }


}
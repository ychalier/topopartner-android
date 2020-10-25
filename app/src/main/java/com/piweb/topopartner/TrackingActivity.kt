package com.piweb.topopartner

import android.content.*
import android.graphics.Color
import android.graphics.Paint
import android.os.AsyncTask
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.room.Room
import com.android.volley.Response
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.Serializable
import java.time.Instant
import java.util.*


class ItineraryWrapper constructor(label: String, distance: Double, uphill: Double, gpx: String) :
    Serializable {

    val mLabel = label
    val mDistance = distance
    val mUphill = uphill
    val mGpx = gpx

}

class ItineraryAdapter(context: Context, objects: List<ItineraryWrapper>) : BaseAdapter() {

    private val mContext = context
    private val mObjects = objects
    private lateinit var mViewHolder: ViewHolder

    class ViewHolder {
        lateinit var mTextViewLabel: TextView
        lateinit var mTextViewDistance: TextView
        lateinit var mTextViewUphill: TextView
    }

    override fun getCount(): Int {
        return mObjects.size
    }

    override fun getItem(position: Int): ItineraryWrapper {
        return mObjects[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var rowView = convertView
        if (convertView == null) {
            mViewHolder = ViewHolder()
            val inflater =
                mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            rowView = inflater.inflate(R.layout.itinerary_list, parent, false)
            mViewHolder.mTextViewLabel = rowView.findViewById(R.id.text_view_label)
            mViewHolder.mTextViewDistance = rowView.findViewById(R.id.text_view_distance)
            mViewHolder.mTextViewUphill = rowView.findViewById(R.id.text_view_uphill)
            rowView.tag = mViewHolder
        } else {
            mViewHolder = convertView.tag as ViewHolder
        }
        val itineraryWrapper = getItem(position)
        mViewHolder.mTextViewLabel.text = itineraryWrapper.mLabel
        mViewHolder.mTextViewDistance.text =
            mContext.getString(R.string.distance, itineraryWrapper.mDistance / 1000)
        mViewHolder.mTextViewUphill.text =
            mContext.getString(R.string.uphill, itineraryWrapper.mUphill)
        return rowView!!
    }

}


class FetchItinerariesRequestTask constructor(activity: TrackingActivity) :
    AsyncTask<Void, Void, Void>() {

    private val mActivity = activity

    override fun doInBackground(vararg params: Void?): Void? {
        mActivity.mDatabase.itineraryDao().deleteAll()
        val api = ServerApi(mActivity)
        api.listItineraries(Response.Listener { response ->
            FetchItinerariesCallbackTask(mActivity).execute(response.getJSONArray("itineraries"))
        }, Response.ErrorListener {
            Log.e("Choose Itinerary Activity", "Could not list the itineraries: $it")
            Toast.makeText(mActivity, "Server Unreachable", Toast.LENGTH_SHORT).show()
        })
        return null
    }

}


class FetchItinerariesCallbackTask constructor(activity: TrackingActivity) :
    AsyncTask<JSONArray, Void, Void>() {

    private val mActivity = activity

    override fun doInBackground(vararg params: JSONArray?): Void? {
        val itineraries = params[0]!!
        for (i in 0 until itineraries.length()) {
            val itineraryObject = itineraries.getJSONObject(i)
            val itinerary = Itinerary(
                // itineraryObject.getInt("tid"),
                itineraryObject.getString("label"),
                itineraryObject.getString("gpx"),
                itineraryObject.getDouble("distance"),
                itineraryObject.getDouble("uphill")
            )
            mActivity.mDatabase.itineraryDao().insert(itinerary)
        }
        return null
    }

    override fun onPostExecute(result: Void?) {
        super.onPostExecute(result)
        mActivity.inflateItineraries()
    }

}


class SaveRecording constructor(activity: TrackingActivity) : AsyncTask<Void, Void, Void>() {

    private val mActivity = activity

    override fun doInBackground(vararg params: Void?): Void? {
        val gpx = Gpx()
        val gpxTrk = GpxTrk()
        gpxTrk.mTrkSegs.add(mActivity.mGpxTrkSeg)
        gpx.mTrks.add(gpxTrk)
        mActivity.mDatabase.recordingDao().insert(
            Recording(
                false,
                gpx.toXml(),
                mActivity.mGpxTrkSeg.mTrkPts.first().mTime?.time
            )
        )
        return null
    }

    override fun onPostExecute(result: Void?) {
        super.onPostExecute(result)
        mActivity.startActivity(Intent(mActivity, MainActivity::class.java))
    }

}


class LoadItineraries constructor(activity: TrackingActivity, listView: ListView) :
    AsyncTask<Void, Void, List<Itinerary>>() {

    private val mActivity = activity
    private val mListView = listView

    override fun doInBackground(vararg params: Void?): List<Itinerary> {
        return mActivity.mDatabase.itineraryDao().getAll()
    }

    override fun onPostExecute(result: List<Itinerary>) {
        super.onPostExecute(result)
        val listItems = mutableListOf<ItineraryWrapper>()
        result.forEach {
            listItems.add(
                ItineraryWrapper(
                    it.label!!,
                    it.distance!!,
                    it.uphill!!,
                    it.gpx!!
                )
            )
        }
        val adapter = ItineraryAdapter(mActivity.applicationContext, listItems)
        mListView.adapter = adapter
        mListView.setOnItemClickListener { _, _, position, _ ->
            val itineraryWrapper = adapter.getItem(position)
            val gpx = GpxXmlParser().parse(itineraryWrapper.mGpx.byteInputStream(Charsets.UTF_8))
            mActivity.findViewById<TextView>(R.id.bottom_sheet_header_subtitle).text =
                itineraryWrapper.mLabel
            mActivity.mItineraryPolyline.setPoints(gpx.getGeoPoints())
            mActivity.mBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            mActivity.mItinerary = itineraryWrapper
        }
    }

}


class TrackingActivity : AppCompatActivity() {

    lateinit var mDatabase: TopopartnerDatabase
    private lateinit var mMapView: MapView
    private lateinit var mLocationOverlay: MyLocationNewOverlay
    val mItineraryPolyline = Polyline().apply {
        this.outlinePaint.color = Color.parseColor("#804caf50")
        this.outlinePaint.strokeCap = Paint.Cap.ROUND
    }
    lateinit var mBottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var mService: TrackingService
    private lateinit var mReceiver: BroadcastReceiver
    private var mBound: Boolean = false
    val mGpxTrkSeg: GpxTrkSeg = GpxTrkSeg()
    private lateinit var mTrack: Polyline
    private var mDistance: Double = 0.0
    private var mElapsed: Long = 0
    private var mRecording: Boolean = false
    private lateinit var mFloatingActionButton: FloatingActionButton
    var mItinerary: ItineraryWrapper? = null

    private val mConnection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.i("Tracking Activity", "Successfully bound to the tracking service.")
            val binder = service as TrackingService.LocalBinder
            mService = binder.getService()
            mBound = true
            setRecording(mService.isForeground())
            if (mRecording) {
                val itinerary = mService.getItinerary()
                if (itinerary != null) {
                    val gpx = GpxXmlParser().parse(itinerary.mGpx.byteInputStream(Charsets.UTF_8))
                    findViewById<TextView>(R.id.bottom_sheet_header_subtitle).text =
                        itinerary.mLabel
                    mItineraryPolyline.setPoints(gpx.getGeoPoints())
                }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            Log.i("Tracking Activity", "Tracking service disconnected.")
            mBound = false
        }

    }

    private fun updateFabIcon() {
        if (mRecording) {
            mFloatingActionButton.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.ic_baseline_stop_24
                )
            )
        } else {
            mFloatingActionButton.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.ic_baseline_play_arrow_24
                )
            )
        }
    }

    private fun setRecording(recording: Boolean) {
        mRecording = recording
        updateFabIcon()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i("Tracking Activity", "Entering .onCreate()")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracking)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        mDatabase =
            Room.databaseBuilder(applicationContext, TopopartnerDatabase::class.java, "topopartner")
                .fallbackToDestructiveMigration()
                .build()
        createMap()
        inflateItineraries()
        mFloatingActionButton = findViewById(R.id.fab_play_stop_trace)
        mFloatingActionButton.setOnClickListener {
            if (mRecording) {
                stopTrace()
            } else {
                startTrace()
            }
        }
        mReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.i("Tracking Activity", "Received intent $intent")
                onReceiveLocationUpdate()
            }
        }
        setupBottomSheet()
    }

    fun inflateItineraries() {
        val listView: ListView = findViewById(R.id.itineraries_list_view)
        LoadItineraries(this, listView).execute()
    }

    private fun setupBottomSheet() {
        val bottomSheetTitle = findViewById<LinearLayout>(R.id.bottom_sheet_header)
        val bottomSheetLayout = findViewById<LinearLayout>(R.id.bottom_sheet_itineraries)
        mBottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout)
        mBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetTitle.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        mBottomSheetBehavior.peekHeight = bottomSheetTitle.measuredHeight
        val listView = findViewById<ListView>(R.id.itineraries_list_view)
        val emptyTextView = findViewById<TextView>(R.id.text_view_empty)
        listView.emptyView = emptyTextView
        findViewById<Button>(R.id.button_refresh_itineraries).setOnClickListener {
            FetchItinerariesRequestTask(this).execute()
        }
    }

    fun onReceiveLocationUpdate() {
        val trkSeg = mService.getGpxTrkSeg()
        for (i in mGpxTrkSeg.mTrkPts.size until trkSeg.mTrkPts.size) {
            if (mGpxTrkSeg.mTrkPts.isNotEmpty()) {
                mDistance += mGpxTrkSeg.mTrkPts.last().toGeoPoint()
                    .distanceToAsDouble(trkSeg.mTrkPts[i].toGeoPoint())
                mElapsed = Date.from(Instant.now()).time - mGpxTrkSeg.mTrkPts.first().mTime!!.time
                val avgSpeed: Double = 3600 * mDistance / mElapsed
                Log.i(
                    "Tracking Activity",
                    "Walked distance is now $mDistance and elapsed is $mElapsed, avg speed is ${avgSpeed}km/h"
                )
                updateDistanceView()
                updateElapsedView()
            }
            mGpxTrkSeg.mTrkPts.add(trkSeg.mTrkPts[i])
            mTrack.addPoint(trkSeg.mTrkPts[i].toGeoPoint())
        }
    }

    private fun updateDistanceView() {
        val textView = findViewById<TextView>(R.id.text_view_distance)
        if (mDistance < 1000) {
            textView.text = getString(R.string.distance_meters).format(mDistance)
        } else {
            textView.text = getString(R.string.distance_kilometers).format(mDistance / 1000)
        }
    }

    private fun updateElapsedView() {
        val textView = findViewById<TextView>(R.id.text_view_elapsed)
        val hours: Int = (mElapsed / 3600000).toInt()
        var remaining = mElapsed - 3600000 * hours
        val minutes: Int = (remaining / 60000).toInt()
        remaining -= 60000 * minutes
        val seconds: Int = (remaining / 1000).toInt()
        if (hours > 0) {
            textView.text = getString(R.string.elapsed).format(hours, minutes)
        } else {
            textView.text = getString(R.string.elapsed).format(minutes, seconds)
        }
    }

    override fun onStart() {
        Log.i("Tracking Activity", "Entering .onStart()")
        super.onStart()
        Intent(this, TrackingService::class.java).also { intent ->
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        }
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(mReceiver, IntentFilter(LOCATION_UPDATE_INTENT_FILTER))
    }

    override fun onResume() {
        super.onResume()
        mMapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mMapView.onPause()
    }

    override fun onStop() {
        Log.i("Tracking Activity", "Entering .onStop()")
        if (mBound) {
            unbindService(mConnection)
            mBound = false
            Log.i("Tracking Activity", "Unbounded tracking service")
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver)
        super.onStop()
    }

    override fun onDestroy() {
        Log.i("Tracking Activity", "Entering .onDestroy()")
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun createMap() {
        Log.i("Tracking Activity", "Entering .createMap()")
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
        mMapView = findViewById(R.id.map)
        // mMapView.setUseDataConnection(false)
        mMapView.setTileSource(TileSourceFactory.OpenTopo)
        mMapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        mMapView.setMultiTouchControls(true)
        mMapView.controller.setZoom(15.0)
        mLocationOverlay = MyLocationNewOverlay(
            GpsMyLocationProvider(this.applicationContext),
            mMapView
        )
        mLocationOverlay.setDirectionArrow(
            ContextCompat.getDrawable(this, R.drawable.ic_baseline_gps_fixed_24_pink)!!.toBitmap(),
            ContextCompat.getDrawable(this, R.drawable.ic_baseline_navigation_24_pink)!!.toBitmap()
        )
        mLocationOverlay.enableMyLocation()
        mLocationOverlay.enableFollowLocation()
        mMapView.overlays.add(mLocationOverlay)
        mTrack = Polyline().apply {
            this.outlinePaint.color = Color.argb(
                128,
                Color.red(getColor(R.color.colorAccent)),
                Color.green(getColor(R.color.colorAccent)),
                Color.blue(getColor(R.color.colorAccent))
            )
            this.outlinePaint.strokeCap = Paint.Cap.ROUND
        }
        mMapView.overlays.add(mTrack)
        mMapView.overlays.add(mItineraryPolyline)
    }

    private fun startTrace() {
        if (mBound) {
            Log.i("Tracking Activity", "Starting the foreground service")
            val intent = Intent(this, TrackingService::class.java)
            if (mItinerary != null) {
                intent.putExtra("itinerary", mItinerary)
            }
            startForegroundService(intent)
            setRecording(true)
        } else {
            Log.w(
                "Tracking Activity",
                "Tried to start the foreground service but it was not bound."
            )
            Toast.makeText(this, "Service Not Bound", Toast.LENGTH_SHORT).show()
        }

    }

    private fun stopTrace() {
        if (mBound) {
            val intent = Intent(this, TrackingService::class.java)
            stopService(intent)
            unbindService(mConnection)
            mBound = false
            if (mGpxTrkSeg.mTrkPts.isNotEmpty()) {
                SaveRecording(this).execute()
            }
            setRecording(false)
        } else {
            Log.w("Tracking Activity", "Tried to stop the foreground service but it was not bound.")
            Toast.makeText(this, "Service Not Bound", Toast.LENGTH_SHORT).show()
        }
    }

    // TODO: 11/10/2020 Add Picture-in-picture (PIP) support
    /*
    override fun onUserLeaveHint() {
        val params = PictureInPictureParams.Builder().setAspectRatio(Rational(2, 3)).build()
        enterPictureInPictureMode(params)
    }
    */

}
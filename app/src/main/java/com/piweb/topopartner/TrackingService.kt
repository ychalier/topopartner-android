package com.piweb.topopartner

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
import android.location.Location
import android.os.*
import android.os.Process.THREAD_PRIORITY_BACKGROUND
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import java.util.*


const val LOCATION_UPDATE_INTENT_FILTER = "com.piweb.topopartner.LOCATION_UPDATE"
const val NOTIFICATION_CHANNEL_ID = "tracking"


class TrackingService : Service() {

    private lateinit var mItinerary: ItineraryWrapper
    private lateinit var mServiceLooper: Looper
    private lateinit var mServiceHandler: ServiceHandler
    private lateinit var mGpxTrkSeg: GpxTrkSeg
    private val mBinder = LocalBinder()
    private var mIsForeground: Boolean = false
    private lateinit var mBroadcaster: LocalBroadcastManager
    private var mAllowRebind = true

    fun getItinerary(): ItineraryWrapper? {
        return mItinerary
    }

    fun getGpxTrkSeg(): GpxTrkSeg {
        return mGpxTrkSeg
    }

    fun isForeground(): Boolean {
        return mIsForeground
    }

    inner class LocalBinder : Binder() {
        fun getService(): TrackingService = this@TrackingService
    }

    private inner class ServiceHandler(looper: Looper) : Handler(looper) {

        private lateinit var mLocationCallback: LocationCallback
        private lateinit var mFusedLocationClient: FusedLocationProviderClient
        private lateinit var mCurrentLocation: Location

        @SuppressLint("MissingPermission")  // Permissions already granted in the activity
        fun trackUser() {
            Log.i("Tracking Service", "Entering .trackUser() on service ${this@TrackingService}")

            mGpxTrkSeg = GpxTrkSeg()

            // https://developer.android.com/training/location/retrieve-current
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(baseContext)
            mFusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                mCurrentLocation = location!!
            }

            // https://developer.android.com/training/location/change-location-settings
            val locationRequest = LocationRequest.create()?.apply {
                interval = 10000
                fastestInterval = 5000
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }

            // https://developer.android.com/training/location/request-updates
            mLocationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult?) {
                    Log.i("Location Update Callback", "Received location result: $locationResult")
                    locationResult ?: return
                    for (location in locationResult.locations) {
                        mGpxTrkSeg.mTrkPts.add(
                            GpxTrkPt(
                                location.latitude,
                                location.longitude,
                                Date(location.time),
                                location.altitude
                            )
                        )
                    }
                    val intent = Intent(LOCATION_UPDATE_INTENT_FILTER)
                    mBroadcaster.sendBroadcast(intent)
                }
            }
            mFusedLocationClient.requestLocationUpdates(
                locationRequest,
                mLocationCallback,
                Looper.getMainLooper()
            )
            Log.i("Tracking Service", "End of .trackUser()")
        }

        fun stopLocationUpdates() {
            Log.i(
                "Tracking Service",
                "Stopping location updates for service ${this@TrackingService}"
            )
            mFusedLocationClient.removeLocationUpdates(mLocationCallback)
        }

        override fun handleMessage(msg: Message) {
            Log.i("Tracking Service", "Handling message $msg for service ${this@TrackingService}")
            try {
                trackUser()
            } catch (e: InterruptedException) {
                Log.e("Tracking Service", "Thread interrupted")
                Thread.currentThread().interrupt()
            }
        }
    }

    override fun onCreate() {
        Log.i("Tracking Service", "Creating service $this")
        mBroadcaster = LocalBroadcastManager.getInstance(this)
        HandlerThread("ServiceStartArguments", THREAD_PRIORITY_BACKGROUND).apply {
            start()
            mServiceLooper = looper
            mServiceHandler = ServiceHandler(looper)
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (mIsForeground) {
            Log.i("Tracking Service", "Service $this is already in foreground")
            Toast.makeText(this, "Already Tracking", Toast.LENGTH_SHORT).show()
        } else {
            Log.i("Tracking Service", "Starting foreground service $this")

            if (intent.hasExtra("itinerary")) {
                mItinerary = intent.getSerializableExtra("itinerary") as ItineraryWrapper
            }

            val notificationIntent = Intent(this, TrackingActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
            val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getText(R.string.app_name))
                .setContentText(getString(R.string.a_track_is_being_recorded))
                .setSmallIcon(R.drawable.ic_baseline_gps_fixed_24)
                .setContentIntent(pendingIntent)
                .build()

            Log.i("Tracking Service", "Here is the notification: $notification")

            mIsForeground = true
            startForeground(1, notification, FOREGROUND_SERVICE_TYPE_LOCATION)

            Toast.makeText(this, "Started Tracking", Toast.LENGTH_SHORT).show()

            mServiceHandler.obtainMessage().also { msg ->
                msg.arg1 = startId
                mServiceHandler.sendMessage(msg)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        Log.i("Tracking Service", "Calling .onBind() on $this")
        return mBinder
    }

    override fun onDestroy() {
        Log.i("Tracking Service", "Service $this is destroyed.")
        if (mIsForeground) {
            mServiceHandler.stopLocationUpdates()
            Toast.makeText(this, "Stopped Tracking", Toast.LENGTH_SHORT).show()
        }
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.i("Tracking Service", "Entering .onUnbind()")
        return mAllowRebind
    }

    override fun onRebind(intent: Intent) {
        Log.i("Tracking Service", "Entering .onRebind()")
    }

}

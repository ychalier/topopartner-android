package com.piweb.topopartner

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.room.Room
import java.text.SimpleDateFormat
import java.util.*


const val VERSION = "0.1.0"


class LoadHistory constructor(activity: MainActivity, listView: ListView) :
    AsyncTask<Void, Void, List<Recording>>() {

    private val mActivity = activity
    private val mListView = listView

    override fun doInBackground(vararg params: Void?): List<Recording> {
        return mActivity.mDatabase.recordingDao().getAll()
    }

    override fun onPostExecute(result: List<Recording>) {
        super.onPostExecute(result)
        Log.i("Main Activity", "History contains ${result.size} items")
        val listItems = mutableListOf<RecordingWrapper>()
        result.forEach {
            if (it.date != null) {
                listItems.add(RecordingWrapper(it.uid, Date(it.date!!), it.gpx!!, it.uploaded))
            }
        }
        val adapter = RecordingAdapter(mActivity.applicationContext, listItems)
        mListView.adapter = adapter
        mListView.setOnItemClickListener { _, _, position, _ ->
            val element = adapter.getItem(position)
            val intent = Intent(mActivity, RecordingActivity::class.java)
            intent.putExtra(INTENT_EXTRA_UID, element.mUid)
            mActivity.startActivity(intent)
        }
    }

}


class RecordingWrapper constructor(uid: Int, date: Date, gpx: String, uploaded: Boolean) {

    val mUid = uid
    val mDate = date
    val mGpx = gpx
    val mUploaded = uploaded

}


fun formatDuration(millis: Long): String {
    val hours: Int = (millis / 3600000).toInt()
    var remaining = millis - 3600000 * hours
    val minutes: Int = (remaining / 60000).toInt()
    remaining -= minutes * 60000
    if (hours > 0) {
        return "${hours}h" + "%02d".format(minutes)
    }
    return "${minutes}min"
}


class RecordingAdapter(context: Context, objects: List<RecordingWrapper>) : BaseAdapter() {

    private val mContext = context
    private val mObjects = objects
    private lateinit var mViewHolder: ViewHolder

    override fun getCount(): Int {
        return mObjects.size
    }

    override fun getItem(position: Int): RecordingWrapper {
        return mObjects[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    class ViewHolder {
        lateinit var mTextViewDay: TextView
        lateinit var mTextViewMonth: TextView
        lateinit var mTextViewTitle: TextView
        lateinit var mTextViewSubtitle: TextView
        lateinit var mImageViewUploaded: ImageView
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var rowView = convertView
        if (convertView == null) {
            mViewHolder = ViewHolder()
            val inflater =
                mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            rowView = inflater.inflate(R.layout.recording_list, parent, false)
            mViewHolder.mTextViewDay = rowView.findViewById(R.id.text_view_day)
            mViewHolder.mTextViewMonth = rowView.findViewById(R.id.text_view_month)
            mViewHolder.mTextViewTitle = rowView.findViewById(R.id.text_view_title)
            mViewHolder.mTextViewSubtitle = rowView.findViewById(R.id.text_view_subtitle)
            mViewHolder.mImageViewUploaded = rowView.findViewById(R.id.image_view_uploaded)
            rowView.tag = mViewHolder
        } else {
            mViewHolder = convertView.tag as ViewHolder
        }
        val recordingWrapper = getItem(position)
        val dayOfMonthFormat = SimpleDateFormat("d", Locale.ENGLISH)
        val monthFormat = SimpleDateFormat("MMM", Locale.ENGLISH)
        val hoursMinutesFormat = SimpleDateFormat("HH:mm", Locale.ENGLISH)
        val gpx = GpxXmlParser().parse(recordingWrapper.mGpx.byteInputStream(Charsets.UTF_8))
        mViewHolder.mTextViewDay.text = dayOfMonthFormat.format(recordingWrapper.mDate)
        mViewHolder.mTextViewMonth.text = monthFormat.format(recordingWrapper.mDate)
        mViewHolder.mTextViewTitle.text = mContext.getString(R.string.recording_list_item_title)
            .format(gpx.getDistance() / 1000, formatDuration(gpx.getElapsed()))
        mViewHolder.mTextViewSubtitle.text =
            mContext.getString(R.string.recording_list_item_subtitle)
                .format(hoursMinutesFormat.format(recordingWrapper.mDate))
        if (recordingWrapper.mUploaded) {
            mViewHolder.mImageViewUploaded.setImageResource(R.drawable.ic_baseline_cloud_upload_24)
        } else {
            mViewHolder.mImageViewUploaded.setImageResource(R.drawable.ic_baseline_cloud_upload_24_faded)
        }
        return rowView!!
    }

}


class MainActivity : AppCompatActivity() {

    lateinit var mDatabase: TopopartnerDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i("Main Activity", "Entering .onCreate()")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mDatabase =
            Room.databaseBuilder(this, TopopartnerDatabase::class.java, "topopartner")
                .fallbackToDestructiveMigration()
                .build()
        findViewById<TextView>(R.id.text_view_version).text =
            getString(R.string.app_version, VERSION)
        findViewById<Button>(R.id.button_start_trace).setOnClickListener {
            Log.i("Main Activity", "Clicked on button_start_trace")
            val intent = Intent(this, TrackingActivity::class.java)
            startActivity(intent)
        }
        requestPermissions()
        createNotificationChannels()
        loadHistory()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.offline -> {
                startActivity(Intent(this, TileCacheActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadHistory() {

        LoadHistory(this, findViewById(R.id.recordings_list_view)).execute()

    }

    private fun hasPermissions(context: Context, vararg permissions: String): Boolean =
        permissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (!hasPermissions(this, *permissions)) {
            Log.i("Main Activity", "Some permissions were not granted. Requesting them.")
            ActivityCompat.requestPermissions(this, permissions, 1)
        }
    }

    private fun createNotificationChannels() {
        Log.i("Main Activity", "Creating notification channel id $NOTIFICATION_CHANNEL_ID")
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Tracking", importance)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

}
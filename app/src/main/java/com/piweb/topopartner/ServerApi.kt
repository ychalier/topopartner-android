package com.piweb.topopartner

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*


class RecordingPostRequest(
    recording: Recording,
    url: String,
    errorListener: Response.ErrorListener
) : Request<String>(Method.POST, url, errorListener) {

    private val mRecording = recording

    override fun getBodyContentType(): String {
        return "application/json"
    }

    override fun getBody(): ByteArray {
        val body = JSONObject()
        body.put("label", "Topopartner Record ${mRecording.uid}")
        body.put("comment", "")
        body.put("gpx", mRecording.gpx!!)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.FRENCH)
        body.put("date_visited", sdf.format(Date(mRecording.date!!)))
        Log.i("Server API", "Post recording body: $body")
        return body.toString().toByteArray()
    }

    override fun parseNetworkResponse(response: NetworkResponse?): Response<String> {
        Log.i("Server API", "Server responded to recording post with code ${response?.statusCode}")
        return Response.success(
            response?.data.toString(),
            HttpHeaderParser.parseCacheHeaders(response)
        )
    }

    override fun deliverResponse(response: String?) {

    }

}


class ServerApi constructor(mContext: Context) {

    private val mPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(
        mContext
    )
    private val mCache = DiskBasedCache(mContext.cacheDir, 1024 * 1024)
    private val mNetwork = BasicNetwork(HurlStack())
    private val mHost: String
    private val mApiKey: String
    private val mRequestQueue: RequestQueue

    init {
        mHost = mPreferences.getString("host", null) ?: "http://localhost"
        mApiKey = mPreferences.getString("api_key", null) ?: ""
        mRequestQueue = RequestQueue(mCache, mNetwork).apply { start() }
        Log.i("Server API", "Loaded preferences host='$mHost' and apiKey='$mApiKey'")
    }

    private fun sendRequest(request: Request<*>) {
        Log.i("Server API", "Sending request ${request.url}")
        mRequestQueue.add(request)
    }

    fun listItineraries(
        listener: Response.Listener<JSONObject>,
        errorListener: Response.ErrorListener
    ) {
        val url = "$mHost/api/itinerary/list?k=$mApiKey"
        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            listener,
            errorListener
        )
        sendRequest(jsonObjectRequest)
    }

    fun postRecording(recording: Recording, errorListener: Response.ErrorListener) {
        val url = "$mHost/api/recording/post?k=$mApiKey"
        val request = RecordingPostRequest(recording, url, errorListener)
        sendRequest(request)
    }

}

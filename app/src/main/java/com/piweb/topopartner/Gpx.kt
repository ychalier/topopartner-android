package com.piweb.topopartner

import android.util.Log
import android.util.Xml
import org.osmdroid.util.GeoPoint
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.lang.StringBuilder
import java.text.SimpleDateFormat
import java.util.*

private val ns: String? = null


fun getISO8601StringForDate(date: Date): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.FRANCE)
    dateFormat.timeZone = TimeZone.getTimeZone("UTC")
    return dateFormat.format(date)
}

fun getDateForISO8601String(string: String): Date? {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.FRANCE)
    dateFormat.timeZone = TimeZone.getTimeZone("UTC")
    return dateFormat.parse(string)
}


open class GpxTrkPt constructor(
    latitude: Double,
    longitude: Double,
    time: Date?,
    altitude: Double?
) {
    constructor(latitude: Double, longitude: Double) : this(latitude, longitude, null, null)

    open var mLatitude: Double = latitude
    open var mLongitude: Double = longitude
    open var mTime: Date? = time
    open var mAltitude: Double? = altitude

    fun toGeoPoint(): GeoPoint {
        return GeoPoint(mLatitude, mLongitude)
    }

    open fun toXml(): String {
        val inner = StringBuilder()
        if (mAltitude != null) {
            inner.append("<ele>$mAltitude</ele>")
        }
        if (mTime != null) {
            inner.append("<time>${getISO8601StringForDate(mTime!!)}</time>")
        }
        return "<trkpt lat=\"$mLatitude\" lon=\"$mLongitude\">$inner</trkpt>\n"
    }

}

class GpxWpt(latitude: Double, longitude: Double, time: Date?, altitude: Double?) :
    GpxTrkPt(latitude, longitude, time, altitude) {

    override fun toXml(): String {
        val inner = StringBuilder()
        if (mAltitude != null) {
            inner.append("<ele>$mAltitude</ele>")
        }
        if (mTime != null) {
            inner.append("<time>${getISO8601StringForDate(mTime!!)}</time>")
        }
        return "<wpt lat=\"$mLatitude\" lon=\"$mLongitude\">$inner</wpt>\n"
    }

}

class GpxTrkSeg {
    val mTrkPts = mutableListOf<GpxTrkPt>()

    fun toXml(): String {
        val output = StringBuilder()
        output.append("<trkseg>\n")
        mTrkPts.forEach {
            output.append(it.toXml())
        }
        output.append("</trkseg>\n")
        return output.toString()
    }

}

class GpxTrk {
    val mTrkSegs = mutableListOf<GpxTrkSeg>()

    fun toXml(): String {
        val output = StringBuilder()
        output.append("<trk>\n")
        mTrkSegs.forEach {
            output.append(it.toXml())
        }
        output.append("</trk>\n")
        return output.toString()
    }
}

class GpxMetadata {

    var mName: String? = null
    var mDesc: String? = null
    var mAuthor: String? = null
    var mTime: Date? = null

    fun copyFrom(other: GpxMetadata) {
        mName = other.mName
        mDesc = other.mDesc
        mAuthor = other.mAuthor
        mTime = other.mTime
    }

    fun toXml(): String {
        val builder = StringBuilder()
        builder.append("<metadata>")
        if (mName != null) {
            builder.append("<name>$mName</name>")
        }
        if (mDesc != null) {
            builder.append("<desc>$mDesc</desc>")
        }
        if (mAuthor != null) {
            builder.append("<author>$mAuthor</author>")
        }
        if (mTime != null) {
            builder.append("<time>${getISO8601StringForDate(mTime!!)}</time>")
        }
        builder.append("</metadata>")
        return builder.toString()
    }

}


class Gpx {
    val mTrks = mutableListOf<GpxTrk>()
    val mWpts = mutableListOf<GpxTrkPt>()
    val mMetadata = GpxMetadata()

    fun getGeoPoints(): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        mTrks.forEach { gpxTrk ->
            gpxTrk.mTrkSegs.forEach { gpxTrkSeg ->
                gpxTrkSeg.mTrkPts.forEach { gpxTrkPt ->
                    points.add(gpxTrkPt.toGeoPoint())
                }
            }
        }
        return points
    }

    fun getPoints(): List<GpxTrkPt> {
        val points = mutableListOf<GpxTrkPt>()
        mTrks.forEach { gpxTrk ->
            gpxTrk.mTrkSegs.forEach { gpxTrkSeg ->
                gpxTrkSeg.mTrkPts.forEach { gpxTrkPt ->
                    points.add(gpxTrkPt)
                }
            }
        }
        return points
    }

    fun getDistance(): Double {
        val points = getGeoPoints()
        var distance = 0.0
        points.forEachIndexed { index, point ->
            if (index > 0) {
                distance += point.distanceToAsDouble(points[index - 1])
            }
        }
        return distance
    }

    fun getElapsed(): Long {
        val points = getPoints()
        return if (points.size >= 2) {
            points.last().mTime!!.time - points.first().mTime!!.time
        } else {
            0
        }
    }

    fun toXml(): String {
        val output = StringBuilder()
        output.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\n")
        output.append("<gpx version=\"1.1\" creator=\"Topopartner\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://www.topografix.com/GPX/1/1\" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")
        output.append(mMetadata.toXml())
        mWpts.forEach {
            output.append(it.toXml())
        }
        mTrks.forEach {
            output.append(it.toXml())
        }
        output.append("</gpx>\n")
        return output.toString()
    }

}


class GpxXmlParser {

    fun parse(inputStream: InputStream): Gpx {
        inputStream.use { inputStream_ ->
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream_, null)
            parser.nextTag()
            return readGpx(parser)
        }
    }

    private fun readGpx(parser: XmlPullParser): Gpx {
        parser.require(XmlPullParser.START_TAG, ns, "gpx")
        val gpx = Gpx()
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            when (parser.name) {
                "trk" -> gpx.mTrks.add(readTrk(parser))
                "metadata" -> gpx.mMetadata.copyFrom(readMetadata(parser))
                else -> skip(parser)
            }
        }
        return gpx
    }

    private fun readMetadata(parser: XmlPullParser): GpxMetadata {
        parser.require(XmlPullParser.START_TAG, ns, "metadata")
        val gpxMetadata = GpxMetadata()
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            when (parser.name) {
                "name" -> gpxMetadata.mName = readTextTag(parser, "name")
                "author" -> gpxMetadata.mAuthor = readTextTag(parser, "author")
                "desc" -> gpxMetadata.mDesc = readTextTag(parser, "desc")
                "time" -> gpxMetadata.mTime = readDateTag(parser, "time")
                else -> skip(parser)
            }
        }
        return gpxMetadata
    }

    private fun readTrk(parser: XmlPullParser): GpxTrk {
        parser.require(XmlPullParser.START_TAG, ns, "trk")
        val gpxTrk = GpxTrk()
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            if (parser.name == "trkseg") {
                gpxTrk.mTrkSegs.add(readTrkSeg(parser))
            } else {
                skip(parser)
            }
        }
        return gpxTrk
    }

    private fun readTrkSeg(parser: XmlPullParser): GpxTrkSeg {
        parser.require(XmlPullParser.START_TAG, ns, "trkseg")
        val gpxTrkSeg = GpxTrkSeg()
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            if (parser.name == "trkpt") {
                gpxTrkSeg.mTrkPts.add(readTrkPt(parser))
            } else {
                skip(parser)
            }
        }
        return gpxTrkSeg
    }

    private fun readTrkPt(parser: XmlPullParser): GpxTrkPt {
        parser.require(XmlPullParser.START_TAG, ns, "trkpt")
        val gpxTrkPt = GpxTrkPt(
            parser.getAttributeValue(null, "lat").toDouble(),
            parser.getAttributeValue(null, "lon").toDouble()
        )
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            when (parser.name) {
                "ele" -> gpxTrkPt.mAltitude = readDoubleTag(parser, "ele")
                "time" -> gpxTrkPt.mTime = readDateTag(parser, "time")
                else -> skip(parser)
            }

        }
        return gpxTrkPt
    }

    private fun readDouble(parser: XmlPullParser): Double {
        val text = readText(parser)
        return text.toDouble()
    }

    private fun readTextTag(parser: XmlPullParser, tag: String): String {
        parser.require(XmlPullParser.START_TAG, ns, tag)
        val text = readText(parser)
        parser.require(XmlPullParser.END_TAG, ns, tag)
        return text
    }

    private fun readDoubleTag(parser: XmlPullParser, tag: String): Double {
        parser.require(XmlPullParser.START_TAG, ns, tag)
        val double = readDouble(parser)
        parser.require(XmlPullParser.END_TAG, ns, tag)
        return double
    }

    private fun readDateTag(parser: XmlPullParser, tag: String): Date {
        parser.require(XmlPullParser.START_TAG, ns, tag)
        val date = readDate(parser)
        parser.require(XmlPullParser.END_TAG, ns, tag)
        return date
    }

    private fun readDate(parser: XmlPullParser): Date {
        val text = readText(parser)
        return getDateForISO8601String(text)!!
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }

}
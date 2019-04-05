package il.org.hack.inspectorwatchout.map

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.location.LocationManager.GPS_PROVIDER
import android.content.Context.LOCATION_SERVICE
import android.content.res.Resources
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.RingtoneManager
import android.support.v4.content.ContextCompat.getSystemService
import android.util.Log
import android.webkit.WebView
import android.support.v4.app.NotificationCompat
import android.media.RingtoneManager.TYPE_NOTIFICATION
import android.media.RingtoneManager.getDefaultUri
import android.net.Uri
import android.os.*
import android.util.JsonReader
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.ProtocolException


class BackgroundService : Service() {

    private val binder = LocationServiceBinder()
    private val TAG = "BackgroundService"
    private var mLocationListener: LocationListener? = null
    private var mLocationManager: LocationManager? = null
    private var notificationManager: NotificationManager? = null
    private var notification: Notification? = null

    private val LOCATION_INTERVAL = 5L
    private val LOCATION_DISTANCE = 10F

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    private inner class LocationListener(provider: String) : android.location.LocationListener {
        private val TAG = "LocationListener"
        private var mLastLocation: Location? = null

        init {
            mLastLocation = Location(provider)
            Log.i(TAG, "LocationSet: $mLastLocation")
        }

        override fun onLocationChanged(location: Location) {
            mLastLocation = location
            (object: AsyncTask<Void, Void, Int>() {
                override fun doInBackground(vararg params: Void): Int {
                    try {
                        Log.i(TAG, "doInBackground $location")
                        if (inRange(location))
                            return 1
                        else
                            return 0
                    } catch (e:Throwable) {
                        e.printStackTrace()
                        return 2
                    }
                }

                override fun onPostExecute(res: Int) {
                    Log.i(TAG, "onPostExecute")
                    when(res) {
                        0 -> {
                            getNotification("protected", false)
                        }
                        1 -> {
                            getNotification("police ahead!", true, R.raw.siren)
                        }
                        2 -> {
                            getNotification("error!", false)
                        }
                    }
                }
            }).execute()
            Log.i(TAG, "LocationChanged: $location")
        }

        override fun onProviderDisabled(provider: String) {
            Log.e(TAG, "onProviderDisabled: $provider")
        }

        override fun onProviderEnabled(provider: String) {
            Log.e(TAG, "onProviderEnabled: $provider")
        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
            Log.e(TAG, "onStatusChanged: $status")
        }
    }


    fun getURL(address: String): String {
//        try {

            val url = URL(address)
            val conn = url.openConnection() as HttpURLConnection
            conn.setReadTimeout(10000)
            conn.setConnectTimeout(15000)
            conn.setRequestProperty("User-Agent", "Interestly")
            conn.setRequestMethod("GET")
            conn.setDoInput(true)
            conn.connect()
            val rd = BufferedReader(InputStreamReader(conn.getInputStream()))
            var resp = ""
            var line = rd.readLine()
            while (line != null) {
                resp += line
                line = rd.readLine()
            }
            rd.close()
            conn.disconnect()
            return resp
//        } catch (e: MalformedURLException) {
//            e.printStackTrace()
//        } catch (e: ProtocolException) {
//            e.printStackTrace()
//        } catch (e: IOException) {
//            e.printStackTrace()
//        }

        return ""
    }

    val dist = 0.0005
    val cacheTtl = 5 * 60 * 1000
    val inspectorTtl = 120 * 60
    var cacheTime = 0L
    var url = "https://hackathon-inspector.herokuapp.com/api/get_all"
    var inspectors: JSONArray? = null
    private fun inRange(location: Location): Boolean {
        val now = (System.currentTimeMillis() / 1000.0)
        if((inspectors == null) || (cacheTime + cacheTtl < now)) {
            val jsonStr = getURL(url)
            Log.i(TAG, jsonStr)
            inspectors = JSONArray(jsonStr)
        }
        for(i in 0..(inspectors!!.length() - 1)) {
            val inspector: JSONObject? = inspectors!!.get(i) as? JSONObject
            if((inspector?.getDouble("timestamp")!! > now - inspectorTtl) &&
                (Math.abs(inspector?.getDouble("lat") - location.latitude) < dist) &&
                (Math.abs(inspector?.getDouble("lon") - location.longitude) < dist)) {
                return true
            }
        }

        return false
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return Service.START_NOT_STICKY
    }

    override fun onCreate() {
        Log.i(TAG, "onCreate")
        startForeground(12345678, this.getNotification("Keep This On", false))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mLocationManager != null) {
            try {
                mLocationManager!!.removeUpdates(mLocationListener)
            } catch (ex: Exception) {
                Log.i(TAG, "fail to remove location listners, ignore", ex)
            }

        }
    }

    private fun initializeLocationManager() {
        if (mLocationManager == null) {
            mLocationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        }
    }

    fun startTracking() {
        initializeLocationManager()
        mLocationListener = LocationListener(LocationManager.GPS_PROVIDER)

        try {
            if(mLocationListener!= null) {
                getNotification("Locating You...", false)
                mLocationManager!!.requestLocationUpdates(
                    "fused",
                    LOCATION_INTERVAL,
                    LOCATION_DISTANCE,
                    mLocationListener
                )
            }

        } catch (ex: java.lang.SecurityException) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (ex: IllegalArgumentException) {
            Log.d(TAG, "gps provider does not exist " + ex.message);
        }

    }

    fun stopTracking() {
        this.onDestroy()
    }

    private fun getNotification(msg: String = "keep me running", sound: Boolean,
                                defaultSound: Int = 0): Notification? {

        Log.i(TAG, msg)
        val defaultSoundUri = if(defaultSound != 0) Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(resources.getResourcePackageName(defaultSound))
            .appendPath(resources.getResourceTypeName(defaultSound))
            .appendPath(resources.getResourceEntryName(defaultSound))
            .build() else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("channel_01", "My Channel", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager = getSystemService<NotificationManager>(NotificationManager::class.java!!)
            notificationManager?.createNotificationChannel(channel)
            notificationManager?.cancel(12345)

            val builder = Notification.Builder(applicationContext, "channel_01").setAutoCancel(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Inspector Watch is Running")
                .setContentText(msg)
                .setColor(resources.getColor(R.color.colorAccent))
            if(sound)
                builder.setSound(defaultSoundUri)
            notification = builder.build()
            notificationManager?.notify(12345, notification)
            return notification

        } else {
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if(notification != null) {
                notificationManager?.cancel(12345)
            }
            val notificationBuilder = NotificationCompat.Builder(this)
                // .setLargeIcon(image)/*Notification icon image*/
                //.setLargeIcon(BitmapFactory.decodeResource(this.resources, R.drawable.ic_launcher_icon))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Inspector Watch is Running")
                .setContentText(msg)
                .setColor(resources.getColor(R.color.colorAccent))
                .setAutoCancel(true)
                .setChannelId(applicationInfo.className)
//                .addAction(NotificationCompat.Action("stop", Intent("stop") {
//
//                }))
            if(sound)
                notificationBuilder.setSound(defaultSoundUri)

            notification = notificationBuilder.build()
            notificationManager?.notify(12345, notification)
            return notification
        }

    }


    inner class LocationServiceBinder : Binder() {
        val service: BackgroundService
            get() = this@BackgroundService
    }}

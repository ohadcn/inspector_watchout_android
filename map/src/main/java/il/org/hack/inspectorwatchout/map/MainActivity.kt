package il.org.hack.inspectorwatchout.map

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.IBinder
import android.webkit.WebView
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    private val PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 3

    private var gpsService: BackgroundService? = null
    //private var appUrl = "http://139.59.207.91/" //"https://hackathon-inspector.herokuapp.com/"
    private var appUrl = "https://hackathon-inspector.herokuapp.com/"

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val name = className.getClassName()
            if (name.endsWith("BackgroundService")) {
                gpsService = (service as BackgroundService.LocationServiceBinder).service
                if(!requestPermission()) {
                    gpsService?.startTracking()
                }
                // btnStartTracking.setEnabled(true)
                // txtStatus.setText("GPS Ready")
            }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            if (className.getClassName().equals("BackgroundService")) {
                gpsService = null
            }
        }
    }


    private fun startWeb() {
        myWebView.webViewClient = MyWebViewClient(this, myWebView, appUrl)
        myWebView.loadUrl(appUrl)
    }

    private fun startService() {
        val intent = Intent(this.application, BackgroundService::class.java)
        this.application.startService(intent)
//        this.getApplication().startForegroundService(intent);
        this.application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun requestPermission(): Boolean {
        if (0 != applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(true)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CHANGE_WIFI_STATE),
                PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION
            )
            return true
        }
        return false

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startWeb()
        startService()
    }

    override fun onBackPressed() {
        if (myWebView.canGoBack())
            myWebView.goBack()
        else {
            myWebView.loadUrl(appUrl)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    gpsService?.startTracking()
                } else {
                    Toast.makeText(this, "we won't follow you...", Toast.LENGTH_LONG).show()
                }
                return
            }
        }
    }

}

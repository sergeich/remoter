package util.remoter.remoterservice

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import util.remoter.service.ISampleService
import util.remoter.service.ISampleService_Stub

/**
 * Service that exposes impl for the remoter way
 */
class SampleService : Service() {
    private val serviceImpl: ISampleService = SampleServiceImpl()
    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            ISampleService_Stub.checkStubProxyMatch = intent.getBooleanExtra("enable", true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.v(TAG, "Service Create")

        //For testing with aidl clients, turn check off
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, IntentFilter("remoter.test.ProxyStubCheck"), RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, IntentFilter("remoter.test.ProxyStubCheck"))
        }
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return ISampleService_Stub(serviceImpl)
    }

    companion object {
        private val TAG: String = SampleService::class.java.getSimpleName()
    }
}

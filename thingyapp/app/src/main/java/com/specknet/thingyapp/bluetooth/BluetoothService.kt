package com.specknet.thingyapp.bluetooth

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.scan.ScanResult
import com.polidea.rxandroidble2.scan.ScanSettings
import com.specknet.thingyapp.utils.Constants
import com.specknet.thingyapp.utils.Utils.processThingyPacket
import io.reactivex.disposables.Disposable
import java.util.*

val CHARACTERISTIC_UUID = UUID(-0x1097fbf964cab6cdL, -0x64efad00568bffbeL)

class BluetoothService : Service() {

    lateinit var rxBleClient: RxBleClient
    lateinit var respeckUUID: String

    var thingyDevice: RxBleDevice? = null
    var thingyFound = false
    lateinit var scanDisposable: Disposable
    var respeckLiveSubscription: Disposable? = null

    override fun onCreate() {
        super.onCreate()
        Log.i("service", "BLT Service created")

        // this will always exist because the service is going to be started only after the respeck has been scanned
        val sharedPreferences = getSharedPreferences(Constants.PREFERENCES_FILE, Context.MODE_PRIVATE)
        respeckUUID = sharedPreferences.getString(Constants.RESPECK_MAC_ADDRESS_PREF, "").toString()
        Log.d("CHECKID", respeckUUID)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("service", "BLT Service started")

        // this is to run the bluetooth service on another thread than the main one.
        Thread {
            rxBleClient = RxBleClient.create(this)

            // scan for devices
            var scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build()
            scanDisposable = rxBleClient.scanBleDevices(scanSettings)
                .doFinally{ Log.i("ble", "Connection terminated") }
                .subscribe({
                    if(thingyFound) {
                        scanDisposable.dispose()
                    }
                    onScanSuccess(it, respeckUUID)
                }, {onScanFailure(it)})
        }.start()

        return START_STICKY
    }

    private fun onScanSuccess(scanResult: ScanResult, respeckUUID: String) {
        Log.i("ble", "Scan successful")
        Log.i("ble", "Found " + scanResult.bleDevice.name + ", " + scanResult.bleDevice.macAddress)

        if(scanResult.bleDevice.macAddress == respeckUUID) {
            Log.i("ble", "Found Thingy!")
            thingyFound = true
            thingyDevice = scanResult.bleDevice
            connectToThingy()
        }
    }

    private fun connectToThingy() {
        // first we want to observe the connection state changes
        val connectionStateChangesObservable = thingyDevice?.observeConnectionStateChanges()
            ?.subscribe({
                when (it) {
                    RxBleConnection.RxBleConnectionState.CONNECTED -> Log.i("ble", "Connection state = connected")
                    RxBleConnection.RxBleConnectionState.CONNECTING -> Log.i("ble", "Connection state = connecting")
                    RxBleConnection.RxBleConnectionState.DISCONNECTED -> Log.i("ble", "Connection state = disconnected")
                    RxBleConnection.RxBleConnectionState.DISCONNECTING -> Log.i("ble", "Connection state = disconnecting")
                }
            },
                { Log.i("ble", "Connection state error = " + it.stackTrace)})


        val connectionObservable = thingyDevice?.establishConnection(false)
        var interval = 0
        respeckLiveSubscription = connectionObservable?.flatMap { it.setupNotification(
            CHARACTERISTIC_UUID) }
            ?.doOnNext{
                Log.i("ble", "Subscribed to Thingy")
                val respeckFoundIntent = Intent(Constants.ACTION_RESPECK_CONNECTED)
                sendBroadcast(respeckFoundIntent)
            }

            ?.flatMap { it }
            ?.subscribe({
                processThingyPacket(it, this)
                val respeckFoundIntent = Intent(Constants.ACTION_RESPECK_CONNECTED)
                sendBroadcast(respeckFoundIntent)
                Log.i("brd", "sent the connected broadcast")
                interval++
            },
                {
                    Log.i("error", "Error when connecting = " + it.message)
                    val respeckDisconnectedIntent = Intent(Constants.ACTION_RESPECK_DISCONNECTED)
                    sendBroadcast(respeckDisconnectedIntent)
                })
    }

    private fun onScanFailure(throwable: Throwable) {
        Log.i("ble", "Scan failure: " + throwable.stackTrace)
    }

    override fun onBind(intent: Intent): IBinder {
        Log.i("service", "BLT Service bound")
        TODO("Return the communication channel to the service.")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("service", "BLT Service destroyed")
        val respeckDisconnectedIntent = Intent(Constants.ACTION_RESPECK_DISCONNECTED)
        sendBroadcast(respeckDisconnectedIntent)
        respeckLiveSubscription?.dispose()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i("service", "BLT Service unbound")
        return super.onUnbind(intent)
    }
}

package net.jobevers.bletest

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.bluetooth.*
import android.content.*
import android.util.Log
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.widget.Toast
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest




class MainActivity : AppCompatActivity() {

    private val REQUEST_ENABLE_BT = 1
    private val MODULE_NAME = "MLT-BT05"

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i("my-tag", "I have been created")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // https://github.com/googlesamples/android-BluetoothLeGatt/issues/21#issuecomment-255369887
        val permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(this, "The permission to get BLE location data is required", Toast.LENGTH_SHORT).show()
            } else {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ), 1
                )
            }
        } else {
            Toast.makeText(this, "Location permissions already granted", Toast.LENGTH_SHORT).show()
        }

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        bluetoothAdapter?.takeIf { it.isDisabled }?.apply {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        pairedDevices?.forEach { device ->
            val deviceName = device.name
            val deviceHardwareAddress = device.address // MAC address
            Log.i("my-tag", "$deviceName : $deviceHardwareAddress")
        }
        // This should be replaced by BluetoothLeScanner
        // https://developer.android.com/reference/android/bluetooth/le/BluetoothLeScanner.html
        // https://stackoverflow.com/a/30223209/2752242
        Log.i("my-tag", "Starting scan")

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        val leScanCallback =  object: ScanCallback() {
             override fun onScanResult(callbackType: Int, result: ScanResult?) {
                 super.onScanResult(callbackType, result)
                 var name = result?.device?.name
                 Log.i("my-tag", "Found $name")
                 Log.i("my-tag", result.toString())
             }
        }
        scanner?.startScan(leScanCallback)
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val BluetoothAdapter.isDisabled: Boolean
        get() = !isEnabled

}


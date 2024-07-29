import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MultiFitnessPeripheral(private val context: Context) {

    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter
    private val bluetoothLeAdvertiser: BluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser

    // Service UUIDs
    private val FTMS_SERVICE_UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    private val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
    private val CYCLING_POWER_SERVICE_UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")

    // Characteristic UUIDs
    private val INDOOR_BIKE_DATA_UUID = UUID.fromString("00002AD2-0000-1000-8000-00805f9b34fb")
    private val HEART_RATE_MEASUREMENT_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
    private val CYCLING_POWER_MEASUREMENT_UUID = UUID.fromString("00002A63-0000-1000-8000-00805f9b34fb")
    private val CYCLING_POWER_FEATURE_UUID = UUID.fromString("00002A65-0000-1000-8000-00805f9b34fb")

    private var gattServer: BluetoothGattServer? = null
    private var serviceAddedDeferred: CompletableDeferred<Boolean>? = null

    suspend fun setupGattServer(): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
                if (gattServer == null) {
                    println("Failed to open GATT server")
                    return@withContext false
                }

                // Heart Rate Service
                val currentMilliseconds = System.currentTimeMillis()
                println("Millisecondi correnti inizio: $currentMilliseconds")
                if (!addServiceAndWait(createHeartRateService())) {
                    println("Failed to add Heart Rate service")
                    return@withContext false
                }

                // Cycling Power Service
                if (!addServiceAndWait(createCyclingPowerService())) {
                    println("Failed to add Cycling Power service")
                    return@withContext false
                }

                // FTMS Service
                if (!addServiceAndWait(createFTMSService())) {
                    println("Failed to add FTMS service")
                    return@withContext false
                }

                // Log all services and characteristics
                println("Logging all services and characteristics:")
                gattServer!!.services.forEach { service ->
                    println("Service: ${service.uuid}")
                    service.characteristics.forEach { characteristic ->
                        println("  Characteristic: ${characteristic.uuid}")
                    }
                }

                true
            } catch (e: Exception) {
                println("Failed to setup GATT server: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }

    private suspend fun addServiceAndWait(service: BluetoothGattService): Boolean {
        return suspendCoroutine { continuation ->
            serviceAddedDeferred = CompletableDeferred()
            if (!gattServer!!.addService(service)) {
                continuation.resume(false)
            } else {
                serviceAddedDeferred?.invokeOnCompletion { throwable ->
                    if (throwable == null) {
                        continuation.resume(true)
                    } else {
                        continuation.resume(false)
                    }
                }
            }
        }
    }

    private fun createHeartRateService(): BluetoothGattService {
        val service = BluetoothGattService(HEART_RATE_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            HEART_RATE_MEASUREMENT_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0
        )
        service.addCharacteristic(characteristic)
        return service
    }

    private fun createCyclingPowerService(): BluetoothGattService {
        val service = BluetoothGattService(CYCLING_POWER_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val measurementCharacteristic = BluetoothGattCharacteristic(
            CYCLING_POWER_MEASUREMENT_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0
        )
        val featureCharacteristic = BluetoothGattCharacteristic(
            CYCLING_POWER_FEATURE_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(measurementCharacteristic)
        service.addCharacteristic(featureCharacteristic)
        return service
    }

    private fun createFTMSService(): BluetoothGattService {
        val service = BluetoothGattService(FTMS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            INDOOR_BIKE_DATA_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0
        )
        service.addCharacteristic(characteristic)
        return service
    }

    fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(FTMS_SERVICE_UUID))
            .addServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
            .addServiceUuid(ParcelUuid(CYCLING_POWER_SERVICE_UUID))
            .build()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        bluetoothLeAdvertiser.startAdvertising(settings, data, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            println("BLE Advertising started.")
        }

        override fun onStartFailure(errorCode: Int) {
            println("BLE Advertising failed: $errorCode")
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                println("Device connected: ${device.address}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                println("Device disconnected: ${device.address}")
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            val currentMilliseconds = System.currentTimeMillis()
            println("Millisecondi correnti: $currentMilliseconds")
            println("Service added: ${service.uuid}, status: $status")
            serviceAddedDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            when (characteristic.uuid) {
                CYCLING_POWER_FEATURE_UUID -> {
                    val featureValue = ByteArray(4) // Example: Basic power measurement
                    featureValue[0] = 0x01
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, featureValue)
                }
            }
        }
    }

    fun updateIndoorBikeData(speed: Float, cadence: Int, power: Int) {
        val characteristic = gattServer?.getService(FTMS_SERVICE_UUID)?.getCharacteristic(INDOOR_BIKE_DATA_UUID)
        characteristic?.let {
            val value = ByteArray(8)
            // Flags (speed present, cadence present, instantaneous power present)
            value[0] = 0x44.toByte()
            value[1] = 0x00
            // Speed (km/h, resolution of 0.01)
            val speedInt = (speed * 100).toInt()
            value[2] = (speedInt and 0xFF).toByte()
            value[3] = ((speedInt shr 8) and 0xFF).toByte()
            // Cadence (rpm)
            value[4] = cadence.toByte()
            // Instantaneous Power (watts)
            value[5] = (power and 0xFF).toByte()
            value[6] = ((power shr 8) and 0xFF).toByte()
            it.value = value
            // Notify connected devices
            notifyCharacteristicChanged(it)
        }
    }

    fun updateHeartRate(heartRate: Int) {
        val characteristic = gattServer?.getService(HEART_RATE_SERVICE_UUID)?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
        characteristic?.let {
            val value = ByteArray(2)
            value[0] = 0 // Flags (8-bit heart rate value)
            value[1] = heartRate.toByte()
            it.value = value
            // Notify connected devices
            notifyCharacteristicChanged(it)
        }
    }

    fun updateCyclingPower(power: Int) {
        val characteristic = gattServer?.getService(CYCLING_POWER_SERVICE_UUID)?.getCharacteristic(CYCLING_POWER_MEASUREMENT_UUID)
        characteristic?.let {
            val value = ByteArray(4)
            value[0] = 0x20 // Flags (only instantaneous power present)
            value[1] = 0x00
            value[2] = (power and 0xFF).toByte()
            value[3] = ((power shr 8) and 0xFF).toByte()
            it.value = value
            // Notify connected devices
            notifyCharacteristicChanged(it)
        }
    }

    private fun notifyCharacteristicChanged(characteristic: BluetoothGattCharacteristic) {
        try {
            val connectedDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
            connectedDevices.forEach { device ->
                gattServer?.notifyCharacteristicChanged(device, characteristic, false)
            }
        } catch (e: Exception) {
            println("Failed to notify characteristic changed: ${e.message}")
        }
    }
}
package prof.ronny.bluetooth_serial_android

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.IOException
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.content.BroadcastReceiver
import android.content.Intent
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.util.*
import kotlin.concurrent.thread

/** Plugin principal Bluetooth Serial Android */
class BluetoothSerialAndroidPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, ActivityAware {

    private lateinit var channel: MethodChannel
    private var context: Context? = null
    private var activity: Activity? = null
    private var adapter: BluetoothAdapter? = null
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var readBuffer = StringBuilder()
    private var timeoutMs: Int = 200
    private val mainHandler = Handler(Looper.getMainLooper())
    private var discoveryReceiver: BroadcastReceiver? = null
    private var discoveryResult: MethodChannel.Result? = null
    private var discoveredDevices = mutableListOf<Map<String, String>>()
    private var connectionReceiver: BroadcastReceiver? = null
    @Volatile private var connecting = false

    private fun finishDiscovery() {
        val receiver = discoveryReceiver
        discoveryReceiver = null
        if (receiver != null) context?.unregisterReceiver(receiver)
        val result = discoveryResult
        discoveryResult = null
        result?.success(discoveredDevices.toList())
    }

    private fun registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            ctx.registerReceiver(receiver, filter)
        }
    }

    private fun watchConnection() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                @Suppress("DEPRECATION")
                val device = intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val current = socket ?: return
                if (intent?.action == BluetoothDevice.ACTION_ACL_DISCONNECTED &&
                    device?.address == current.remoteDevice.address) {
                    val address = device.address
                    disconnect()
                    channel.invokeMethod("onDisconnected", mapOf("address" to address))
                }
            }
        }
        connectionReceiver = receiver
        registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED))
    }

    override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "bluetooth_serial_android")
        channel.setMethodCallHandler(this)
        context = binding.applicationContext
        adapter = (context?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        watchConnection()
    }

    // ========================================================
    //                  MÉTODOS FLUTTER
    // ========================================================
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "ensurePermissions" -> ensurePermissions(result)
            "getPairedDevices" -> getPairedDevices(result)
            "scanDevices" -> scanDevices(result)
            "cancelDiscovery" -> {
                try {
                    finishDiscovery()
                    val ctx = context
                    val canScan = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        (ctx != null && ContextCompat.checkSelfPermission(
                            ctx, Manifest.permission.BLUETOOTH_SCAN
                        ) == PackageManager.PERMISSION_GRANTED)
                    if (canScan && adapter?.isDiscovering == true) adapter?.cancelDiscovery()
                    result.success(null)
                } catch (e: Exception) {
                    result.error("DISCOVERY_CANCEL_FAILED", e.message, null)
                }
            }
            "connect" -> {
                val address = call.argument<String>("address")
                val uuidStr = call.argument<String>("uuid") ?: "00001101-0000-1000-8000-00805F9B34FB"
                val timeout = call.argument<Int>("timeoutMs") ?: 200
                connect(address, uuidStr, timeout, result)
            }
            "disconnect" -> {
                disconnect()
                result.success(true)
            }
            "write" -> {
                val message = call.argument<String>("message") ?: ""
                write(message, result)
            }
            "read" -> read(result)
            "readLine" -> {
                val delimiter = call.argument<String>("delimiter") ?: "\n"
                readLine(delimiter, result)
            }
            else -> result.notImplemented()
        }
    }

    // ========================================================
    //                  PERMISSÕES
    // ========================================================
    private fun requiredPermissionsForScan(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun missingPermissions(ctx: Context, permissions: List<String>): List<String> {
        return permissions.filter {
            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun ensurePermissions(result: MethodChannel.Result) {
        val act = activity
        val ctx = context
        if (act == null || ctx == null) {
            result.error("NO_ACTIVITY", "Activity não disponível", null)
            return
        }

        val missingPermissions = missingPermissions(ctx, requiredPermissionsForScan())
        if (missingPermissions.isEmpty()) {
            result.success(true)
            return
        }

        ActivityCompat.requestPermissions(act, missingPermissions.toTypedArray(), 1001)
        result.success(false)
    }

    // ========================================================
    //                  LISTAR DISPOSITIVOS PAREADOS
    // ========================================================
    private fun getPairedDevices(result: MethodChannel.Result) {
        if (adapter == null) {
            result.error("NO_ADAPTER", "Bluetooth não suportado", null)
            return
        }

        val devices = adapter!!.bondedDevices.map {
            mapOf("name" to (it.name ?: "Desconhecido"), "address" to it.address)
        }
        result.success(devices)
    }

    // ========================================================
    //                  BUSCAR DISPOSITIVOS (SCAN)
    // ========================================================
    private fun scanDevices(result: MethodChannel.Result) {
        val ctx = context ?: return result.error("NO_CONTEXT", "Contexto ausente", null)
        val act = activity ?: return result.error("NO_ACTIVITY", "Activity ausente", null)
        val adapter = adapter ?: return result.error("NO_ADAPTER", "Bluetooth não suportado", null)

        val missingPermissions = missingPermissions(ctx, requiredPermissionsForScan())
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(act, missingPermissions.toTypedArray(), 1001)
            result.error("NO_PERMISSION", "Permissões não concedidas", null)
            return
        }

        if (discoveryResult != null) {
            result.error("DISCOVERY_IN_PROGRESS", "A descoberta já está em andamento", null)
            return
        }
        val devicesFound = mutableListOf<Map<String, String>>()

        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: android.content.Intent?) {
                val action = intent?.action
                if (BluetoothDevice.ACTION_FOUND == action) {
                    @Suppress("DEPRECATION")
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        val map = mapOf(
                            "name" to (device.name ?: "Desconhecido"),
                            "address" to device.address
                        )
                        if (!devicesFound.any { it["address"] == device.address }) {
                            devicesFound.add(map)
                            channel.invokeMethod("onDeviceFound", map)
                        }
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                    finishDiscovery()
                }
            }
        }

        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        // Bluetooth events can come from a privileged system
        // process, so Android 13+ requires RECEIVER_EXPORTED.
        if (adapter.isDiscovering) adapter.cancelDiscovery()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            ctx.registerReceiver(receiver, filter)
        }

        discoveryReceiver = receiver
        discoveryResult = result
        discoveredDevices = devicesFound
        try {
            if (!adapter.startDiscovery()) {
                discoveryResult = null
                finishDiscovery()
                result.error("DISCOVERY_FAILED", "Não foi possível iniciar o scan", null)
            }
        } catch (e: Exception) {
            discoveryResult = null
            finishDiscovery()
            result.error("DISCOVERY_FAILED", e.message, null)
        }
    }

    // ========================================================
    //                  CONECTAR A UM DISPOSITIVO
    // ========================================================
    private fun connect(address: String?, uuidStr: String, timeoutMs: Int, result: MethodChannel.Result) {
        if (address == null || !BluetoothAdapter.checkBluetoothAddress(address)) {
            result.error("INVALID_ADDRESS", "Endereço Bluetooth inválido", null)
            return
        }
        if (connecting || socket != null) {
            result.error("CONNECTION_IN_PROGRESS", "Desconecte a sessão atual primeiro", null)
            return
        }
        val currentAdapter = adapter ?: return result.error("NO_ADAPTER", "Bluetooth indisponível", null)
        val pending: BluetoothSocket
        try {
            pending = currentAdapter.getRemoteDevice(address)
                .createRfcommSocketToServiceRecord(UUID.fromString(uuidStr))
        } catch (e: Exception) {
            result.error("CONNECTION_FAILED", e.message, null)
            return
        }
        connecting = true
        socket = pending
        val timeout = Runnable {
            try { pending.close() } catch (_: IOException) { }
        }
        mainHandler.postDelayed(timeout, timeoutMs.coerceAtLeast(1000).toLong())
        thread {
            try {
                pending.connect()
                val connectedInput = pending.inputStream
                val connectedOutput = pending.outputStream
                mainHandler.post {
                    mainHandler.removeCallbacks(timeout)
                    connecting = false
                    if (socket === pending && pending.isConnected) {
                        input = connectedInput
                        output = connectedOutput
                        result.success(true)
                    } else {
                        try { pending.close() } catch (_: IOException) { }
                        result.error("CONNECTION_FAILED", "Conexão cancelada", null)
                    }
                }
            } catch (e: Exception) {
                try { pending.close() } catch (_: IOException) { }
                mainHandler.post {
                    mainHandler.removeCallbacks(timeout)
                    connecting = false
                    if (socket === pending) disconnect()
                    result.error("CONNECTION_FAILED", e.message, null)
                }
            }
        }
    }

    // ========================================================
    //                  DESCONECTAR
    // ========================================================
    private fun disconnect() {
        val current = socket
        socket = null
        input = null
        output = null
        readBuffer.clear()
        try { current?.close() } catch (_: IOException) { }
    }

    // ========================================================
    //                  ESCRITA (ASSÍNCRONA)
    // ========================================================
    private fun write(message: String, result: MethodChannel.Result) {
        val current = socket
        val stream = output
        if (current == null || stream == null) {
            result.error("NOT_CONNECTED", "Nenhuma conexão ativa", null)
            return
        }
        thread {
            try {
                synchronized(stream) {
                    stream.write(message.toByteArray(Charsets.UTF_8))
                    stream.flush()
                }
                mainHandler.post { result.success(true) }
            } catch (e: IOException) {
                mainHandler.post {
                    if (socket === current) {
                        val address = current.remoteDevice.address
                        disconnect()
                        channel.invokeMethod("onDisconnected", mapOf("address" to address))
                    }
                    result.error("WRITE_ERROR", e.message, null)
                }
            }
        }
    }

    // ========================================================
    //                  LEITURA (ASSÍNCRONA)
    // ========================================================
    private fun read(result: MethodChannel.Result) {
        thread {
            try {
                val buffer = ByteArray(1024)
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < timeoutMs) {
                    val available = input?.available() ?: 0
                    if (available > 0) {
                        val bytesRead = input!!.read(buffer, 0, available)
                        val data = String(buffer, 0, bytesRead)
                        activity?.runOnUiThread { result.success(data) }
                        return@thread
                    }
                    Thread.sleep(10)
                }
                activity?.runOnUiThread { result.success(null) } // timeout sem dados
            } catch (e: IOException) {
                activity?.runOnUiThread { result.error("READ_ERROR", e.message, null) }
            }
        }
    }

    // ========================================================
    //                  LEITURA POR LINHA (ASSÍNCRONA)
    // ========================================================
    private fun readLine(delimiter: String, result: MethodChannel.Result) {
        thread {
            try {
                val buffer = ByteArray(256)
                val start = System.currentTimeMillis()

                while (System.currentTimeMillis() - start < timeoutMs) {
                    val available = input?.available() ?: 0
                    if (available > 0) {
                        val bytesRead = input!!.read(buffer, 0, available)
                        val chunk = String(buffer, 0, bytesRead)
                        readBuffer.append(chunk)

                        val index = readBuffer.indexOf(delimiter)
                        if (index != -1) {
                            val line = readBuffer.substring(0, index)
                            readBuffer.delete(0, index + delimiter.length)
                            activity?.runOnUiThread { result.success(line) }
                            return@thread
                        }
                    }
                    Thread.sleep(10)
                }

                activity?.runOnUiThread { result.success(null) } // timeout sem linha completa

            } catch (e: SocketTimeoutException) {
                activity?.runOnUiThread { result.success(null) }
            } catch (e: IOException) {
                activity?.runOnUiThread { result.error("READLINE_ERROR", e.message, null) }
            }
        }
    }

    // ========================================================
    //                  GERENCIAMENTO DE ACTIVITY
    // ========================================================
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        finishDiscovery()
        connectionReceiver?.let { context?.unregisterReceiver(it) }
        connectionReceiver = null
        disconnect()
        channel.setMethodCallHandler(null)
    }
}

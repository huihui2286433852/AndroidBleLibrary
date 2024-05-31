package com.scin.blelibrary.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.scin.blelibrary.ext.appLogD
import com.scin.blelibrary.ext.toHexString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nordicsemi.android.common.core.DataByteArray
import no.nordicsemi.android.kotlin.ble.client.main.callback.ClientBleGatt
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattCharacteristic
import no.nordicsemi.android.kotlin.ble.core.ServerDevice
import no.nordicsemi.android.kotlin.ble.scanner.BleScanner
import no.nordicsemi.android.kotlin.ble.scanner.aggregator.BleScanResultAggregator
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue

class BleUtils {
    private val TAG = "BleUtils"
    //自定义UUID
    private val UUID_SERVICE: UUID = UUID.fromString("00001002-0000-1000-8000-00805F9B34FB")
    private val UUID_CHAR_READ = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")
    private val UUID_CHAR_WRITE = UUID.fromString("00001236-0000-1000-8000-00805f9b34fb")
    private lateinit var context: Context
    private lateinit var bleScanner: BleScanner
    private val aggregator = BleScanResultAggregator()//创建聚合器，将扫描记录与设备连接起来
    private var scanBleJob: Job? = null//扫描任务
    private var writeBleIo: ClientBleGattCharacteristic? = null
    private lateinit var scope: CoroutineScope
    private var client: ClientBleGatt? = null
    private var sendDataList = LinkedBlockingQueue<SendDataBean>()//发送数据队列，线程安全的队列
    private var receiveDataList = LinkedBlockingQueue<ByteArray>()//接收数据队列，线程安全的队列
    private var connectBleJob: Job? = null
    private var readBleDataJob: Job? = null
    private var responseJob: Job? = null
    private var sendJob: Job? = null

    fun initBle(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        if (bleScanner != null) return
        this.context = context
        this.bleScanner = BleScanner(context)
        this.scope = scope
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    fun scanBle(scanResult: (List<ServerDevice>) -> Unit) {
        if (this.context == null || this.bleScanner == null || this.scope == null) return
        if (scanBleJob != null) {
            scanBleJob?.cancel()
        }
        scanBleJob = bleScanner.scan()
            .map { aggregator.aggregateDevices(it) } //添加新设备并返回聚合列表
            .onEach { bleList ->
                val list = bleList.filter { !it.name.isNullOrBlank() }
                withContext(Dispatchers.Main) {
                    scanResult.invoke(list)
                }
            } //将状态传播到ui
            .launchIn(scope) //离开屏幕后扫描将停止
    }
    /**
     * 连接蓝牙设备
     * @param device 蓝牙设备
     * @param connectResult 连接结果回调
     * @param responseCallback 接收数据回调
     */
    fun connectBle(
        device: ServerDevice,
        connectResult: (Boolean) -> Unit = {},
        responseCallback: (ByteArray) -> Unit = {}
    ) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            connectResult.invoke(false)
            return
        }
        connectBleJob = scope.launch(Dispatchers.IO) {
            try {
                ClientBleGatt.connect(context, device.address, scope).also {
                    this@BleUtils.client = it
                }
                client?.let {
                    connectResult.invoke(it.isConnected)
                    if (!it.isConnected) {//连接失败
                        return@launch
                    }
                    //发现蓝牙le设备上的服务。这是一个挂起功能，等待设备发现完成。
                    val services = it.discoverServices()
                    //记住与dk通信所需的服务和特性。
                    val service = services.findService(UUID_SERVICE)!!
                    writeBleIo = service.findCharacteristic(UUID_CHAR_WRITE)
                    val readBleIo = service.findCharacteristic(UUID_CHAR_READ)
                    //getNotifications()是一个挂起函数，它会等待直到启用通知。
                    readBleDataJob = readBleIo?.getNotifications()?.onEach { data ->
                        appLogD(TAG, "接收消息: ${data.value.toHexString()}")
                        //接收到数据
                        if (data.value.isNotEmpty()) {
                            receiveDataList.offer(data.value)
                            response(responseCallback)
                        }
                    }?.launchIn(scope)
                } ?: run {
                    connectResult.invoke(false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                connectResult.invoke(false)
            }
        }
    }

    //响应数据(将接收数据队列数据，发出去)
    private fun response(callback: (ByteArray) -> Unit) {
        if (responseJob == null || responseJob?.isActive == false) return
        responseJob = scope.launch(Dispatchers.IO) {
            receiveDataList.forEach {
                withContext(Dispatchers.Main) {
                    callback.invoke(it)
                }
            }
            responseJob = null
        }
    }

    /**
     * 发送数据
     */
    fun sendData(data: ByteArray, sendResult: ((Boolean) -> Unit)? = {}) {
        if (client == null || !client!!.isConnected || writeBleIo == null) {
            sendResult?.invoke(false)
            return
        }
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            sendResult?.invoke(false)
            return
        }
        Log.d(TAG, "发送消息消息: ${data.toHexString()}")
        sendDataList.add(SendDataBean(data, sendResult))
        send()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun send() {
        if (sendJob == null || sendJob?.isActive == false) return
        sendJob = scope.launch(Dispatchers.IO) {
            sendDataList.forEach {
                try {
                    writeBleIo?.write(DataByteArray(it.data))
                    it.sendResult?.invoke(true)
                } catch (e: Exception) {
                    it.sendResult?.invoke(false)
                    e.printStackTrace()
                }
            }
            sendJob = null
        }
    }
    /**
     * 断开连接
     */
    fun disconnect() {
        scanBleJob?.cancel()
        scanBleJob = null
        connectBleJob?.cancel()
        connectBleJob = null
        readBleDataJob?.cancel()
        readBleDataJob = null
        sendJob?.cancel()
        sendJob = null
        responseJob?.cancel()
        responseJob = null
        client?.disconnect()
        client = null
        writeBleIo = null
    }
}
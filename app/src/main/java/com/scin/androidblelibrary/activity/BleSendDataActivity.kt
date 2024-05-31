package com.scin.androidblelibrary.activity

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.scin.androidblelibrary.adapter.MsgAdapter
import com.scin.androidblelibrary.bean.MsgDataBean
import com.scin.androidblelibrary.bean.BleDevicesBean
import com.scin.androidblelibrary.databinding.ActivityBleSendDataBinding
import com.scin.blelibrary.ext.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import no.nordicsemi.android.common.core.DataByteArray
import no.nordicsemi.android.kotlin.ble.client.main.callback.ClientBleGatt
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattCharacteristic
import java.util.UUID

class BleSendDataActivity : AppCompatActivity() {
    private val TAG = "BleSendDataActivity"
    private val bind by lazy { ActivityBleSendDataBinding.inflate(layoutInflater) }
    private val devicesBean by lazy { intent.getParcelableExtra<BleDevicesBean>("data") }
    private val adapter = MsgAdapter()
    private val bleDataList = mutableListOf<MsgDataBean>()
    private var client: ClientBleGatt? = null

    //自定义UUID
    private val UUID_SERVICE: UUID = UUID.fromString("00001002-0000-1000-8000-00805F9B34FB")
    private val UUID_CHAR_READ = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")
    private val UUID_CHAR_WRITE = UUID.fromString("00001236-0000-1000-8000-00805f9b34fb")

    private var writeCharacteristic: ClientBleGattCharacteristic? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(bind.root)
        connectBle()
        initRV()
        initClick()
    }

    @SuppressLint("MissingPermission")
    private fun connectBle() {
        lifecycleScope.launch(Dispatchers.IO) {
            //Connect a Bluetooth LE device.
            val client = ClientBleGatt.connect(this@BleSendDataActivity, devicesBean!!.address, lifecycleScope).also {
                this@BleSendDataActivity.client = it
            }

            if (!client.isConnected) {
                Toast.makeText(this@BleSendDataActivity,"蓝牙连接失败",Toast.LENGTH_SHORT).show()
                return@launch
            }


            //发现蓝牙le设备上的服务。这是一个挂起功能，等待设备发现完成。
            val services = client.discoverServices()
            //记住与dk通信所需的服务和特性。
            val service = services.findService(UUID_SERVICE)!!
            writeCharacteristic = service.findCharacteristic(UUID_CHAR_WRITE)
            val readCharacteristic = service.findCharacteristic(UUID_CHAR_READ)
            //getNotifications()是一个挂起函数，它会等待直到启用通知。
            readCharacteristic?.getNotifications()?.onEach {
                Log.d(TAG, "接收消息: ${it.value.toHexString()}")
                //接收到数据
                bleDataList.add(0, MsgDataBean(false,it.value.toHexString()))
                adapter.setList(bleDataList)
            }?.launchIn(lifecycleScope)
        }
    }

    private fun initRV() {
        bind.rvRecyclerView.layoutManager = LinearLayoutManager(this)
        bind.rvRecyclerView.adapter = adapter
        adapter.setList(bleDataList)
    }
    @SuppressLint("MissingPermission")
    private fun initClick() {
        bind.btnSend.setOnClickListener {
            if (writeCharacteristic == null){
                Toast.makeText(this,"蓝牙连接失败",Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
//            val data = bind.etEditText.text.toString()
//            if (data.isBlank()) return@setOnClickListener
            bleDataList.add(0, MsgDataBean(true,a1.toHexString()))
            bleDataList.add(0, MsgDataBean(true,a2.toHexString()))
            adapter.setList(bleDataList)
            lifecycleScope.launch(Dispatchers.IO) {
                Log.d(TAG, "发送消息消息: ${a1.toHexString()}")
                Log.d(TAG, "发送消息消息: ${a2.toHexString()}")
                writeCharacteristic?.write(DataByteArray(a1))
                writeCharacteristic?.write(DataByteArray(a2))
            }
        }
    }
    var a1 = byteArrayOf(1, 1, 1, 14, 2, 0, 1, 0, 17, 67, 97, 90, 72, 50, 48, 50, 51, 48, 248.toByte(), 4)
    var a2 = byteArrayOf(1, 1, 2, 8, 53, 49, 49, 48, 48, 48, 49, 52, 179.toByte(), 7)
}
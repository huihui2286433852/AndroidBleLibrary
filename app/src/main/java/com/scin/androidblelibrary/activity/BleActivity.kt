package com.scin.androidblelibrary.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.permissionx.guolindev.PermissionX
import com.scin.androidblelibrary.adapter.BleAdapter
import com.scin.androidblelibrary.bean.BleDevicesBean
import com.scin.androidblelibrary.databinding.ActivityBleBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import no.nordicsemi.android.kotlin.ble.scanner.BleScanner
import no.nordicsemi.android.kotlin.ble.scanner.aggregator.BleScanResultAggregator

class BleActivity : AppCompatActivity() {
    private val bind by lazy { ActivityBleBinding.inflate(layoutInflater) }
    private val deviceList = mutableSetOf<BleDevicesBean>()
    private val bleDataAdapter = BleAdapter()
    private var scanBleScope: CoroutineScope? = CoroutineScope(Dispatchers.IO)
    private val aggregator = BleScanResultAggregator()//创建聚合器，将扫描记录与设备连接起来
    private var bleScanner: BleScanner? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(bind.root)
        initClick()
        initRV()
    }

    private fun initRV() {
        bind.rvRecyclerView.layoutManager = LinearLayoutManager(this)
        bind.rvRecyclerView.adapter = bleDataAdapter
        bleDataAdapter.setList(deviceList)
        bleDataAdapter.setOnItemClickListener { _, _, position ->
            val intent = Intent(this, BleSendDataActivity::class.java)
            intent.putExtra("data", bleDataAdapter.getItem(position))
            startActivity(intent)
        }
    }


    private fun initClick() {
        bind.btnScanBle.setOnClickListener {
            PermissionX.init(this).permissions(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ).request { allGranted, _, _ ->
                if (allGranted) {
                    scanBle()
                } else {
                    Toast.makeText(this, "请开启蓝牙和定位权限", Toast.LENGTH_SHORT).show()
                }
            }
        }
        bind.btnStopScan.setOnClickListener {
            if (scanBleScope != null) {
                scanBleScope?.cancel(null)
                scanBleScope = null
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanBle() {
        if (scanBleScope == null) scanBleScope = CoroutineScope(Dispatchers.IO)
        if (bleScanner == null) bleScanner = BleScanner(this)
        bleScanner!!.scan()
            .map { aggregator.aggregateDevices(it) } //添加新设备并返回聚合列表
            .onEach {
                val list = it.filter { !it.name.isNullOrBlank() }.map { BleDevicesBean(it.name!!,it.address,it) }
                val count = deviceList.size
                deviceList.addAll(list)
                if(deviceList.size>count) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        bleDataAdapter.setList(deviceList)
                    }
                }
            } //将状态传播到ui
            .launchIn(scanBleScope!!) //离开屏幕后扫描将停止
    }

    override fun onDestroy() {
        scanBleScope?.cancel(null)
        scanBleScope = null
        super.onDestroy()
    }
}
package com.scin.androidblelibrary.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.scin.androidblelibrary.adapter.MsgAdapter
import com.scin.androidblelibrary.bean.MsgDataBean
import com.scin.androidblelibrary.databinding.ActivityMqttBinding
import com.scin.blelibrary.mqtt.MqttCallListener
import com.scin.blelibrary.mqtt.MqttUtils
import org.eclipse.paho.client.mqttv3.IMqttToken

class MqttActivity : AppCompatActivity() {
    init {
        MqttUtils.initMqtt(lifecycleScope, this)
    }
    private val bind by lazy { ActivityMqttBinding.inflate(layoutInflater) }
    val mqttManager by lazy { MqttUtils.get() }
    private val adapter = MsgAdapter()
    private val msgDataList = mutableListOf<MsgDataBean>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(bind.root)
        initRV()
        initMqtt()
        bind.btnSend.setOnClickListener {
            val msg = bind.etMsg.text.toString()
            if (msg.isNotEmpty()) {
                val topic = "\$rsp/setting/pV99G0NUwqh/1792895064491786241"
                MqttUtils.get().mqttPublish(topic,msg.toByteArray())
                msgDataList.add(0,MsgDataBean(true, msg))
                adapter.setList(msgDataList)
            }
        }
    }

    private fun initMqtt() {
        mqttManager.doClientConnection("tcp://test-zmq.scin-power.com:1883",
            "1792895064491786241;newcabinet;1716294789",
            "d15d96c2d83ddc70de9bc8511ade1d68feeda29f;hmacsha1".toCharArray(),
            "1792895064491786241;pV99G0NUwqh;cabinet", listener)
    }
    private val listener =  object : MqttCallListener() {
        override fun onConnectResult(//链接状态
            isSuccess: Boolean,
            token: IMqttToken?,
            exception: Throwable?
        ) {
            Toast.makeText(this@MqttActivity, if (isSuccess) "mqtt连接成功" else "mqtt连接失败", Toast.LENGTH_SHORT).show()
            if (isSuccess) {
                mqttManager.mqttSub(mutableListOf("\$cmd/setting/pV99G0NUwqh/1792895064491786241"))
            } else {
//                lifecycleScope.launch(Dispatchers.IO) {
//                    delay(2000)
//                    mqttManager.doClientConnection("", "", charArrayOf(), "", listener)
//                }
            }
        }

        override fun onSubscribeResult(
            isSuccess: Boolean,
            token: IMqttToken?,
            exception: Throwable?
        ) {
            Toast.makeText(this@MqttActivity, if (isSuccess) "mqtt订阅成功" else "mqtt订阅失败", Toast.LENGTH_SHORT).show()
        }

        override fun onReceivedMsg(topic: String?, message: ByteArray) {//接收到消息
            msgDataList.add(0, MsgDataBean(false, message.toString()))
            adapter.setList(msgDataList)
        }
    }

    private fun initRV() {
        bind.rvRecyclerView.layoutManager = LinearLayoutManager(this)
        bind.rvRecyclerView.adapter = adapter
        adapter.setList(msgDataList)
    }

    override fun onDestroy() {
        mqttManager.disconnect()
        super.onDestroy()
    }
}
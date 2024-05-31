package com.scin.blelibrary.mqtt

import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttMessage

abstract class MqttCallListener {
    /**
     * 连接结果 自带重连机制
     * @param isSuccess 是否成功
     * @param token
     * @param exception
     */
    abstract fun onConnectResult(isSuccess:Boolean,token: IMqttToken? = null,exception: Throwable? = null)
    /**
     * 订阅结果 自带重新订阅机制
     * @param isSuccess 是否成功
     * @param token
     * @param exception
     */
    open fun onSubscribeResult(isSuccess:Boolean,token: IMqttToken? = null,exception: Throwable? = null){}
    /**
     * 接收到消息
     * @param topic
     * @param message
     */
    abstract fun onReceivedMsg(topic: String?, message: ByteArray)
    /**
     * 连接断开
     * @param exception
     */
    open fun onConnectionLost(exception: Throwable?){}

}
package com.scin.blelibrary.mqtt

import android.accounts.NetworkErrorException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.http.NetworkException
import com.scin.blelibrary.ext.appLogD
import com.scin.blelibrary.ext.toHexString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.android.service.MqttService
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage

/**
 * @author  linXQ
 */
class MqttUtils {
    private val TAG = "MqttManager"
    private var cleanSession = true
    private var connectionTimeout = 10
    private var keepAliveInterval = 60 * 5
    private var retryConnectInterval = 5L
    private val mutex = Mutex()
    private var topicList: MutableList<String>? = null
    private var mqttUrl = ""
    private var userName = ""
    private var password = charArrayOf()
    private var mqttClientId = ""
    private lateinit var context: Context
    private lateinit var scope: CoroutineScope
    private var listener: MqttCallListener? = null
    private var mqttAndroidClient: MqttAndroidClient? = null
    private var mqttConnectOptions: MqttConnectOptions? = null
    private var activeDisconnect = false

    companion object {
        private val mqtt: MqttUtils by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { MqttUtils() }
        fun get(): MqttUtils {
            return mqtt
        }

        fun initMqtt(
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            context: Context
        ) {
            mqtt.scope = scope
            mqtt.context = context
        }
    }

    fun mqttIsConnecting(): Boolean {
        return mqttAndroidClient?.isConnected == true
    }

    fun doClientConnection(
        mqttUrl: String,
        userName: String,
        password: CharArray,
        mqttClientId: String,
        listener: MqttCallListener?
    ) {
        this.mqttUrl = mqttUrl
        this.userName = userName
        this.password = password
        this.mqttClientId = mqttClientId
        this.listener = listener
        scope.launch(Dispatchers.IO) {
            if (mqttAndroidClient == null || mqttConnectOptions == null) {
                initMQTT()
            }
            if (mqttAndroidClient?.isConnected == true) {//已经连接
                return@launch
            }
            if (!isConnectIsNormal()) {//没有网络
                listener?.onConnectResult(false, exception = Throwable("网络异常"))
                return@launch
            }
            appLogD(TAG, "mqtt连接url：$mqttUrl")
            try {
                val token = mqttAndroidClient?.connect(
                    mqttConnectOptions,
                    null,
                    object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            appLogD(TAG, "mqtt连接成功")
                            scope.launch(Dispatchers.Main) {
                                listener?.onConnectResult(true, asyncActionToken)
                            }
                        }

                        override fun onFailure(
                            asyncActionToken: IMqttToken?,
                            exception: Throwable?
                        ) {
                            appLogD(TAG, "mqtt连接失败:${exception?.message}")
                            scope.launch(Dispatchers.Main) {
                                listener?.onConnectResult(true, asyncActionToken, exception)
                            }
                        }
                    })
                token?.waitForCompletion(60 * 1000)//阻塞当前线程，知道完成或超时
                appLogD("连接操作完成：${token?.isComplete}")
            } catch (e: Exception) {
                appLogD(TAG, "mqtt连接异常")
                listener?.onConnectResult(false, exception = e)
                e.printStackTrace()
            }
        }
    }

    private fun initMQTT() {
        if (mqttAndroidClient != null || mqttConnectOptions != null) {
            return
        }
        if (mqttUrl.isBlank()) throw NullPointerException()
        appLogD(TAG, "mqtt init")
        activeDisconnect = false

        mqttAndroidClient = MqttAndroidClient(context, mqttUrl, mqttClientId)
        mqttAndroidClient?.setCallback(mqttCallBack)
        mqttConnectOptions = MqttConnectOptions()
        mqttConnectOptions?.isCleanSession = cleanSession
        mqttConnectOptions?.connectionTimeout = connectionTimeout
        mqttConnectOptions?.keepAliveInterval = keepAliveInterval
        mqttConnectOptions?.userName = userName
        mqttConnectOptions?.password = password
        mqttConnectOptions?.maxInflight = 100
        appLogD("MQTT_CLIENT-${mqttClientId}")
        appLogD("MQTT_USERNAME-${userName}")
        appLogD("MQTT_PWD-${String(password)}")
    }

    /**
     * 判断网络是否连接
     */
    private fun isConnectIsNormal(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val info = connectivityManager.activeNetworkInfo
        return if (info != null && info.isAvailable) {
            val name = info.typeName
            appLogD(TAG, "网络连接类型：$name")
            true
        } else {
            appLogD(TAG, "没有网络")
            false
        }
    }

    fun mqttSub(topicList: MutableList<String>) {
        scope.launch(Dispatchers.IO) {
            try {
                //订阅主题，参数：主题、服务质量
                val token = mqttAndroidClient?.subscribe(
                    topicList.toTypedArray(),
                    IntArray(topicList.size),
                    null,
                    object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            scope.launch(Dispatchers.Main) {
                                listener?.onSubscribeResult(true, asyncActionToken)
                            }
                            this@MqttUtils.topicList = topicList
                            appLogD("mqtt订阅成功")
                        }

                        override fun onFailure(
                            asyncActionToken: IMqttToken?,
                            exception: Throwable?
                        ) {
                            scope.launch(Dispatchers.Main) {
                                listener?.onSubscribeResult(false, asyncActionToken, exception)
                            }
                            appLogD("mqtt订阅失败")
                        }
                    }
                )
                token?.waitForCompletion(60 * 1000)
                appLogD(TAG, "mqttSub操作完成：${token?.isComplete}")
            } catch (e: Exception) {
                e.printStackTrace()
                appLogD("mqtt订阅异常，自动重新订阅")
                delay(1000)
                mqttSub(topicList)
            }
        }
    }


    private val mqttCallBack: MqttCallback = object : MqttCallback {
        override fun messageArrived(topic: String?, message: MqttMessage?) {//接收到消息
            message?.payload?.let {data->
                appLogD(
                    TAG, "topic： $topic" +
                            "\nmessage：${data.toHexString()}"
                )
                scope.launch(Dispatchers.Main) {
                    listener?.onReceivedMsg(topic, data)
                }
            }
        }

        override fun connectionLost(cause: Throwable?) {
            if (activeDisconnect) return //主动断开连接不处理
            scope.launch(Dispatchers.Main) {
                listener?.onConnectionLost(cause)
            }
            appLogD("连接中断：${cause.toString()}")
            scope.launch(Dispatchers.IO) {
                delay(retryConnectInterval * 1000)
                appLogD(TAG, "连接中断，自动重连...")
                doClientConnection(mqttUrl, userName, password, mqttClientId, listener)
            }
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) {//服务端接收到消息后回调

        }
    }

    /**
     * 发送消息
     * @param topic     发送消息的主题
     * @param msg       消息内容
     * @param qos       消息保证等级 0：最多一次 1：至少一次 2：仅一次
     * @param retained  服务器是否应保留此消息。
     * @param listener
     */
    fun mqttPublish(
        topic: String,
        msg: ByteArray,
        qos: Int = 1,
        retained: Boolean = false,
        listener: IMqttActionListener? = null
    ) {
        if (mqttAndroidClient == null || mqttAndroidClient?.isConnected == false) {
            listener?.onFailure(null, NullPointerException())
            return
        }
        appLogD(
            TAG, "repTopic： $topic" +
                    "\nmsg： ${msg.toHexString()}" +
                    "\nqos： $qos" +
                    "\nretained： $retained"
        )
        scope.launch(Dispatchers.IO) {
            if (listener != null) {
                mqttAndroidClient?.publish(topic, msg, qos, retained, null, listener)
                return@launch
            }
            mqttAndroidClient?.publish(
                topic, msg, qos, retained, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        appLogD(TAG, "消息发送失败：exception=${exception?.toString()}")
                    }
                })
        }
    }

    fun disconnect() {
        appLogD(TAG, "mqtt开始断开")
        activeDisconnect = true
        if (mqttAndroidClient == null || mqttAndroidClient?.isConnected == false) {
            appLogD(TAG, "mqtt未连接")
            return
        }
        mqttAndroidClient?.unsubscribe(topicList?.toTypedArray())
        mqttAndroidClient?.unregisterResources()
        val intent = Intent(context, MqttService::class.java)
        context.stopService(intent)
        try {
            mqttAndroidClient?.disconnect(2000) //断开连接
        } catch (e: Exception) {
            e.printStackTrace()
            appLogD(TAG, "waitForCompletion")
        }
        mqttAndroidClient = null
        appLogD(TAG, "mqtt断开成功")
    }
}
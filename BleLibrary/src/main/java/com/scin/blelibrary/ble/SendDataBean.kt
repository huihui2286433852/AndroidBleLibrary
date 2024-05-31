package com.scin.blelibrary.ble

class SendDataBean(var data: ByteArray, var sendResult: ((Boolean) -> Unit)?) {
}
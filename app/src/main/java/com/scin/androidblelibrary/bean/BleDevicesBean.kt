package com.scin.androidblelibrary.bean

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import no.nordicsemi.android.kotlin.ble.core.ServerDevice

@Parcelize
data class BleDevicesBean(var name:String, var address:String,val device: ServerDevice): Parcelable {
}
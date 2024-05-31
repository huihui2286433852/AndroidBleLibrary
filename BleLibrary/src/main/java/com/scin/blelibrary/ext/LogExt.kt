package com.scin.blelibrary.ext

import android.util.Log

fun Any.appLogD(tag: String, msg: String) {
    Log.d(this.javaClass.name, msg)
}

fun Any.appLogD(msg: String) {
    Log.d(this.javaClass.name, msg)
}
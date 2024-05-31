package com.scin.androidblelibrary.activity

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.scin.androidblelibrary.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private val bind by lazy { ActivityMainBinding.inflate(layoutInflater) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(bind.root)
        bind.btnBle.setOnClickListener {
            startActivity(Intent(this, BleActivity::class.java))
        }
        bind.btnMqtt.setOnClickListener {
            startActivity(Intent(this, MqttActivity::class.java))
        }
    }
}
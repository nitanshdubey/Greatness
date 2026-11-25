package com.greatness.app

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { if (it.resultCode == RESULT_OK) startFilter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        layout.addView(TextView(this).apply { text = "Greatness"; textSize = 32f; gravity = Gravity.CENTER })
        layout.addView(Button(this).apply {
            text = "Start protection"
            setOnClickListener {
                val i = VpnService.prepare(this@MainActivity)
                if (i != null) vpnPermission.launch(i) else startFilter()
            }
        })
        layout.addView(Button(this).apply {
            text = "Stop"
            setOnClickListener {
                startService(Intent(this@MainActivity, DnsVpnService::class.java).setAction("STOP"))
            }
        })
        setContentView(layout)
    }

    private fun startFilter() {
        startService(Intent(this, DnsVpnService::class.java))
    }
}

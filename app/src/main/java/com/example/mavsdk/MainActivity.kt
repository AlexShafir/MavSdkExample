package com.example.mavsdk


import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.mavsdk.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.round
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private val droneModel = DroneModel
    private lateinit var v: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //enableEdgeToEdge()

        v = ActivityMainBinding.inflate(layoutInflater)
        setContentView(v.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        v.btnConnect.setOnClickListener {
            when (droneModel.connState.value) {
                DroneModel.ConnState.DISCONNECTED -> droneModel.connect(v.editIp.text.toString())
                DroneModel.ConnState.CONNECTING, DroneModel.ConnState.CONNECTED -> droneModel.disconnect()
            }
        }

        v.btnArm.setOnClickListener {
            val isArmed = droneModel.state.value.isArmed ?: return@setOnClickListener
            if (isArmed) {
                droneModel.drone!!.action.disarm().subscribe({}, {})
            } else {
                droneModel.drone!!.action.arm().subscribe({}, {})
            }
        }

        v.btnTakeoff.setOnClickListener {
            droneModel.drone!!.action.setTakeoffAltitude(5f).subscribe()
            droneModel.drone!!.action.takeoff().subscribe({}, {})
        }

        v.btnLand.setOnClickListener {
            droneModel.drone!!.action.land().subscribe({}, {})
        }

        launch {
            droneModel.connState.collect { state ->
                when (state) {
                    DroneModel.ConnState.DISCONNECTED -> {
                        v.btnConnect.setText(R.string.connect)
                        v.textStatus.text = ""
                        v.textPosition.text = ""
                    }
                    DroneModel.ConnState.CONNECTING, DroneModel.ConnState.CONNECTED ->
                        v.btnConnect.setText(R.string.disconnect)
                }

                val connected = when (state) {
                    DroneModel.ConnState.DISCONNECTED, DroneModel.ConnState.CONNECTING -> false
                    DroneModel.ConnState.CONNECTED -> true
                }

                v.editIp.isEnabled = !connected
                v.btnTakeoff.isEnabled = connected
                v.btnLand.isEnabled = connected
                v.btnArm.isEnabled = connected

                if (connected) {
                    launch {
                        droneModel.state.collect {
                            render(it)
                        }
                    }
                }
            }
        }

    }

    private fun render(s: DroneModel.State) {
        v.btnArm.text = when (s.isArmed) {
            false, null -> getString(R.string.arm)
            true -> getString(R.string.disarm)
        }

        v.textStatus.text = s.status?.text ?: ""

        val alt = s.position?.get(2)
        val altStr = if(alt != null) round(alt / 100) * 100 else "-"
        v.textPosition.text = "Alt: $altStr"
    }

    private fun launch(func: suspend (CoroutineScope) -> Unit) {
        lifecycleScope.launch { func(this) }
    }
}
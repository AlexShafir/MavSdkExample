package com.example.mavsdk

import io.mavsdk.MavsdkEventQueue
import io.mavsdk.System
import io.mavsdk.core.Core
import io.mavsdk.mavsdkserver.MavsdkServer
import io.mavsdk.telemetry.Telemetry
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow

object DroneModel {
    @Volatile
    var drone: System? = null
        private set

    private val mMavsdkServer = MavsdkServer()

    data class State(
        val connState: Core.ConnectionState? = null,
        val isArmed: Boolean? = null,
        val status: Telemetry.StatusText? = null,
        val position: DoubleArray? = null
    )
    private val mState = MutableStateFlow(State())
    val state: StateFlow<State> = mState

    enum class ConnState { DISCONNECTED, CONNECTING, CONNECTED }
    private val mConnState = MutableStateFlow(ConnState.DISCONNECTED)
    val connState: StateFlow<ConnState> = mConnState

    fun connect(ip: String) {
        if(mConnState.value == ConnState.CONNECTING) return

        MavsdkEventQueue.executor().execute {
            mConnState.value = ConnState.CONNECTING
            val mavsdkServerPort = mMavsdkServer.run(ip)
            drone = System("127.0.0.1", mavsdkServerPort)

            GlobalScope.launch {
                drone!!.telemetry.statusText.asFlow().collect {
                    mState.value = mState.value.copy(status = it)
                }
            }

            GlobalScope.launch {
                drone!!.telemetry.armed.asFlow().collect {
                    mState.value = mState.value.copy(isArmed = it)
                }
            }

            GlobalScope.launch {
                drone!!.core.connectionState.asFlow().collect {
                   if(it.isConnected) {
                       mConnState.value = ConnState.CONNECTED
                       drone!!.telemetry.setRatePosition(2.0).subscribe({}, {})
                   }
                   else disconnect()
                }
            }

            GlobalScope.launch {
                drone!!.telemetry.position.asFlow().collect {
                    //if(it.latitudeDeg == 0.0) return@collect
                    mState.value = mState.value.copy(position = doubleArrayOf(it.latitudeDeg, it.longitudeDeg, it.relativeAltitudeM.toDouble()))
                }
            }

        }
    }

    fun disconnect() {
        if(mConnState.value == ConnState.DISCONNECTED) return

        MavsdkEventQueue.executor().execute {
            drone?.dispose()
            drone = null

            mMavsdkServer.stop()
            mMavsdkServer.destroy()

            mConnState.value = ConnState.DISCONNECTED
        }
    }
}
package com.example.santiagoface.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * Advertises SantiagoFace's embedded HTTP/MJPEG server on the local network via mDNS/NSD
 * so SantiagoRemote can auto-discover it without manual IP entry.
 */
class FaceNsdAdvertiser(private val context: Context) {
    private val nsdManager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun register(port: Int = 8080) {
        if (registrationListener != null) return
        val manager = nsdManager ?: return

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "NSD registered: ${info.serviceName} on port $port")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD registration failed: $errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "NSD unregistered")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD unregistration failed: $errorCode")
            }
        }
        registrationListener = listener
        runCatching {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            Log.w(TAG, "Failed to register NSD service: ${it.message}")
            registrationListener = null
        }
    }

    fun unregister() {
        val listener = registrationListener ?: return
        registrationListener = null
        runCatching {
            nsdManager?.unregisterService(listener)
        }
    }

    companion object {
        private const val TAG = "FaceNsdAdvertiser"
        const val SERVICE_TYPE = "_santiagoface._tcp."
        const val SERVICE_NAME = "SantiagoFace"
    }
}

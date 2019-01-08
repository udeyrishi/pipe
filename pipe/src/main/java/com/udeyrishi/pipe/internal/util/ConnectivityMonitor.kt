/**
 * Copyright (c) 2019 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.util

import android.Manifest
import android.Manifest.permission.ACCESS_NETWORK_STATE
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission

private val Context.connectivityManager: ConnectivityManager
    get() = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

private val ConnectivityManager.isConnected: Boolean
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    get() = activeNetworkInfo?.isConnected == true

internal abstract class ConnectivityMonitor(protected val context: Context) : Observable<Boolean> {
    private val connected = MutableObservable(context.connectivityManager.isConnected)

    final override fun observe(observer: Observer<Boolean>) = connected.observe(observer)
    final override fun removeObserver(observer: Observer<Boolean>) = connected.removeObserver(observer)
    final override fun clearObservers() = connected.clearObservers()

    @RequiresPermission(ACCESS_NETWORK_STATE)
    fun start() {
        postUpdate(context.connectivityManager.isConnected)
        onStarted()
    }

    @RequiresPermission(ACCESS_NETWORK_STATE)
    abstract fun stop()

    @RequiresPermission(ACCESS_NETWORK_STATE)
    protected abstract fun onStarted()

    protected fun postUpdate(connected: Boolean) = this.connected.postValue(connected)
}

@RequiresPermission(ACCESS_NETWORK_STATE)
internal fun Context.createConnectivityMonitor(): ConnectivityMonitor {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1 && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
        ICSConnectivityMonitor(this)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        LollipopConnectivityMonitor(this)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        NougatConnectivityMonitor(this)
    } else {
        throw IllegalArgumentException("SDK ${Build.VERSION.SDK_INT} not supported.")
    }
}

@RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
private class ICSConnectivityMonitor @RequiresPermission(ACCESS_NETWORK_STATE) constructor(context: Context) : ConnectivityMonitor(context) {
    private val connectivityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) = postUpdate(context.connectivityManager.isConnected)
    }

    @Suppress("DEPRECATION")
    @RequiresPermission(ACCESS_NETWORK_STATE)
    override fun onStarted() {
        context.registerReceiver(connectivityReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }

    @RequiresPermission(ACCESS_NETWORK_STATE)
    override fun stop() = context.unregisterReceiver(connectivityReceiver)
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
private class LollipopConnectivityMonitor @RequiresPermission(ACCESS_NETWORK_STATE) constructor(context: Context) : ConnectivityMonitor(context) {
    private val networkCallback = createNetworkCallback(::postUpdate)

    @RequiresPermission(ACCESS_NETWORK_STATE)
    override fun onStarted() = context.connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback)

    @RequiresPermission(ACCESS_NETWORK_STATE)
    override fun stop() = context.connectivityManager.unregisterNetworkCallback(networkCallback)
}

@RequiresApi(Build.VERSION_CODES.N)
private class NougatConnectivityMonitor @RequiresPermission(ACCESS_NETWORK_STATE) constructor(context: Context) : ConnectivityMonitor(context) {
    private val networkCallback = createNetworkCallback(::postUpdate)

    @RequiresPermission(ACCESS_NETWORK_STATE)
    override fun onStarted() = context.connectivityManager.registerDefaultNetworkCallback(networkCallback)

    @RequiresPermission(ACCESS_NETWORK_STATE)
    override fun stop() = context.connectivityManager.unregisterNetworkCallback(networkCallback)
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
private inline fun createNetworkCallback(crossinline onNetworkEvent: (connected: Boolean) -> Unit) = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network?) = onNetworkEvent(true)
    override fun onLost(network: Network?) = onNetworkEvent(false)
}

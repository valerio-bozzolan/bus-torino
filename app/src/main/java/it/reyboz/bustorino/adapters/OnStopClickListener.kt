package it.reyboz.bustorino.adapters

import it.reyboz.bustorino.backend.Stop

fun interface OnStopClickListener {
    fun onStopClick(stop: Stop)
}
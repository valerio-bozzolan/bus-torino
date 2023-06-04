package it.reyboz.bustorino.data.gtfs

enum class WheelchairAccess(val value: Int){
    UNKNOWN(0),
    SOMETIMES(1),
    IMPOSSIBLE(2);

    companion object {
        private val VALUES = values()
        fun getByValue(value: Int) = VALUES.firstOrNull { it.value == value }
    }
}
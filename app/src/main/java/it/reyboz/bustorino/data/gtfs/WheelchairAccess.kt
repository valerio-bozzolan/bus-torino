package it.reyboz.bustorino.data.gtfs

enum class WheelchairAccess(val value: Int){
    UNKNOWN(0),
    SOMETIMES(1),
    IMPOSSIBLE(2);

    // BE CAREFUL: WheelchairAccess is saved as a String in the DB due to a catastrophic error.
    // However, everything works perfectly, so... finchè la barca va...
    companion object {
        private val VALUES = values()
        fun getByValue(value: Int) = VALUES.firstOrNull { it.value == value }
    }
}
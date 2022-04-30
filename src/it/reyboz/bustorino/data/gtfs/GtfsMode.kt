package it.reyboz.bustorino.data.gtfs

enum class GtfsMode(val intType: Int) {
    TRAM(0),
    SUBWAY(1),
    RAIL(2),
    BUS(3),
    FERRY(4),
    CABLE_TRAM(5),
    GONDOLA(6),
    FUNICULAR(7),
    TROLLEYBUS(11),
    MONORAIL(12);

    companion object {
        private val VALUES = values()
        fun getByValue(value: Int) = VALUES.firstOrNull { it.intType == value }
    }
}
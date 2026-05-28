package it.reyboz.bustorino.backend

import it.reyboz.bustorino.backend.VehicleUtils.VehicleType
import java.util.Locale.getDefault

data class VehicleClassInfo(
    val vehClass: Int,
    val name: String,
    val kindString: String,
    val type: VehicleType,
    val matricolaStart: Int,
    val matricolaEnd: Int,
) {
    constructor(vehicleClass: Int, name: String, type: String, matricolaStart: Int, matricolaEnd: Int) : this(
        vehicleClass, name, type,VehicleType.fromString(type), matricolaStart, matricolaEnd
    )
}

object VehicleUtils {
    val items = listOf(
        VehicleClassInfo(30, "BYD K9UB", "E-Bus", 30, 49),
        VehicleClassInfo(50, "BYD K7", "E-Bus", 50, 57),
        VehicleClassInfo(60, "Indcar BlueBus", "E-Bus", 60, 88),
        VehicleClassInfo(110, "BMC Neocity", "Bus", 110, 115),
        VehicleClassInfo(800, "Irisbus Citelis 18m", "Bus 18m", 790, 797),
        VehicleClassInfo(800, "Irisbus Citelis 18m", "Bus 18m", 800, 869),
        VehicleClassInfo(800, "Irisbus Citelis 18m ", "Bus 18m", 870, 874),
        VehicleClassInfo(800, "Irisbus Citelis CNG 18m", "Bus 18m", 1310, 1313),
        VehicleClassInfo(1350, "Mercedes Conecto 18m", "Bus 18m", 1350, 1396),
        VehicleClassInfo(2300, "Irisbus CityClass", "Bus", 2300, 2349),
        VehicleClassInfo(3400, "Mercedes Conecto", "Bus", 2400, 2447),
        VehicleClassInfo(2300, "Irisbus CityClass", "Bus", 2700, 2787),
        VehicleClassInfo(2800, "2800 (prima serie)", "Tram", 2801, 2857),
        VehicleClassInfo(2800, "2800 (seconda serie)", "Tram", 2858, 2902),
        VehicleClassInfo(3000, "Irisbus Citelis", "Bus", 3000, 3099),
        VehicleClassInfo(3000, "Irisbus Citelis", "Bus", 3300, 3380),
        VehicleClassInfo(3400, "Mercedes Conecto", "Bus", 3400, 3440),
        VehicleClassInfo(5000, "P.R. (5000)", "Tram", 5000, 5053),
        VehicleClassInfo(6000, "CityWay (6000) monodir.", "Tram", 6000, 6005),
        VehicleClassInfo(6000, "CityWay (6000) bidir.", "Tram", 6006, 6054),
        VehicleClassInfo(8000, "Hitachi (8000)", "Tram", 8000, 8099),
        VehicleClassInfo(9000, "BYD K9UB", "E-Bus", 9000, 9059),
        VehicleClassInfo(9000, "BYD K9UB", "E-Bus", 9060, 9119),
        VehicleClassInfo(9000, "BYD K9UB", "E-Bus", 9120, 9121),
        VehicleClassInfo(9200, "Menarini Citymood", "Bus", 9200, 9251),
        VehicleClassInfo(9200, "Menarini Citymood", "Bus", 9252, 9261),
        VehicleClassInfo(9300, "Iveco Urbanway 18m", "Bus 18m", 9300, 9318),
        VehicleClassInfo(9300, "Iveco Urbanway 18m", "Bus 18m", 9320, 9356),
        VehicleClassInfo(9400, "Iveco E-Way", "E-Bus", 9400, 9599),
        VehicleClassInfo(9600, "Iveco E-Way 18m", "E-Bus", 9600, 9699),
        VehicleClassInfo(9700, "Iveco E-Way 18m BRT", "E-Bus", 9700, 9799)
    )


    fun getTypeForLabel(label: String): VehicleClassInfo? {
        try {
            val matricola = Integer.parseInt(label)
            for (el in items) {
                if(matricola >= el.matricolaStart && matricola<= el.matricolaEnd) {
                    return el
                }
            }
            return null

        } catch (e: Exception) {
            return null
        }
    }
    enum class VehicleType {
        BUS, TRAM, ELECTRIC_BUS;

        fun getName(): String {
            return when (this) {
                BUS -> "Bus"
                TRAM -> "Tram"
                ELECTRIC_BUS -> "E-Bus"
            }
        }

        companion object {
            @JvmStatic
            fun fromString(string: String): VehicleType {
                return when (string.lowercase(getDefault())) {
                    "bus" -> BUS
                    "bus 18m" -> BUS
                    "tram" -> TRAM
                    "e-bus" -> ELECTRIC_BUS
                    "e-bus 18m" -> ELECTRIC_BUS
                    else -> throw IllegalArgumentException("Unknown vehicle type: $string")
                }
            }
        }
    }
}
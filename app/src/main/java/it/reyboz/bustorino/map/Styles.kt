package it.reyboz.bustorino.map
import android.content.Context
import it.reyboz.bustorino.util.ViewUtils
import org.maplibre.android.maps.Style

object Styles {
    const val DEMOTILES = "https://demotiles.maplibre.org/style.json"

    const val VERSATILES = "https://tiles.versatiles.org/assets/styles/colorful.json"

    const val AMERICANA = "https://americanamap.org/style.json"

    const val OPENFREEMAP_LIBERTY = "https://tiles.openfreemap.org/styles/liberty"

    const val OPENFREEMAP_BRIGHT = "https://tiles.openfreemap.org/styles/bright"

    const val BASIC_V8 = "mapbox://styles/mapbox/streets-v8"


    const val AWS_OPEN_DATA_STANDARD_LIGHT =
        "https://maps.geo.us-east-2.amazonaws.com/maps/v0/maps/OpenDataStyle/style-descriptor?key=v1.public.eyJqdGkiOiI1NjY5ZTU4My0yNWQwLTQ5MjctODhkMS03OGUxOTY4Y2RhMzgifR_7GLT66TNRXhZJ4KyJ-GK1TPYD9DaWuc5o6YyVmlikVwMaLvEs_iqkCIydspe_vjmgUVsIQstkGoInXV_nd5CcmqRMMa-_wb66SxDdbeRDvmmkpy2Ow_LX9GJDgL2bbiCws0wupJPFDwWCWFLwpK9ICmzGvNcrPbX5uczOQL0N8V9iUvziA52a1WWkZucIf6MUViFRf3XoFkyAT15Ll0NDypAzY63Bnj8_zS8bOaCvJaQqcXM9lrbTusy8Ftq8cEbbK5aMFapXRjug7qcrzUiQ5sr0g23qdMvnKJQFfo7JuQn8vwAksxrQm6A0ByceEXSfyaBoVpFcTzEclxUomhY.NjAyMWJkZWUtMGMyOS00NmRkLThjZTMtODEyOTkzZTUyMTBi"

    private fun protomaps(style: String): String {
        return "https://api.protomaps.com/styles/v2/${style}.json?key=e761cc7daedf832a"
    }

    private fun makeStyleMapBoxUrl(dark: Boolean) =
        if (dark)
            "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
        else //"https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"
            "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"

    val PROTOMAPS_LIGHT = protomaps("light")

    val PROTOMAPS_DARK = protomaps("dark")

    val PROTOMAPS_GRAYSCALE = protomaps("grayscale")

    val PROTOMAPS_WHITE = protomaps("white")

    val PROTOMAPS_BLACK = protomaps("black")

    val CARTO_DARK  = makeStyleMapBoxUrl(true)

    val CARTO_VOYAGER = makeStyleMapBoxUrl(false)

    fun getPredefinedStyleWithFallback(name: String): String {
        try {
            val style = Style.getPredefinedStyle(name)
            return style
        } catch (e: Exception) {
            return OPENFREEMAP_LIBERTY
        }
    }
    fun getJsonStyleFromAsset(context: Context, filename: String) = ViewUtils.loadJsonFromAsset(context,filename)
}
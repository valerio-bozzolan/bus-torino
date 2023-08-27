package it.reyboz.bustorino.backend.mato

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import info.mqtt.android.service.Ack
import it.reyboz.bustorino.backend.gtfs.LivePositionUpdate
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.*
import info.mqtt.android.service.MqttAndroidClient
import info.mqtt.android.service.QoS

import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONArray
import org.json.JSONException
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.Properties

typealias PositionsMap = HashMap<String, HashMap<String, LivePositionUpdate> >

class MQTTMatoClient private constructor(): MqttCallbackExtended{

    private var isStarted = false
    private var subscribedToAll = false

    private lateinit var client: MqttAndroidClient
    //private var clientID = ""

    private val respondersMap = HashMap<String, ArrayList<WeakReference<MQTTMatoListener>>>()

    private val currentPositions = PositionsMap()

    private lateinit var lifecycle: LifecycleOwner
    private var context: Context?= null

    private fun connect(context: Context, iMqttActionListener: IMqttActionListener?){

        val clientID = "mqttjs_${getRandomString(8)}"

        client = MqttAndroidClient(context,SERVER_ADDR,clientID,Ack.AUTO_ACK)

        val options = MqttConnectOptions()
        //options.sslProperties =
        options.isCleanSession = true
        val headersPars = Properties()
        headersPars.setProperty("Origin","https://mato.muoversiatorino.it")
        headersPars.setProperty("Host","mapi.5t.torino.it")
        options.customWebSocketHeaders = headersPars

        //actually connect
        client.connect(options,null, iMqttActionListener)
        isStarted = true
        client.setCallback(this)

        if (this.context ==null)
            this.context = context.applicationContext
    }


    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
        Log.d(DEBUG_TAG, "Connected to server, reconnect: $reconnect")
        Log.d(DEBUG_TAG, "Have listeners: $respondersMap")
    }

    fun startAndSubscribe(lineId: String, responder: MQTTMatoListener, context: Context): Boolean{
        //start the client, and then subscribe to the topic
        val topic = mapTopic(lineId)
        synchronized(this) {
            if(!isStarted){
                connect(context, object : IMqttActionListener{
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        val disconnectedBufferOptions = DisconnectedBufferOptions()
                        disconnectedBufferOptions.isBufferEnabled = true
                        disconnectedBufferOptions.bufferSize = 100
                        disconnectedBufferOptions.isPersistBuffer = false
                        disconnectedBufferOptions.isDeleteOldestMessages = false
                        client.setBufferOpts(disconnectedBufferOptions)
                        client.subscribe(topic, QoS.AtMostOnce.value)
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e(DEBUG_TAG, "FAILED To connect to the server")
                    }

                })
                //wait for connection
            } else {
                client.subscribe(topic, QoS.AtMostOnce.value)
            }
        }



        synchronized(this){
            if (!respondersMap.contains(lineId))
                respondersMap[lineId] = ArrayList()
            respondersMap[lineId]!!.add(WeakReference(responder))
            Log.d(DEBUG_TAG, "Add MQTT Listener for line $lineId, topic $topic")
        }

        return true
    }

    fun desubscribe(responder: MQTTMatoListener){
        var removed = false
        for ((line,v)in respondersMap.entries){
            var done = false
            for (el in v){
                if (el.get()==null){
                    v.remove(el)
                } else if(el.get() == responder){
                    v.remove(el)
                    done = true
                }
                if (done)
                    break
            }
            if(done) Log.d(DEBUG_TAG, "Removed one listener for line $line, listeners: $v")
            //if (done) break
            if (v.isEmpty()){
                //actually unsubscribe
                client.unsubscribe( mapTopic(line))
            }
            removed = done || removed
        }
        // remove lines that have no responders
        for(line in respondersMap.keys){
            if(respondersMap[line]?.isEmpty() == true){
                respondersMap.remove(line)
            }
        }
        Log.d(DEBUG_TAG, "Removed: $removed, respondersMap: $respondersMap")
    }
    fun getPositions(): PositionsMap{
        return currentPositions
    }

    fun sendUpdateToResponders(responders: ArrayList<WeakReference<MQTTMatoListener>>): Boolean{
        var sent = false
        for (wrD in responders)
            if (wrD.get() == null)
                responders.remove(wrD)
            else {
                wrD.get()!!.onUpdateReceived(currentPositions)
                sent = true
            }
        return sent
    }

    override fun connectionLost(cause: Throwable?) {
        Log.w(DEBUG_TAG, "Lost connection in MQTT Mato Client")


        synchronized(this){
           // isStarted = false
            //var i = 0
           // while(i < 20 && !isStarted) {
                connect(context!!, object: IMqttActionListener{
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        //relisten to messages
                        for ((line,elms) in respondersMap.entries){
                            val topic = mapTopic(line)
                            if(elms.isEmpty())
                                respondersMap.remove(line)
                            else
                                client.subscribe(topic, QoS.AtMostOnce.value, null, null)
                        }
                        Log.d(DEBUG_TAG, "Reconnected to MQTT Mato Client")

                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.w(DEBUG_TAG, "Failed to reconnect to MQTT server")
                    }
                })

            }


    }

    override fun messageArrived(topic: String?, message: MqttMessage?) {
        if (topic==null || message==null) return

        parseMessageAndAddToList(topic, message)
        //GlobalScope.launch { }

    }

    private fun parseMessageAndAddToList(topic: String, message: MqttMessage){

        val vals = topic.split("/")
        val lineId = vals[1]
        val vehicleId = vals[2]
        val timestamp = (System.currentTimeMillis() / 1000 ) as Long

        val  messString = String(message.payload)


        try {
            val jsonList = JSONArray(messString)
            //val full = if(jsonList.length()>7) {
            //    if (jsonList.get(7).equals(null)) null else jsonList.getInt(7)
            //}else null
            /*val posUpdate = MQTTPositionUpdate(lineId+"U", vehicleId,
                jsonList.getDouble(0),
                jsonList.getDouble(1),
                if(jsonList.get(2).equals(null)) null else jsonList.getInt(2),
                if(jsonList.get(3).equals(null)) null else jsonList.getInt(3),
                if(jsonList.get(4).equals(null)) null else jsonList.getString(4)+"U",
                if(jsonList.get(5).equals(null)) null else jsonList.getInt(5),
                if(jsonList.get(6).equals(null)) null else jsonList.getInt(6),
                //full
                )

             */
            if(jsonList.get(4)==null){
                Log.d(DEBUG_TAG, "We have null tripId: line $lineId veh $vehicleId: $jsonList")
                return
            }
            val posUpdate = LivePositionUpdate(
                jsonList.getString(4)+"U",
                null,
                null,
                lineId+"U",
                vehicleId,
                jsonList.getDouble(0), //latitude
                jsonList.getDouble(1), //longitude
                if(jsonList.get(2).equals(null)) null else jsonList.getInt(2).toFloat(), //"heading" (same as bearing?)
                timestamp,
                if(jsonList.get(6).equals(null)) null else jsonList.getInt(6).toString() //nextStop
            )

            //add update
            var valid = false
            if(!currentPositions.contains(lineId))
                currentPositions[lineId] = HashMap()
            currentPositions[lineId]?.let{
                it[vehicleId] = posUpdate
                valid = true
            }
            var sent = false
            if (LINES_ALL in respondersMap.keys) {
                sent = sendUpdateToResponders(respondersMap[LINES_ALL]!!)


            }
            if(lineId in respondersMap.keys){
                sent = sendUpdateToResponders(respondersMap[lineId]!!) or sent

            }
            if(!sent){
                Log.w(DEBUG_TAG, "We have received an update but apparently there is no one to send it")
                var emptyResp = true
                for(en in respondersMap.values){
                    if(!en.isEmpty()){
                        emptyResp=false
                        break
                    }
                }
                //try unsubscribing to all
                if(emptyResp) {
                    Log.d(DEBUG_TAG, "Unsubscribe all")
                    client.unsubscribe(LINES_ALL)
                }
            }
            //Log.d(DEBUG_TAG, "We have update on line $lineId, vehicle $vehicleId")
        } catch (e: JSONException){
            Log.e(DEBUG_TAG,"Cannot decipher message on topic $topic, line $lineId, veh $vehicleId")
            e.printStackTrace()
        }
    }


    override fun deliveryComplete(token: IMqttDeliveryToken?) {
        //NOT USED (we're not sending any messages)
    }


    companion object{

        const val SERVER_ADDR="wss://mapi.5t.torino.it:443/scre"
        const val LINES_ALL="ALL"
        private const val DEBUG_TAG="BusTO-MatoMQTT"
        @Volatile
        private var instance: MQTTMatoClient? = null

        fun getInstance() = instance?: synchronized(this){
            instance?: MQTTMatoClient().also { instance= it }
        }

        @JvmStatic
        fun mapTopic(lineId: String): String{
            return if(lineId== LINES_ALL || lineId == "#")
                "#"
            else{
                "/${lineId}/#"
            }
        }

        fun getRandomString(length: Int) : String {
            val allowedChars = ('a'..'f') + ('0'..'9')
            return (1..length)
                .map { allowedChars.random() }
                .joinToString("")
        }


        fun interface MQTTMatoListener{
            //positionsMap is a dict with line -> vehicle -> Update
            fun onUpdateReceived(posUpdates: PositionsMap)
        }
    }
}

data class MQTTPositionUpdate(
    val lineId: String,
    val vehicleId: String,
    val latitude: Double,
    val longitude: Double,
    val heading: Int?,
    val speed: Int?,
    val tripId: String?,
    val direct: Int?,
    val nextStop: Int?,
    //val full: Int?
)
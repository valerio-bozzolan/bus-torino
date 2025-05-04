package it.reyboz.bustorino.backend.mato

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import info.mqtt.android.service.Ack
import info.mqtt.android.service.MqttAndroidClient
import info.mqtt.android.service.QoS
import it.reyboz.bustorino.backend.Notifications
import it.reyboz.bustorino.backend.gtfs.LivePositionUpdate
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONArray
import org.json.JSONException
import java.lang.ref.WeakReference
import java.util.*

typealias PositionsMap = HashMap<String, HashMap<String, LivePositionUpdate> >

class MQTTMatoClient(): MqttCallbackExtended{

    private var isStarted = false
    private var subscribedToAll = false

    private var client: MqttAndroidClient? = null
    //private var clientID = ""

    private val respondersMap = HashMap<String, ArrayList<WeakReference<MQTTMatoListener>>>()

    private val currentPositions = PositionsMap()

    private lateinit var lifecycle: LifecycleOwner
    //TODO: remove class reference to context (always require context in all methods)
    private var context: Context?= null
    private var connectionTrials = 0
    private var notification: Notification? = null

    //private lateinit var notification: Notification

    private fun connect(context: Context, iMqttActionListener: IMqttActionListener?){

        val clientID = "mqtt-explorer-${getRandomString(8)}"//"mqttjs_${getRandomString(8)}"

        //notification = Notifications.makeMQTTServiceNotification(context)

        client = MqttAndroidClient(context,SERVER_ADDR,clientID,Ack.AUTO_ACK)
        // WE DO NOT WANT A FOREGROUND SERVICE -> it's only more mayhem
        // (and the positions need to be downloaded only when the app is shown)
        // update, 2024-04: Google Play doesn't understand our needs, so we put back the notification
        // and add a video of it working as Google wants
        /*if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            //we need a notification
            Notifications.createLivePositionsChannel(context)
            val notific = Notifications.makeMQTTServiceNotification(context)
            client!!.setForegroundService(notific)
            notification=notific
        }*/


        val options = MqttConnectOptions()
        //options.sslProperties =
        options.isCleanSession = true
        val headersPars = Properties()
        headersPars.setProperty("Origin","https://mato.muoversiatorino.it")
        headersPars.setProperty("Host","mapi.5t.torino.it")
        options.customWebSocketHeaders = headersPars

        Log.d(DEBUG_TAG,"client name: $clientID")
        //actually connect
        isStarted = true
        client!!.connect(options,null, iMqttActionListener)
        client!!.setCallback(this)

        if (this.context ==null)
            this.context = context.applicationContext
    }


    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
        Log.d(DEBUG_TAG, "Connected to server, reconnect: $reconnect")
        Log.d(DEBUG_TAG, "Have listeners: $respondersMap")
    }
    private fun connectTopic(topic: String){
        if(context==null){
            Log.e(DEBUG_TAG, "Trying to connect but context is null")
            return
        }
        connectionTrials += 1
        connect(context!!, object : IMqttActionListener{
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                val disconnectedBufferOptions = DisconnectedBufferOptions()
                disconnectedBufferOptions.isBufferEnabled = true
                disconnectedBufferOptions.bufferSize = 100
                disconnectedBufferOptions.isPersistBuffer = false
                disconnectedBufferOptions.isDeleteOldestMessages = false
                client!!.setBufferOpts(disconnectedBufferOptions)
                client!!.subscribe(topic, QoS.AtMostOnce.value)
                isStarted = true
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e(DEBUG_TAG, "FAILED To connect to the server",exception)
                if (connectionTrials < 10) {
                    Log.d(DEBUG_TAG, "Reconnecting")
                    connectTopic(topic)
                }
                else {
                    //reset connection trials
                    connectionTrials = 0
                }
            }

        })
    }

    fun startAndSubscribe(lineId: String, responder: MQTTMatoListener, context: Context): Boolean{
        //start the client, and then subscribe to the topic
        val topic = mapTopic(lineId)
        this.context = context.applicationContext
        synchronized(this) {
            if(!isStarted){

                connectTopic(topic)
                //wait for connection
            } else {
                client!!.subscribe(topic, QoS.AtMostOnce.value)
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

    fun stopMatoRequests(responder: MQTTMatoListener){
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
                client?.unsubscribe( mapTopic(line))
            }
            removed = done || removed
        }
        // check responders map, remove lines that have no responders

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

    /**
     * Cancel the notification
     */
    fun removeNotification(context: Context){
        val notifManager = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notifManager.cancel(MQTT_NOTIFICATION_ID)
    }

    private fun sendUpdateToResponders(responders: ArrayList<WeakReference<MQTTMatoListener>>): Int{
        //var sent = false
        var count = 0
        for (wrD in responders) {
            if (wrD.get() == null) {
                Log.d(DEBUG_TAG, "Removing weak reference")
                responders.remove(wrD)
            }
            else {
                wrD.get()!!.onUpdateReceived(currentPositions)
                //sent = true
                count++
            }
        }

        return count
    }

    override fun connectionLost(cause: Throwable?) {
        var doReconnect = false
        for ((line,elms) in respondersMap.entries){
            if(!elms.isEmpty()){
                doReconnect = true
                break
            }
        }
        if (!doReconnect){
            Log.d(DEBUG_TAG, "Disconnected, but no responders to give the positions, avoid reconnecting")
            //finish here
            return
        }
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
                            else {
                                client!!.subscribe(topic, QoS.AtMostOnce.value, null, null)
                                Log.d(DEBUG_TAG, "Resubscribed with topic $topic")
                            }
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
        //Log.d(DEBUG_TAG,"Arrived message on topic $topic, ${String(message.payload)}")
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
            //sending
            //Log.d(DEBUG_TAG, "Parsed update on topic $topic, line $lineId, responders $respondersMap")
            var cc = 0
            if (LINES_ALL in respondersMap.keys) {
                val count = sendUpdateToResponders(respondersMap[LINES_ALL]!!)
                cc +=count
            }

            if(lineId in respondersMap.keys){
                cc += sendUpdateToResponders(respondersMap[lineId]!!)

            }
            //Log.d(DEBUG_TAG, "Sent to $cc responders, have $respondersMap")
            if(cc==0){
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
                    client!!.unsubscribe(LINES_ALL)
                }
            }
            //Log.d(DEBUG_TAG, "We have update on line $lineId, vehicle $vehicleId")
        } catch (e: JSONException){
            Log.w(DEBUG_TAG,"Cannot decipher message on topic $topic, line $lineId, veh $vehicleId (bad JSON)")

        } catch (e: Exception){
            Log.e(DEBUG_TAG, "Exception occurred", e)
        }
    }


    override fun deliveryComplete(token: IMqttDeliveryToken?) {
        //NOT USED (we're not sending any messages)
    }

    /*/**
     * Stop the service forever. Client has not to be used again!!
     */
    fun closeClientForever(){
        client.disconnect()
        client.close()
    }*/

    fun disconnect(){
        client?.disconnect()
    }


    companion object{

        const val SERVER_ADDR="wss://mapi.5t.torino.it:443/scre"
        const val LINES_ALL="ALL"
        private const val DEBUG_TAG="BusTO-MatoMQTT"
        //this has to match the value in MQTT library (MQTTAndroidClient)
        const val MQTT_NOTIFICATION_ID: Int = 77


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
package it.reyboz.bustorino.backend.mato

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.lifecycle.MqttClientAutoReconnect
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import it.reyboz.bustorino.BuildConfig
import it.reyboz.bustorino.backend.LivePositionsServiceStatus
import it.reyboz.bustorino.backend.gtfs.LivePositionUpdate
import org.json.JSONArray
import org.json.JSONException
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.TimeUnit

typealias PositionsMap = HashMap<String, HashMap<String, LivePositionUpdate> >

class MQTTMatoClient(){

    private var isStarted = false
    //private var subscribedToAll = false

    private var client: Mqtt3AsyncClient? = null
    //private var clientID = ""

    private val respondersMap = HashMap<String, ArrayList<WeakReference<MQTTMatoListener>>>()

    private val currentPositions = PositionsMap()

    private lateinit var lifecycle: LifecycleOwner
    //TODO: remove class reference to context (always require context in all methods)
    private var context: Context?= null
    //private var connectionTrials = 0
    //private var notification: Notification? = null


    private fun connect(context: Context, onConnect: OnConnect){

        //val clientID = "mqtt-explorer-${getRandomString(8)}"//"mqttjs_${getRandomString(8)}"
        if (this.context ==null)
            this.context = context.applicationContext
        //notification = Notifications.makeMQTTServiceNotification(context)

        val mclient = makeClientBuilder().buildAsync()
        Log.d(DEBUG_TAG, "Connecting to MQTT server")
        //actually connect
        val mess = mclient.connect().whenComplete { subAck, throwable ->
            if(throwable!=null){
                Log.w(DEBUG_TAG,"Failed to connect to MQTT server:")
                Log.w(DEBUG_TAG,throwable.toString())
            }
            else{
                isStarted = true
                Log.d(DEBUG_TAG, "Connected to MQTT server")
                onConnect.onConnectSuccess()
            }
        }
        client = mclient
       /* if (mess.returnCode != Mqtt3ConnAckReturnCode.SUCCESS){
            Log.w(DEBUG_TAG,"Failed to connect to MQTT client")
            return false
        } else{
            client = blockingClient.toAsync()
            isStarted = true
            return true
        }

        */
    }


    /*override fun connectComplete(reconnect: Boolean, serverURI: String?) {
        Log.d(DEBUG_TAG, "Connected to server, reconnect: $reconnect")
        Log.d(DEBUG_TAG, "Have listeners: $respondersMap")
    }

     */
    private fun subscribeMQTTTopic(topic: String){
        if(context==null){
            Log.e(DEBUG_TAG, "Trying to connect but context is null")
            return
        }

        client!!.subscribeWith()
            .topicFilter(topic).callback {publish ->
                messageArrived(publish.topic.toString(), publish)
            }.send().whenComplete { subAck, throwable->
                if(throwable != null){
                    Log.e(DEBUG_TAG, "Error while subscribing to topic $topic", throwable)
                }
                else{
                    //add the responder to the list
                    Log.d(DEBUG_TAG, "Subscribed to topic $topic, responders ready: ${respondersMap[topic]}")
                }
            }

    }

    private fun subscribeTopicAddResponder(topic: String, responderWR: WeakReference<MQTTMatoListener>, lineId: String){
        if (!respondersMap.contains(lineId)){
            respondersMap[lineId] = ArrayList()
        }
        respondersMap[lineId]!!.add(responderWR)
        subscribeMQTTTopic(topic)
    }

    fun startAndSubscribe(lineId: String, responder: MQTTMatoListener, context: Context): Boolean{
        //start the client, and then subscribe to the topic
        val topic = mapTopic(lineId)
        val vrResp = WeakReference(responder)
        this.context = context.applicationContext

        synchronized(this) {
            if(!isStarted || (client == null)){

                connect(context.applicationContext) {
                    //when connection is done, run this
                    subscribeTopicAddResponder(topic, vrResp, lineId)

                }
                //wait for connection
            } else {
              subscribeTopicAddResponder(topic, vrResp, lineId)
            }
            //recheck if it is started
        }


        return true
    }

    fun stopMatoRequests(responder: MQTTMatoListener){
        var removed = false
        for ((lineTopic,responderList)in respondersMap.entries){
            var done = false
            for (el in responderList){
                if (el.get()==null){
                    responderList.remove(el)
                } else if(el.get() == responder){
                    responderList.remove(el)
                    done = true
                }
                if (done)
                    break
            }
            if(done) Log.d(DEBUG_TAG, "Removed one listener for topic $lineTopic, listeners: $responderList")
            //if (done) break
            if (responderList.isEmpty()){
                //actually unsubscribe
                try {
                    val topic = mapTopic(lineTopic)
                    client?.run{
                        unsubscribeWith().addTopicFilter(topic).send().whenComplete { subAck, throwable ->
                            if (throwable!=null){
                                //error occurred
                                Log.e(DEBUG_TAG, "Error while unsubscribing to topic $topic",throwable)
                            }
                        }
                    }
                } catch (e: Exception){
                    Log.e(DEBUG_TAG, "Tried unsubscribing but there was an error in the client library:\n$e")
                }
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
                val resp  = wrD.get()!!
                resp.onStatusUpdate(LivePositionsServiceStatus.OK)
                resp.onUpdateReceived(currentPositions)
                //sent = true
                count++
            }
        }

        return count
    }
    private fun sendStatusToResponders(status: LivePositionsServiceStatus){
        val responders = respondersMap
        for (els in respondersMap.values)
        for (wrD in els) {

            if (wrD.get() == null) {
                Log.d(DEBUG_TAG, "Removing weak reference")
                els.remove(wrD)
            }
            else {
                wrD.get()!!.onStatusUpdate(status)
                //sent = true
            }
        }

    }

    /*override fun connectionLost(cause: Throwable?) {
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

     */

    fun messageArrived(topic: String?, message: Mqtt3Publish) {
        if (topic==null) return
        //Log.d(DEBUG_TAG,"Arrived message on topic $topic, ${String(message.payload)}")
        parseMessageAndAddToList(topic, message)
        //GlobalScope.launch { }

    }

    private fun parseMessageAndAddToList(topic: String, message: Mqtt3Publish){

        //val mqttTopic = message.topic
        //Log.d(DEBUG_TAG, "Topic of message received: $mqttTopic")
        val vals = topic.split("/")
        val lineId = vals[1]
        val vehicleId = vals[2]
        val timestamp = makeUnixTimestamp()

        val  messString = String(message.payloadAsBytes)

        if(BuildConfig.DEBUG)
            Log.d(DEBUG_TAG, "Received message on topic: $topic")
        try {
            val jsonList = JSONArray(messString)

            if(jsonList.get(4)==null){
                Log.d(DEBUG_TAG, "We have null tripId: line $lineId veh $vehicleId: $jsonList")
                sendStatusToResponders(LivePositionsServiceStatus.NO_POSITIONS)
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
                    //client!!.unsubscribe(LINES_ALL)
                }
            }
            //Log.d(DEBUG_TAG, "We have update on line $lineId, vehicle $vehicleId")
        } catch (e: JSONException){
            Log.w(DEBUG_TAG,"Cannot decipher message on topic $topic, line $lineId, veh $vehicleId (bad JSON)")
            sendStatusToResponders(LivePositionsServiceStatus.ERROR_PARSING_RESPONSE)


        } catch (e: Exception){
            Log.e(DEBUG_TAG, "Exception occurred", e)
            sendStatusToResponders(LivePositionsServiceStatus.ERROR_PARSING_RESPONSE)
        }
    }

    /**
     * Remove positions older than `timeMins` minutes
     */
    fun clearOldPositions(timeMins: Int){
        val currentTimeStamp = makeUnixTimestamp()
        var c = 0
        for((k, manyp) in currentPositions.entries){
            for ((t, p) in manyp.entries){
                if (currentTimeStamp - p.timestamp > timeMins*60){
                    manyp.remove(t)
                    c+=1
                }
            }
        }
        Log.d(DEBUG_TAG, "Removed $c positions older than $timeMins minutes")
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

        const val SERVER_ADDR="mapi.5t.torino.it"
        const val SERVER_PATH="/scre"
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


        interface MQTTMatoListener{
            //positionsMap is a dict with line -> vehicle -> Update
            fun onUpdateReceived(posUpdates: PositionsMap)

            fun onStatusUpdate(status: LivePositionsServiceStatus)
        }
        fun getHTTPHeaders(): HashMap<String,String> {
            val headers = HashMap<String,String>()
            headers["Origin"] = "https://mato.muoversiatorino.it"
            headers["Host"] = "mapi.5t.torino.it"

            return headers
        }
        private fun makeClientBuilder() : Mqtt3ClientBuilder{
            val clientID = "mqtt-explorer-${getRandomString(8)}"

            val r =  MqttClient.builder()
                .useMqttVersion3()
                .identifier(clientID)
                .serverHost(SERVER_ADDR)
                .serverPort(443)
                .sslWithDefaultConfig()
                .automaticReconnect(MqttClientAutoReconnect.builder()
                  .initialDelay(500, TimeUnit.MILLISECONDS)
                 .maxDelay(60, TimeUnit.SECONDS).build())
                .webSocketConfig()
                    .httpHeaders(getHTTPHeaders())
                .serverPath("scre")
                    .applyWebSocketConfig()
            //.webSocketWithDefaultConfig()
            return r
        }
        private fun makeUnixTimestamp(): Long{
            val timestamp = (System.currentTimeMillis() / 1000 )
            return timestamp
        }


    }

    private fun interface OnConnect{
        fun onConnectSuccess()
    }
}

/*data class MQTTPositionUpdate(
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
)*/
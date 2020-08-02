package kamelong.com.diadata

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kamelong.com.tool.SQLiteHelper
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.PrintWriter
import java.util.*
import kotlin.collections.ArrayList

/**
 * 時刻表内の１つの駅
 */
class Station(val route: Route){
    //固有駅ID
    var stationID:UUID=UUID.randomUUID()
        private set
    //複数の駅が１つの複合駅を形成している時、stationBlockIDは共通化する
    var stationBlockID:UUID=UUID.randomUUID()
        private set
    //駅名
    var name:String=""

    //時刻表表示形式
    var showArrival= booleanArrayOf(false,false)
    var showDeparture= booleanArrayOf(true,true)
    var showTrack= booleanArrayOf(false,false)

    var showInfo:Int
    get(){
        var result=0
        if(showTrack[0]){
            result+=1
        }
        if(showDeparture[0]){
            result+=2
        }
        if(showArrival[0]){
            result+=4
        }
        if(showTrack[1]){
            result+=8
        }
        if(showDeparture[1]){
            result+=16
        }
        if(showArrival[1]){
            result+=32
        }
        return result
    }
    set(value:Int){
        var v=value
        showTrack[0]=(v%2==1)
        v=v/2
        showDeparture[0]=(v%2==1)
        v=v/2
        showArrival[0]=(v%2==1)
        v=v/2
        showTrack[1]=(v%2==1)
        v=v/2
        showDeparture[1]=(v%2==1)
        v=v/2
        showArrival[1]=(v%2==1)
    }

    //主要駅であるかどうか
    var mainStation:Boolean=false
    //特定駅の分岐駅である場合、分岐元の駅index
    //-1の時は分岐駅が存在しない
    var brunchStationIndex:Int=-1

    var border:Boolean=false

    //発着番線
    var track:ArrayList<Track> = ArrayList()
    var mainTrack:Array<Int> = arrayOf(0,1)

    val stationIndex:Int
    get()=route.stations.indexOf(this)
    companion object{
        fun createTableSQL():String{
            return "create table station(id int primary key,stationID text,routeID text,sequence int,stationBlockID text,name text,showInfo int,mainStation int,brunchStation int)"
        }
    }
    fun toSQL(sqLiteHelper: SQLiteHelper){
        val sql="insert into station(stationID,routeID,sequence,stationBlockID,name,showInfo,mainStation,brunchStation) values(?,?,?,?,?,?,?,?)"
        val statement=sqLiteHelper.getStatement(sql)
        statement.setString(1,stationID.toString())
        statement.setString(2,route.routeID.toString())
        statement.setInt(3,stationIndex)
        statement.setString(4,stationBlockID.toString())
        statement.setString(5,name)
        statement.setInt(6,showInfo)
        statement.setInt(7,if(mainStation){1}else{0})
        statement.setInt(8,brunchStationIndex)
        statement.executeUpdate()
    }


    //OuDia形式のデータを取り込む
    fun setOuDiaValue(key:String,value:String){
        when (key) {
            "Ekimei" -> name = value
            "stationID" -> stationID = UUID.fromString(value)
            "Ekijikokukeisiki" -> setTimeTableStyleOuDia(value)
            "Ekikibo" -> mainStation = value == "Ekikibo_Syuyou"
            "DownMain" -> {
                mainTrack[Direction.DOWN.ordinal] = value.toInt()
            }
            "UpMain" -> {
                mainTrack[Direction.UP.ordinal] = value.toInt()
            }
            "BrunchCoreEkiIndex" -> brunchStationIndex = Integer.valueOf(value)
            "JikokuhyouTrackDisplayKudari" -> showTrack[Direction.DOWN.ordinal] = value == "1"
            "JikokuhyouTrackDisplayNobori" -> showTrack[Direction.UP.ordinal] = value == "1"
            "JikokuhyouJikokuDisplayKudari" -> {
                showArrival[Direction.DOWN.ordinal] = value.split(",")[0] == "1"
                showDeparture[Direction.DOWN.ordinal] = value.split(",")[1] == "1"
            }
            "JikokuhyouJikokuDisplayNobori" -> {
                showArrival[1] = value.split(",").toTypedArray()[0] == "1"
                showDeparture[1] = value.split(",").toTypedArray()[1] == "1"
            }
            "Kyoukaisen" -> border = value == "1"
        }
    }
    fun saveAsOuDia(output:PrintWriter){
        output.write("Eki.\n")
        output.write("Ekimei=$name\n")
        output.write("Ekijikokukeisiki=${getTimeTableStyleOuDia()}\n")
        if(mainStation){
            output.write("Ekikibo=Ekikibo_Syuyou\n")
        }else{
            output.write("Ekikibo=Ekikibo_Ippan\n")
        }
        if(border){
            output.write("Kyoukaisen=1\n")

        }

    }
    fun toJSON():JsonObject{
        val json=JsonObject()
        json.addProperty("stationID",stationID.toString())
        json.addProperty("name",name)
        val showAriJson=JsonArray()
        showAriJson.add(showArrival[0])
        showAriJson.add(showArrival[1])
        json.add("showArrival",showAriJson)
        val showDepJson=JsonArray()
        showDepJson.add(showDeparture[0])
        showDepJson.add(showDeparture[1])
        json.add("showDeparture",showDepJson)
        val showTrackJson=JsonArray()
        showTrackJson.add(showTrack[0])
        showTrackJson.add(showTrack[1])
        json.add("showTrack",showTrackJson)
        json.addProperty("mainStation", mainStation)
        json.addProperty("brunchStation",brunchStationIndex)
        val mainTrackJson=JsonArray()
        mainTrackJson.add(mainTrack[0])
        mainTrackJson.add(mainTrack[1])
        json.add("mainTrack",mainTrackJson)
        val trackJson=JsonArray()
        for(track in this.track) {
            trackJson.add(track.toJSON())
        }
        json.add("track",trackJson)
        return json
    }
    fun fromJSON(json:JsonObject){
        stationID=UUID.fromString(json["stationID"].asString)
        stationBlockID=UUID.fromString(json["stationBlockID"].asString)
        name=json["station"].asString
        showArrival[0]=json["showArrival"].asJsonArray[0].asBoolean
        showArrival[1]=json["showArrival"].asJsonArray[1].asBoolean
        showDeparture[0]=json["showDeparture"].asJsonArray[0].asBoolean
        showDeparture[1]=json["showDeparture"].asJsonArray[1].asBoolean
        showTrack[0]=json["showTrack"].asJsonArray[0].asBoolean
        showTrack[1]=json["showTrack"].asJsonArray[1].asBoolean
        mainStation=json["mainStation"].asBoolean
        brunchStationIndex=json["brunchStation"].asInt
        mainTrack[0]=json["mainTrack"].asJsonArray[0].asInt
        mainTrack[1]=json["mainTrack"].asJsonArray[1].asInt
        track= ArrayList()
        for(trackJson in json["track"].asJsonArray){
            track.add(Track(this).apply { fromJSON(trackJson.asJsonObject) })
        }
    }

    private fun setTimeTableStyleOuDia(value:String){
        when (value) {
            "Jikokukeisiki_Hatsu" -> {
                showArrival[Direction.DOWN.ordinal] = false
                showArrival[Direction.UP.ordinal] = false
                showDeparture[Direction.DOWN.ordinal] = true
                showDeparture[Direction.UP.ordinal] = true
            }
            "Jikokukeisiki_Hatsuchaku" -> {
                showArrival[Direction.DOWN.ordinal] = true
                showArrival[Direction.UP.ordinal] = true
                showDeparture[0] = true
                showDeparture[Direction.UP.ordinal] = true
            }
            "Jikokukeisiki_NoboriChaku" -> {
                showArrival[Direction.DOWN.ordinal] = false
                showArrival[Direction.UP.ordinal] = true
                showDeparture[Direction.DOWN.ordinal] = true
                showDeparture[Direction.UP.ordinal] = false
            }
            "Jikokukeisiki_KudariChaku" -> {
                showArrival[Direction.DOWN.ordinal] = true
                showArrival[Direction.UP.ordinal] = false
                showDeparture[Direction.DOWN.ordinal] = false
                showDeparture[Direction.UP.ordinal] = true
            }
            "Jikokukeisiki_NoboriHatsuChaku" -> {
                showArrival[Direction.DOWN.ordinal] = false
                showArrival[Direction.UP.ordinal] = true
                showDeparture[Direction.DOWN.ordinal] = true
                showDeparture[Direction.UP.ordinal] = true
            }
            "Jikokukeisiki_KudariHatsuChaku" -> {
                showArrival[Direction.DOWN.ordinal] = true
                showArrival[Direction.UP.ordinal] = true
                showDeparture[Direction.DOWN.ordinal] = false
                showDeparture[Direction.UP.ordinal] = true
            }
            else -> {
                showArrival[Direction.DOWN.ordinal] = false
                showArrival[Direction.UP.ordinal] = false
                showDeparture[Direction.DOWN.ordinal] = true
                showDeparture[Direction.UP.ordinal] = true
            }
        }

    }
    private fun getTimeTableStyleOuDia():String{
        var result = 0
        if (showArrival[1]) {
            result += 8
        }
        if (showDeparture[1]) {
            result += 4
        }
        if (showArrival[0]) {
            result += 2
        }
        if (showDeparture[0]) {
            result += 1
        }
        return when (result) {
            5 -> "Jikokukeisiki_Hatsu"
            15 -> "Jikokukeisiki_Hatsuchaku"
            6 -> "Jikokukeisiki_KudariChaku"
            9 -> "Jikokukeisiki_NoboriChaku"
            13 -> "Jikokukeisiki_NoboriHatsuChaku"
            7 -> "Jikokukeisiki_KudariHatsuChaku"
            else -> "Jikokukeisiki_Hatsu"
        }

    }
    fun toXml(docuemnt:Document): Element {
        val stationDom=docuemnt.createElement("駅明細")
        stationDom.appendChild(docuemnt.createElement("駅名").apply { textContent=name })
        stationDom.appendChild(docuemnt.createElement("距離").apply {textContent=(route.stations.indexOf(this@Station)).toString()})
        stationDom.appendChild(docuemnt.createElement("到着").apply {textContent=
            if(showArrival[0]){
                "True"
            }else{
                "False"
            }
        })
        stationDom.appendChild(docuemnt.createElement("出発").apply {textContent=
            if(showDeparture[0]){
                "True"
            }else{
                "False"
            }
        })
        stationDom.appendChild(docuemnt.createElement("線種").apply { textContent="1" })
        stationDom.appendChild(docuemnt.createElement("駅種別").apply { textContent="0" })
        stationDom.appendChild(docuemnt.createElement("駅描画のオフセット").apply { textContent="0" })
        stationDom.appendChild(docuemnt.createElement("配線図のリンク先"))
        return stationDom
    }

}
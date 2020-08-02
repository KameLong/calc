package kamelong.com.diadata

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kamelong.com.tool.SQLiteHelper
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.PrintWriter
import java.sql.ResultSet
import java.util.*
import kotlin.collections.ArrayList


class Train(val route: Route, val diagram:Diagram){
    var trainID:UUID= UUID.randomUUID()
        private set

    /**
     * 複数列車を同一列車とするとき、BlockIDは同一となる
     */
    var trainBlockID:UUID=UUID.randomUUID()
        private set(value){
            route.diaData.trainBlocks[field]?.trainList?.remove(this)
            field=value
            route.diaData.trainBlocks[value]?.trainList?.add(this)
        }

    //方向
    var direction:Direction=Direction.DOWN
    //列車種別
    var type:Int=0

    //列車番号
    var number:String=""
    //列車名
    var name:String=""
    var count:String=""
    var remark:String=""

    var stationTimes:ArrayList<StationTime> = ArrayList<StationTime>()

    /**
     * 駅数
     */
    val stationCount:Int
        get() = route.stationCount

    val trainType:TrainType
        get()=route.types[type]

    /**
     * この列車が空かどうか
     */
    val isNull:Boolean
        get(){
            for (stationTime in stationTimes) {
                if (stationTime.stopType != StopType.NONE){
                    return false
                }
            }
            return true
        }
    /**
     * 列車の始発駅のindex
     * この列車がisNullの時は-1が返る
     */
    val startStation:Int
        get() {
            for (stationIndex in if (direction == Direction.DOWN) {
                0 until stationCount
            } else {
                stationCount - 1 downTo 0
            }) {
                when (stationTimes[stationIndex].stopType) {
                    StopType.STOP->return stationIndex
                    StopType.PASS->return stationIndex
                }
            }
            return -1
        }
    /**
     * 列車の終着駅のindex
     * この列車がisNullの時は-1が返る
     */
    val endStation:Int
        get() {
            for (stationIndex in if (direction == Direction.DOWN) {
                stationCount - 1 downTo 0
            } else {
                0 until stationCount
            }) {
                when (stationTimes[stationIndex].stopType) {
                    StopType.STOP->return stationIndex
                    StopType.PASS->return stationIndex
                }
            }
            return -1
        }

    /**
     * 時刻が存在する始発駅を調べる
     * 時刻が存在しないとき-1を返す
     */
    val timeStartStation:Int
        get() {
            for (stationIndex in if (direction == Direction.DOWN) {
                0 until stationCount
            } else {
                stationCount - 1 downTo 0
            }) {
                if(stationTimes[stationIndex].timeExist()){
                    return stationIndex
                }
            }
            return -1
    }    /**
     * 時刻が存在する終着駅を調べる
     * 時刻が存在しないとき-1を返す
     */
    val timeEndStation:Int
        get() {
            for (stationIndex in if (direction == Direction.DOWN) {
                stationCount - 1 downTo 0
            } else {
                0 until stationCount
            }) {
                if(stationTimes[stationIndex].timeExist()){
                    return stationIndex
                }
            }
            return -1
        }

    /**
     * 列車順の番号
     */
    val trainIndex:Int
        get()=diagram.trains[direction.ordinal].indexOf(this)

    /**
     * 運用
     */
    var operationItem:ArrayList<OperationItem> = ArrayList()

    /**
     * 初期設定
     */
    init{
        operationItem.add(OperationItem(Operation(route.diaData),this))
    }


    /**
     * 当駅始発にする
     */
    fun startAtTheStation(stationIndex:Int){
        if(direction==Direction.DOWN){
            for(i in 0 until stationIndex){
                stationTimes[i].reset()
            }
        }else{
            for(i in stationIndex+1 until stationCount){
                stationTimes[i].reset()
            }
        }
        stationTimes[stationIndex].ariTime.time=-1
    }
    /**
     * 当駅止めにする
     */
    fun endAtTheStation(stationIndex:Int){
        if(direction==Direction.DOWN){
            for(i in stationIndex+1 until stationCount){
                stationTimes[i].reset()
            }
        }else{
            for(i in 0 until stationIndex){
                stationTimes[i].reset()
            }
        }
        stationTimes[stationIndex].depTime.time=-1
    }
    /**
     * 列車を結合する
     * 結合駅の出発時刻はotherのものを用いる
     * @param other
     */
    fun combineTrain(other:Train){
        if (direction == Direction.DOWN) {
            for (i in startStation + 1 until stationCount) {
                stationTimes.set(i, other.stationTimes[i].clone(this))
            }
        } else {
            for (i in 0 until startStation) {
                stationTimes.set(i, other.stationTimes[i].clone(this))
            }
        }
        if (other.startStation == endStation) {
            this.stationTimes[endStation].depTime=other.stationTimes[endStation].depTime
        } else {
            for (i in startStation + 1 until endStation) {
                stationTimes[i].stopType=StopType.NOVIA
            }
        }
    }

    /**
     * 2駅間の所要時間を返す。もし片方の駅に時刻がなければ-1を返す
     */
    fun reqTime(station1: Int, station2: Int): Int {
        return if (stationTimes[station1].timeExist() && stationTimes[station2].timeExist()) {
            if ((1 - direction.ordinal * 2) * (station2 - station1) > 0) {
                stationTimes[station2].getTime(1, true) - stationTimes[station1].getTime(0, true)
            } else {
                stationTimes[station1].getTime(1, true) - stationTimes[station2].getTime(0, true)
            }
        } else -1
    }

    //時刻表順のindexをダイヤデータのindexに変更する
    fun getStationIndex(timeTableIndex:Int):Int{
        if(timeTableIndex<0){
            throw ArrayIndexOutOfBoundsException("timetableIndex=$timeTableIndex")
        }
        if(timeTableIndex>=route.stationCount){
            throw ArrayIndexOutOfBoundsException("timetableIndex=$timeTableIndex")
        }
        if(direction==Direction.DOWN){
            return timeTableIndex
        }else{
            return route.stationCount-timeTableIndex-1
        }
    }


    /**
     * 列車の通過予想時刻
     * この駅を列車が通過しないと判断したら-1が返る
     */
    fun getPredictionTime(station: Int): Int {
        return getPredictionTime(station,0)
    }

    /**
     * 列車の通過予想時刻
     * AD=1の時、着時刻が存在する場合は着時刻っを優先する
     */
    fun getPredictionTime(station: Int, AD: Int): Int {
        val sTime=stationTimes[station]
        if (sTime.stopType == StopType.NOVIA || sTime.stopType ==StopType.NONE) {
            return -1
        }
        if (AD == 1 && sTime.timeExist(1)) {
            return sTime.ariTime.time
        }
        if (sTime.timeExist()) {
            return sTime.getTime( AD, true)
        }
        if (sTime.stopType == StopType.PASS) {
            //通過時間を予測します
            var afterTime = -1 //後方の時刻あり駅の発車時間
            var beforeTime = -1 //後方の時刻あり駅の発車時間
            var afterMinTime = 0 //後方の時刻あり駅までの最小時間
            var beforeMinTime = 0 //前方の時刻あり駅までの最小時間
            val minstationTime: MutableList<Int> = route.getStationTime()

            //対象駅より先の駅で駅時刻が存在する駅までの最小所要時間
            for (i in station + 1 until route.stationCount) {
                if (stationTimes[i].stopType == StopType.NONE || stationTimes[i].stopType == StopType.NOVIA || stationTimes[i-1].stopType == StopType.NONE || stationTimes[i-1].stopType == StopType.NOVIA) {
                    continue
                }
                afterMinTime = afterMinTime + minstationTime[i] - minstationTime[i - 1]
                if (stationTimes[i].timeExist()) {
                    if (direction == Direction.UP) {
                        afterTime = stationTimes[i].getTime(1, true)
                    } else {
                        afterTime = stationTimes[i].getTime(0, true)
                    }
                    break
                }
            }
            if (afterTime < 0) {
                //対象駅より先の駅で駅時刻が存在する駅がなかった
                return -1
            }
            //対象駅より前方の駅で駅時刻が存在する駅までの最小所要時間と駅時刻
            var startStation = 0
            for (i in station downTo 1) {
                if (stationTimes[i].stopType == StopType.NONE || stationTimes[i].stopType == StopType.NOVIA || stationTimes[i-1].stopType == StopType.NONE || stationTimes[i-1].stopType == StopType.NOVIA) {
                    continue
                }
                beforeMinTime = beforeMinTime + minstationTime[i] - minstationTime[i - 1]
                if (stationTimes[i-1].timeExist()) {
                    if (direction == Direction.UP) {
                        beforeTime = stationTimes[i].getTime(0, true)
                    } else {
                        beforeTime = stationTimes[i].getTime(1, true)
                    }
                    startStation = i - 1
                    break
                }
            }
            return if (beforeTime < 0) {
                -1
            } else stationTimes[startStation].depTime.time + (afterTime - beforeTime) * beforeMinTime / (afterMinTime + beforeMinTime)
        }
        return -1
    }


    //OuDia形式のデータを取り込む
    fun setOuDiaValue(key:String,value:String) {
        when (key) {
            "trainID"->trainID=UUID.fromString(value)
            "trainBlockID"->trainBlockID=UUID.fromString(value)
            "Syubetsu" -> type = value.toInt()
            "Ressyabangou" -> number = value
            "Ressyamei" -> name = value
            "Gousuu" -> count = value
            "EkiJikoku" -> setOuDiaTime(value.split(","))
            "RessyaTrack" -> setOuDiaTrack(value.split(","))
            "Bikou" -> remark = value
        }

    }
    fun saveAsOuDia(out: PrintWriter) {
        out.println("Ressya.")
        out.println("trainID=${trainID}")
        out.println("trainBlockID=${trainBlockID}")
        if (direction == Direction.DOWN) {
            out.println("Houkou=Kudari")
        } else {
            out.println("Houkou=Nobori")
        }
        out.println("Syubetsu=$type")
        if (number.length > 0) {
            out.println("Ressyabangou=$number")
        }
        if (name.length > 0) {
            out.println("Ressyamei=$name")
        }
        if (count.length > 0) {
            out.println("Gousuu=$count")
        }
        out.println("EkiJikoku=" + getEkijikokuOudia(false))
        if (remark.length > 0) {
            out.println("Bikou=$remark")
        }
        out.println(".")
    }

    private fun setOuDiaTime(values:List<String>){
        stationTimes = ArrayList()
        for (i in 0 until route.stationCount) {
            stationTimes.add(StationTime(this))
        }
        var i = 0
        while (i < values.size && i < route.stationCount) {
            stationTimes[getStationIndex(i)].setOuDiaTimeValue(values[i])
            i++
        }

    }
    private fun setOuDiaTrack(value:List<String>) {
        var i = 0
        while (i < value.size && i < route.stationCount) {
            stationTimes[getStationIndex(i)].setOuDiaTrackValue(value[i])
            i++
        }
    }
    /**
     * OuDia形式の駅時刻行を作成します。
     * @param secondFrag trueの時oudia2nd形式に対応します。
     * @return
     */
    private fun getEkijikokuOudia(secondFrag: Boolean): String? {
        val result = StringBuilder()
        if (stationTimes.size > route.stationCount) {
            println("駅数オーバーフロー")
            return ""
        }
        for (i in 0 until stationTimes.size) {
            val stationIndex: Int = getStationIndex(i)
            result.append(stationTimes[stationIndex].getOuDiaString(secondFrag))
            result.append(",")
        }
        return result.toString()
    }

    fun toJSON():JsonObject{
        val json=JsonObject()
        json.addProperty("trainID",trainID.toString())
        json.addProperty("trainBlockID",trainBlockID.toString())
        json.addProperty("direction",direction.ordinal)
        json.addProperty("type",type)
        json.addProperty("number",number)
        json.addProperty("name",name)
        json.addProperty("count",count)
        json.addProperty("remark",remark)
        val operationJson=JsonArray()
        for(item in operationItem){
            operationJson.add(item.toJSON())
        }
        json.add("operationItems",operationJson)
        val stationTimeJson=JsonArray()
        for (sTime in stationTimes){
            stationTimeJson.add(sTime.toJSON())
        }
        json.add("stationTime",stationTimeJson)
        return json
    }
    fun fromJSON(json:JsonObject){
        trainID=UUID.fromString(json.get("trainID").asString)
        trainBlockID=UUID.fromString(json.get("trainBlockID").asString)
        direction=Direction.values()[json.get("direction").asInt]
        type=json.get("type").asInt
        number=json.get("number").asString
        name=json.get("name").asString
        count=json.get("count").asString
        remark=json.get("remark").asString
        for(operationJson in json.getAsJsonArray("operationItems")){
            operationItem.add(OperationItem(route.diaData.getOperation(UUID.fromString(operationJson.asJsonObject.get("operationID").asString)),this).apply { this.fromJSON(operationJson.asJsonObject) })
        }
        stationTimes= ArrayList()
        for(stationTimeJson in json.getAsJsonArray("stationTime")){
            stationTimes.add(StationTime(this).apply { this.fromJSON(stationTimeJson.asJsonObject) })
        }

    }
    fun toSQL(sqLiteHelper: SQLiteHelper){
        val sql="insert into train (trainID,diagramID,sequence,direction,type,number,name,count,remark) values(?,?,?,?,?,?,?,?,?)"
        val statement=sqLiteHelper.getStatement(sql)
        statement.setString(1,trainID.toString())
        statement.setString(2,diagram.diagramID.toString())
        statement.setInt(3,diagram.trains[direction.ordinal].indexOf(this))
        statement.setInt(4,direction.ordinal)
        statement.setInt(5,type)
        statement.setString(6,number)
        statement.setString(7,name)
        statement.setString(8,count)
        statement.setString(9,remark)
        statement.executeUpdate()
        for(time in stationTimes){
            time.toSQL(sqLiteHelper)
        }
        for(item in operationItem){
            item.toSQL(sqLiteHelper)
        }
    }
    fun fromSQL(rs:ResultSet,diagramMap:HashMap<UUID,Diagram>){
        trainID=UUID.fromString(rs.getString("trainID"))
        direction= Direction.values()[rs.getInt("direction")]
        type=rs.getInt("type")
        number=rs.getString("number")
        name=rs.getString("name")
        count=rs.getString("count")
        remark=rs.getString("remark")
    }
    fun saveAsXml(document: Document):Element{
        val trainDom=document.createElement("列車明細")
        trainDom.appendChild(document.createElement("列車番号").apply { textContent=trainID.toString() })
        trainDom.appendChild(document.createElement("列車名").apply { textContent=name })
        trainDom.appendChild(document.createElement("列車号番号").apply {
            if(count.length==0){
                textContent="0"
            }else{
                textContent=count
            }
        })
        trainDom.appendChild(document.createElement("列車種別").apply { textContent=type.toString() })
        trainDom.appendChild(document.createElement("動力種別").apply { textContent="2" })
        trainDom.appendChild(document.createElement("輸送種別").apply { textContent="2" })
        trainDom.appendChild(document.createElement("運転日").apply { textContent=diagram.route.diagrams.indexOf(diagram).toString() })
        trainDom.appendChild(document.createElement("他線へ直通_起点側").apply { textContent="False" })
        trainDom.appendChild(document.createElement("他線へ直通_起点側_反転").apply { textContent="False" })
        trainDom.appendChild(document.createElement("他線へ直通_終点側").apply { textContent="False" })
        trainDom.appendChild(document.createElement("他線へ直通_終点側_反転").apply { textContent="False" })
        trainDom.appendChild(document.createElement("他線へ直通_中間部").apply { textContent="False" })
        trainDom.appendChild(document.createElement("時刻表要素").apply {
            if(direction==Direction.DOWN){
                for(time in stationTimes){
                    if(!time.isNull) {
                        appendChild(time.saveAsXml(document))
                    }
                }

            }
            else{
                for(time in stationTimes.reversed()){
                    if(!time.isNull) {
                        appendChild(time.saveAsXml(document))
                    }
                }
            }


        })

        return trainDom
    }


    companion object {
        fun createTableSQL(): String {
            return "create table train(id int primary key,trainID text,diagramID text,sequence int,direction int,type int,number tet,name text,count text,remark text)"
        }
    }

}
enum class Direction(){
    DOWN,
    UP
}
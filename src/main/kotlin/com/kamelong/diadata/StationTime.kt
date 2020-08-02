package kamelong.com.diadata

import com.google.gson.JsonObject
import com.kamelong.tool.SDlog
import kamelong.com.tool.SQLiteHelper
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.sql.ResultSet

class StationTime(@Transient val train:Train) {
    //着時刻、秒単位
    var depTime: Time = Time(train.route)
    //発時刻、秒単位
    var ariTime: Time =Time(train.route)

    var stopType: StopType = StopType.NONE
    //発着番線　-1の時はデフォルト
    var stopTrack: Int = -1


    /**
     * 時刻が存在するかどうか
     */
    fun timeExist():Boolean{
        return !(depTime.isNull()&&ariTime.isNull())
    }
    /**
     * 時刻が存在するかどうか
     */
    fun timeExist(AD:Int):Boolean{
        return when(AD){
            0->!depTime.isNull()
            1->!ariTime.isNull()
            else->timeExist()
        }
    }
    /**
     * 時刻取得(発時刻、着時刻別）
     * useOther=trueの時、該当時刻が存在しないとき、代わりに同一駅の（発時刻、着時刻)を使用する。
     * 両方ともないときは-1
     */
    fun getTime(AD: Int, useOther: Boolean): Int {
        if(AD==0){
            if(depTime.isNull()&&useOther){
                return ariTime.time
            }
            return depTime.time
        }else{
            if(ariTime.isNull()&&useOther){
                return depTime.time
            }
            return ariTime.time
        }
    }

    /**
     * 発着時刻をリセットする
     */
    fun reset(){
        ariTime.time=-1
        depTime.time=-1
        stopType=StopType.NONE
        stopTrack=-1
    }

    fun clone(mTrain:Train):StationTime{
        return StationTime(mTrain).apply {
            this.ariTime=this@StationTime.ariTime.clone(mTrain.route)
            this.depTime=this@StationTime.depTime.clone(mTrain.route)
            this.stopTrack=this@StationTime.stopTrack
            this.stopType=this@StationTime.stopType

        }
    }

    /**
     * OuDia形式の時刻情報を格納する
     */
    fun setOuDiaTimeValue(value: String) {
        if (value.length == 0) {
            stopType = StopType.NONE
            return
        }
        if (!value.contains(";")) {
            stopType = StopType.values()[value.toInt()]
            return
        }
        stopType = StopType.values()[value.split(";")[0].toInt()]
        val timeValue = value.split(";")[1]
        if (value.contains("/")) {
            ariTime.fromOuDiaString(timeValue.split("/")[0])
            if (value.split("/")[1].length != 0) {
                depTime.fromOuDiaString(timeValue.split("/")[1])
            }
        } else {
            depTime.fromOuDiaString(timeValue)
        }
    }
    /**
     * OuDia形式の番線情報を格納する
     */
    fun setOuDiaTrackValue(value: String) {
        var value = value
        try {
            if (value.length == 0) {
                stopTrack = -1
                return
            }
            if (value.contains(";")) {
                value = value.split(";")[0]
            }
            stopTrack = value.toInt() - 1
        } catch (e: Exception) {
            stopTrack = -1
            SDlog.log(e)
        }

    }

    fun toJSON():JsonObject{
        val json=JsonObject()
        json.addProperty("a",ariTime.time)
        json.addProperty("d",depTime.time)
        json.addProperty("ty",stopType.ordinal-1)
        json.addProperty("tr",stopTrack)
        return json
    }
    fun fromJSON(json:JsonObject){
        ariTime.time=json["a"].asInt
        depTime.time=json["d"].asInt
        stopType= StopType.values()[json["ty"].asInt]
        stopTrack=json["tr"].asInt
    }
    fun toSQL(sqLiteHelper: SQLiteHelper){
        if(isNull){
            return
        }
        val sql="insert into stop_time (trainID,sequence,ariTime,depTime,stopType,stopTrack) values(?,?,?,?,?,?)"
        val statement=sqLiteHelper.getStatement(sql)
        statement.setString(1,train.trainID.toString())
        statement.setInt(2,train.stationTimes.indexOf(this))
        statement.setInt(3,ariTime.time)
        statement.setInt(4,depTime.time)
        statement.setInt(5,stopType.ordinal-1)
        statement.setInt(6,stopTrack)
        statement.executeUpdate()
    }
    companion object{
        fun createTableSQL():String{
            return "create table stop_time(id int primary key,trainID text,sequence int,ariTime int,depTime int,stopType int,stopTrack int)"
        }
    }
    fun fromSQL(rs:ResultSet){
        ariTime.time=rs.getInt("ariTime")
        depTime.time=rs.getInt("depTime")
        stopType= StopType.values()[rs.getInt("stopType")]
        stopTrack=rs.getInt("stopTrack")

    }
    fun getOuDiaString(oudia2ndFrag: Boolean): String {
        var result: String = ""
        if (stopType == StopType.NONE) {
            return result
        }
        result += stopType.ordinal
        if(ariTime.isNull()&&depTime.isNull()){
            return result
        }
        if (ariTime.isNull()) {
            result += ariTime.toOuDiaString() + "/"
        }
        if (depTime.isNull()) {
            result += depTime.toOuDiaString()
        }
        if (oudia2ndFrag) {
            result += "$" + stopTrack
        }
        return result
    }
    fun saveAsXml(document:Document):Element{
        val timeDom=document.createElement("時刻明細")
        timeDom.appendChild(document.createElement("駅名").apply { textContent=train.diagram.route.stations[train.stationTimes.indexOf(this@StationTime)].name })
        timeDom.appendChild(document.createElement("発車時刻").apply {
            if(depTime.isNull()){
                textContent=ariTime.toXmlString()

            }else{
                textContent=depTime.toXmlString()

            }
        })
        timeDom.appendChild(document.createElement("停車時間").apply { textContent=Time.minus(depTime,ariTime).toXmlString2() })
        timeDom.appendChild(document.createElement("停車種類").apply {
            textContent=
                when(stopType){
                    StopType.NONE->"0"
                    StopType.STOP->"0"
                    StopType.PASS->"2"
                    StopType.NOVIA->"2"
            } })
        return timeDom
    }
    val isNull:Boolean
    get(){
        return stopType==StopType.NONE
    }


}
enum class StopType{
    NONE,STOP,PASS,NOVIA
}

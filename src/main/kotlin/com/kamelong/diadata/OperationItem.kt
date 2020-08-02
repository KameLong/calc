package kamelong.com.diadata

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kamelong.com.tool.SQLiteHelper
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.util.*

/**
 * 運用のうち、1列車に対応するクラス
 * 複数のOperationItemのリストが一連の運用順となる
 * 運用順はOperation
 */
class OperationItem (var operation:Operation,var train:Train){
    var id:UUID= UUID.randomUUID()
    /**
     * 運用開始駅
     * -1の時はtrainの始発駅
     */
    var startStation:Int=-1
    val actualStartStation:Int
        get(){
            if(startStation<0){
                return train.startStation
            }
            return startStation
        }
    /**
     * 運用終了駅
     * -1の時はtrainの終着駅
     */
    var endStation:Int=-1
    val actualEndStation:Int
        get(){
            if(endStation<0){
                return train.endStation
            }
            return endStation
        }
    val route:Route
        get()=train.route


    /**
     * 初期設定
     */
    init{
        route.diaData.operationItems[this.id]=this
        operation.addItem(this.id)
    }

    fun changeOperation(newOperation:Operation){
        operation.removeItem(this.id)
        operation=newOperation
        operation.addItem(this.id)
    }


    fun toJSON(): JsonObject {
        val json= JsonObject()
        json.addProperty("operationItemID",id.toString())
        json.addProperty("startStation",startStation)
        json.addProperty("endStation",endStation)
        return json
    }
    fun fromJSON(json:JsonObject){
        id=UUID.fromString(json["operationItemID"].asString)
        startStation=json["startStation"].asInt
        endStation=json["endStation"].asInt
    }
    fun toSQL(sqLiteHelper: SQLiteHelper){
        val sql="insert into operationItem (operationItemID,trainID,startStation,endStation) values(?,?,?,?)"
        val statement=sqLiteHelper.getStatement(sql)
        statement.setString(1,id.toString())
        statement.setString(2,train.trainID.toString())
        statement.setInt(3,startStation)
        statement.setInt(4,endStation)
        statement.executeUpdate()
    }

    companion object{
        fun createTableSQL():String{
            return "create table operationItem(id int primary key,operationItemID text,trainID text,startStation int,endStation int)"
        }

    }

}
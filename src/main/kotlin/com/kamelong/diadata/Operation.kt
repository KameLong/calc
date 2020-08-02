package kamelong.com.diadata

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kamelong.com.tool.SQLiteHelper
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.sql.ResultSet
import java.util.*
import kotlin.collections.ArrayList

/**
 * 運用を表すクラス
 * このクラスは複数のLineFileにまたがることができる。
 */
class Operation(val diaData: DiaData){
    constructor(diaData:DiaData,rs:ResultSet):this(diaData){
        operationID=UUID.fromString(rs.getString("operationID"))
        name=rs.getString("name")
        vehicleName=rs.getString("vehicleName")
        vehicleNumber=rs.getInt("vehicleNumber")
    }
    var operationID:UUID= UUID.randomUUID()
    /**
     * 運用名
     */
    var name:String=""
    /**
     * 車両名
     */
    var vehicleName:String=""
    /**
     * 両数
     */
    var vehicleNumber:Int=0

    /**
     * 運用要素リスト
     */
    var operationItems:ArrayList<UUID> = ArrayList()

    init{
        if(operationID in diaData.operations.keys){

        }else{
            diaData.operations[operationID]=this
        }
    }

    /**
     * この運用から運用要素を削除する
     */
    fun removeItem(itemID:UUID){
        operationItems.remove(itemID)
        if(operationItems.size==0){
            diaData.operations.remove(this.operationID)
        }
    }

    /**
     * この運用に運用要素を追加する
     */
    fun addItem(itemID:UUID){
        if(itemID in operationItems){

        }else{
            operationItems.add(itemID)
        }
    }

    fun conbineOperation(item:OperationItem){
        item.operation.operationItems.remove(item.id)
        operationItems.add(item.id)
        item.operation=this
        
    }
    fun toJSON(): JsonObject {
        val json= JsonObject()
        json.addProperty("operationID",operationID.toString())
        json.addProperty("name",name)
        json.addProperty("vehicleName",vehicleName)
        json.addProperty("vehicleNumber",vehicleNumber)
        val items=JsonArray()
        for( item in operationItems){
            items.add(item.toString())
        }
        json.add("operationItemIDs",items)
        return json
    }
    fun fromJSON(json:JsonObject){
        operationID=UUID.fromString(json["operationID"].asString)
        name=json["name"].asString
        vehicleName=json["vehicleName"].asString
        vehicleNumber=json["vehicleNumber"].asInt
    }
    fun toSQL(sqLiteHelper: SQLiteHelper){
        val sql="insert into operation (operationID,name,vehicleName,vehicleNumber) values(?,?,?,?)"
        val statement=sqLiteHelper.getStatement(sql)
        statement.setString(1,operationID.toString())
        statement.setString(2,name)
        statement.setString(3,vehicleName)
        statement.setInt(4,vehicleNumber)
        statement.executeUpdate()
        for(item in operationItems){
            val sql2="insert into operationItemList (operationID,operationItemID,sequence) values(?,?,?)"
            val statement2=sqLiteHelper.getStatement(sql2)
            statement2.setString(1,operationID.toString())
            statement2.setString(2,item.toString())
            statement2.setInt(3,operationItems.indexOf(item))
            statement2.executeUpdate()

        }
    }
    companion object{
        fun createTableSQL():String{
            return "create table operation(id int primary key,operationID text,name text,vehicleName text,vehicleNumber int)"
        }
        fun createOperationItemListSQL():String{
            return "create table operationItemList(id int primary key,operationID text,operationItemID text,sequence int)"
        }

        fun fromSQL(sqLiteHelper: SQLiteHelper,diaData: DiaData){
            val sql="select * from operation"
            val statement=sqLiteHelper.getStatement(sql)
            val rs: ResultSet =statement.executeQuery()
            while(rs.next()){
                Operation(diaData,rs)
            }

            val sql2="select * from operationItemList order by sequence"
            val statement2=sqLiteHelper.getStatement(sql2)
            val rs2: ResultSet =statement2.executeQuery()
            while(rs2.next()){
                diaData.getOperation(UUID.fromString(rs2.getString("operationID"))).addItem(UUID.fromString(rs2.getString("operationItemID")))
            }

        }

    }


}
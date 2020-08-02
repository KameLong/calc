package kamelong.com.diadata

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kamelong.com.tool.SQLiteHelper
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.PrintWriter
import java.sql.ResultSet
import java.util.*

/**
 * 時刻表の「平日」とか「土日」別のデータ
 */
class Diagram(@Transient val route:Route){
    var diagramID:UUID= UUID.randomUUID()

    var name:String=""
    var trains:Array<MutableList<Train>> = arrayOf(mutableListOf(), mutableListOf())
    //OuDia形式のデータを取り込む
    fun setOuDiaValue(key:String,value:String) {
        when (key) {
            "DiaName" -> name = value
            "diagramID"->diagramID=UUID.fromString(value)
        }

    }
    /**
     * OuDia形式で出力します
     */
    fun toOuDia(out: PrintWriter) {
        out.println("Dia.")
        out.println("DiaName=$name")
        out.println("diagramID=$diagramID")
        out.println("Kudari.")
        for (t in trains[0]) {
            t.saveAsOuDia(out)
        }
        out.println(".")
        out.println("Nobori.")
        for (t in trains[1]) {
            t.saveAsOuDia(out)
        }
        out.println(".")
        out.println(".")
    }
    fun toJSON():JsonObject{
        val json=JsonObject()
        json.addProperty("diagramID",diagramID.toString())
        json.addProperty("name",name)
        val trainJson=JsonObject()
        val downTrainJson=JsonArray()
        val upTrainJson=JsonArray()
        for(train in trains[Direction.DOWN.ordinal]){
            downTrainJson.add(train.toJSON())
        }
        for(train in trains[Direction.UP.ordinal]){
            upTrainJson.add(train.toJSON())
        }
        trainJson.add("down",downTrainJson)
        trainJson.add("up",upTrainJson)
        json.add("train",trainJson)
        return json
    }
    fun fromJson(json:JsonObject){
        diagramID=UUID.fromString(json.get("diagramID").asString)
        name=json.get("name").asString
        val trainsJson=json.getAsJsonObject("train")
        for(trainJson in trainsJson.getAsJsonArray("down")){
            trains[Direction.DOWN.ordinal].add(Train(route,this).apply {this.fromJSON(trainJson.asJsonObject) })
        }
        for(trainJson in trainsJson.getAsJsonArray("up")){
            trains[Direction.UP.ordinal].add(Train(route,this).apply {this.fromJSON(trainJson.asJsonObject) })
        }

    }
    fun toSQL(sqLiteHelper: SQLiteHelper){
        val sql="insert into diagram (diagramID,routeID,sequence,name) values(?,?,?,?)"
        val statement=sqLiteHelper.getStatement(sql)
        statement.setString(1,diagramID.toString())
        statement.setString(2,route.routeID.toString())
        statement.setInt(3,route.diagrams.indexOf(this))
        statement.setString(4,name)
        statement.executeUpdate()
        for(train in trains[Direction.DOWN.ordinal]){
            train.toSQL(sqLiteHelper)
        }
        for(train in trains[Direction.UP.ordinal]){
            train.toSQL(sqLiteHelper)
        }
    }
    fun saveAsXml(document: Document):Element{
        val diagramDom=document.createElement("Trains")
        val trainsDown=document.createElement("TrainsDown")
        val trainsUp=document.createElement("TrainsUp")
        for(train in trains[0]){
            trainsDown.appendChild(train.saveAsXml(document))
        }
        for(train in trains[1]){
            trainsUp.appendChild(train.saveAsXml(document))
        }
        diagramDom.appendChild(document.createElement("TrainItems").apply {
            appendChild(trainsDown)
            appendChild(trainsUp)
        })
        return diagramDom
    }
    companion object {

        fun createTableSQL(): String {
            return "create table diagram(id int primary key,diagramID text,routeID text,sequence int,name text)"
        }
        fun fromSQL(sqLiteHelper: SQLiteHelper,diaData: DiaData){
            val sql="select * from diagram order by sequence"
            val statement=sqLiteHelper.getStatement(sql)
            val rs: ResultSet =statement.executeQuery()
            while(rs.next()){
//                val route=diaData.getRouteFromID(UUID.fromString(rs.getString("routeID")))
//                val diagram=Diagram()
                diaData.routes.add(Route(diaData,rs))
            }
        }    }


}
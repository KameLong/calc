package kamelong.com.diadata

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kamelong.com.tool.SQLiteHelper
import kamelong.com.tool.ShiftJISBufferedReader
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.PrintWriter
import java.sql.ResultSet
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * １路線の時刻表
 */
class Route(val diaData: DiaData){
    var routeID:UUID= UUID.randomUUID()

    var name:String=""
    var filePath:String=""

    var stations:MutableList<Station> = mutableListOf()
    var diagrams:MutableList<Diagram> = mutableListOf()
    var types:MutableList<TrainType> = mutableListOf()

    var diagramStartTime:Int=3*3600
    var comment:String=""
    var secondShift:Array<Int> = arrayOf(10,15)

    private var stationTime: MutableList<Int> = mutableListOf()

    private fun finalize(){
        //まずは現段階の列車種別に同じ種別名が存在しないかチェックする
        for(type1 in types){
            for(type2 in types){
                if(type1!=type2&&type1.name==type2.name){
                    type2.name=type2.name+"?"
                }
            }
        }

        //列車種別の配置パターンをDiaDataに揃える
        //列車種別の変換法則　index=aのものをindex=bにするとき　typeChangeMap[a]=bとなるようにする
        val typeChangeMap = HashMap<Int,Int>()
        for(type in types){
            run{
                for(parentType in diaData.trainTypes){
                    if(type.name==parentType.name){
                        typeChangeMap[types.indexOf(type)]=diaData.trainTypes.indexOf(parentType)
                        return@run
                    }
                }
                diaData.trainTypes.add(type)
                typeChangeMap[types.indexOf(type)]=diaData.trainTypes.size-1
            }
        }
        types=diaData.trainTypes
        //各列車の列車湯別を更新します。
        for(diagram in diagrams){
            for(trains in diagram.trains){
                for(train in trains){
                    train.type=typeChangeMap[train.type]?:0
                }
            }
        }

        //次にダイヤ名に重複がないか確認します
        for(dia1 in diagrams){
            for(dia2 in diagrams){
                if(dia1!=dia2&&dia1.name==dia2.name){
                    dia2.name=dia2.name+"?"
                }
            }
        }
        //ダイヤをDiaDataに合わせて更新
        val newDiagram = ArrayList<Diagram>()
        for(parentDiaName in diaData.diagramNames){
            run{
                for(dia2 in diagrams){
                    if(parentDiaName==dia2.name){
                        newDiagram.add(dia2)
                        return@run
                    }
                }
                newDiagram.add(Diagram(this).apply{name=parentDiaName})
            }
        }
        for(dia2 in diagrams){
            run{
                for(parentDiaName in diaData.diagramNames){
                    if(parentDiaName==dia2.name){
                        return@run
                    }
                }
                newDiagram.add(dia2)
            }
        }

        diagrams=newDiagram



    }

    fun getStationTime(): MutableList<Int> {
        if (stationTime.size != stations.size) {
            calcStationTime()
        }
        return stationTime
    }
    fun calcStationTime() {
        stationTime = java.util.ArrayList()
        stationTime.add(0)
        for (dia in diagrams) {
            if (dia.name.equals("基準運転時分")) {
                for (i in 1 until stationCount) {
                    var minTime = 100000
                    for (train in dia.trains[0]) {
                        if (train.stationTimes[i - 1].timeExist() && train.stationTimes[i ].timeExist()) {
                            var time = train.reqTime(i - 1, i)
                            if (time < 0) continue
                            if (train.stationTimes[i - 1].stopType !== StopType.STOP) {
                                time += 30
                            }
                            if (train.stationTimes[i ].stopType !== StopType.STOP) {
                                time += 30
                            }
                            if (time < minTime) {
                                minTime = time
                            }
                        }
                    }
                    for (train in dia.trains[1]) {
                        if (train.stationTimes[i - 1].timeExist() && train.stationTimes[i ].timeExist()) {
                            var time = train.reqTime(i - 1, i)
                            if (time < 0) continue
                            if (train.stationTimes[i - 1].stopType !== StopType.STOP) {
                                time += 30
                            }
                            if (train.stationTimes[i ].stopType !== StopType.STOP) {
                                time += 30
                            }
                            if (time < minTime) {
                                minTime = time
                            }
                        }
                    }
                    if (minTime > 90000) {
                        minTime = 180
                    }
                    stationTime.add(stationTime[stationTime.size - 1] + minTime)
                }
                return
            }
        }
        for (i in 1 until stationCount) {
            var minTime = 100000
            for (dia in diagrams) {
                for(trains in dia.trains){
                    for (train in trains) {
                        if (train.stationTimes[i - 1].timeExist() && train.stationTimes[i ].timeExist()) {
                            var time = train.reqTime(i - 1, i)
                            if (time < 0) continue
                            if (train.stationTimes[i - 1].stopType !== StopType.STOP) {
                                time += 30
                            }
                            if (train.stationTimes[i ].stopType !== StopType.STOP) {
                                time += 30
                            }
                            if (time < minTime) {
                                minTime = time
                            }
                        }
                    }
                }
            }
            if (minTime > 90000) {
                minTime = 180
            }
            if (minTime < 30) {
                minTime = 30
            }
            stationTime.add(stationTime[stationTime.size - 1] + minTime)
        }
    }


    fun fromOuDia(br:ShiftJISBufferedReader){
        var direction = Direction.DOWN
        stations = ArrayList()
        diagrams = ArrayList()
        var property = ""
        val propertyStack = Stack<String>()
        var line = br.readLine()
        var tempStation = Station(this)
        var tempTrack = Track(tempStation)
        var tempType = TrainType()
        var tempDia = Diagram(this)
        var tempTrain = Train(this,tempDia)
        while (line != null) {
            if (line == ".") {
                //読み込みプロパティ終了
                property = propertyStack.pop()
                line = br.readLine()
                continue
            }
            if (line.endsWith(".")) {
                propertyStack.push(property)
                property = line.substring(0, line.length - 1)
                when (property) {
                    "Eki" -> {
                        tempStation = Station(this)
                        stations.add(tempStation)
                    }
                    "EkiTrack2" -> {
                        tempTrack = Track(tempStation)
                        tempStation.track.add(tempTrack)
                    }
                    "Ressyasyubetsu" -> {
                        tempType = TrainType()
                        types.add(tempType)
                    }
                    "Dia" -> {
                        tempDia = Diagram(this)
                        diagrams.add(tempDia)
                    }
                    "Kudari" -> direction = Direction.DOWN
                    "Nobori" -> direction = Direction.UP
                    "Ressya" -> {
                        tempTrain = Train(this,tempDia)
                        tempTrain.direction=direction
                        tempDia.trains[direction.ordinal].add(tempTrain)
                    }
                }
                line = br.readLine()
                continue
            }
            if (line.contains("=")) {
                val title = line.substring(0, line.indexOf("="))
                val value = line.substring(line.indexOf("=") + 1)
                when (property) {
                    "Eki" -> tempStation.setOuDiaValue(title, value)
                    "EkiTrack2" -> tempTrack.setOuDiaValue(title, value)
//                    "OuterTerminal" -> tempOuterTerminal.setValue(title, value)
                    "Ressyasyubetsu" -> tempType.setOuDiaValue(title, value)
                    "Dia" -> tempDia.setOuDiaValue(title, value)
                    "Ressya" -> tempTrain.setOuDiaValue(title, value)
                    "Rosen" -> this.setOuDiaValue(title, value)
                    "DispProp" -> this.setOuDiaValue(title, value)
                }
            }
            line = br.readLine()
        }
        finalize()

    }
    fun saveAsJSON(filePath:String):JsonObject{
        val json=JsonObject()
        json.addProperty("routeID",routeID.toString())
        json.addProperty("name",name)
        json.addProperty("diagramStartTime",diagramStartTime)
        json.addProperty("comment",comment)
        val secondShiftJson=JsonArray()
        secondShiftJson.add(secondShift[0])
        secondShiftJson.add(secondShift[1])
        json.add("secondShift",secondShiftJson)
        val stationJSON=JsonArray()
        for(s in stations){
            stationJSON.add(s.toJSON())
        }
        json.add("station",stationJSON)
        val typeJson=JsonArray()
        for(type in this.types){
            typeJson.add(type.toJSON())
        }
        json.add("type",typeJson)
        val diagramJSON=JsonArray()
        for(diagram in diagrams){
            diagramJSON.add(diagram.toJSON())
        }
        json.add("diagram",diagramJSON)
        return json
    }

    fun fromJSON(json:JsonObject){
        routeID=UUID.fromString(json["routeID"].asString)
        name=json["name"].asString
        diagramStartTime=json["diagramStartTime"].asInt
        comment=json["comment"].asString
        secondShift[0]=json["secondShift"].asJsonArray[0].asInt
        secondShift[1]=json["secondShift"].asJsonArray[1].asInt
        stations= ArrayList()
        for(stationJson in json["station"].asJsonArray){
            stations.add(Station(this).apply { this.fromJSON(stationJson.asJsonObject) })
        }
        types= ArrayList()
        for(typeJson in json["type"].asJsonArray){
            types.add(TrainType().apply { this.fromJSON(typeJson.asJsonObject) })
        }
        diagrams= ArrayList()
        for(diagramJson in json["diagram"].asJsonArray){
            diagrams.add(Diagram(this).apply { this.fromJson(diagramJson.asJsonObject) })
        }

    }
    fun saveAsOuDia(filePath: String,trainTypes:ArrayList<TrainType>){
        val file=File(filePath)
        if(file.exists()){
            throw FileAlreadyExistsException(file)
        }
        val output=PrintWriter(File(filePath))
        output.write("FileType=OuDia.1.02\n")
        output.write("Rosen.\n")
        output.write("Rosenmei=$name.\n")
        output.write("lineID=${routeID.toString()}.\n")
        for(station in stations){
            station.saveAsOuDia(output)
        }
    }

    fun toSQL(sqLiteHelper: SQLiteHelper){
        val sql="insert into route (routeID,name,diagramStartTime,comment) values(?,?,?,?)"
        val statement=sqLiteHelper.getStatement(sql)
        statement.setString(1,routeID.toString())
        statement.setString(2,name)
        statement.setInt(3,diagramStartTime)
        statement.setString(4,comment)
        statement.executeUpdate()
        for(diagram in diagrams){
            diagram.toSQL(sqLiteHelper)
        }
        for(station in stations){
            station.toSQL(sqLiteHelper)
        }
        for(type in types){
            type.toSQL(sqLiteHelper)
        }
    }

    fun toXml(document: Document):Element{
        val sujitaro=document.createElement("Sujitaro")
        sujitaro.appendChild(document.createElement("Version").apply { textContent="3.03" })
        val settingDom=document.createElement("ダイヤグラム設定")
        settingDom.appendChild(document.createElement("何分目か").apply { textContent="2" })
        settingDom.appendChild(document.createElement("倍率").apply { textContent="130" })
        settingDom.appendChild(document.createElement("駅間倍率").apply { textContent="100" })
        settingDom.appendChild(document.createElement("前後範囲を描画しない").apply { textContent="True" })
        settingDom.appendChild(document.createElement("開始時刻").apply { textContent="0" })
        settingDom.appendChild(document.createElement("終了時刻").apply { textContent="24" })
        settingDom.appendChild(document.createElement("描画省略開始時刻").apply { textContent="0" })
        settingDom.appendChild(document.createElement("描画省略終了時刻").apply { textContent="0" })
        settingDom.appendChild(document.createElement("枠の描画色").apply { textContent="255-0-0-0" })
        settingDom.appendChild(document.createElement("小さい列車番号").apply { textContent="False" })
        settingDom.appendChild(document.createElement("小さい停車記号の円").apply { textContent="False" })
        settingDom.appendChild(document.createElement("デフォルトの列車種別").apply { textContent="0" })
        settingDom.appendChild(document.createElement("車両運用の描画").apply { textContent="True" })
        settingDom.appendChild(document.createElement("列車種別").apply {
            for(type in types){
                appendChild(type.toXml(document))
            }
        })
        sujitaro.appendChild(document.createElement("DiagramSetting").apply { appendChild(settingDom) })
        sujitaro.appendChild(document.createElement("TimetableSetting").apply {
            appendChild(document.createElement("時刻表設定").apply {
                appendChild(document.createElement("運転停車表示タイプ").apply { textContent = "True" })
                appendChild(document.createElement("秒表示タイプ").apply { textContent = "False" })
            })
        })
        sujitaro.appendChild(document.createElement("AutomaticTrainSetting").apply {
            appendChild(document.createElement("自動列車追加用設定").apply {
                appendChild(document.createElement("下り列車種別単位情報"))
                appendChild(document.createElement("上り列車種別単位情報"))
                appendChild(document.createElement("線路本数情報"))
                appendChild(document.createElement("駅情報"))
            })

        })


        val lineInfo=document.createElement("LineInfo")
        lineInfo.appendChild(document.createElement("名称").apply { textContent=name})
        lineInfo.appendChild(document.createElement("開始駅名").apply { textContent=stations.first().name })
        lineInfo.appendChild(document.createElement("終了駅名").apply { textContent=stations.last().name })
        lineInfo.appendChild(document.createElement("開始距離").apply { textContent="0" })
        lineInfo.appendChild(document.createElement("終了距離").apply { textContent="0" })

        lineInfo.appendChild(document.createElement("StationItems").apply {
            for (station in stations) {
                appendChild(station.toXml(document))
            }
        })
        sujitaro.appendChild(document.createElement("Lines").apply {
            appendChild(lineInfo)
        })
        sujitaro.appendChild(diagrams[0].saveAsXml(document))
        sujitaro.appendChild(document.createElement("CarManagement")).apply {
            appendChild(document.createElement("車両運用情報"))
        }
        sujitaro.appendChild(document.createElement("TrainSplitMerge")).apply {
            appendChild(document.createElement("分割併合情報"))
        }
        return sujitaro


    }


    val stationCount:Int
        get(){return stations.size}
    /**
     * OuDia形式の1行を読み込みます。
     * Rosen.とDispProp.に関する情報をここで読み込みます。
     * @param title
     * @param value
     */
    protected fun setOuDiaValue(title: String?, value: String) {
        when (title) {
            "Rosenmei" -> name = value
            "routeID" -> routeID = UUID.fromString(value)
            "KitenJikoku" -> diagramStartTime = when (value.length) {
                3 -> 3600 * value.substring(0, 1).toInt() + 60 * value.substring(1, 3).toInt()
                4 -> 3600 * value.substring(0, 2).toInt() + 60 * value.substring(2, 4).toInt()
                else -> 60 * value.toInt()
            }
            "Comment" -> comment = value.replace("\\n", "\n")
            "AnySecondIncDec1" -> secondShift[0] = value.toInt()
            "AnySecondIncDec2" -> secondShift[1] = value.toInt()
        }
    }

    constructor(diaData: DiaData,rs:ResultSet):this(diaData){
        routeID=UUID.fromString(rs.getString("routeID"))
        name=rs.getString(rs.getString("name"))
        diagramStartTime=rs.getInt("diagramStartTime")
        comment=rs.getString("comment")
    }

    companion object{
        fun createTableSQL():String{
            return "create table route(id int primary key,routeID text,name text,diagramStartTime int,comment text)"
        }
        fun fromSQL(sqLiteHelper: SQLiteHelper,diaData: DiaData){
            val sql="select * from route"
            val statement=sqLiteHelper.getStatement(sql)
            val rs: ResultSet =statement.executeQuery()
            while(rs.next()){
                diaData.routes.add(Route(diaData,rs))
            }
        }
    }

}
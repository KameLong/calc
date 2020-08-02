package kamelong.com.diadata

import com.kamelong.diadata.TrainBlock
import kamelong.com.tool.SQLiteHelper
import kamelong.com.tool.ShiftJISBufferedReader
import org.w3c.dom.Document
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.sql.ResultSet
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


/**
 * 時刻表データの主体となるもの
 *１冊の時刻表のイメージ
 */
class DiaData{
    var id:UUID= UUID.randomUUID()
    var name:String=""

    /**
     * 時刻表のリスト
     */
    var routes:MutableList<Route> = mutableListOf()
    var trainTypes:MutableList<TrainType> = mutableListOf()
    var diagramNames:MutableList<String> = mutableListOf()

    var operations:HashMap<UUID,Operation> = HashMap()
    var operationItems:HashMap<UUID,OperationItem> = HashMap()
    var trains:HashMap<UUID,Train> = HashMap()
    var trainBlocks:HashMap<UUID,TrainBlock> = HashMap()

    /**
     * ファイルの読み込み操作を行う。
     * どんなファイルであってもまずこの関数を用いる。
     */
    fun loadFile(file:File){
        if(!file.isFile){
            throw Exception("not found this file")
        }
        if(file.name.endsWith(".oud")){
            val br= ShiftJISBufferedReader(InputStreamReader(FileInputStream(file), "Shift-JIS"))
            val route=Route(this)
            route.fromOuDia(br)
            this.routes.add(route)
        }
        if(file.name.endsWith("json")){
        }
    }

    /**
     * SQLite形式で保存する
     * まだ完成されていない
     */
    fun saveAsSQL(filePath:String){
        val file= File(filePath)
        if(file.isFile){
            file.delete()
        }
        val startTime=System.currentTimeMillis()
        val sqlite=SQLiteHelper(filePath)
        sqlite.execute(Route.createTableSQL())
        sqlite.execute(Station.createTableSQL())
        sqlite.execute(Diagram.createTableSQL())
        sqlite.execute(Train.createTableSQL())
        sqlite.execute(StationTime.createTableSQL())
        sqlite.execute(TrainType.createTableSQL())
        sqlite.execute(Operation.createTableSQL())
        sqlite.execute(Operation.createOperationItemListSQL())
        sqlite.execute(OperationItem.createTableSQL())
        sqlite.beginTransaction()

        for(route in routes){
            route.toSQL(sqlite)
        }
        for(operation in operations.values){
            operation.toSQL(sqlite)
        }
        sqlite.endTransaction()
        println("time=${System.currentTimeMillis()-startTime}")
    }
    fun fromSQL(filePath: String){
        val file= File(filePath)
        val startTime=System.currentTimeMillis()
        val sqlite=SQLiteHelper(filePath)
        sqlite.beginTransaction()
        Operation.fromSQL(sqlite,this)

//        for(operation in operations) {
//            println(operation)
//        }
        //operationの読み込み


    }
    fun saveAsOuDia(filePath:String,routeIndex:Int){
//        routes[routeIndex].saveAsOuDia(filePath,trainTypes)
    }
    fun saveAsXml(filePath:String,routeIndex: Int){
        val document: Document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        document.appendChild(routes[routeIndex].toXml(document))
        val transformerFactory = TransformerFactory.newInstance()
        val transformer: Transformer = transformerFactory.newTransformer()
        val source = DOMSource(document)
        val result = StreamResult(File(filePath))
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "Shift_JIS");
        transformer.transform(source, result)
    }
    fun saveAsJson(filePath:String,routeIndex:Int){
        val outFile= PrintWriter(File(filePath))
        outFile.write(routes[routeIndex].saveAsJSON(filePath).asString)
        outFile.close()

    }

    /**
     * 新規運用を追加する
     * 既にUUIDがmapに存在する時はエラーが返る
     */
    fun addOperation(operation: Operation){
        if(operation.operationID in operations.keys){
            throw Exception("operation is already exsist")
        }
        operations[operation.operationID]=operation
    }

    /**
     * 指定UUIDのoperationを取得する。
     * 存在しない場合は作成する
     */
    fun getOperation(operationID:UUID):Operation{
        return operations[operationID]?:Operation(this).apply { operations[this.operationID]=this }
    }

}
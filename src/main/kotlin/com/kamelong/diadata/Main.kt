package kamelong.com.diadata

import java.io.File


fun main(){
    val start=System.currentTimeMillis()
    println("test")
    val data=DiaData()
    data.fromSQL("test.aod")
    println(System.currentTimeMillis()-start)
//    val directory=File("C:\\Users\\kame\\Documents\\趣味関連\\ekikara2oudia-kai\\ekikara2oudia-kai\\src\\test")
//    for(file in directory.listFiles()){
//        data.loadFile(file)
//    }
//    println(System.currentTimeMillis()-start)
//    data.saveAsSQL("test.aod")
//    println(System.currentTimeMillis()-start)
//    data.saveAsXml("C:\\Users\\kame\\Documents\\sample.xml")
    println(System.currentTimeMillis()-start)
//    data.saveAsJson("C:\\Users\\kame\\Documents\\sample.json",0)
    println(System.currentTimeMillis()-start)
}
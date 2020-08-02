package com.kamelong.diadata

import kamelong.com.diadata.DiaData
import kamelong.com.diadata.Train
import java.util.*

class TrainBlock(val diaData: DiaData){
    var id:UUID= UUID.randomUUID()
    var trainList:MutableList<Train> = mutableListOf()
}
package com.kamelong.tool

import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

/*
 * Copyright (c) 2019 KameLong
 * contact:kamelong.com
 *
 * This source code is released under GNU GPL ver3.
 */ /**
 * Logを出力させるためのクラス
 */
object SDlog {
    private const val able = false

    fun toast(string: String?) {
    }

    val nowDate: String
        get() {
            val df: DateFormat = SimpleDateFormat("yyyyMMddHHmmss")
            val date = Date(System.currentTimeMillis())
            return df.format(date)
        }

    fun log(e: Exception) {
        e.printStackTrace()
    }

    fun log(value: Any?) {
        println(value)
    }

    fun log(value1: Any, value2: Any) {
        println("$value1,$value2")
    }

    fun Send(filename: String): Int {
        return 0
    }
}
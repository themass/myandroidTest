package com.openapi.ks.test

import android.util.Log
import java.util.ArrayList

class Test(val name: String, val age: Int) {
        // 定义一个方法
        fun sayHello() {
            println("Hello, my name is $name and I am $age years old.")
            Log.i("test log", "$name --- $age")
            var l = ArrayList<String>()
            l.add("test11")
            l.add("test22")
            Log.i("test log22222", l.toString())
        }
    // 可以直接运行
    fun main() {
        // 创建一个 Person 对象
        val person = Test("Alice", 25)

        // 调用对象的方法
        person.sayHello()
    }

}
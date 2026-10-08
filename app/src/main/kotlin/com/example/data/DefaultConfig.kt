package com.example.data

import android.content.Context
import org.json.JSONObject

object DefaultConfig {
    fun load(context: Context): JSONObject = context.assets.open("default_config.json").bufferedReader(Charsets.UTF_8).use {
        JSONObject(it.readText())
    }
}

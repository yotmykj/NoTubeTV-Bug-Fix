package com.ycngmn.notubetv.utils

import android.content.Context

fun readAsset(context: Context, fileName: String): String {
    return try {
        context.assets.open(fileName).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        ""
    }
}

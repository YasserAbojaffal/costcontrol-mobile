package com.islandeden.costcontrol

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/** خط IBM Plex Sans Arabic (نفس خط البرنامج) — لو غير موجود يُستخدم خط النظام */
object Fonts {
    private var regular: Typeface? = null
    private var bold: Typeface? = null
    private var loaded = false

    private fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        regular = try { Typeface.createFromAsset(ctx.assets, "fonts/plex-regular.ttf") } catch (e: Exception) { null }
        bold = try { Typeface.createFromAsset(ctx.assets, "fonts/plex-bold.ttf") } catch (e: Exception) { null }
    }

    fun apply(v: View) {
        load(v.context)
        if (regular == null) return
        if (v is TextView) {
            val isBold = v.typeface?.isBold == true
            v.typeface = if (isBold) (bold ?: regular) else regular
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) apply(v.getChildAt(i))
    }
}

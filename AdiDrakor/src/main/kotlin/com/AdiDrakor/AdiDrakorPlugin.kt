package com.AdiDrakor

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import android.app.AlertDialog
import android.widget.Toast

@CloudstreamPlugin
class AdiDrakorPlugin : Plugin() {
    override fun load(context: Context) {
        // Mendaftarkan API dan Extractor
        registerMainAPI(AdiDrakor())
        registerExtractorAPI(Jeniusplay2())
    }

    // Daftar Sumber dan Key Penyimpanannya
    companion object {
        private const val PREF_NAME = "AdiDrakor_Settings"

        val sources = listOf(
            Triple("AdiMoviebox", "enable_adimoviebox", true),
            Triple("Idlix", "enable_idlix", true),
            Triple("VidsrcCC", "enable_vidsrccc", true),
            Triple("Vidsrc", "enable_vidsrc", true),
            Triple("Watchsomuch", "enable_watchsomuch", true),
            Triple("Vixsrc", "enable_vixsrc", true),
            Triple("Vidlink", "enable_vidlink", true),
            Triple("Vidfast", "enable_vidfast", true),
            Triple("Mapple", "enable_mapple", true),
            Triple("Wyzie", "enable_wyzie", true),
            Triple("VidsrcCx", "enable_vidsrccx", true),
            Triple("Superembed", "enable_superembed", true),
            Triple("Vidrock", "enable_vidrock", true),
            Triple("Gomovies", "enable_gomovies", true)
        )

        fun isSourceEnabled(context: Context, key: String, default: Boolean): Boolean {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(key, default)
        }
    }

    // HAPUS KATA KUNCI 'override' DI SINI
    fun openSettings(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val sourceNames = sources.map { it.first }.toTypedArray()
        val checkedItems = sources.map { prefs.getBoolean(it.second, it.third) }.toBooleanArray()

        val builder = AlertDialog.Builder(context)
        builder.setTitle("AdiDrakor Sources")
        builder.setMultiChoiceItems(sourceNames, checkedItems) { _, which, isChecked ->
            val editor = prefs.edit()
            editor.putBoolean(sources[which].second, isChecked)
            editor.apply()
        }

        builder.setPositiveButton("Close") { dialog, _ ->
            dialog.dismiss()
            Toast.makeText(context, "Settings Saved", Toast.LENGTH_SHORT).show()
        }

        builder.setNegativeButton("Enable All") { _, _ ->
            val editor = prefs.edit()
            sources.forEach { editor.putBoolean(it.second, true) }
            editor.apply()
            Toast.makeText(context, "All Sources Enabled", Toast.LENGTH_SHORT).show()
        }
        
        builder.setNeutralButton("Disable All") { _, _ ->
            val editor = prefs.edit()
            sources.forEach { editor.putBoolean(it.second, false) }
            editor.apply()
            Toast.makeText(context, "All Sources Disabled", Toast.LENGTH_SHORT).show()
        }

        builder.show()
    }
}

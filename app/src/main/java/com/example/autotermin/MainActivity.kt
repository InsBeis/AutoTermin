package com.example.autotermin

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// === KOLORY APLIKACJI ===
val BgColor = Color(0xFF0F1522)
val CardBgColor = Color(0xFF1B2333)
val BlueAccent = Color(0xFF3B82F6)
val TextGray = Color(0xFF8B98AD)
val YellowAccent = Color(0xFFF59E0B)
val OrangeAccent = Color(0xFFF97316)
val GreenAccent = Color(0xFF22C55E)
val RedAccent = Color(0xFFEF4444)
val IconBgColor = Color(0xFF263045)

val CardGradientTop = Color(0xFF1E273A)
val CardGradientBottom = Color(0xFF131926)
val CardBorder = Color(0xFF2A374F)
val CardGradient = Brush.verticalGradient(listOf(CardGradientTop, CardGradientBottom))

val verticalBrightDividerBrush = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.25f), Color.Transparent)
)
val horizontalTaperingBrush = Brush.horizontalGradient(
    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.25f), Color.Transparent)
)

// === LISTA OBSŁUGIWANYCH WALUT ===
val DostepneWaluty = listOf("PLN", "EUR", "USD", "GBP", "CHF", "CZK", "NOK")

// === MODELE DANYCH ===
data class Pojazd(val id: Int, val markaModel: String, val rejestracja: String, val avatarRes: Int)
data class Termin(
    val id: Int,
    val pojazd: Pojazd,
    val typ: String,
    val data: String,
    val przypomnienieData: String,
    val przypomnienieGodzina: String,
    val opis: String = "",
    val sztywnyAlert: Boolean = false
)

data class Naprawa(
    val id: Int,
    val nazwa: String,
    val koszt: String,
    val notatki: String = "",
    val waluta: String = "PLN"
)

data class Wizyta(
    val id: Int,
    val pojazd: Pojazd,
    val warsztat: String,
    val przebieg: String,
    val data: String,
    val naprawy: List<Naprawa> = emptyList()
)

data class ZarchiwizowanaNaprawa(
    val idArch: Int,
    val wizytaId: Int,
    val warsztat: String,
    val przebieg: String,
    val dataWizyty: String,
    val pojazd: Pojazd,
    val naprawa: Naprawa
)

data class Dokument(
    val id: Int,
    val nazwa: String,
    val uriString: String,
    val dataDodania: String,
    val typ: String, // "IMAGE", "PDF", "OTHER"
    val kategoria: String = "Inny",
    val pojazdId: Int = -1,
    val pojazdNazwa: String = "",
    val pojazdRejestracja: String = ""
)

// === STAN JĘZYKA (PL / EN) ===
class LanguageManager {
    var isEnglish by mutableStateOf(false)
    fun t(pl: String, en: String): String = if (isEnglish) en else pl
}
val LocalLanguage = compositionLocalOf { LanguageManager() }

fun translateTyp(typ: String, isEnglish: Boolean): String {
    if (!isEnglish) return typ
    return when (typ) {
        "OC" -> "TP"
        "AC" -> "Comp"
        "OC/AC" -> "TP/Comp"
        "Badanie techniczne" -> "Technical inspection"
        "Wymiana opon" -> "Tire change"
        "Wymiana oleju/filtrów" -> "Oil/filter change"
        "Serwis klimatyzacji" -> "A/C service"
        "Inne" -> "Other"
        else -> typ
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Sprawdzamy czy aplikacja została uruchomiona z powiadomienia z żądaniem przejścia do zakładki
        val poczatkowaZakladka = intent?.getStringExtra("docelowa_zakladka") ?: "Pulpit"

        setContent {
            val languageManager = remember { LanguageManager() }
            CompositionLocalProvider(LocalLanguage provides languageManager) {
                AutoTerminTheme {
                    GlownyEkranAplikacji(startowaZakladka = poczatkowaZakladka)
                }
            }
        }
    }
}

@Composable
fun AutoTerminTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = BgColor,
            surface = Color.Transparent,
            primary = BlueAccent
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(9.dp) // Dopasowuje rogi i cień każdego DropdownMenu w aplikacji idealnie do ramki 9.dp
        ),
        content = content
    )
}

// === FUNKCJE ZAPISU I ODCZYTU DANYCH ===
fun zapiszDane(
    context: Context,
    listaAut: List<Pojazd>,
    listaTerminow: List<Termin>,
    kluczAut: String = "auta",
    kluczTerminow: String = "terminy"
) {
    val prefs = context.getSharedPreferences("AutoTerminPrefs", Context.MODE_PRIVATE)
    val autaSsidArray = JSONArray()
    for (auto in listaAut) {
        val obj = JSONObject()
        obj.put("id", auto.id)
        obj.put("markaModel", auto.markaModel)
        obj.put("rejestracja", auto.rejestracja)
        obj.put("avatarRes", auto.avatarRes)
        autaSsidArray.put(obj)
    }
    val terminyArray = JSONArray()
    for (t in listaTerminow) {
        val obj = JSONObject()
        obj.put("id", t.id)
        obj.put("tipo", t.typ)
        obj.put("data", t.data)
        obj.put("pData", t.przypomnienieData)
        obj.put("pGodz", t.przypomnienieGodzina)
        obj.put("opis", t.opis)
        obj.put("sztywnyAlert", t.sztywnyAlert)
        val autoObj = JSONObject()
        autoObj.put("id", t.pojazd.id)
        autoObj.put("markaModel", t.pojazd.markaModel)
        autoObj.put("rejestracja", t.pojazd.rejestracja)
        autoObj.put("avatarRes", t.pojazd.avatarRes)
        obj.put("pojazd", autoObj)
        terminyArray.put(obj)
    }
    prefs.edit().apply {
        putString(kluczAut, autaSsidArray.toString())
        putString(kluczTerminow, terminyArray.toString())
        apply()
    }
}

fun wczytajAuta(context: Context, klucz: String = "auta"): List<Pojazd> {
    val prefs = context.getSharedPreferences("AutoTerminPrefs", Context.MODE_PRIVATE)
    val jsonStr = prefs.getString(klucz, null) ?: return emptyList()
    val list = mutableListOf<Pojazd>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                Pojazd(
                    obj.getInt("id"),
                    obj.getString("markaModel"),
                    obj.getString("rejestracja"),
                    obj.getInt("avatarRes")
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun wczytajTerminy(context: Context, klucz: String = "terminy"): List<Termin> {
    val prefs = context.getSharedPreferences("AutoTerminPrefs", Context.MODE_PRIVATE)
    val jsonStr = prefs.getString(klucz, null) ?: return emptyList()
    val list = mutableListOf<Termin>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val autoObj = obj.getJSONObject("pojazd")
            val pojazd = Pojazd(
                autoObj.getInt("id"),
                autoObj.getString("markaModel"),
                autoObj.getString("rejestracja"),
                autoObj.getInt("avatarRes")
            )
            val opis = if (obj.has("opis")) obj.getString("opis") else ""
            val sztywnyAlert = obj.optBoolean("sztywnyAlert", false)
            list.add(
                Termin(
                    obj.getInt("id"),
                    pojazd,
                    obj.getString("tipo"),
                    obj.getString("data"),
                    obj.getString("pData"),
                    obj.getString("pGodz"),
                    opis,
                    sztywnyAlert
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun zapiszWizyty(context: Context, listaWizyt: List<Wizyta>, klucz: String = "wizyty") {
    val prefs = context.getSharedPreferences("AutoTerminPrefs", Context.MODE_PRIVATE)
    val arr = JSONArray()
    for (w in listaWizyt) {
        val obj = JSONObject().apply {
            put("id", w.id)
            put("warsztat", w.warsztat)
            put("przebieg", w.przebieg)
            put("data", w.data)
            val autoObj = JSONObject().apply {
                put("id", w.pojazd.id)
                put("markaModel", w.pojazd.markaModel)
                put("rejestracja", w.pojazd.rejestracja)
                put("avatarRes", w.pojazd.avatarRes)
            }
            put("pojazd", autoObj)
            val naprawyArr = JSONArray()
            for (n in w.naprawy) {
                val nObj = JSONObject().apply {
                    put("id", n.id)
                    put("nazwa", n.nazwa)
                    put("koszt", n.koszt)
                    put("notatki", n.notatki)
                    put("waluta", n.waluta)
                }
                naprawyArr.put(nObj)
            }
            put("naprawy", naprawyArr)
        }
        arr.put(obj)
    }
    prefs.edit().putString(klucz, arr.toString()).apply()
}

fun wczytajWizyty(context: Context, klucz: String = "wizyty"): List<Wizyta> {
    val prefs = context.getSharedPreferences("AutoTerminPrefs", Context.MODE_PRIVATE)
    val jsonStr = prefs.getString(klucz, null) ?: return emptyList()
    val list = mutableListOf<Wizyta>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val autoObj = obj.getJSONObject("pojazd")
            val pojazd = Pojazd(
                autoObj.getInt("id"),
                autoObj.getString("markaModel"),
                autoObj.getString("rejestracja"),
                autoObj.getInt("avatarRes")
            )
            val naprawyList = mutableListOf<Naprawa>()
            if (obj.has("naprawy")) {
                val nArr = obj.getJSONArray("naprawy")
                for (j in 0 until nArr.length()) {
                    val nObj = nArr.getJSONObject(j)
                    val notatki = if (nObj.has("notatki")) nObj.getString("notatki") else ""
                    val waluta = if (nObj.has("waluta")) nObj.getString("waluta") else "PLN"
                    naprawyList.add(
                        Naprawa(
                            nObj.getInt("id"),
                            nObj.getString("nazwa"),
                            nObj.getString("koszt"),
                            notatki,
                            waluta
                        )
                    )
                }
            }
            list.add(
                Wizyta(
                    id = obj.getInt("id"),
                    pojazd = pojazd,
                    warsztat = obj.getString("warsztat"),
                    przebieg = obj.getString("przebieg"),
                    data = obj.getString("data"),
                    naprawy = naprawyList
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun zapiszNaprawyArchiwum(context: Context, lista: List<ZarchiwizowanaNaprawa>) {
    val prefs = context.getSharedPreferences("AutoTerminPrefs", Context.MODE_PRIVATE)
    val arr = JSONArray()
    for (item in lista) {
        val obj = JSONObject().apply {
            put("idArch", item.idArch)
            put("wizytaId", item.wizytaId)
            put("warsztat", item.warsztat)
            put("przebieg", item.przebieg)
            put("dataWizyty", item.dataWizyty)
            val autoObj = JSONObject().apply {
                put("id", item.pojazd.id)
                put("markaModel", item.pojazd.markaModel)
                put("rejestracja", item.pojazd.rejestracja)
                put("avatarRes", item.pojazd.avatarRes)
            }
            put("pojazd", autoObj)
            val nObj = JSONObject().apply {
                put("id", item.naprawa.id)
                put("nazwa", item.naprawa.nazwa)
                put("koszt", item.naprawa.koszt)
                put("notatki", item.naprawa.notatki)
                put("waluta", item.naprawa.waluta)
            }
            put("naprawa", nObj)
        }
        arr.put(obj)
    }
    prefs.edit().putString("archiwum_naprawy", arr.toString()).apply()
}

fun wczytajNaprawyArchiwum(context: Context): List<ZarchiwizowanaNaprawa> {
    val prefs = context.getSharedPreferences("AutoTerminPrefs", Context.MODE_PRIVATE)
    val jsonStr = prefs.getString("archiwum_naprawy", null) ?: return emptyList()
    val list = mutableListOf<ZarchiwizowanaNaprawa>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val autoObj = obj.getJSONObject("pojazd")
            val pojazd = Pojazd(
                autoObj.getInt("id"),
                autoObj.getString("markaModel"),
                autoObj.getString("rejestracja"),
                autoObj.getInt("avatarRes")
            )
            val nObj = obj.getJSONObject("naprawa")
            val waluta = if (nObj.has("waluta")) nObj.getString("waluta") else "PLN"
            val naprawa = Naprawa(
                nObj.getInt("id"),
                nObj.getString("nazwa"),
                nObj.getString("koszt"),
                if (nObj.has("notatki")) nObj.getString("notatki") else "",
                waluta
            )
            list.add(
                ZarchiwizowanaNaprawa(
                    idArch = obj.getInt("idArch"),
                    wizytaId = obj.getInt("wizytaId"),
                    warsztat = obj.getString("warsztat"),
                    przebieg = obj.optString("przebieg", ""),
                    dataWizyty = obj.getString("dataWizyty"),
                    pojazd = pojazd,
                    naprawa = naprawa
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun zapiszDokumenty(context: Context, lista: List<Dokument>, klucz: String = "dokumenty") {
    val prefs = context.getSharedPreferences("AutoTerminPrefs", Context.MODE_PRIVATE)
    val array = JSONArray()
    for (d in lista) {
        val obj = JSONObject()
        obj.put("id", d.id)
        obj.put("nazwa", d.nazwa)
        obj.put("uriString", d.uriString)
        obj.put("dataDodania", d.dataDodania)
        obj.put("typ", d.typ)
        obj.put("kategoria", d.kategoria)
        obj.put("pojazdId", d.pojazdId)
        obj.put("pojazdNazwa", d.pojazdNazwa)
        obj.put("pojazdRejestracja", d.pojazdRejestracja)
        array.put(obj)
    }
    prefs.edit().putString(klucz, array.toString()).apply()
}

fun wczytajDokumenty(context: Context, klucz: String = "dokumenty"): List<Dokument> {
    val prefs = context.getSharedPreferences("AutoTerminPrefs", Context.MODE_PRIVATE)
    val jsonStr = prefs.getString(klucz, null) ?: return emptyList()
    val list = mutableListOf<Dokument>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                Dokument(
                    id = obj.getInt("id"),
                    nazwa = obj.getString("nazwa"),
                    uriString = obj.getString("uriString"),
                    dataDodania = obj.getString("dataDodania"),
                    typ = obj.getString("typ"),
                    kategoria = obj.optString("kategoria", "Inny"),
                    pojazdId = obj.optInt("pojazdId", -1),
                    pojazdNazwa = obj.optString("pojazdNazwa", ""),
                    pojazdRejestracja = obj.optString("pojazdRejestracja", "")
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun wczytajWarsztaty(context: Context): Set<String> {
    val prefs = context.getSharedPreferences("AutoTerminPrefs", Context.MODE_PRIVATE)
    val jsonStr = prefs.getString("warsztaty_history", "[]") ?: "[]"
    val set = mutableSetOf<String>()
    try {
        val arr = JSONArray(jsonStr)
        for (i in 0 until arr.length()) {
            set.add(arr.getString(i))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return set
}

fun zapiszWarsztat(context: Context, warsztat: String) {
    if (warsztat.isBlank()) return
    val set = wczytajWarsztaty(context).toMutableSet()
    set.add(warsztat.trim())
    val prefs = context.getSharedPreferences("AutoTerminPrefs", Context.MODE_PRIVATE)
    prefs.edit().putString("warsztaty_history", JSONArray(set.toList()).toString()).apply()
}

fun wczytajNaprawyHistory(context: Context): Set<String> {
    val prefs = context.getSharedPreferences("AutoTerminPrefs", Context.MODE_PRIVATE)
    val jsonStr = prefs.getString("naprawy_history", "[]") ?: "[]"
    val set = mutableSetOf<String>()
    try {
        val arr = JSONArray(jsonStr)
        for (i in 0 until arr.length()) {
            set.add(arr.getString(i))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return set
}

fun zapiszNaprawaHistory(context: Context, nazwa: String) {
    if (nazwa.isBlank()) return
    val set = wczytajNaprawyHistory(context).toMutableSet()
    set.add(nazwa.trim())
    val prefs = context.getSharedPreferences("AutoTerminPrefs", Context.MODE_PRIVATE)
    prefs.edit().putString("naprawy_history", JSONArray(set.toList()).toString()).apply()
}

fun zapiszUkrytePowiadomienia(context: Context, ukryte: Set<Int>) {
    val prefs = context.getSharedPreferences("AutoTerminPrefs", Context.MODE_PRIVATE)
    prefs.edit().putString("ukryte_powiadomienia", JSONArray(ukryte.toList()).toString()).apply()
}

fun wczytajUkrytePowiadomienia(context: Context): Set<Int> {
    val prefs = context.getSharedPreferences("AutoTerminPrefs", Context.MODE_PRIVATE)
    val jsonStr = prefs.getString("ukryte_powiadomienia", "[]")
    val set = mutableSetOf<Int>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            set.add(array.getInt(i))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return set
}

fun formatujKwote(kwota: Double): String {
    val formatter = java.text.DecimalFormat("#,###")
    val symbols = java.text.DecimalFormatSymbols(Locale.getDefault())
    symbols.groupingSeparator = '.'
    formatter.decimalFormatSymbols = symbols
    return formatter.format(kwota)
}

fun formatujKwotePDF(kwota: Double): String {
    return String.format(Locale.US, "%.2f", kwota)
}

fun parsujKwote(kosztStr: String): Double {
    return kosztStr.replace(" ", "").replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
}

fun podsumujKosztyWgWalut(naprawy: List<Naprawa>): String {
    if (naprawy.isEmpty()) return "0 PLN"
    val pogrupowane = naprawy.groupBy { it.waluta.ifBlank { "PLN" } }
    return pogrupowane.entries.joinToString(" · ") { entry ->
        val suma = entry.value.sumOf { parsujKwote(it.koszt) }
        "${formatujKwote(suma)} ${entry.key}"
    }
}

fun skopiujPlikDoPamięciAplikacji(context: Context, uri: Uri, typ: String): String? {
    return try {
        val folder = File(context.filesDir, "dokumenty")
        if (!folder.exists()) folder.mkdirs()
        val rozszerzenie = when (context.contentResolver.getType(uri)) {
            "application/pdf" -> ".pdf"
            "image/png" -> ".png"
            "image/jpeg", "image/jpg" -> ".jpg"
            else -> if (typ == "IMAGE") ".jpg" else if (typ == "PDF") ".pdf" else ""
        }
        val unikalnaNazwa = "dok_${System.currentTimeMillis()}$rozszerzenie"
        val plikDocelowy = File(folder, unikalnaNazwa)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(plikDocelowy).use { output ->
                input.copyTo(output)
            }
        }
        plikDocelowy.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun pobierzNazwePliku(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "Nowy dokument"
}

fun getIkonaDlaTypu(typ: String): ImageVector {
    return when (typ) {
        "OC", "AC", "OC/AC" -> Icons.Default.Security
        "Badanie techniczne" -> Icons.Default.Build
        "Wymiana oleju/filtrów" -> Icons.Default.Opacity
        "Serwis klimatyzacji" -> Icons.Default.AcUnit
        "Wymiana opon" -> Icons.Default.Settings
        "Inne" -> Icons.Default.MoreHoriz
        else -> Icons.Default.Event
    }
}

@Composable
fun DialogPotwierdzeniaUsuniecia(
    tytul: String,
    tresc: String,
    tekstPotwierdzenia: String? = null,
    onPotwierdz: () -> Unit,
    onAnuluj: () -> Unit
) {
    val lang = LocalLanguage.current
    val finalnyTekstPotwierdzenia = tekstPotwierdzenia ?: lang.t("Usuń", "Delete")
    AlertDialog(
        onDismissRequest = onAnuluj,
        modifier = Modifier
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .background(CardGradient, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.Transparent,
        title = {
            Text(tytul, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(tresc, color = TextGray, fontSize = 13.sp)
        },
        confirmButton = {
            Button(
                onClick = onPotwierdz,
                colors = ButtonDefaults.buttonColors(containerColor = RedAccent)
            ) {
                Text(finalnyTekstPotwierdzenia, color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onAnuluj) {
                Text(lang.t("Anuluj", "Cancel"), color = TextGray)
            }
        }
    )
}

// === GENEROWANIE PLIKU PDF ===
fun wygenerujPlikRaportuPDF(context: Context, wybraneWizyty: List<Wizyta>, isEnglish: Boolean = false): File? {
    if (wybraneWizyty.isEmpty()) return null
    val pdfDocument = android.graphics.pdf.PdfDocument()
    var numerStrony = 1
    var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, numerStrony).create()
    var page = pdfDocument.startPage(pageInfo)
    var canvas = page.canvas
    val whiteFillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.FILL
    }
    val mainTitleBluePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(13, 71, 161)
        textSize = 19f
        isFakeBoldText = true
    }
    val topDateGrayPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(102, 102, 102)
        textSize = 9.5f
    }
    val mainSubtitleBlackPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 12.5f
        isFakeBoldText = true
    }
    val mainDividerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(51, 51, 51)
        strokeWidth = 1.1f
    }
    val cardBorderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(210, 214, 220)
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1f
    }
    val cardTitleBluePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(13, 71, 161)
        textSize = 11.5f
        isFakeBoldText = true
    }
    val cardCostGreenPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(46, 125, 50)
        textSize = 11.5f
        isFakeBoldText = true
    }
    val mileageGrayPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(85, 85, 85)
        textSize = 9.5f
    }
    val tableHeaderBgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(242, 242, 242)
        style = android.graphics.Paint.Style.FILL
    }
    val tableHeaderTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 9.5f
        isFakeBoldText = true
    }
    val tableCellTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(30, 30, 30)
        textSize = 9.5f
    }
    val tableGridPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(224, 224, 224)
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 0.8f
    }
    val footerTotalBluePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(13, 71, 161)
        textSize = 12.5f
        isFakeBoldText = true
    }

    fun rozpocznijNowaStrone() {
        pdfDocument.finishPage(page)
        numerStrony++
        pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, numerStrony).create()
        page = pdfDocument.startPage(pageInfo)
        canvas = page.canvas
        canvas.drawPaint(whiteFillPaint)
    }
    canvas.drawPaint(whiteFillPaint)
    val sdfWizyty = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val posortowaneWizyty = wybraneWizyty.sortedByDescending {
        try { sdfWizyty.parse(it.data)?.time ?: 0L } catch (e: Exception) { 0L }
    }
    val leftMargin = 35f
    val rightMargin = 560f
    var y = 52f
    val titlePrefix = if (isEnglish) "AutoTermin - Repair Report" else "AutoTermin - Raport Napraw"
    canvas.drawText(titlePrefix, leftMargin, y, mainTitleBluePaint)

    val dataNaglowka = if (posortowaneWizyty.size == 1) {
        posortowaneWizyty.first().data
    } else {
        sdfWizyty.format(Date())
    }
    val dateLabel = if (isEnglish) "Date: $dataNaglowka" else "Data: $dataNaglowka"
    val szerDaty = topDateGrayPaint.measureText(dateLabel)
    canvas.drawText(dateLabel, rightMargin - szerDaty, y - 3f, topDateGrayPaint)
    y += 22f

    val autaNazwy = posortowaneWizyty.map { it.pojazd.markaModel }.distinct().joinToString(", ")
    val podtytulRaportu = if (isEnglish) {
        "Selected visits (${posortowaneWizyty.size}) - $autaNazwy"
    } else {
        "Wybrane wizyty (${posortowaneWizyty.size}) - $autaNazwy"
    }
    canvas.drawText(podtytulRaportu, leftMargin, y, mainSubtitleBlackPaint)
    y += 10f
    canvas.drawLine(leftMargin, y, rightMargin, y, mainDividerPaint)
    y += 18f

    for (wizyta in posortowaneWizyty) {
        val rowH = 22f
        val col1Width = (320f - leftMargin - 12f).toInt()
        val col2Width = (425f - 320f - 12f).toInt()

        // Najpierw obliczamy faktyczną wysokość, jaką zajmą wszystkie wiersze z uwzględnieniem zawijania
        var calculatedTableHeight = rowH // nagłówek tabeli
        if (wizyta.naprawy.isEmpty()) {
            calculatedTableHeight += rowH
        } else {
            for (naprawa in wizyta.naprawy) {
                val nazwaLayout = android.text.StaticLayout.Builder.obtain(naprawa.nazwa, 0, naprawa.nazwa.length, android.text.TextPaint(tableCellTextPaint), maxOf(10, col1Width))
                    .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .build()

                val notatkiTekst = if (naprawa.notatki.isNotBlank()) naprawa.notatki else ""
                val notatkiLayout = android.text.StaticLayout.Builder.obtain(notatkiTekst, 0, notatkiTekst.length, android.text.TextPaint(tableCellTextPaint), maxOf(10, col2Width))
                    .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .build()

                val maxLines = maxOf(nazwaLayout.lineCount, notatkiLayout.lineCount, 1)
                calculatedTableHeight += maxOf(rowH, (maxLines * 12f) + 10f)
            }
        }

        // Całkowita wysokość karty = nagłówek wizyty (ok. 52f) + wysokość tabeli + marginesy wewnętrzne
        val cardHeight = 52f + calculatedTableHeight + 12f

        if (y + cardHeight > 765f) {
            rozpocznijNowaStrone()
            y = 50f
        }

        // Rysujemy zaokrągloną ramkę karty o idealnie dopasowanej wysokości
        val cardRect = android.graphics.RectF(leftMargin, y, rightMargin, y + cardHeight)
        canvas.drawRoundRect(cardRect, 7f, 7f, whiteFillPaint)
        canvas.drawRoundRect(cardRect, 7f, 7f, cardBorderPaint)
        val innerLeft = leftMargin + 12f
        val innerRight = rightMargin - 12f

        val titleY = y + 18f

        // 1 linijka: Nazwa warsztatu
        canvas.drawText(wizyta.warsztat, innerLeft, titleY, cardTitleBluePaint)

        // Koszt wizyty po prawej stronie
        val kosztWizytyStr = wizyta.naprawy.groupBy { it.waluta.ifBlank { "PLN" } }.entries.joinToString(" · ") { entry ->
            val suma = entry.value.sumOf { parsujKwote(it.koszt) }
            "${formatujKwotePDF(suma)} ${entry.key}"
        }
        val szerKosztuWizyty = cardCostGreenPaint.measureText(kosztWizytyStr)
        canvas.drawText(kosztWizytyStr, innerRight - szerKosztuWizyty, titleY, cardCostGreenPaint)

        // 2 linijka: Nazwa auta + numer rejestracyjny
        val linijkaAuta = if (wizyta.pojazd.rejestracja.isNotBlank()) {
            "${wizyta.pojazd.markaModel} · ${wizyta.pojazd.rejestracja}"
        } else {
            wizyta.pojazd.markaModel
        }
        val line2Y = titleY + 14f
        canvas.drawText(linijkaAuta, innerLeft, line2Y, mileageGrayPaint)

        // 3 linijka: Przebieg + "Naprawy: ilość"
        val przebiegStr = if (wizyta.przebieg.isNotBlank()) "${wizyta.przebieg} km" else "-"
        val napisyNapraw = if (isEnglish) "Repairs" else "Naprawy"
        val linijkaTrzecia = "$przebiegStr · $napisyNapraw: ${wizyta.naprawy.size}"
        val line3Y = line2Y + 13f
        canvas.drawText(linijkaTrzecia, innerLeft, line3Y, mileageGrayPaint)

        val tableTop = line3Y + 8f
        val col1End = 320f
        val col2End = 425f

        // Rysujemy nagłówek tabeli
        canvas.drawRect(innerLeft, tableTop, innerRight, tableTop + rowH, tableHeaderBgPaint)
        canvas.drawRect(innerLeft, tableTop, innerRight, tableTop + rowH, tableGridPaint)
        canvas.drawLine(col1End, tableTop, col1End, tableTop + rowH, tableGridPaint)
        canvas.drawLine(col2End, tableTop, col2End, tableTop + rowH, tableGridPaint)

        val col1Title = if (isEnglish) "Service / Part" else "Wykonana usługa / część"
        val col2Title = if (isEnglish) "Notes" else "Notatki"
        val col3Title = if (isEnglish) "Cost" else "Koszt"

        canvas.drawText(col1Title, innerLeft + 6f, tableTop + 14.5f, tableHeaderTextPaint)
        canvas.drawText(col2Title, col1End + 6f, tableTop + 14.5f, tableHeaderTextPaint)
        val kosztHeaderWidth = tableHeaderTextPaint.measureText(col3Title)
        canvas.drawText(col3Title, innerRight - 6f - kosztHeaderWidth, tableTop + 14.5f, tableHeaderTextPaint)

        var currentRowTop = tableTop + rowH
        if (wizyta.naprawy.isEmpty()) {
            canvas.drawRect(innerLeft, currentRowTop, innerRight, currentRowTop + rowH, tableGridPaint)
            canvas.drawLine(col1End, currentRowTop, col1End, currentRowTop + rowH, tableGridPaint)
            canvas.drawLine(col2End, currentRowTop, col2End, currentRowTop + rowH, tableGridPaint)
            canvas.drawText(if (isEnglish) "No entries" else "Brak wpisów", innerLeft + 6f, currentRowTop + 14.5f, mileageGrayPaint)
        } else {
            for (naprawa in wizyta.naprawy) {
                // Precyzyjne szerokości kolumn (z marginesem 6px z obu stron)
                val col1Width = (col1End - innerLeft - 12f).toInt()
                val col2Width = (col2End - col1End - 12f).toInt()

                // StaticLayout dla nazwy (Kolumna 1) – teraz tekst zawinie się w granicach kolumny!
                val nazwaLayout = android.text.StaticLayout.Builder.obtain(naprawa.nazwa, 0, naprawa.nazwa.length, android.text.TextPaint(tableCellTextPaint), maxOf(10, col1Width))
                    .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .build()

                // StaticLayout dla notatek (Kolumna 2)
                val notatkiTekst = if (naprawa.notatki.isNotBlank()) naprawa.notatki else ""
                val notatkiLayout = android.text.StaticLayout.Builder.obtain(notatkiTekst, 0, notatkiTekst.length, android.text.TextPaint(tableCellTextPaint), maxOf(10, col2Width))
                    .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .build()

                // Dynamiczna wysokość wiersza na podstawie wyższego z układów tekstu
                val maxLinesInRow = maxOf(nazwaLayout.lineCount, notatkiLayout.lineCount, 1)
                val dynamicRowHeight = maxOf(rowH, (maxLinesInRow * 12f) + 10f)

                if (currentRowTop + dynamicRowHeight > 780f) {
                    rozpocznijNowaStrone()
                    currentRowTop = 50f
                }

                // Rysujemy obramowanie wiersza i linie pionowe
                canvas.drawRect(innerLeft, currentRowTop, innerRight, currentRowTop + dynamicRowHeight, tableGridPaint)
                canvas.drawLine(col1End, currentRowTop, col1End, currentRowTop + dynamicRowHeight, tableGridPaint)
                canvas.drawLine(col2End, currentRowTop, col2End, currentRowTop + dynamicRowHeight, tableGridPaint)

                // Rysujemy nazwę w kolumnie 1
                canvas.save()
                canvas.translate(innerLeft + 6f, currentRowTop + 6f)
                nazwaLayout.draw(canvas)
                canvas.restore()

                // Rysujemy notatki w kolumnie 2
                if (notatkiTekst.isNotBlank()) {
                    canvas.save()
                    canvas.translate(col1End + 6f, currentRowTop + 6f)
                    notatkiLayout.draw(canvas)
                    canvas.restore()
                }

                // Rysujemy koszt w kolumnie 3 (wyrównany do prawej)
                val kosztNaprawy = parsujKwote(naprawa.koszt)
                val kosztNaprawyStr = "${formatujKwotePDF(kosztNaprawy)} ${naprawa.waluta}"
                val szerKosztuNaprawy = tableCellTextPaint.measureText(kosztNaprawyStr)
                canvas.drawText(kosztNaprawyStr, innerRight - 6f - szerKosztuNaprawy, currentRowTop + 14.5f, tableCellTextPaint)

                currentRowTop += dynamicRowHeight
            }
        }
        y += cardHeight + 16f
    }
    if (y > 780f) {
        rozpocznijNowaStrone()
        y = 50f
    }
    y += 4f
    canvas.drawLine(leftMargin, y, rightMargin, y, mainDividerPaint)
    y += 20f
    // Łączny koszt w stopce w formacie 1200.00 PLN
    val laczneNaprawy = posortowaneWizyty.flatMap { it.naprawy }
    val lacznyPodsumowanieStr = laczneNaprawy.groupBy { it.waluta.ifBlank { "PLN" } }.entries.joinToString(" · ") { entry ->
        val suma = entry.value.sumOf { parsujKwote(it.koszt) }
        "${formatujKwotePDF(suma)} ${entry.key}"
    }
    val lacznyKosztStr = if (isEnglish) "TOTAL COST: $lacznyPodsumowanieStr" else "ŁĄCZNY KOSZT: $lacznyPodsumowanieStr"
    val szerLacznego = footerTotalBluePaint.measureText(lacznyKosztStr)
    canvas.drawText(lacznyKosztStr, rightMargin - szerLacznego, y, footerTotalBluePaint)
    pdfDocument.finishPage(page)
    val tempFile = File(context.cacheDir, "Podglad_Raport_Napraw.pdf")
    return try {
        FileOutputStream(tempFile).use { out -> pdfDocument.writeTo(out) }
        pdfDocument.close()
        tempFile
    } catch (e: Exception) {
        e.printStackTrace()
        pdfDocument.close()
        null
    }
}

fun drukujPlikPDF(context: Context, pdfFile: File) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "AutoTermin_Raport_Napraw"
    val printAdapter = object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback?.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder("Raport_Napraw.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback?.onLayoutFinished(info, true)
        }
        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor?,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback?
        ) {
            if (destination == null) {
                callback?.onWriteFailed("Brak dostępu")
                return
            }
            try {
                FileInputStream(pdfFile).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        input.copyTo(output)
                    }
                }
                callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                e.printStackTrace()
                callback?.onWriteFailed(e.message)
            }
        }
    }
    printManager.print(jobName, printAdapter, null)
}

fun renderujStronyPDF(pdfFile: File): List<Bitmap> {
    val bitmaps = mutableListOf<Bitmap>()
    try {
        val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val width = page.width * 3
            val height = page.height * 3
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmaps.add(bitmap)
        }
        renderer.close()
        pfd.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return bitmaps
}

fun zaplanujPowiadomienie(
    context: Context,
    pojazd: Pojazd,
    typ: String,
    dataWaznosci: String,
    przypomnienieData: String,
    przypomnienieGodzina: String,
    terminId: Int = (System.currentTimeMillis() % 10000).toInt(),
    sztywnyAlert: Boolean = false
) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val teraz = System.currentTimeMillis()

    // 1. NIEZALEŻNY ALERT RĘCZNY (własny requestCode = terminId * 100)
    try {
        if (przypomnienieData.isNotBlank() && przypomnienieGodzina.isNotBlank()) {
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            val targetDate = sdf.parse("$przypomnienieData $przypomnienieGodzina")
            if (targetDate != null && targetDate.time > teraz) {
                val intent = Intent(context, PowiadomienieReceiver::class.java).apply {
                    putExtra("auto", pojazd.markaModel)
                    putExtra("typ", typ)
                    putExtra("data", dataWaznosci)
                    putExtra("docelowa_zakladka", "Terminy") // Dodajemy informację o zakładce docelowej
                }
                val requestCodeReczny = terminId * 100
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCodeReczny,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    targetDate.time,
                    pendingIntent
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // 2. NIEZALEŻNY SZTYWNY ALERT (5, 4, 3, 2, 1 dzień przed terminem codziennie o 09:00)
    // Używa osobnych kodów: terminId * 100 + 1..5, dzięki czemu nigdy nie gryzie się z ręcznym
    try {
        val sdfData = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val dataDocelowa = sdfData.parse(dataWaznosci)
        if (dataDocelowa != null) {
            for (dniPrzed in 1..5) {
                val requestCodeSztywny = terminId * 100 + dniPrzed
                val intent = Intent(context, PowiadomienieReceiver::class.java).apply {
                    putExtra("auto", pojazd.markaModel)
                    putExtra("typ", typ)
                    putExtra("data", dataWaznosci)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCodeSztywny,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                if (sztywnyAlert) {
                    val cal = Calendar.getInstance().apply {
                        time = dataDocelowa
                        add(Calendar.DAY_OF_YEAR, -dniPrzed)
                        set(Calendar.HOUR_OF_DAY, 9)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    // Planujemy tylko te dni z 5-dniowego okresu, które jeszcze nie minęły
                    if (cal.timeInMillis > teraz) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            cal.timeInMillis,
                            pendingIntent
                        )
                    }
                } else {
                    // Jeśli użytkownik wyłączył suwak, anulujemy tylko sztywne alerty
                    alarmManager.cancel(pendingIntent)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// === GŁÓWNY EKRAN Z NAWIGACJĄ ===
@Composable
fun GlownyEkranAplikacji(startowaZakladka: String = "Pulpit") {
    var aktualnaZakladka by rememberSaveable { mutableStateOf(startowaZakladka) }
    var poprzedniaZakladka by rememberSaveable { mutableStateOf("Więcej") }
    val context = LocalContext.current

    val listaAut = remember { mutableStateListOf<Pojazd>().apply { addAll(wczytajAuta(context)) } }
    val listaTerminow = remember { mutableStateListOf<Termin>().apply { addAll(wczytajTerminy(context)) } }
    val listaWizyt = remember { mutableStateListOf<Wizyta>().apply { addAll(wczytajWizyty(context)) } }
    val listaDokumentow = remember { mutableStateListOf<Dokument>().apply { addAll(wczytajDokumenty(context)) } }

    val archiwumAut = remember { mutableStateListOf<Pojazd>().apply { addAll(wczytajAuta(context, "archiwum_auta")) } }
    val archiwumTerminow = remember { mutableStateListOf<Termin>().apply { addAll(wczytajTerminy(context, "archiwum_terminy")) } }
    val archiwumWizyt = remember { mutableStateListOf<Wizyta>().apply { addAll(wczytajWizyty(context, "archiwum_wizyty")) } }
    val archiwumNapraw = remember { mutableStateListOf<ZarchiwizowanaNaprawa>().apply { addAll(wczytajNaprawyArchiwum(context)) } }
    val archiwumDokumentow = remember { mutableStateListOf<Dokument>().apply { addAll(wczytajDokumenty(context, "archiwum_dokumenty")) } }

    var ukrytePowiadomienia by remember { mutableStateOf(wczytajUkrytePowiadomienia(context)) }
    var wybranaWizyta by remember { mutableStateOf<Wizyta?>(null) }
    val launcherUprawnien = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    BackHandler(enabled = true) {
        when (aktualnaZakladka) {
            "SzczegolyWizyty" -> aktualnaZakladka = "DziennikNapraw"
            "DziennikNapraw" -> aktualnaZakladka = poprzedniaZakladka
            "Archiwum" -> aktualnaZakladka = poprzedniaZakladka
            "RaportPDF" -> aktualnaZakladka = poprzedniaZakladka
            "Dokumenty" -> aktualnaZakladka = poprzedniaZakladka
            "Pulpit" -> {}
            else -> aktualnaZakladka = "Pulpit"
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            launcherUprawnien.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        }
    }

    Scaffold(
        bottomBar = { DolnyPasekNawigacji(aktualnaZakladka, { aktualnaZakladka = it }) },
        containerColor = BgColor
    ) { paddingValues ->
        when (aktualnaZakladka) {
            "Pulpit" -> EkranPulpitu(
                paddingValues = paddingValues,
                listaAut = listaAut,
                listaTerminow = listaTerminow,
                ukrytePowiadomienia = ukrytePowiadomienia,
                onUkryjPowiadomienie = { id ->
                    val noweUkryte = ukrytePowiadomienia + id
                    ukrytePowiadomienia = noweUkryte
                    zapiszUkrytePowiadomienia(context, noweUkryte)
                },
                onPrzejdzDoDziennika = { poprzedniaZakladka = "Pulpit"; aktualnaZakladka = "DziennikNapraw" },
                onPrzejdzDoRaportuPDF = { poprzedniaZakladka = "Pulpit"; aktualnaZakladka = "RaportPDF" },
                onPrzejdzDoArchiwum = { poprzedniaZakladka = "Pulpit"; aktualnaZakladka = "Archiwum" },
                onPrzejdzDoDokumentow = { poprzedniaZakladka = "Pulpit"; aktualnaZakladka = "Dokumenty" }
            )
            "Auta" -> EkranAut(paddingValues, listaAut, listaTerminow, archiwumAut, archiwumTerminow)
            "Terminy" -> EkranTerminow(paddingValues, listaAut, listaTerminow, archiwumTerminow)
            "Więcej" -> EkranWiecej(
                paddingValues = paddingValues,
                onPrzejdzDoDziennika = { poprzedniaZakladka = "Więcej"; aktualnaZakladka = "DziennikNapraw" },
                onPrzejdzDoRaportuPDF = { poprzedniaZakladka = "Więcej"; aktualnaZakladka = "RaportPDF" },
                onPrzejdzDoArchiwum = { poprzedniaZakladka = "Więcej"; aktualnaZakladka = "Archiwum" },
                onPrzejdzDoDokumentow = { poprzedniaZakladka = "Więcej"; aktualnaZakladka = "Dokumenty" }
            )
            "DziennikNapraw" -> EkranDziennikNapraw(
                paddingValues = paddingValues,
                listaAut = listaAut,
                listaWizyt = listaWizyt,
                archiwumWizyt = archiwumWizyt,
                onPowrot = { aktualnaZakladka = poprzedniaZakladka },
                onPrzejdzDoSzczegolow = { wizyta -> wybranaWizyta = wizyta; aktualnaZakladka = "SzczegolyWizyty" }
            )
            "SzczegolyWizyty" -> EkranSzczegolyWizyty(
                paddingValues = paddingValues,
                wizytaId = wybranaWizyta?.id,
                listaWizyt = listaWizyt,
                archiwumNapraw = archiwumNapraw,
                onPowrot = { aktualnaZakladka = "DziennikNapraw" }
            )
            "RaportPDF" -> EkranRaportPDF(paddingValues, listaAut, listaWizyt, onPowrot = { aktualnaZakladka = poprzedniaZakladka })
            "Archiwum" -> EkranArchiwum(
                paddingValues = paddingValues,
                listaAut = listaAut,
                listaTerminow = listaTerminow,
                listaWizyt = listaWizyt,
                listaDokumentow = listaDokumentow,
                archiwumAut = archiwumAut,
                archiwumTerminow = archiwumTerminow,
                archiwumWizyt = archiwumWizyt,
                archiwumNapraw = archiwumNapraw,
                archiwumDokumentow = archiwumDokumentow,
                onPowrot = { aktualnaZakladka = poprzedniaZakladka }
            )
            "Dokumenty" -> EkranDokumenty(paddingValues, listaDokumentow, archiwumDokumentow, onPowrot = { aktualnaZakladka = poprzedniaZakladka })
        }
    }
}

// === 1. EKRAN PULPITU ===
@Composable
fun EkranPulpitu(
    paddingValues: PaddingValues,
    listaAut: SnapshotStateList<Pojazd>,
    listaTerminow: SnapshotStateList<Termin>,
    ukrytePowiadomienia: Set<Int>,
    onUkryjPowiadomienie: (Int) -> Unit,
    onPrzejdzDoDziennika: () -> Unit,
    onPrzejdzDoRaportuPDF: () -> Unit,
    onPrzejdzDoArchiwum: () -> Unit,
    onPrzejdzDoDokumentow: () -> Unit
) {
    val lang = LocalLanguage.current
    var wybraneAutoFiltru by remember { mutableStateOf<Pojazd?>(null) }
    var pokazWszystkieTerminy by remember { mutableStateOf(false) }
    val format = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    val posortowaneTerminy = remember(listaTerminow.toList()) {
        listaTerminow.sortedBy { try { format.parse(it.data)?.time ?: Long.MAX_VALUE } catch (e: Exception) { Long.MAX_VALUE } }
    }
    val przefiltrowaneTerminy = if (wybraneAutoFiltru == null) posortowaneTerminy else posortowaneTerminy.filter { it.pojazd.id == wybraneAutoFiltru!!.id }
    val terminyDoWyswietlenia = if (pokazWszystkieTerminy) przefiltrowaneTerminy else przefiltrowaneTerminy.take(2)
    LazyColumn(
        modifier = Modifier.padding(paddingValues).fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item { TopBar(listaTerminow, ukrytePowiadomienia, onUkryjPowiadomienie) }
        item { KartaPodsumowania(listaAut, listaTerminow, wybraneAutoFiltru) }
        if (listaAut.isEmpty()) {
            item { Text(lang.t("Brak dodanych pojazdów.", "No vehicles added."), color = TextGray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 24.dp)) }
        } else {
            item { ListaAutHorizontal(listaAut, wybraneAutoFiltru) { wybraneAuto -> wybraneAutoFiltru = wybraneAuto; pokazWszystkieTerminy = false } }
        }
        item { NaglowekTerminow(listaAut, wybraneAutoFiltru) { wybraneAuto -> wybraneAutoFiltru = wybraneAuto; pokazWszystkieTerminy = false } }
        if (przefiltrowaneTerminy.isEmpty()) {
            item { Text(lang.t("Brak zbliżających się terminów.", "No upcoming schedules."), color = TextGray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 28.dp)) }
        } else {
            item { ListaTerminow(listaTerminow = terminyDoWyswietlenia, modifier = Modifier.padding(bottom = if (przefiltrowaneTerminy.size > 2) 12.dp else 28.dp)) }
            if (przefiltrowaneTerminy.size > 2) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), contentAlignment = Alignment.Center) {
                        Row(
                            modifier = Modifier.background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp)).clip(RoundedCornerShape(9.dp)).clickable { pokazWszystkieTerminy = !pokazWszystkieTerminy }.padding(horizontal = 16.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = if (pokazWszystkieTerminy) lang.t("Zwiń", "Collapse") else lang.t("Wyświetl więcej", "Show more"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        item { SekcjaSzybkiDostep(onPrzejdzDoDziennika, onPrzejdzDoRaportuPDF, onPrzejdzDoArchiwum, onPrzejdzDoDokumentow) }
    }
}

// === 2. EKRAN AUT ===
@Composable
fun EkranAut(
    paddingValues: PaddingValues,
    listaAut: SnapshotStateList<Pojazd>,
    listaTerminow: SnapshotStateList<Termin>,
    archiwumAut: SnapshotStateList<Pojazd>,
    archiwumTerminow: SnapshotStateList<Termin>
) {
    val lang = LocalLanguage.current
    var pokazFormularzDodawania by remember { mutableStateOf(false) }
    var autoDoEdycji by remember { mutableStateOf<Pojazd?>(null) }
    var autoDoUsuniecia by remember { mutableStateOf<Pojazd?>(null) }
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.padding(paddingValues).fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(lang.t("Zarządzaj autami", "Manage vehicles"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp)).clip(RoundedCornerShape(9.dp)).clickable { pokazFormularzDodawania = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(lang.t("Dodaj auto", "Add vehicle"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(horizontalTaperingBrush))
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (listaAut.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) { Text(lang.t("Pusto! Kliknij przycisk powyżej, aby dodać auto.", "Empty! Click above to add a vehicle."), color = TextGray, fontSize = 14.sp) } }
        } else {
            items(listaAut, key = { it.id }) { auto ->
                KartaPionowaAuta(auto = auto, onEdytuj = { autoDoEdycji = auto }, onUsun = { autoDoUsuniecia = auto })
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
    if (pokazFormularzDodawania || autoDoEdycji != null) {
        DialogFormularzAuta(
            autoPoczatkowe = autoDoEdycji,
            onZapisz = { nazwa, rejestracja, awatarId ->
                if (autoDoEdycji != null) {
                    val index = listaAut.indexOfFirst { it.id == autoDoEdycji!!.id }
                    if (index != -1) {
                        listaAut[index] = autoDoEdycji!!.copy(markaModel = nazwa, rejestracja = rejestracja, avatarRes = awatarId)
                        for (i in 0 until listaTerminow.size) {
                            if (listaTerminow[i].pojazd.id == autoDoEdycji!!.id) {
                                listaTerminow[i] = listaTerminow[i].copy(pojazd = listaAut[index])
                            }
                        }
                    }
                } else {
                    val maxId = (listaAut + archiwumAut).maxOfOrNull { it.id } ?: 0
                    listaAut.add(Pojazd(id = maxId + 1, markaModel = nazwa, rejestracja = rejestracja, avatarRes = awatarId))
                }
                zapiszDane(context, listaAut, listaTerminow)
                pokazFormularzDodawania = false
                autoDoEdycji = null
            },
            onAnuluj = { pokazFormularzDodawania = false; autoDoEdycji = null }
        )
    }
    if (autoDoUsuniecia != null) {
        DialogPotwierdzeniaUsuniecia(
            tytul = lang.t("Usuń auto", "Delete vehicle"),
            tresc = lang.t("Czy na pewno chcesz usunąć pojazd ${autoDoUsuniecia?.markaModel}? Pojazd oraz jego terminy trafią do archiwum.", "Are you sure you want to delete ${autoDoUsuniecia?.markaModel}? It will be moved to archive."),
            onPotwierdz = {
                val usuwane = autoDoUsuniecia!!
                val powiazaneTerminy = listaTerminow.filter { it.pojazd.id == usuwane.id }
                if (archiwumAut.none { it.id == usuwane.id }) archiwumAut.add(0, usuwane)
                archiwumTerminow.addAll(0, powiazaneTerminy)
                listaTerminow.removeAll { it.pojazd.id == usuwane.id }
                listaAut.remove(usuwane)
                zapiszDane(context, listaAut, listaTerminow)
                zapiszDane(context, archiwumAut, archiwumTerminow, "archiwum_auta", "archiwum_terminy")
                Toast.makeText(context, lang.t("Przeniesiono pojazd do archiwum", "Vehicle moved to archive"), Toast.LENGTH_SHORT).show()
                autoDoUsuniecia = null
            },
            onAnuluj = { autoDoUsuniecia = null }
        )
    }
}

// === 3. EKRAN TERMINÓW ===
@Composable
fun EkranTerminow(
    paddingValues: PaddingValues,
    listaAut: SnapshotStateList<Pojazd>,
    listaTerminow: SnapshotStateList<Termin>,
    archiwumTerminow: SnapshotStateList<Termin>
) {
    val lang = LocalLanguage.current
    var pokazFormularzTerminu by remember { mutableStateOf(false) }
    var terminDoUsuniecia by remember { mutableStateOf<Termin?>(null) }
    var terminDoEdycji by remember { mutableStateOf<Termin?>(null) }
    var terminDoPrzypomnieniaAlertu by remember { mutableStateOf<Pair<Termin, Int>?>(null) }
    var wybraneAutoFiltru by remember { mutableStateOf<Pojazd?>(null) }
    var rozwinMenuAut by remember { mutableStateOf(false) }
    var wybranyTypFiltru by remember { mutableStateOf<String?>(null) }
    var rozwinMenuTypow by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val format = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    val posortowaneTerminy = remember(listaTerminow.toList()) {
        listaTerminow.sortedBy { try { format.parse(it.data)?.time ?: Long.MAX_VALUE } catch (e: Exception) { Long.MAX_VALUE } }
    }
    val przefiltrowaneTerminy = posortowaneTerminy.filter { termin ->
        val pasujeAuto = wybraneAutoFiltru == null || termin.pojazd.id == wybraneAutoFiltru!!.id
        val pasujeTyp = wybranyTypFiltru == null || termin.typ == wybranyTypFiltru
        pasujeAuto && pasujeTyp
    }
    val unikalneTypy = posortowaneTerminy.map { it.typ }.distinct().sorted()
    LazyColumn(
        modifier = Modifier.padding(paddingValues).fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(lang.t("Wszystkie terminy", "All schedules"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp)).clip(RoundedCornerShape(9.dp)).clickable { pokazFormularzTerminu = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(lang.t("Dodaj termin", "Add schedule"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(horizontalTaperingBrush))
            Spacer(modifier = Modifier.height(14.dp))
            if (listaAut.isNotEmpty() || posortowaneTerminy.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Lewy filtr (Typy) - zajmuje równo połowę wiersza
                    Box(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardGradient, RoundedCornerShape(9.dp))
                                .border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                                .clip(RoundedCornerShape(9.dp))
                                .clickable { rozwinMenuTypow = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (wybranyTypFiltru != null) translateTyp(wybranyTypFiltru!!, lang.isEnglish) else lang.t("Wszystkie typy", "All types"),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = rozwinMenuTypow,
                            onDismissRequest = { rozwinMenuTypow = false },
                            // Brak sztywnej szerokości – menu dopasuje się do długości nazw typów
                            modifier = Modifier.background(CardGradient).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { wybranyTypFiltru = null; rozwinMenuTypow = false }
                                        .padding(vertical = 10.dp, horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = lang.t("Wszystkie typy", "All types"),
                                        color = if (wybranyTypFiltru == null) BlueAccent else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = if (wybranyTypFiltru == null) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 8.dp).background(horizontalTaperingBrush))
                                unikalneTypy.forEachIndexed { index, typ ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { wybranyTypFiltru = typ; rozwinMenuTypow = false }
                                            .padding(vertical = 10.dp, horizontal = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = translateTyp(typ, lang.isEnglish),
                                            color = if (wybranyTypFiltru == typ) BlueAccent else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = if (wybranyTypFiltru == typ) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (index < unikalneTypy.size - 1) {
                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 8.dp).background(horizontalTaperingBrush))
                                    }
                                }
                            }
                        }
                    }

                    // Prawy filtr (Auta) - zajmuje drugą połowę wiersza (dokładnie taka sama szerokość)
                    // Prawy filtr (Auta) z płynnym i równym menu rozwijanym
                    // Prawy filtr (Auta) - równe pole, a menu dopasowane szerokością do nazw
                    // Prawy filtr (Auta) z wyrównaniem rozwijanego menu do prawej krawędzi
                    // Prawy filtr (Auta) z kotwicą w prawym dolnym rogu przycisku
                    Box(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardGradient, RoundedCornerShape(9.dp))
                                .border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                                .clip(RoundedCornerShape(9.dp))
                                .clickable { rozwinMenuAut = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = wybraneAutoFiltru?.markaModel ?: lang.t("Wszystkie auta", "All vehicles"),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }

                        // Kotwica w prawym dolnym rogu pola filtrowania wymusza zrównanie menu do prawej krawędzi
                        Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                            DropdownMenu(
                                expanded = rozwinMenuAut,
                                onDismissRequest = { rozwinMenuAut = false },
                                modifier = Modifier
                                    .background(CardGradient)
                                    .border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                            ) {
                                Column(modifier = Modifier.wrapContentWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { wybraneAutoFiltru = null; rozwinMenuAut = false }
                                            .padding(vertical = 10.dp, horizontal = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = lang.t("Wszystkie auta", "All vehicles"),
                                            color = if (wybraneAutoFiltru == null) BlueAccent else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = if (wybraneAutoFiltru == null) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 8.dp).background(horizontalTaperingBrush))
                                    listaAut.forEachIndexed { index, auto ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { wybraneAutoFiltru = auto; rozwinMenuAut = false }
                                                .padding(vertical = 10.dp, horizontal = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = auto.markaModel,
                                                color = if (wybraneAutoFiltru?.id == auto.id) BlueAccent else Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = if (wybraneAutoFiltru?.id == auto.id) FontWeight.Bold else FontWeight.Normal,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (index < listaAut.size - 1) {
                                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 8.dp).background(horizontalTaperingBrush))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
        if (przefiltrowaneTerminy.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) { Text(lang.t("Brak dodanych terminów.", "No schedules added."), color = TextGray, fontSize = 14.sp) } }
        } else {
            items(przefiltrowaneTerminy, key = { it.id }) { termin ->
                KartaTerminu(
                    termin = termin,
                    onUsun = { terminDoUsuniecia = termin },
                    onEdytuj = { terminDoEdycji = termin },
                    onPrzedluz = { miesiace ->
                        try {
                            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                            val cal = Calendar.getInstance()
                            sdf.parse(termin.data)?.let { cal.time = it }
                            cal.add(Calendar.MONTH, miesiace)

                            DatePickerDialog(
                                context,
                                android.R.style.Theme_Material_Dialog,
                                { _, rok, miesiac, dzien ->
                                    val nowaDataStr = String.format("%02d.%02d.%04d", dzien, miesiac + 1, rok)
                                    val index = listaTerminow.indexOfFirst { it.id == termin.id }
                                    if (index != -1) {
                                        val zaktualizowany = termin.copy(data = nowaDataStr)
                                        listaTerminow[index] = zaktualizowany
                                        zapiszDane(context, listaAut, listaTerminow)
                                        // Wywołujemy okienko przypomnienia na środku ekranu
                                        terminDoPrzypomnieniaAlertu = Pair(zaktualizowany, miesiace)
                                    }
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
    if (pokazFormularzTerminu) {
        DialogFormularzTerminu(
            listaAut = listaAut,
            onZapisz = { wybraneAuto, typ, data, dataPrzyp, godzPrzyp, opis, sztywnyAlert ->
                val noweId = ((listaTerminow + archiwumTerminow).maxOfOrNull { it.id } ?: 0) + 1
                listaTerminow.add(Termin(noweId, wybraneAuto, typ, data, dataPrzyp, godzPrzyp, opis, sztywnyAlert))
                zapiszDane(context, listaAut, listaTerminow)
                pokazFormularzTerminu = false
                zaplanujPowiadomienie(context, wybraneAuto, typ, data, dataPrzyp, godzPrzyp, noweId, sztywnyAlert)
            },
            onAnuluj = { pokazFormularzTerminu = false }
        )
    }
    if (terminDoUsuniecia != null) {
        DialogPotwierdzeniaUsuniecia(
            tytul = lang.t("Usuń termin", "Delete schedule"),
            tresc = lang.t("Czy na pewno chcesz usunąć termin \"${translateTyp(terminDoUsuniecia?.typ ?: "", lang.isEnglish)}\"? Wpis trafi do archiwum.", "Are you sure you want to delete \"${translateTyp(terminDoUsuniecia?.typ ?: "", lang.isEnglish)}\"? It will be moved to archive."),
            onPotwierdz = {
                val usuwany = terminDoUsuniecia!!
                // Przy usuwaniu terminu anulujemy jego sztywne alerty
                zaplanujPowiadomienie(context, usuwany.pojazd, usuwany.typ, usuwany.data, "", "", usuwany.id, false)
                archiwumTerminow.add(0, usuwany)
                listaTerminow.remove(usuwany)
                val archAuta = wczytajAuta(context, "archiwum_auta")
                zapiszDane(context, listaAut, listaTerminow)
                zapiszDane(context, archAuta, archiwumTerminow, "archiwum_auta", "archiwum_terminy")
                Toast.makeText(context, lang.t("Przeniesiono termin do archiwum", "Schedule moved to archive"), Toast.LENGTH_SHORT).show()
                terminDoUsuniecia = null
            },
            onAnuluj = { terminDoUsuniecia = null }
        )
    }
    if (terminDoEdycji != null) {
        DialogFormularzEdycjiTerminu(
            termin = terminDoEdycji!!,
            listaAut = listaAut,
            onZapisz = { wybraneAuto, typ, data, dataPrzyp, godzPrzyp, opis, sztywnyAlert ->
                val edytowanyId = terminDoEdycji!!.id
                val index = listaTerminow.indexOfFirst { it.id == edytowanyId }
                if (index != -1) {
                    listaTerminow[index] = terminDoEdycji!!.copy(
                        pojazd = wybraneAuto,
                        typ = typ,
                        data = data,
                        przypomnienieData = dataPrzyp,
                        przypomnienieGodzina = godzPrzyp,
                        opis = opis,
                        sztywnyAlert = sztywnyAlert
                    )
                    zapiszDane(context, listaAut, listaTerminow)
                    zaplanujPowiadomienie(context, wybraneAuto, typ, data, dataPrzyp, godzPrzyp, edytowanyId, sztywnyAlert)
                    Toast.makeText(context, lang.t("Zaktualizowano termin", "Schedule updated"), Toast.LENGTH_SHORT).show()
                }
                terminDoEdycji = null
            },
            onAnuluj = { terminDoEdycji = null }
        )
    }
    if (terminDoEdycji != null) {
        DialogFormularzEdycjiTerminu(
            termin = terminDoEdycji!!,
            listaAut = listaAut,
            onZapisz = { wybraneAuto, typ, data, dataPrzyp, godzPrzyp, opis, sztywnyAlert ->
                val edytowanyId = terminDoEdycji!!.id
                val index = listaTerminow.indexOfFirst { it.id == edytowanyId }
                if (index != -1) {
                    listaTerminow[index] = terminDoEdycji!!.copy(
                        pojazd = wybraneAuto,
                        typ = typ,
                        data = data,
                        przypomnienieData = dataPrzyp,
                        przypomnienieGodzina = godzPrzyp,
                        opis = opis,
                        sztywnyAlert = sztywnyAlert
                    )
                    zapiszDane(context, listaAut, listaTerminow)
                    zaplanujPowiadomienie(context, wybraneAuto, typ, data, dataPrzyp, godzPrzyp, edytowanyId, sztywnyAlert)
                    Toast.makeText(context, lang.t("Zaktualizowano termin", "Schedule updated"), Toast.LENGTH_SHORT).show()
                }
                terminDoEdycji = null
            },
            onAnuluj = { terminDoEdycji = null }
        )
    }

    if (terminDoPrzypomnieniaAlertu != null) {
        val (aktualnyTermin, przesuniecieMiesiecy) = terminDoPrzypomnieniaAlertu!!
        AlertDialog(
            onDismissRequest = { terminDoPrzypomnieniaAlertu = null },
            modifier = Modifier
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                .background(CardGradient, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.Transparent,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = YellowAccent, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = lang.t("Pamiętaj o alercie!", "Don't forget the alert!"),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = lang.t(
                        "Termin ważności został przedłużony do ${aktualnyTermin.data}.\n\nPamiętaj, aby ustawić nową datę i godzinę powiadomienia (alertu) dla tego terminu!",
                        "Expiration date extended to ${aktualnyTermin.data}.\n\nRemember to set a new notification date and time (alert) for this schedule!"
                    ),
                    color = TextGray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                        val calAlert = Calendar.getInstance()
                        try {
                            sdf.parse(aktualnyTermin.przypomnienieData)?.let { calAlert.time = it }
                            calAlert.add(Calendar.MONTH, przesuniecieMiesiecy)
                        } catch (e: Exception) {}

                        terminDoPrzypomnieniaAlertu = null

                        // Otwiera od razu kalendarz z przesuniętą datą alertu
                        DatePickerDialog(
                            context,
                            android.R.style.Theme_Material_Dialog,
                            { _, rok, miesiac, dzien ->
                                val nowaDataAlertu = String.format("%02d.%02d.%04d", dzien, miesiac + 1, rok)

                                // Po wyborze daty od razu pytamy o godzinę
                                val parts = aktualnyTermin.przypomnienieGodzina.split(":")
                                val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
                                val m = parts.getOrNull(1)?.toIntOrNull() ?: 0

                                TimePickerDialog(
                                    context,
                                    android.R.style.Theme_Material_Dialog,
                                    { _, godz, min ->
                                        val nowaGodzinaAlertu = String.format("%02d:%02d", godz, min)
                                        val idx = listaTerminow.indexOfFirst { it.id == aktualnyTermin.id }
                                        if (idx != -1) {
                                            listaTerminow[idx] = listaTerminow[idx].copy(
                                                przypomnienieData = nowaDataAlertu,
                                                przypomnienieGodzina = nowaGodzinaAlertu
                                            )
                                            zapiszDane(context, listaAut, listaTerminow)
                                            zaplanujPowiadomienie(
                                                context,
                                                aktualnyTermin.pojazd,
                                                aktualnyTermin.typ,
                                                aktualnyTermin.data,
                                                nowaDataAlertu,
                                                nowaGodzinaAlertu,
                                                aktualnyTermin.id,
                                                aktualnyTermin.sztywnyAlert
                                            )
                                            Toast.makeText(context, lang.t("Ustawiono nowy alert: $nowaDataAlertu o $nowaGodzinaAlertu", "New alert set: $nowaDataAlertu at $nowaGodzinaAlertu"), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    h, m, true
                                ).show()
                            },
                            calAlert.get(Calendar.YEAR),
                            calAlert.get(Calendar.MONTH),
                            calAlert.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
                ) {
                    Text(lang.t("Ustaw alert teraz", "Set alert now"), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { terminDoPrzypomnieniaAlertu = null }) {
                    Text(lang.t("Później", "Later"), color = TextGray)
                }
            }
        )
    }
}

@Composable
fun DialogFormularzEdycjiTerminu(
    termin: Termin,
    listaAut: List<Pojazd>,
    onZapisz: (Pojazd, String, String, String, String, String) -> Unit,
    onAnuluj: () -> Unit
) {
    val lang = LocalLanguage.current
    val context = LocalContext.current
    val kalendarz = Calendar.getInstance()
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    var wybraneAuto by remember { mutableStateOf(termin.pojazd) }
    val typyTerminow = listOf("OC", "AC", "OC/AC", "Badanie techniczne", "Wymiana opon", "Wymiana oleju/filtrów", "Serwis klimatyzacji", "Inne")
    var wybranyTyp by remember { mutableStateOf(termin.typ) }
    var menuRozwiniete by remember { mutableStateOf(false) }
    var dodatkowyOpis by remember { mutableStateOf(termin.opis) }
    var wprowadzonaData by remember { mutableStateOf(termin.data) }
    var recznaDataPrzyp by remember { mutableStateOf(termin.przypomnienieData) }
    var recznaGodzinaPrzyp by remember { mutableStateOf(termin.przypomnienieGodzina) }

    val datePickerWaznosc = DatePickerDialog(
        context,
        android.R.style.Theme_Material_Dialog,
        { _, rok, miesiac, dzien -> wprowadzonaData = String.format("%02d.%02d.%04d", dzien, miesiac + 1, rok) },
        try { sdf.parse(termin.data)?.let { Calendar.getInstance().apply { time = it }.get(Calendar.YEAR) } ?: kalendarz.get(Calendar.YEAR) } catch(e:Exception){kalendarz.get(Calendar.YEAR)},
        try { sdf.parse(termin.data)?.let { Calendar.getInstance().apply { time = it }.get(Calendar.MONTH) } ?: kalendarz.get(Calendar.MONTH) } catch(e:Exception){kalendarz.get(Calendar.MONTH)},
        try { sdf.parse(termin.data)?.let { Calendar.getInstance().apply { time = it }.get(Calendar.DAY_OF_MONTH) } ?: kalendarz.get(Calendar.DAY_OF_MONTH) } catch(e:Exception){kalendarz.get(Calendar.DAY_OF_MONTH)}
    )
    val datePickerPrzypomnienie = DatePickerDialog(
        context,
        android.R.style.Theme_Material_Dialog,
        { _, rok, miesiac, dzien -> recznaDataPrzyp = String.format("%02d.%02d.%04d", dzien, miesiac + 1, rok) },
        kalendarz.get(Calendar.YEAR),
        kalendarz.get(Calendar.MONTH),
        kalendarz.get(Calendar.DAY_OF_MONTH)
    )

    AlertDialog(
        onDismissRequest = onAnuluj,
        modifier = Modifier.border(1.dp, CardBorder, RoundedCornerShape(16.dp)).background(CardGradient, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.Transparent,
        title = { Text(lang.t("Edytuj termin", "Edit schedule"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(lang.t("Wybierz pojazd:", "Select vehicle:"), color = TextGray, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listaAut) { auto ->
                        val isSelected = wybraneAuto.id == auto.id
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(id = auto.avatarRes),
                                contentDescription = auto.markaModel,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Color.Transparent)
                                    .border(width = if (isSelected) 1.5.dp else 0.dp, color = if (isSelected) BlueAccent else Color.Transparent, shape = RoundedCornerShape(9.dp))
                                    .padding(if (isSelected) 3.dp else 0.dp)
                                    .clickable { wybraneAuto = auto }
                            )
                            Text(auto.rejestracja.ifBlank { auto.markaModel }, color = if (isSelected) Color.White else TextGray, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().background(CardBgColor, RoundedCornerShape(12.dp)).border(1.dp, CardBorder, RoundedCornerShape(12.dp)).clickable { menuRozwiniete = true }.padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(getIkonaDlaTypu(wybranyTyp), contentDescription = null, tint = BlueAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = translateTyp(wybranyTyp, lang.isEnglish), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextGray)
                    }
                    DropdownMenu(
                        expanded = menuRozwiniete,
                        onDismissRequest = { menuRozwiniete = false },
                        modifier = Modifier.background(CardGradient).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                    ) {
                        typyTerminow.forEach { typ ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(getIkonaDlaTypu(typ), contentDescription = null, tint = if (wybranyTyp == typ) BlueAccent else TextGray, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(translateTyp(typ, lang.isEnglish), color = if (wybranyTyp == typ) BlueAccent else Color.White, fontSize = 13.sp)
                                    }
                                },
                                onClick = { wybranyTyp = typ; menuRozwiniete = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = dodatkowyOpis,
                    onValueChange = { dodatkowyOpis = it },
                    placeholder = { Text(lang.t("Dodatkowy opis", "Additional description"), color = TextGray, fontSize = 14.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = CardBgColor, unfocusedContainerColor = CardBgColor, focusedBorderColor = CardBorder, unfocusedBorderColor = CardBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = BlueAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier.fillMaxWidth().background(CardBgColor, RoundedCornerShape(12.dp)).border(1.dp, CardBorder, RoundedCornerShape(12.dp)).clickable { datePickerWaznosc.show() }.padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = TextGray, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${lang.t("Termin", "Schedule")}: $wprowadzonaData",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().background(CardBgColor, RoundedCornerShape(12.dp)).border(1.dp, CardBorder, RoundedCornerShape(12.dp)).clickable { datePickerPrzypomnienie.show() }.padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = YellowAccent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Alert: $recznaDataPrzyp",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().background(CardBgColor, RoundedCornerShape(12.dp)).border(1.dp, CardBorder, RoundedCornerShape(12.dp)).clickable {
                        val parts = recznaGodzinaPrzyp.split(":")
                        val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
                        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        TimePickerDialog(context, android.R.style.Theme_Material_Dialog, { _, godz, min -> recznaGodzinaPrzyp = String.format("%02d:%02d", godz, min) }, h, m, true).show()
                    }.padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = YellowAccent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "${lang.t("Godzina powiadomienia", "Notification time")}: $recznaGodzinaPrzyp", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (wprowadzonaData.isNotBlank() && recznaDataPrzyp.isNotBlank()) {
                        onZapisz(wybraneAuto, wybranyTyp, wprowadzonaData, recznaDataPrzyp, recznaGodzinaPrzyp, dodatkowyOpis)
                    } else {
                        Toast.makeText(context, lang.t("Uzupełnij daty terminu i powiadomienia", "Fill in schedule and notification dates"), Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
            ) { Text(lang.t("Zapisz zmiany", "Save changes"), color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onAnuluj) { Text(lang.t("Anuluj", "Cancel"), color = TextGray) }
        }
    )
}
// === 4. EKRAN DZIENNIKA NAPRAW ===
@Composable
fun EkranDziennikNapraw(
    paddingValues: PaddingValues,
    listaAut: SnapshotStateList<Pojazd>,
    listaWizyt: SnapshotStateList<Wizyta>,
    archiwumWizyt: SnapshotStateList<Wizyta>,
    onPowrot: () -> Unit,
    onPrzejdzDoSzczegolow: (Wizyta) -> Unit
) {
    val lang = LocalLanguage.current
    val context = LocalContext.current
    var pokazFormularzWizyty by remember { mutableStateOf(false) }
    var wizytaDoEdycji by remember { mutableStateOf<Wizyta?>(null) }
    var wizytaDoUsuniecia by remember { mutableStateOf<Wizyta?>(null) }
    var wybraneAutaFiltru by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var wybraneWarsztatyFiltru by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pokazFiltry by remember { mutableStateOf(false) }
    val unikalneWarsztaty = remember(listaWizyt.size) {
        listaWizyt.map { it.warsztat }.filter { it.isNotBlank() }.toSet().sorted()
    }
    val przefiltrowaneWizyty = listaWizyt.filter { wizyta ->
        val autoPasuje = wybraneAutaFiltru.isEmpty() || wybraneAutaFiltru.contains(wizyta.pojazd.id)
        val warsztatPasuje = wybraneWarsztatyFiltru.isEmpty() || wybraneWarsztatyFiltru.contains(wizyta.warsztat)
        autoPasuje && warsztatPasuje
    }
    LazyColumn(
        modifier = Modifier.padding(paddingValues).fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Powrót", tint = Color.White, modifier = Modifier.padding(end = 12.dp).size(24.dp).clip(CircleShape).clickable { onPowrot() })
                        Text(lang.t("Dziennik napraw", "Repair log"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(lang.t("Wizyty", "Visits"), color = TextGray, fontSize = 13.sp, fontWeight = FontWeight.Normal, modifier = Modifier.padding(start = 36.dp, top = 2.dp))
                }
                Row(
                    modifier = Modifier.background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp)).clip(RoundedCornerShape(9.dp)).clickable { pokazFormularzWizyty = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(lang.t("Dodaj wizytę", "Add visit"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(horizontalTaperingBrush))
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { pokazFiltry = !pokazFiltry }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val aktywneFiltry = wybraneAutaFiltru.isNotEmpty() || wybraneWarsztatyFiltru.isNotEmpty()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.List, contentDescription = null, tint = if (aktywneFiltry) BlueAccent else TextGray, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (aktywneFiltry) lang.t("Filtry są aktywne", "Filters are active") else lang.t("Filtruj listę wizyt", "Filter visits list"), color = if (aktywneFiltry) Color.White else TextGray, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextGray)
            }
            if (pokazFiltry) {
                Spacer(modifier = Modifier.height(12.dp))
                if (listaAut.isNotEmpty()) {
                    Text(lang.t("Wybierz pojazdy:", "Select vehicles:"), color = TextGray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listaAut) { auto ->
                            val zaznaczony = wybraneAutaFiltru.contains(auto.id)
                            KartaFiltru(tekst = auto.markaModel, zaznaczony = zaznaczony) {
                                wybraneAutaFiltru = if (zaznaczony) wybraneAutaFiltru - auto.id else wybraneAutaFiltru + auto.id
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (unikalneWarsztaty.isNotEmpty()) {
                    Text(lang.t("Wybierz warsztaty:", "Select workshops:"), color = TextGray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(unikalneWarsztaty) { warsztat ->
                            val zaznaczony = wybraneWarsztatyFiltru.contains(warsztat)
                            KartaFiltru(tekst = warsztat, zaznaczony = zaznaczony) {
                                wybraneWarsztatyFiltru = if (zaznaczony) wybraneWarsztatyFiltru - warsztat else wybraneWarsztatyFiltru + warsztat
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (wybraneAutaFiltru.isNotEmpty() || wybraneWarsztatyFiltru.isNotEmpty()) {
                    Text(
                        text = lang.t("Wyczyść wszystkie filtry", "Clear all filters"),
                        color = RedAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { wybraneAutaFiltru = emptySet(); wybraneWarsztatyFiltru = emptySet() }.padding(vertical = 4.dp, horizontal = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (przefiltrowaneWizyty.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) { Text(lang.t("Brak zapisanych wizyt w warsztacie.", "No workshop visits saved."), color = TextGray, fontSize = 14.sp) } }
        } else {
            items(przefiltrowaneWizyty, key = { it.id }) { wizyta ->
                KartaWizyty(
                    wizyta = wizyta,
                    onClick = { onPrzejdzDoSzczegolow(wizyta) },
                    onEdytuj = { wizytaDoEdycji = wizyta },
                    onUsun = { wizytaDoUsuniecia = wizyta }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
    if (pokazFormularzWizyty || wizytaDoEdycji != null) {
        DialogFormularzWizyty(
            wizytaPoczatkowa = wizytaDoEdycji,
            listaAut = listaAut,
            onZapisz = { pojazd, warsztat, przebieg, data ->
                if (wizytaDoEdycji != null) {
                    val idx = listaWizyt.indexOfFirst { it.id == wizytaDoEdycji!!.id }
                    if (idx != -1) {
                        listaWizyt[idx] = wizytaDoEdycji!!.copy(pojazd = pojazd, warsztat = warsztat, przebieg = przebieg, data = data)
                    }
                } else {
                    val maxId = (listaWizyt + archiwumWizyt).maxOfOrNull { it.id } ?: 0
                    listaWizyt.add(Wizyta(maxId + 1, pojazd, warsztat, przebieg, data))
                }
                zapiszWizyty(context, listaWizyt)
                zapiszWarsztat(context, warsztat)
                pokazFormularzWizyty = false
                wizytaDoEdycji = null
            },
            onAnuluj = { pokazFormularzWizyty = false; wizytaDoEdycji = null }
        )
    }
    if (wizytaDoUsuniecia != null) {
        DialogPotwierdzeniaUsuniecia(
            tytul = lang.t("Usuń wizytę", "Delete visit"),
            tresc = lang.t("Czy na pewno chcesz usunąć wizytę \"${wizytaDoUsuniecia?.warsztat}\"? Wizyta wraz z przypisanymi naprawami trafi do archiwum.", "Are you sure you want to delete visit \"${wizytaDoUsuniecia?.warsztat}\"? It will be moved to archive."),
            onPotwierdz = {
                val usuwana = wizytaDoUsuniecia!!
                archiwumWizyt.add(0, usuwana)
                listaWizyt.remove(usuwana)
                zapiszWizyty(context, listaWizyt)
                zapiszWizyty(context, archiwumWizyt, "archiwum_wizyty")
                Toast.makeText(context, lang.t("Przeniesiono wizytę do archiwum", "Visit moved to archive"), Toast.LENGTH_SHORT).show()
                wizytaDoUsuniecia = null
            },
            onAnuluj = { wizytaDoUsuniecia = null }
        )
    }
}

@Composable
fun KartaFiltru(tekst: String, zaznaczony: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.background(if (zaznaczony) BlueAccent else CardBgColor, RoundedCornerShape(8.dp)).border(1.dp, if (zaznaczony) BlueAccent else CardBorder, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = tekst, color = Color.White, fontSize = 11.sp, fontWeight = if (zaznaczony) FontWeight.Bold else FontWeight.Normal)
    }
}

// === 5. EKRAN SZCZEGÓŁÓW WIZYTY ===
// === 5. EKRAN SZCZEGÓŁÓW WIZYTY ===
// === 5. EKRAN SZCZEGÓŁÓW WIZYTY ===
@Composable
fun EkranSzczegolyWizyty(
    paddingValues: PaddingValues,
    wizytaId: Int?,
    listaWizyt: SnapshotStateList<Wizyta>,
    archiwumNapraw: SnapshotStateList<ZarchiwizowanaNaprawa>,
    onPowrot: () -> Unit
) {
    val lang = LocalLanguage.current
    val context = LocalContext.current
    if (wizytaId == null) { onPowrot(); return }
    val aktualnaWizyta = listaWizyt.find { it.id == wizytaId }
    if (aktualnaWizyta == null) { onPowrot(); return }
    var pokazFormularzNaprawy by remember { mutableStateOf(false) }
    var naprawaDoEdycji by remember { mutableStateOf<Naprawa?>(null) }
    var naprawaDoUsuniecia by remember { mutableStateOf<Naprawa?>(null) }

    val ciasnyStyl = androidx.compose.ui.text.TextStyle(
        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
    )

    LazyColumn(
        modifier = Modifier.padding(paddingValues).fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(
                    modifier = Modifier
                        .weight(1f) // Zatrzymuje kolumnę przed przyciskiem i zawija nazwę warsztatu w dół
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Powrót", tint = Color.White, modifier = Modifier.padding(end = 12.dp).size(24.dp).clip(CircleShape).clickable { onPowrot() })
                        Text(lang.t("Szczegóły wizyty", "Visit details"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, style = ciasnyStyl)
                    }
                    Text(
                        text = aktualnaWizyta.warsztat,
                        color = TextGray,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Normal,
                        style = ciasnyStyl,
                        modifier = Modifier.padding(start = 36.dp, top = 2.dp)
                    )
                }
                Row(
                    modifier = Modifier.background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp)).clip(RoundedCornerShape(9.dp)).clickable { pokazFormularzNaprawy = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(lang.t("Dodaj wpis", "Add entry"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, style = ciasnyStyl)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(horizontalTaperingBrush))
            Spacer(modifier = Modifier.height(16.dp))

            // Karta podsumowująca wizytę
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier.fillMaxWidth().background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = aktualnaWizyta.pojazd.avatarRes),
                        contentDescription = "Auto",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(width = 56.dp, height = 38.dp).clip(RoundedCornerShape(9.dp)).background(Color.Transparent)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(aktualnaWizyta.pojazd.markaModel, color = Color.White, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, style = ciasnyStyl)
                            Text(aktualnaWizyta.data, color = TextGray, fontSize = 11.sp, lineHeight = 13.sp, style = ciasnyStyl)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            val przebiegTekst = if (aktualnaWizyta.przebieg.isNotBlank()) "${aktualnaWizyta.przebieg} km" else ""
                            Text(lang.t("Przebieg: $przebiegTekst", "Mileage: $przebiegTekst"), color = TextGray, fontSize = 10.sp, lineHeight = 12.sp, style = ciasnyStyl)
                            val sumaPodsumowanie = podsumujKosztyWgWalut(aktualnaWizyta.naprawy)
                            Text(sumaPodsumowanie, color = BlueAccent, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, style = ciasnyStyl)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = lang.t("Wykonane naprawy i części", "Performed repairs and parts"),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                style = ciasnyStyl
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (aktualnaWizyta.naprawy.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) { Text(lang.t("Brak dodanych napraw dla tej wizyty.", "No repairs added for this visit."), color = TextGray, fontSize = 14.sp) } }
        } else {
            items(aktualnaWizyta.naprawy, key = { it.id }) { naprawa ->
                KartaNaprawy(
                    naprawa = naprawa,
                    onEdytuj = { naprawaDoEdycji = naprawa },
                    onUsun = { naprawaDoUsuniecia = naprawa }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
    if (pokazFormularzNaprawy || naprawaDoEdycji != null) {
        DialogFormularzNaprawy(
            naprawaPoczatkowa = naprawaDoEdycji,
            onZapisz = { nazwa, koszt, notatki, waluta ->
                val zmodyfikowaneNaprawy = aktualnaWizyta.naprawy.toMutableList()
                if (naprawaDoEdycji != null) {
                    val idx = zmodyfikowaneNaprawy.indexOfFirst { it.id == naprawaDoEdycji!!.id }
                    if (idx != -1) {
                        zmodyfikowaneNaprawy[idx] = naprawaDoEdycji!!.copy(nazwa = nazwa, koszt = koszt, notatki = notatki, waluta = waluta)
                    }
                } else {
                    val noweId = if (zmodyfikowaneNaprawy.isEmpty()) 1 else zmodyfikowaneNaprawy.maxOf { it.id } + 1
                    zmodyfikowaneNaprawy.add(Naprawa(noweId, nazwa, koszt, notatki, waluta))
                }
                val idxWizyty = listaWizyt.indexOfFirst { it.id == aktualnaWizyta.id }
                if (idxWizyty != -1) {
                    listaWizyt[idxWizyty] = aktualnaWizyta.copy(naprawy = zmodyfikowaneNaprawy)
                    zapiszWizyty(context, listaWizyt)
                    zapiszNaprawaHistory(context, nazwa)
                }
                pokazFormularzNaprawy = false
                naprawaDoEdycji = null
            },
            onAnuluj = { pokazFormularzNaprawy = false; naprawaDoEdycji = null }
        )
    }
    if (naprawaDoUsuniecia != null) {
        DialogPotwierdzeniaUsuniecia(
            tytul = lang.t("Usuń pozycję", "Delete item"),
            tresc = lang.t("Czy na pewno chcesz usunąć pozycję \"${naprawaDoUsuniecia?.nazwa}\"? Wpis trafi do archiwum.", "Are you sure you want to delete \"${naprawaDoUsuniecia?.nazwa}\"? It will be moved to archive."),
            onPotwierdz = {
                val usuwana = naprawaDoUsuniecia!!
                val noweIdArch = if (archiwumNapraw.isEmpty()) 1 else archiwumNapraw.maxOf { it.idArch } + 1
                archiwumNapraw.add(0, ZarchiwizowanaNaprawa(
                    idArch = noweIdArch,
                    wizytaId = aktualnaWizyta.id,
                    warsztat = aktualnaWizyta.warsztat,
                    przebieg = aktualnaWizyta.przebieg,
                    dataWizyty = aktualnaWizyta.data,
                    pojazd = aktualnaWizyta.pojazd,
                    naprawa = usuwana
                ))
                zapiszNaprawyArchiwum(context, archiwumNapraw)
                val zmodyfikowaneNaprawy = aktualnaWizyta.naprawy.toMutableList()
                zmodyfikowaneNaprawy.remove(usuwana)
                val idxWizyty = listaWizyt.indexOfFirst { it.id == aktualnaWizyta.id }
                if (idxWizyty != -1) {
                    listaWizyt[idxWizyty] = aktualnaWizyta.copy(naprawy = zmodyfikowaneNaprawy)
                    zapiszWizyty(context, listaWizyt)
                }
                Toast.makeText(context, lang.t("Przeniesiono wpis do archiwum", "Item moved to archive"), Toast.LENGTH_SHORT).show()
                naprawaDoUsuniecia = null
            },
            onAnuluj = { naprawaDoUsuniecia = null }
        )
    }
}

// === 6. EKRAN RAPORTU PDF ===
@Composable
fun EkranRaportPDF(
    paddingValues: PaddingValues,
    listaAut: List<Pojazd>,
    listaWizyt: List<Wizyta>,
    onPowrot: () -> Unit
) {
    val lang = LocalLanguage.current
    val context = LocalContext.current
    var zaznaczoneWizytyIds by remember { mutableStateOf(listaWizyt.map { it.id }.toSet()) }
    var filtrAutIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var rozwinMenuAut by remember { mutableStateOf(false) }
    var plikPodgladuPDF by remember { mutableStateOf<File?>(null) }

    val widoczneWizyty = remember(listaWizyt.toList(), filtrAutIds) {
        if (filtrAutIds.isEmpty()) listaWizyt else listaWizyt.filter { filtrAutIds.contains(it.pojazd.id) }
    }
    val wybraneWizytyDoRaportu = remember(listaWizyt.toList(), zaznaczoneWizytyIds, filtrAutIds) {
        val bazowe = if (filtrAutIds.isEmpty()) listaWizyt else listaWizyt.filter { filtrAutIds.contains(it.pojazd.id) }
        bazowe.filter { zaznaczoneWizytyIds.contains(it.id) }
    }
    val liczbaWybranychAut = remember(wybraneWizytyDoRaportu) { wybraneWizytyDoRaportu.map { it.pojazd.id }.distinct().size }
    val laczneNaprawy = remember(wybraneWizytyDoRaportu) { wybraneWizytyDoRaportu.flatMap { it.naprawy } }
    val podsumowanieKosztowRaportu = remember(laczneNaprawy) { podsumujKosztyWgWalut(laczneNaprawy) }
    val aktualnyPlikPodgladu by rememberUpdatedState(plikPodgladuPDF)

    val createPdfLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        val plikZrodlowy = aktualnyPlikPodgladu
        if (uri != null && plikZrodlowy != null && plikZrodlowy.exists()) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(plikZrodlowy).use { inputStream -> inputStream.copyTo(outputStream) }
                }
                Toast.makeText(context, lang.t("Raport PDF został pomyślnie zapisany!", "PDF report saved successfully!"), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, lang.t("Błąd podczas zapisywania pliku PDF", "Error saving PDF file"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.padding(paddingValues).fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Powrót", tint = Color.White, modifier = Modifier.padding(end = 12.dp).size(24.dp).clip(CircleShape).clickable { onPowrot() })
                        Text(lang.t("Raport PDF", "PDF Report"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(lang.t("Dziennik napraw", "Repair log"), color = TextGray, fontSize = 13.sp, fontWeight = FontWeight.Normal, modifier = Modifier.padding(start = 36.dp, top = 2.dp))
                }
                Row(
                    modifier = Modifier.background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, if (wybraneWizytyDoRaportu.isNotEmpty()) BlueAccent else CardBorder, RoundedCornerShape(9.dp)).clip(RoundedCornerShape(9.dp)).clickable {
                        if (wybraneWizytyDoRaportu.isEmpty()) {
                            Toast.makeText(context, lang.t("Zaznacz przynajmniej jedną wizytę", "Select at least one visit"), Toast.LENGTH_SHORT).show()
                        } else {
                            plikPodgladuPDF = wygenerujPlikRaportuPDF(context, wybraneWizytyDoRaportu, lang.isEnglish)
                        }
                    }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(lang.t("Podgląd PDF", "PDF Preview"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(horizontalTaperingBrush))
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier.fillMaxWidth().background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(lang.t("Podsumowanie raportu", "Report summary"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = lang.t("Wybrane wizyty: ${wybraneWizytyDoRaportu.size} · Pojazdy: $liczbaWybranychAut", "Selected visits: ${wybraneWizytyDoRaportu.size} · Vehicles: $liczbaWybranychAut"), color = TextGray, fontSize = 11.sp)
                    }
                    Box(modifier = Modifier.fillMaxHeight().padding(vertical = 2.dp, horizontal = 10.dp).width(1.5.dp).background(verticalBrightDividerBrush))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(lang.t("Suma kosztów", "Total cost"), color = TextGray, fontSize = 10.sp)
                        Text(text = podsumowanieKosztowRaportu, color = BlueAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                val etykietaFiltruAut = when {
                    filtrAutIds.isEmpty() -> lang.t("Wszystkie auta", "All vehicles")
                    filtrAutIds.size == 1 -> listaAut.find { it.id == filtrAutIds.first() }?.markaModel ?: lang.t("Wybrano: 1", "Selected: 1")
                    else -> lang.t("Wybrane auta (${filtrAutIds.size})", "Selected vehicles (${filtrAutIds.size})")
                }
                Box {
                    Row(
                        modifier = Modifier.widthIn(min = 135.dp, max = 165.dp).background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, if (filtrAutIds.isNotEmpty()) BlueAccent else CardBorder, RoundedCornerShape(9.dp)).clip(RoundedCornerShape(9.dp)).clickable { rozwinMenuAut = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                            Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = null, tint = if (filtrAutIds.isNotEmpty()) BlueAccent else TextGray, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = etykietaFiltruAut, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = rozwinMenuAut,
                        onDismissRequest = { rozwinMenuAut = false },
                        modifier = Modifier.width(200.dp).background(CardGradient).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = lang.t("Pokaż wszystkie auta", "Show all vehicles"), color = if (filtrAutIds.isEmpty()) BlueAccent else Color.White, fontSize = 12.sp, fontWeight = if (filtrAutIds.isEmpty()) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { filtrAutIds = emptySet(); rozwinMenuAut = false }
                        )
                        listaAut.forEach { auto ->
                            val czyAutoWybrane = filtrAutIds.contains(auto.id)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier.size(16.dp).background(if (czyAutoWybrane) BlueAccent else CardBgColor, RoundedCornerShape(4.dp)).border(1.dp, if (czyAutoWybrane) BlueAccent else CardBorder, RoundedCornerShape(4.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (czyAutoWybrane) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(text = auto.markaModel, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (auto.rejestracja.isNotBlank()) Text(auto.rejestracja, color = TextGray, fontSize = 9.sp)
                                        }
                                    }
                                },
                                onClick = { filtrAutIds = if (czyAutoWybrane) filtrAutIds - auto.id else filtrAutIds + auto.id }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp)).clip(RoundedCornerShape(9.dp)).clickable { zaznaczoneWizytyIds = zaznaczoneWizytyIds + widoczneWizyty.map { it.id } }.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(lang.t("Wszystkie", "All"), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }
                    Row(
                        modifier = Modifier.background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp)).clip(RoundedCornerShape(9.dp)).clickable { zaznaczoneWizytyIds = zaznaczoneWizytyIds - widoczneWizyty.map { it.id } }.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = RedAccent, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(lang.t("Odznacz", "None"), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (listaWizyt.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) { Text(lang.t("Brak wizyt w Dzienniku napraw do wygenerowania raportu.", "No visits in repair log to generate report."), color = TextGray, fontSize = 14.sp, textAlign = TextAlign.Center) } }
        } else if (widoczneWizyty.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) { Text(lang.t("Brak wizyt dla wybranych pojazdów.", "No visits for selected vehicles."), color = TextGray, fontSize = 14.sp) } }
        } else {
            items(widoczneWizyty, key = { it.id }) { wizyta ->
                val czyZaznaczona = zaznaczoneWizytyIds.contains(wizyta.id)
                KartaWizytyDoRaportu(
                    wizyta = wizyta,
                    zaznaczona = czyZaznaczona,
                    onToggle = { zaznaczoneWizytyIds = if (czyZaznaczona) zaznaczoneWizytyIds - wizyta.id else zaznaczoneWizytyIds + wizyta.id }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
    if (plikPodgladuPDF != null) {
        DialogPodgladuPDF(
            pdfFile = plikPodgladuPDF!!,
            onZamknij = { plikPodgladuPDF = null },
            onDrukuj = { drukujPlikPDF(context, plikPodgladuPDF!!) },
            onZapisz = {
                val znacznik = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                createPdfLauncher.launch("Raport_$znacznik.pdf")
            }
        )
    }
}

@Composable
fun DialogPodgladuPDF(
    pdfFile: File,
    onZamknij: () -> Unit,
    onDrukuj: () -> Unit,
    onZapisz: () -> Unit
) {
    val lang = LocalLanguage.current
    val stronyBitmapy = remember(pdfFile) { renderujStronyPDF(pdfFile) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(onDismissRequest = onZamknij, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.fillMaxSize().background(BgColor).padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Zamknij", tint = Color.White, modifier = Modifier.padding(end = 10.dp).size(24.dp).clip(CircleShape).clickable { onZamknij() })
                        Text(text = lang.t("Podgląd PDF", "PDF Preview"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(text = lang.t("Stron: ${stronyBitmapy.size}", "Pages: ${stronyBitmapy.size}"), color = TextGray, fontSize = 11.sp, modifier = Modifier.padding(start = 34.dp, top = 2.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp)).clip(RoundedCornerShape(9.dp)).clickable { onDrukuj() }.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(text = lang.t("Drukuj", "Print"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.background(BlueAccent, RoundedCornerShape(9.dp)).border(1.dp, BlueAccent, RoundedCornerShape(9.dp)).clip(RoundedCornerShape(9.dp)).clickable { onZapisz() }.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(text = lang.t("Zapisz PDF", "Save PDF"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(horizontalTaperingBrush))
            Spacer(modifier = Modifier.height(14.dp))

            // Kontener roboczy z pełnym klipowaniem, eliminujący wolne tło na zewnątrz
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Transparent)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = newScale
                            if (newScale > 1f) {
                                // Precyzyjne dopasowanie granic, żeby tło nie wychodziło poza krawędź pliku
                                val limitX = size.width * (scale - 1f) / 2f
                                val limitY = size.height * (scale - 1f) / 2f
                                offset = Offset(
                                    x = (offset.x + pan.x).coerceIn(-limitX, limitX),
                                    y = (offset.y + pan.y).coerceIn(-limitY, limitY)
                                )
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                    userScrollEnabled = scale <= 1.05f,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(stronyBitmapy) { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "PDF Page",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun KartaWizytyDoRaportu(wizyta: Wizyta, zaznaczona: Boolean, onToggle: () -> Unit) {
    val lang = LocalLanguage.current
    val obramowanieKolor = if (zaznaczona) BlueAccent else CardBorder
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier.fillMaxWidth().background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, obramowanieKolor, RoundedCornerShape(9.dp)).clip(RoundedCornerShape(9.dp)).clickable { onToggle() }
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(22.dp).background(if (zaznaczona) BlueAccent else CardBgColor, RoundedCornerShape(6.dp)).border(1.dp, if (zaznaczona) BlueAccent else CardBorder, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (zaznaczona) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))

            // Lewa kolumna z 3-liniowym układem jak w Dzienniku napraw
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy((1).dp)
            ) {
                // 1 linijka: Nazwa warsztatu (zawijana w dół)
                Text(
                    text = wizyta.warsztat,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                // 2 linijka: Nazwa auta + nr rejestracyjny
                val autoTekst = if (wizyta.pojazd.rejestracja.isNotBlank()) {
                    "${wizyta.pojazd.markaModel} · ${wizyta.pojazd.rejestracja}"
                } else {
                    wizyta.pojazd.markaModel
                }
                Text(
                    text = autoTekst,
                    color = TextGray,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                // 3 linijka: Przebieg + Naprawy
                val przebiegTekst = if (wizyta.przebieg.isNotBlank()) "${wizyta.przebieg} km · " else ""
                Text(
                    text = "$przebiegTekst${lang.t("Naprawy", "Repairs")}: ${wizyta.naprawy.size}",
                    color = TextGray,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(wizyta.data, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                val sumaStr = podsumujKosztyWgWalut(wizyta.naprawy)
                Text(text = sumaStr, color = BlueAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// === 7. EKRAN ARCHIWUM ===
@Composable
fun EkranArchiwum(
    paddingValues: PaddingValues,
    listaAut: SnapshotStateList<Pojazd>,
    listaTerminow: SnapshotStateList<Termin>,
    listaWizyt: SnapshotStateList<Wizyta>,
    listaDokumentow: SnapshotStateList<Dokument>,
    archiwumAut: SnapshotStateList<Pojazd>,
    archiwumTerminow: SnapshotStateList<Termin>,
    archiwumWizyt: SnapshotStateList<Wizyta>,
    archiwumNapraw: SnapshotStateList<ZarchiwizowanaNaprawa>,
    archiwumDokumentow: SnapshotStateList<Dokument>,
    onPowrot: () -> Unit
) {
    val lang = LocalLanguage.current
    val context = LocalContext.current
    var pokazDialogWyczyscWszystko by remember { mutableStateOf(false) }
    var autoDoTrwalegoUsuniecia by remember { mutableStateOf<Pojazd?>(null) }
    var terminDoTrwalegoUsuniecia by remember { mutableStateOf<Termin?>(null) }
    var wizytaDoTrwalegoUsuniecia by remember { mutableStateOf<Wizyta?>(null) }
    var naprawaDoTrwalegoUsuniecia by remember { mutableStateOf<ZarchiwizowanaNaprawa?>(null) }
    var dokumentDoTrwalegoUsuniecia by remember { mutableStateOf<Dokument?>(null) }

    val lacznaLiczba = archiwumAut.size + archiwumTerminow.size + archiwumWizyt.size + archiwumNapraw.size + archiwumDokumentow.size

    fun upewnijSieZeAutoAktywne(pojazd: Pojazd) {
        if (listaAut.none { it.id == pojazd.id }) {
            listaAut.add(pojazd)
            archiwumAut.removeAll { it.id == pojazd.id }
        }
    }

    LazyColumn(
        modifier = Modifier.padding(paddingValues).fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Powrót", tint = Color.White, modifier = Modifier.padding(end = 12.dp).size(24.dp).clip(CircleShape).clickable { onPowrot() })
                        Text(lang.t("Archiwum", "Archive"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(lang.t("Usunięte wpisy", "Deleted entries"), color = TextGray, fontSize = 13.sp, fontWeight = FontWeight.Normal, modifier = Modifier.padding(start = 36.dp, top = 2.dp))
                }
                if (lacznaLiczba > 0) {
                    Row(
                        modifier = Modifier.background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp)).clip(RoundedCornerShape(9.dp)).clickable { pokazDialogWyczyscWszystko = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = RedAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(lang.t("Wyczyść", "Clear"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(horizontalTaperingBrush))
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (lacznaLiczba == 0) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) { Text(lang.t("Brak usuniętych wpisów w archiwum.", "No deleted entries in archive."), color = TextGray, fontSize = 14.sp) } }
        } else {
            items(archiwumAut, key = { "auto_${it.id}" }) { auto ->
                KartaElementuArchiwum(
                    ikona = Icons.Default.DirectionsCar,
                    kolorIkony = BlueAccent,
                    kategoriaEtykieta = lang.t("POJAZD", "VEHICLE"),
                    tytul = auto.markaModel,
                    podtytul = "${lang.t("Rejestracja", "Registration")}: ${auto.rejestracja}",
                    onPrzywroc = {
                        if (listaAut.none { it.id == auto.id }) listaAut.add(auto)
                        archiwumAut.remove(auto)
                        zapiszDane(context, listaAut, listaTerminow)
                        zapiszDane(context, archiwumAut, archiwumTerminow, "archiwum_auta", "archiwum_terminy")
                        Toast.makeText(context, lang.t("Przywrócono pojazd", "Vehicle restored"), Toast.LENGTH_SHORT).show()
                    },
                    onUsunTrwale = { autoDoTrwalegoUsuniecia = auto }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(archiwumTerminow, key = { "termin_${it.id}" }) { termin ->
                val opisDodatkowy = if (termin.opis.isNotBlank()) " · ${termin.opis}" else ""
                KartaElementuArchiwum(
                    ikona = getIkonaDlaTypu(termin.typ),
                    kolorIkony = YellowAccent,
                    kategoriaEtykieta = "${lang.t("TERMIN", "SCHEDULE")} · ${termin.data}",
                    tytul = translateTyp(termin.typ, lang.isEnglish),
                    podtytul = "${termin.pojazd.markaModel} (${termin.pojazd.rejestracja})$opisDodatkowy",
                    onPrzywroc = {
                        upewnijSieZeAutoAktywne(termin.pojazd)
                        val noweId = if (listaTerminow.any { it.id == termin.id }) (listaTerminow + archiwumTerminow).maxOf { it.id } + 1 else termin.id
                        listaTerminow.add(termin.copy(id = noweId))
                        archiwumTerminow.remove(termin)
                        zapiszDane(context, listaAut, listaTerminow)
                        zapiszDane(context, archiwumAut, archiwumTerminow, "archiwum_auta", "archiwum_terminy")
                        Toast.makeText(context, lang.t("Przywrócono termin", "Schedule restored"), Toast.LENGTH_SHORT).show()
                    },
                    onUsunTrwale = { terminDoTrwalegoUsuniecia = termin }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(archiwumWizyt, key = { "wizyta_${it.id}" }) { wizyta ->
                val sumaStr = podsumujKosztyWgWalut(wizyta.naprawy)
                val kosztTekst = if (wizyta.naprawy.isNotEmpty()) " · $sumaStr" else ""
                KartaElementuArchiwum(
                    ikona = Icons.Default.Build,
                    kolorIkony = BlueAccent,
                    kategoriaEtykieta = "${lang.t("WIZYTA", "VISIT")} · ${wizyta.data}",
                    tytul = wizyta.warsztat,
                    podtytul = "${wizyta.pojazd.markaModel} · ${lang.t("Wpisów", "Entries")}: ${wizyta.naprawy.size}$kosztTekst",
                    onPrzywroc = {
                        upewnijSieZeAutoAktywne(wizyta.pojazd)
                        val noweId = if (listaWizyt.any { it.id == wizyta.id }) (listaWizyt + archiwumWizyt).maxOf { it.id } + 1 else wizyta.id
                        listaWizyt.add(wizyta.copy(id = noweId))
                        archiwumWizyt.remove(wizyta)
                        zapiszWizyty(context, listaWizyt)
                        zapiszWizyty(context, archiwumWizyt, "archiwum_wizyty")
                        Toast.makeText(context, lang.t("Przywrócono wizytę", "Visit restored"), Toast.LENGTH_SHORT).show()
                    },
                    onUsunTrwale = { wizytaDoTrwalegoUsuniecia = wizyta }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(archiwumNapraw, key = { "naprawa_${it.idArch}" }) { archNaprawa ->
                KartaElementuArchiwum(
                    ikona = Icons.Default.Settings,
                    kolorIkony = GreenAccent,
                    kategoriaEtykieta = "${lang.t("NAPRAWA", "REPAIR")} · ${archNaprawa.naprawa.koszt} ${archNaprawa.naprawa.waluta}",
                    tytul = archNaprawa.naprawa.nazwa,
                    podtytul = "${archNaprawa.warsztat} (${archNaprawa.dataWizyty}) · ${archNaprawa.pojazd.markaModel}",
                    onPrzywroc = {
                        upewnijSieZeAutoAktywne(archNaprawa.pojazd)
                        val idxWizyty = listaWizyt.indexOfFirst { it.id == archNaprawa.wizytaId }
                        if (idxWizyty != -1) {
                            val w = listaWizyt[idxWizyty]
                            val noweNaprawy = w.naprawy.toMutableList()
                            val noweId = if (noweNaprawy.isEmpty()) 1 else noweNaprawy.maxOf { it.id } + 1
                            noweNaprawy.add(archNaprawa.naprawa.copy(id = noweId))
                            listaWizyt[idxWizyty] = w.copy(naprawy = noweNaprawy)
                        } else {
                            val wizytaZArch = archiwumWizyt.find { it.id == archNaprawa.wizytaId }
                            if (wizytaZArch != null) {
                                val noweNaprawy = wizytaZArch.naprawy.toMutableList()
                                noweNaprawy.add(archNaprawa.naprawa)
                                listaWizyt.add(wizytaZArch.copy(naprawy = noweNaprawy))
                                archiwumWizyt.remove(wizytaZArch)
                                zapiszWizyty(context, archiwumWizyt, "archiwum_wizyty")
                            } else {
                                val noweWizytaId = if (listaWizyt.isEmpty()) 1 else listaWizyt.maxOf { it.id } + 1
                                listaWizyt.add(Wizyta(id = noweWizytaId, pojazd = archNaprawa.pojazd, warsztat = archNaprawa.warsztat, przebieg = archNaprawa.przebieg, data = archNaprawa.dataWizyty, naprawy = listOf(archNaprawa.naprawa)))
                            }
                        }
                        archiwumNapraw.remove(archNaprawa)
                        zapiszWizyty(context, listaWizyt)
                        zapiszNaprawyArchiwum(context, archiwumNapraw)
                        Toast.makeText(context, lang.t("Przywrócono wpis", "Entry restored"), Toast.LENGTH_SHORT).show()
                    },
                    onUsunTrwale = { naprawaDoTrwalegoUsuniecia = archNaprawa }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(archiwumDokumentow, key = { "dok_arch_${it.id}" }) { dok ->
                val (ikonaPliku, kolorIkony) = when (dok.typ) {
                    "IMAGE" -> Pair(Icons.Default.Image, YellowAccent)
                    "PDF" -> Pair(Icons.Default.PictureAsPdf, RedAccent)
                    else -> Pair(Icons.Default.InsertDriveFile, BlueAccent)
                }

                val tytulDok = translateKategoriaDok(dok.kategoria, lang.isEnglish)
                val autoInfo = if (dok.pojazdNazwa.isNotBlank()) {
                    if (dok.pojazdRejestracja.isNotBlank()) " · ${dok.pojazdNazwa} (${dok.pojazdRejestracja})" else " · ${dok.pojazdNazwa}"
                } else ""
                val opisDodatkowy = if (dok.nazwa != dok.kategoria && dok.nazwa.isNotBlank()) " · ${dok.nazwa}" else ""

                val ciasnyStyl = androidx.compose.ui.text.TextStyle(
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.fillMaxWidth().background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp) // Ciasny padding taki sam jak w KartaElementuArchiwum
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(IconBgColor, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(ikonaPliku, contentDescription = null, tint = kolorIkony, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = "${lang.t("DOKUMENT", "DOCUMENT")} · ${dok.dataDodania}",
                                color = BlueAccent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                style = ciasnyStyl
                            )
                            Text(
                                text = tytulDok,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = ciasnyStyl
                            )
                            Text(
                                text = "${dok.typ}$autoInfo$opisDodatkowy",
                                color = TextGray,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = ciasnyStyl
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.fillMaxHeight().padding(vertical = 2.dp).width(1.5.dp).background(verticalBrightDividerBrush))
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(IconBgColor, RoundedCornerShape(6.dp))
                                .border(1.dp, GreenAccent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    val noweId = if (listaDokumentow.any { it.id == dok.id }) (listaDokumentow + archiwumDokumentow).maxOf { it.id } + 1 else dok.id
                                    listaDokumentow.add(dok.copy(id = noweId))
                                    archiwumDokumentow.remove(dok)
                                    zapiszDokumenty(context, listaDokumentow)
                                    zapiszDokumenty(context, archiwumDokumentow, "archiwum_dokumenty")
                                    Toast.makeText(context, lang.t("Przywrócono dokument", "Document restored"), Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Przywróć", tint = GreenAccent, modifier = Modifier.size(14.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(IconBgColor, RoundedCornerShape(6.dp))
                                .border(1.dp, RedAccent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    dokumentDoTrwalegoUsuniecia = dok
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Usuń trwale", tint = RedAccent, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
    if (autoDoTrwalegoUsuniecia != null) {
        DialogPotwierdzeniaUsuniecia(
            tytul = lang.t("Usuń trwale pojazd", "Permanently delete vehicle"),
            tresc = lang.t("Czy na pewno chcesz bezpowrotnie usunąć pojazd \"${autoDoTrwalegoUsuniecia?.markaModel}\"?", "Are you sure you want to permanently delete \"${autoDoTrwalegoUsuniecia?.markaModel}\"?"),
            tekstPotwierdzenia = lang.t("Usuń trwale", "Delete permanently"),
            onPotwierdz = {
                archiwumAut.remove(autoDoTrwalegoUsuniecia)
                zapiszDane(context, archiwumAut, archiwumTerminow, "archiwum_auta", "archiwum_terminy")
                autoDoTrwalegoUsuniecia = null
            },
            onAnuluj = { autoDoTrwalegoUsuniecia = null }
        )
    }
    if (terminDoTrwalegoUsuniecia != null) {
        DialogPotwierdzeniaUsuniecia(
            tytul = lang.t("Usuń trwale termin", "Permanently delete schedule"),
            tresc = lang.t("Czy na pewno chcesz bezpowrotnie usunąć termin?", "Are you sure you want to permanently delete schedule?"),
            tekstPotwierdzenia = lang.t("Usuń trwale", "Delete permanently"),
            onPotwierdz = {
                archiwumTerminow.remove(terminDoTrwalegoUsuniecia)
                zapiszDane(context, archiwumAut, archiwumTerminow, "archiwum_auta", "archiwum_terminy")
                terminDoTrwalegoUsuniecia = null
            },
            onAnuluj = { terminDoTrwalegoUsuniecia = null }
        )
    }
    if (wizytaDoTrwalegoUsuniecia != null) {
        DialogPotwierdzeniaUsuniecia(
            tytul = lang.t("Usuń trwale wizytę", "Permanently delete visit"),
            tresc = lang.t("Czy na pewno chcesz bezpowrotnie usunąć wizytę?", "Are you sure you want to permanently delete visit?"),
            tekstPotwierdzenia = lang.t("Usuń trwale", "Delete permanently"),
            onPotwierdz = {
                archiwumWizyt.remove(wizytaDoTrwalegoUsuniecia)
                zapiszWizyty(context, archiwumWizyt, "archiwum_wizyty")
                wizytaDoTrwalegoUsuniecia = null
            },
            onAnuluj = { wizytaDoTrwalegoUsuniecia = null }
        )
    }
    if (naprawaDoTrwalegoUsuniecia != null) {
        DialogPotwierdzeniaUsuniecia(
            tytul = lang.t("Usuń trwale wpis", "Permanently delete entry"),
            tresc = lang.t("Czy na pewno chcesz bezpowrotnie usunąć ten wpis?", "Are you sure you want to permanently delete this entry?"),
            tekstPotwierdzenia = lang.t("Usuń trwale", "Delete permanently"),
            onPotwierdz = {
                archiwumNapraw.remove(naprawaDoTrwalegoUsuniecia)
                zapiszNaprawyArchiwum(context, archiwumNapraw)
                naprawaDoTrwalegoUsuniecia = null
            },
            onAnuluj = { naprawaDoTrwalegoUsuniecia = null }
        )
    }
    if (dokumentDoTrwalegoUsuniecia != null) {
        DialogPotwierdzeniaUsuniecia(
            tytul = lang.t("Usuń trwale dokument", "Permanently delete document"),
            tresc = lang.t("Czy na pewno chcesz bezpowrotnie usunąć dokument z archiwum?", "Are you sure you want to permanently delete document from archive?"),
            tekstPotwierdzenia = lang.t("Usuń trwale", "Delete permanently"),
            onPotwierdz = {
                val sciezka = dokumentDoTrwalegoUsuniecia?.uriString ?: ""
                if (sciezka.startsWith("/")) {
                    val plik = File(sciezka)
                    if (plik.exists()) plik.delete()
                }
                archiwumDokumentow.remove(dokumentDoTrwalegoUsuniecia)
                zapiszDokumenty(context, archiwumDokumentow, "archiwum_dokumenty")
                dokumentDoTrwalegoUsuniecia = null
            },
            onAnuluj = { dokumentDoTrwalegoUsuniecia = null }
        )
    }
    if (pokazDialogWyczyscWszystko) {
        DialogPotwierdzeniaUsuniecia(
            tytul = lang.t("Wyczyść całe archiwum", "Clear entire archive"),
            tresc = lang.t("Czy na pewno chcesz bezpowrotnie usunąć wszystkie elementy z archiwum?", "Are you sure you want to permanently remove all items from archive?"),
            tekstPotwierdzenia = lang.t("Wyczyść wszystko", "Clear all"),
            onPotwierdz = {
                archiwumAut.clear()
                archiwumTerminow.clear()
                archiwumWizyt.clear()
                archiwumNapraw.clear()
                archiwumDokumentow.clear()
                zapiszDane(context, archiwumAut, archiwumTerminow, "archiwum_auta", "archiwum_terminy")
                zapiszWizyty(context, archiwumWizyt, "archiwum_wizyty")
                zapiszNaprawyArchiwum(context, archiwumNapraw)
                zapiszDokumenty(context, archiwumDokumentow, "archiwum_dokumenty")
                pokazDialogWyczyscWszystko = false
                Toast.makeText(context, lang.t("Archiwum wyczyszczone", "Archive cleared"), Toast.LENGTH_SHORT).show()
            },
            onAnuluj = { pokazDialogWyczyscWszystko = false }
        )
    }
}

// === EKRAN DOKUMENTY ===
fun translateKategoriaDok(kat: String, isEnglish: Boolean): String {
    if (!isEnglish) return kat
    return when (kat) {
        "Faktura / Paragon" -> "Invoice / Receipt"
        "Polisa OC/AC" -> "Insurance Policy"
        "Dowód rejestracyjny" -> "Registration Cert."
        "Badanie techniczne" -> "Technical Inspection"
        "Inny" -> "Other"
        else -> kat
    }
}

@Composable
fun EkranDokumenty(
    paddingValues: PaddingValues,
    listaDokumentow: SnapshotStateList<Dokument>,
    archiwumDokumentow: SnapshotStateList<Dokument>,
    onPowrot: () -> Unit
) {
    val lang = LocalLanguage.current
    val context = LocalContext.current
    val listaAut = remember { wczytajAuta(context) }

    var dokumentDoUsuniecia by remember { mutableStateOf<Dokument?>(null) }
    var dokumentDoPodgladu by remember { mutableStateOf<Dokument?>(null) }
    var dokumentDoEdycji by remember { mutableStateOf<Dokument?>(null) }
    var pokazDialogDodawania by remember { mutableStateOf(false) }

    var wybraneUri by remember { mutableStateOf<Uri?>(null) }
    var nazwaWybranegoPliku by remember { mutableStateOf("") }
    var typWybranego by remember { mutableStateOf("OTHER") }

    // Lista kategorii bez "Karta pojazdu"
    val kategorieDokumentow = listOf("Faktura / Paragon", "Polisa OC/AC", "Dowód rejestracyjny", "Badanie techniczne", "Inny")
    var wybranaKategoriaFiltru by remember { mutableStateOf<String?>(null) }
    var wybraneAutoFiltru by remember { mutableStateOf<Pojazd?>(null) }
    var rozwinMenuKategorii by remember { mutableStateOf(false) }
    var rozwinMenuAut by remember { mutableStateOf(false) }

    val przefiltrowaneDokumenty = listaDokumentow.filter { dok ->
        val pasujeKategoria = wybranaKategoriaFiltru == null || dok.kategoria == wybranaKategoriaFiltru
        val pasujeAuto = wybraneAutoFiltru == null || dok.pojazdId == wybraneAutoFiltru!!.id
        pasujeKategoria && pasujeAuto
    }

    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                wybraneUri = uri
                nazwaWybranegoPliku = pobierzNazwePliku(context, uri)
                val mimeType = context.contentResolver.getType(uri)
                typWybranego = when {
                    mimeType?.startsWith("image/") == true -> "IMAGE"
                    mimeType == "application/pdf" -> "PDF"
                    else -> "OTHER"
                }
            } catch (e: Exception) {
                Toast.makeText(context, lang.t("Nie udało się otworzyć pliku", "Failed to open file"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.padding(paddingValues).fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Powrót",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable { onPowrot() }
                        )
                        Text(
                            text = lang.t("Dokumenty", "Documents"),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = lang.t("Faktury, polisy i inne", "Invoices, policies & files"),
                        color = TextGray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 28.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .background(CardGradient, RoundedCornerShape(9.dp))
                        .border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                        .clip(RoundedCornerShape(9.dp))
                        .clickable {
                            wybraneUri = null
                            nazwaWybranegoPliku = ""
                            typWybranego = "OTHER"
                            pokazDialogDodawania = true
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(lang.t("Dodaj plik", "Add file"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(horizontalTaperingBrush))
            Spacer(modifier = Modifier.height(12.dp))

            // Pasek dwóch filtrów: Kategoria (lewy) oraz Auto (prawy z kotwicą do prawej krawędzi)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Lewy filtr: Kategorie dokumentów
                Box(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardGradient, RoundedCornerShape(9.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { rozwinMenuKategorii = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = wybranaKategoriaFiltru?.let { translateKategoriaDok(it, lang.isEnglish) } ?: lang.t("Wszystkie typy", "All types"),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }

                    DropdownMenu(
                        expanded = rozwinMenuKategorii,
                        onDismissRequest = { rozwinMenuKategorii = false },
                        modifier = Modifier.background(CardGradient).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                    ) {
                        Column(modifier = Modifier.wrapContentWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { wybranaKategoriaFiltru = null; rozwinMenuKategorii = false }
                                    .padding(vertical = 10.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lang.t("Wszystkie typy", "All types"),
                                    color = if (wybranaKategoriaFiltru == null) BlueAccent else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (wybranaKategoriaFiltru == null) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 8.dp).background(horizontalTaperingBrush))
                            kategorieDokumentow.forEachIndexed { index, kat ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { wybranaKategoriaFiltru = kat; rozwinMenuKategorii = false }
                                        .padding(vertical = 10.dp, horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = translateKategoriaDok(kat, lang.isEnglish),
                                        color = if (wybranaKategoriaFiltru == kat) BlueAccent else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = if (wybranaKategoriaFiltru == kat) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                if (index < kategorieDokumentow.size - 1) {
                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 8.dp).background(horizontalTaperingBrush))
                                }
                            }
                        }
                    }
                }

                // Prawy filtr: Pojazdy (z kotwicą w prawym dolnym rogu)
                Box(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardGradient, RoundedCornerShape(9.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { rozwinMenuAut = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = wybraneAutoFiltru?.markaModel ?: lang.t("Wszystkie auta", "All vehicles"),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }

                    Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                        DropdownMenu(
                            expanded = rozwinMenuAut,
                            onDismissRequest = { rozwinMenuAut = false },
                            modifier = Modifier.background(CardGradient).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                        ) {
                            Column(modifier = Modifier.wrapContentWidth()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { wybraneAutoFiltru = null; rozwinMenuAut = false }
                                        .padding(vertical = 10.dp, horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = lang.t("Wszystkie auta", "All vehicles"),
                                        color = if (wybraneAutoFiltru == null) BlueAccent else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = if (wybraneAutoFiltru == null) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                if (listaAut.isNotEmpty()) {
                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 8.dp).background(horizontalTaperingBrush))
                                }
                                listaAut.forEachIndexed { index, auto ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { wybraneAutoFiltru = auto; rozwinMenuAut = false }
                                            .padding(vertical = 10.dp, horizontal = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = auto.markaModel,
                                            color = if (wybraneAutoFiltru?.id == auto.id) BlueAccent else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = if (wybraneAutoFiltru?.id == auto.id) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (index < listaAut.size - 1) {
                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 8.dp).background(horizontalTaperingBrush))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (przefiltrowaneDokumenty.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextGray.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(lang.t("Brak dodanych dokumentów.", "No added documents."), color = TextGray, fontSize = 14.sp)
                    }
                }
            }
        } else {
            items(przefiltrowaneDokumenty.reversed(), key = { it.id }) { dokument ->
                KartaDokumentu(
                    dokument = dokument,
                    onClick = {
                        if (dokument.typ == "IMAGE" || dokument.typ == "PDF") {
                            dokumentDoPodgladu = dokument
                        } else {
                            try {
                                val uri = Uri.parse(dokument.uriString)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, lang.t("Otwórz za pomocą...", "Open with...")))
                            } catch (e: Exception) {
                                Toast.makeText(context, lang.t("Nie można otworzyć tego pliku", "Cannot open this file"), Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onEdytuj = { dokumentDoEdycji = dokument },
                    onUsun = { dokumentDoUsuniecia = dokument }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Okno dodawania nowego dokumentu (najpierw wybór auta, typu, opisu oraz przycisk Wybierz plik)
    if (pokazDialogDodawania) {
        DialogFormularzDokumentu(
            tytulOkna = lang.t("Dodaj dokument", "Add document"),
            tekstPrzycisku = lang.t("Zapisz", "Save"),
            listaAut = listaAut,
            poczatkowyOpis = "",
            poczatkowaKategoria = "Faktura / Paragon",
            poczatkoweAutoId = listaAut.firstOrNull()?.id ?: -1,
            pokazWyborPliku = true,
            nazwaWybranegoPliku = nazwaWybranegoPliku,
            onWybierzPlik = { launcher.launch(arrayOf("*/*")) },
            onZapisz = { wybranyPojazd, wybranaKat, dodatkowyOpis ->
                if (wybraneUri != null) {
                    val formatDaty = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    val sciezkaWewnetrzna = skopiujPlikDoPamięciAplikacji(context, wybraneUri!!, typWybranego)
                    if (sciezkaWewnetrzna != null) {
                        val maxId = (listaDokumentow + archiwumDokumentow).maxOfOrNull { it.id } ?: 0
                        listaDokumentow.add(
                            Dokument(
                                id = maxId + 1,
                                nazwa = dodatkowyOpis.trim(), // Przechowuje opcjonalny dodatkowy opis, a główną nazwą jest wybranaKat
                                uriString = sciezkaWewnetrzna,
                                dataDodania = formatDaty.format(Date()),
                                typ = typWybranego,
                                kategoria = wybranaKat,
                                pojazdId = wybranyPojazd?.id ?: -1,
                                pojazdNazwa = wybranyPojazd?.markaModel ?: "",
                                pojazdRejestracja = wybranyPojazd?.rejestracja ?: ""
                            )
                        )
                        zapiszDokumenty(context, listaDokumentow)
                    } else {
                        Toast.makeText(context, lang.t("Wystąpił błąd podczas zapisywania pliku", "Error saving file"), Toast.LENGTH_SHORT).show()
                    }
                    pokazDialogDodawania = false
                } else {
                    Toast.makeText(context, lang.t("Najpierw wybierz plik", "Please select a file first"), Toast.LENGTH_SHORT).show()
                }
            },
            onAnuluj = { pokazDialogDodawania = false }
        )
    }

    // Okno edycji istniejącego dokumentu
    // Okno edycji istniejącego dokumentu
    if (dokumentDoEdycji != null) {
        val edytowany = dokumentDoEdycji!!
        val poczatkowyOpisEdycji = if (edytowany.nazwa == edytowany.kategoria) "" else edytowany.nazwa

        var edycjaUri by remember { mutableStateOf<Uri?>(null) }
        var edycjaNazwaPliku by remember { mutableStateOf("") }
        var edycjaTyp by remember { mutableStateOf(edytowany.typ) }

        val edycjaLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    edycjaUri = uri
                    edycjaNazwaPliku = pobierzNazwePliku(context, uri)
                    val mimeType = context.contentResolver.getType(uri)
                    edycjaTyp = when {
                        mimeType?.startsWith("image/") == true -> "IMAGE"
                        mimeType == "application/pdf" -> "PDF"
                        else -> "OTHER"
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, lang.t("Nie udało się otworzyć pliku", "Failed to open file"), Toast.LENGTH_SHORT).show()
                }
            }
        }

        DialogFormularzDokumentu(
            tytulOkna = lang.t("Edytuj dokument", "Edit document"),
            tekstPrzycisku = lang.t("Zapisz zmiany", "Save changes"),
            listaAut = listaAut,
            poczatkowyOpis = poczatkowyOpisEdycji,
            poczatkowaKategoria = edytowany.kategoria,
            poczatkoweAutoId = edytowany.pojazdId,
            pokazWyborPliku = true,
            nazwaWybranegoPliku = if (edycjaNazwaPliku.isNotBlank()) edycjaNazwaPliku else lang.t("Zmień plik (opcjonalnie)", "Change file (optional)"),
            onWybierzPlik = { edycjaLauncher.launch(arrayOf("*/*")) },
            onZapisz = { wybranyPojazd, wybranaKat, dodatkowyOpis ->
                val index = listaDokumentow.indexOfFirst { it.id == edytowany.id }
                if (index != -1) {
                    var finalnaSciezka = edytowany.uriString
                    var finalnyTyp = edytowany.typ

                    if (edycjaUri != null) {
                        val nowaSciezkaWewnetrzna = skopiujPlikDoPamięciAplikacji(context, edycjaUri!!, edycjaTyp)
                        if (nowaSciezkaWewnetrzna != null) {
                            if (finalnaSciezka.startsWith("/")) {
                                val staryPlik = File(finalnaSciezka)
                                if (staryPlik.exists()) staryPlik.delete()
                            }
                            finalnaSciezka = nowaSciezkaWewnetrzna
                            finalnyTyp = edycjaTyp
                        }
                    }

                    listaDokumentow[index] = edytowany.copy(
                        nazwa = dodatkowyOpis.trim(),
                        uriString = finalnaSciezka,
                        typ = finalnyTyp,
                        kategoria = wybranaKat,
                        pojazdId = wybranyPojazd?.id ?: -1,
                        pojazdNazwa = wybranyPojazd?.markaModel ?: "",
                        pojazdRejestracja = wybranyPojazd?.rejestracja ?: ""
                    )
                    zapiszDokumenty(context, listaDokumentow)
                    Toast.makeText(context, lang.t("Zaktualizowano dokument", "Document updated"), Toast.LENGTH_SHORT).show()
                }
                dokumentDoEdycji = null
            },
            onAnuluj = { dokumentDoEdycji = null }
        )
    }

    if (dokumentDoUsuniecia != null) {
        val nazwaUsuwanego = translateKategoriaDok(dokumentDoUsuniecia!!.kategoria, lang.isEnglish)
        DialogPotwierdzeniaUsuniecia(
            tytul = lang.t("Usuń dokument", "Delete document"),
            tresc = lang.t("Czy na pewno chcesz usunąć dokument \"$nazwaUsuwanego\"? Zostanie przeniesiony do archiwum.", "Are you sure you want to delete \"$nazwaUsuwanego\"? It will be moved to archive."),
            onPotwierdz = {
                val dok = dokumentDoUsuniecia!!
                archiwumDokumentow.add(0, dok)
                listaDokumentow.remove(dok)
                zapiszDokumenty(context, listaDokumentow)
                zapiszDokumenty(context, archiwumDokumentow, "archiwum_dokumenty")
                Toast.makeText(context, lang.t("Przeniesiono dokument do archiwum", "Document moved to archive"), Toast.LENGTH_SHORT).show()
                dokumentDoUsuniecia = null
            },
            onAnuluj = { dokumentDoUsuniecia = null }
        )
    }

    if (dokumentDoPodgladu != null) {
        DialogPodgladuDokumentu(
            dokument = dokumentDoPodgladu!!,
            onZamknij = { dokumentDoPodgladu = null }
        )
    }
}

@Composable
fun DialogFormularzDokumentu(
    tytulOkna: String,
    tekstPrzycisku: String,
    listaAut: List<Pojazd>,
    poczatkowyOpis: String,
    poczatkowaKategoria: String,
    poczatkoweAutoId: Int,
    pokazWyborPliku: Boolean,
    nazwaWybranegoPliku: String,
    onWybierzPlik: () -> Unit,
    onZapisz: (Pojazd?, String, String) -> Unit,
    onAnuluj: () -> Unit
) {
    val lang = LocalLanguage.current
    val context = LocalContext.current
    val kategorie = listOf("Faktura / Paragon", "Polisa OC/AC", "Dowód rejestracyjny", "Badanie techniczne", "Inny")

    var wybraneAuto by remember {
        mutableStateOf(listaAut.find { it.id == poczatkoweAutoId } ?: listaAut.firstOrNull())
    }

    val jestWbudowana = poczatkowaKategoria in kategorie
    var wybranaKategoria by remember {
        mutableStateOf(if (jestWbudowana) poczatkowaKategoria else "Inny")
    }
    var wlasnaNazwaKategorii by remember {
        mutableStateOf(if (!jestWbudowana) poczatkowaKategoria else "")
    }

    var menuKategoriiRozwiniete by remember { mutableStateOf(false) }
    var dodatkowyOpis by remember { mutableStateOf(poczatkowyOpis) }

    AlertDialog(
        onDismissRequest = onAnuluj,
        modifier = Modifier.border(1.dp, CardBorder, RoundedCornerShape(16.dp)).background(CardGradient, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.Transparent,
        title = { Text(tytulOkna, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (listaAut.isNotEmpty()) {
                    Text(lang.t("Wybierz pojazd:", "Select vehicle:"), color = TextGray, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listaAut) { auto ->
                            val isSelected = wybraneAuto?.id == auto.id
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    painter = painterResource(id = auto.avatarRes),
                                    contentDescription = auto.markaModel,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color.Transparent)
                                        .border(width = if (isSelected) 1.5.dp else 0.dp, color = if (isSelected) BlueAccent else Color.Transparent, shape = RoundedCornerShape(9.dp))
                                        .padding(if (isSelected) 3.dp else 0.dp)
                                        .clickable { wybraneAuto = auto }
                                )
                                Text(auto.rejestracja.ifBlank { auto.markaModel }, color = if (isSelected) Color.White else TextGray, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }

                Text(lang.t("Typ dokumentu:", "Document type:"), color = TextGray, fontSize = 12.sp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBgColor, RoundedCornerShape(12.dp))
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                        .clickable { menuKategoriiRozwiniete = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = translateKategoriaDok(wybranaKategoria, lang.isEnglish), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextGray)
                    }
                    DropdownMenu(
                        expanded = menuKategoriiRozwiniete,
                        onDismissRequest = { menuKategoriiRozwiniete = false },
                        modifier = Modifier.background(CardGradient).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                    ) {
                        kategorie.forEach { kat ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = translateKategoriaDok(kat, lang.isEnglish),
                                        color = if (wybranaKategoria == kat) BlueAccent else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = if (wybranaKategoria == kat) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    wybranaKategoria = kat
                                    menuKategoriiRozwiniete = false
                                }
                            )
                        }
                    }
                }

                if (wybranaKategoria == "Inny") {
                    OutlinedTextField(
                        value = wlasnaNazwaKategorii,
                        onValueChange = { wlasnaNazwaKategorii = it },
                        placeholder = { Text(lang.t("Wpisz własną nazwę dokumentu", "Enter custom document name"), color = TextGray, fontSize = 14.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardBgColor,
                            unfocusedContainerColor = CardBgColor,
                            focusedBorderColor = BlueAccent,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = BlueAccent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = dodatkowyOpis,
                    onValueChange = { dodatkowyOpis = it },
                    placeholder = { Text(lang.t("Dodatkowy opis (opcjonalnie)", "Additional description (optional)"), color = TextGray, fontSize = 14.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBgColor,
                        unfocusedContainerColor = CardBgColor,
                        focusedBorderColor = BlueAccent,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = BlueAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (pokazWyborPliku) {
                    val czyWybrano = nazwaWybranegoPliku.isNotBlank() && !nazwaWybranegoPliku.contains("zmień plik", ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardBgColor, RoundedCornerShape(12.dp))
                            .border(1.dp, if (czyWybrano) BlueAccent else CardBorder, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onWybierzPlik() }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (czyWybrano) Icons.Default.CheckCircle else Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = if (czyWybrano) BlueAccent else TextGray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (nazwaWybranegoPliku.isNotBlank()) nazwaWybranegoPliku else lang.t("Wybierz plik", "Select file"),
                                color = if (czyWybrano) Color.White else TextGray,
                                fontSize = 14.sp,
                                fontWeight = if (czyWybrano) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalnaKategoria = if (wybranaKategoria == "Inny" && wlasnaNazwaKategorii.isNotBlank()) {
                        wlasnaNazwaKategorii.trim()
                    } else {
                        wybranaKategoria
                    }

                    if (wybranaKategoria == "Inny" && wlasnaNazwaKategorii.isBlank()) {
                        Toast.makeText(context, lang.t("Wpisz nazwę dokumentu", "Enter document name"), Toast.LENGTH_SHORT).show()
                    } else if (pokazWyborPliku && nazwaWybranegoPliku.isBlank()) {
                        Toast.makeText(context, lang.t("Wybierz plik", "Select file"), Toast.LENGTH_SHORT).show()
                    } else {
                        onZapisz(wybraneAuto, finalnaKategoria, dodatkowyOpis)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
            ) { Text(tekstPrzycisku, color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onAnuluj) { Text(lang.t("Anuluj", "Cancel"), color = TextGray) }
        }
    )
}

@Composable
fun KartaDokumentu(
    dokument: Dokument,
    onClick: () -> Unit,
    onEdytuj: (() -> Unit)? = null,
    onUsun: (() -> Unit)? = null
) {
    val lang = LocalLanguage.current
    var rozwinMenu by remember { mutableStateOf(false) }

    val (ikona, kolorIkony) = when (dokument.typ) {
        "IMAGE" -> Pair(Icons.Default.Image, BlueAccent)
        "PDF" -> Pair(Icons.Default.PictureAsPdf, RedAccent)
        else -> Pair(Icons.Default.InsertDriveFile, YellowAccent)
    }

    val tytulGlowny = translateKategoriaDok(dokument.kategoria, lang.isEnglish)
    val dodatkowyOpis = if (dokument.nazwa != dokument.kategoria) dokument.nazwa else ""

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(CardGradient, RoundedCornerShape(9.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(9.dp))
            .clip(RoundedCornerShape(9.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(32.dp).background(IconBgColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(ikona, contentDescription = null, tint = kolorIkony, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp) // Minimalny odstęp między wierszami
            ) {
                // Pierwszy wiersz: Typ dokumentu po lewej, sama data po prawej na równi
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tytulGlowny,
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = dokument.dataDodania,
                        color = TextGray,
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Drugi wiersz: Powiększona nazwa auta i nr rejestracyjny
                val autoInfo = if (dokument.pojazdNazwa.isNotBlank()) {
                    if (dokument.pojazdRejestracja.isNotBlank()) "${dokument.pojazdNazwa} · ${dokument.pojazdRejestracja}" else dokument.pojazdNazwa
                } else ""

                if (autoInfo.isNotBlank()) {
                    Text(
                        text = autoInfo,
                        color = BlueAccent,
                        fontSize = 12.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Trzeci wiersz (opcjonalny): Dodatkowy opis
                if (dodatkowyOpis.isNotBlank()) {
                    Text(
                        text = dodatkowyOpis,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (onEdytuj != null || onUsun != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Box {
                    IconButton(onClick = { rozwinMenu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opcje", tint = TextGray, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = rozwinMenu,
                        onDismissRequest = { rozwinMenu = false },
                        modifier = Modifier.background(CardGradient).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                    ) {
                        if (onEdytuj != null) {
                            DropdownMenuItem(
                                text = { Text(lang.t("Edytuj", "Edit"), color = Color.White, fontSize = 12.sp) },
                                onClick = {
                                    rozwinMenu = false
                                    onEdytuj()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = BlueAccent) }
                            )
                        }
                        if (onUsun != null) {
                            DropdownMenuItem(
                                text = { Text(lang.t("Usuń", "Delete"), color = RedAccent, fontSize = 12.sp) },
                                onClick = {
                                    rozwinMenu = false
                                    onUsun()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = RedAccent) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// === KARTA ELEMENTU W ARCHIWUM ===
// === KARTA ELEMENTU W ARCHIWUM ===
@Composable
fun KartaElementuArchiwum(
    ikona: ImageVector,
    kolorIkony: Color,
    kategoriaEtykieta: String,
    tytul: String,
    podtytul: String,
    onPrzywroc: () -> Unit,
    onUsunTrwale: () -> Unit
) {
    val ciasnyStyl = androidx.compose.ui.text.TextStyle(
        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier.fillMaxWidth().background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp) // Zmniejszone z 10.dp na 6.dp, żeby puste pole góra/dół zniknęło
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp) // Nieco mniejsza ikona (była 32.dp)
                    .background(IconBgColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(ikona, contentDescription = null, tint = kolorIkony, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = kategoriaEtykieta,
                    color = BlueAccent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = ciasnyStyl
                )
                Text(
                    text = tytul,
                    color = Color.White,
                    fontSize = 12.sp, // Lekko dopasowany rozmiar
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = ciasnyStyl
                )
                Text(
                    text = podtytul,
                    color = TextGray,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = ciasnyStyl
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.fillMaxHeight().padding(vertical = 2.dp).width(1.5.dp).background(verticalBrightDividerBrush))
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(26.dp) // Zmniejszone przyciski akcji
                    .background(IconBgColor, RoundedCornerShape(6.dp))
                    .border(1.dp, GreenAccent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onPrzywroc() },
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Przywróć", tint = GreenAccent, modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(26.dp) // Zmniejszone przyciski akcji
                    .background(IconBgColor, RoundedCornerShape(6.dp))
                    .border(1.dp, RedAccent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onUsunTrwale() },
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Usuń trwale", tint = RedAccent, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// === KARTA POJEDYNCZEJ NAPRAWY ===
// === KARTA POJEDYNCZEJ NAPRAWY ===
// === KARTA POJEDYNCZEJ NAPRAWY ===
@Composable
fun KartaNaprawy(naprawa: Naprawa, onEdytuj: () -> Unit, onUsun: () -> Unit) {
    val lang = LocalLanguage.current
    var rozwinMenu by remember { mutableStateOf(false) }

    val ciasnyStyl = androidx.compose.ui.text.TextStyle(
        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier.fillMaxWidth().background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp) // Identyczny padding jak w KartaWizyty
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Identyczny kontener i rozmiar ikony (32.dp z ikoną 18.dp) jak w KartaWizyty
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(IconBgColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp) // Spójny odstęp 3.dp jak w KartaWizyty
            ) {
                Text(
                    text = naprawa.nazwa,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                    style = ciasnyStyl
                )
                if (naprawa.notatki.isNotBlank()) {
                    Text(
                        text = naprawa.notatki,
                        color = TextGray,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        style = ciasnyStyl,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.fillMaxHeight().padding(vertical = 2.dp).width(1.5.dp).background(verticalBrightDividerBrush))
            Spacer(modifier = Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${naprawa.koszt} ${naprawa.waluta}",
                    color = BlueAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    style = ciasnyStyl
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box {
                    IconButton(onClick = { rozwinMenu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opcje", tint = TextGray, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = rozwinMenu,
                        onDismissRequest = { rozwinMenu = false },
                        modifier = Modifier.background(CardGradient).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text(lang.t("Edytuj", "Edit"), color = Color.White) },
                            onClick = { rozwinMenu = false; onEdytuj() },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = BlueAccent) }
                        )
                        DropdownMenuItem(
                            text = { Text(lang.t("Usuń", "Delete"), color = RedAccent) },
                            onClick = { rozwinMenu = false; onUsun() },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = RedAccent) }
                        )
                    }
                }
            }
        }
    }
}

// === FORMULARZ NAPRAWY Z WYBOREM WALUTY ===
@Composable
fun DialogFormularzNaprawy(
    naprawaPoczatkowa: Naprawa?,
    onZapisz: (String, String, String, String) -> Unit,
    onAnuluj: () -> Unit
) {
    val lang = LocalLanguage.current
    val context = LocalContext.current
    var nazwa by remember { mutableStateOf(naprawaPoczatkowa?.nazwa ?: "") }
    var koszt by remember { mutableStateOf(naprawaPoczatkowa?.koszt ?: "") }
    var notatki by remember { mutableStateOf(naprawaPoczatkowa?.notatki ?: "") }
    var wybranaWaluta by remember { mutableStateOf(naprawaPoczatkowa?.waluta ?: "PLN") }
    var pokazMenuWalut by remember { mutableStateOf(false) }

    val zapisaneNaprawy = remember { wczytajNaprawyHistory(context).toList() }
    var pokazMenuNapraw by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused) {
        if (isFocused && zapisaneNaprawy.isNotEmpty()) pokazMenuNapraw = true
    }
    val tytulOkienka = if (naprawaPoczatkowa != null) lang.t("Edytuj wpis", "Edit entry") else lang.t("Dodaj naprawę / część", "Add repair / part")

    AlertDialog(
        onDismissRequest = onAnuluj,
        modifier = Modifier.border(1.dp, CardBorder, RoundedCornerShape(16.dp)).background(CardGradient, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.Transparent,
        title = { Text(tytulOkienka, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                var szerokoscPolaNaprawy by remember { mutableStateOf(0) }
                val densityNaprawy = LocalDensity.current

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = nazwa,
                        onValueChange = { nazwa = it; pokazMenuNapraw = true },
                        placeholder = { Text(lang.t("Nazwa części / usługi", "Part / service name"), color = TextGray, fontSize = 14.sp) },
                        singleLine = true,
                        interactionSource = interactionSource,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = CardBgColor, unfocusedContainerColor = CardBgColor, focusedBorderColor = CardBorder, unfocusedBorderColor = CardBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = BlueAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                szerokoscPolaNaprawy = coords.size.width
                            }
                    )
                    val pasujaceNaprawy = if (nazwa.isBlank()) zapisaneNaprawy else zapisaneNaprawy.filter { it.contains(nazwa, ignoreCase = true) }
                    if (pokazMenuNapraw && pasujaceNaprawy.isNotEmpty()) {
                        val scrollState = rememberScrollState()
                        val szerokoscMenuDp = with(densityNaprawy) { szerokoscPolaNaprawy.toDp() }
                        val wysWiersza = 34.dp
                        val widocznePozycje = minOf(pasujaceNaprawy.size, 5)
                        val wysListy = wysWiersza * widocznePozycje

                        DropdownMenu(
                            expanded = pokazMenuNapraw,
                            onDismissRequest = { pokazMenuNapraw = false },
                            properties = androidx.compose.ui.window.PopupProperties(focusable = false),
                            modifier = Modifier
                                .width(szerokoscMenuDp)
                                .background(CardGradient)
                                .border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().height(wysListy)) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                ) {
                                    pasujaceNaprawy.forEachIndexed { index, naprawaH ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(wysWiersza)
                                                .clickable {
                                                    nazwa = naprawaH
                                                    pokazMenuNapraw = false
                                                }
                                                .padding(horizontal = 14.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = naprawaH,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (index < pasujaceNaprawy.size - 1) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(1.dp)
                                                    .padding(horizontal = 8.dp)
                                                    .background(horizontalTaperingBrush)
                                            )
                                        }
                                    }
                                }
                                if (pasujaceNaprawy.size > 5) {
                                    val ulamekWidoczny = 5f / pasujaceNaprawy.size.toFloat()
                                    val wysSuwaka = wysListy * ulamekWidoczny
                                    val postep = if (scrollState.maxValue > 0) scrollState.value.toFloat() / scrollState.maxValue.toFloat() else 0f
                                    val przesuniecieY = (wysListy - wysSuwaka) * postep
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(end = 3.dp)
                                            .offset(y = przesuniecieY)
                                            .width(3.dp)
                                            .height(wysSuwaka)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(BlueAccent.copy(alpha = 0.8f))
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = koszt,
                        onValueChange = { input ->
                            val czysteCyfry = input.filter { it.isDigit() }
                            koszt = if (czysteCyfry.isNotEmpty()) czysteCyfry.reversed().chunked(3).joinToString(".").reversed() else ""
                        },
                        placeholder = { Text(lang.t("Koszt", "Cost"), color = TextGray, fontSize = 14.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = CardBgColor, unfocusedContainerColor = CardBgColor, focusedBorderColor = CardBorder, unfocusedBorderColor = CardBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = BlueAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        Row(
                            modifier = Modifier
                                .height(56.dp)
                                .background(CardBgColor, RoundedCornerShape(12.dp))
                                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { pokazMenuWalut = true }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(text = wybranaWaluta, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextGray, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = pokazMenuWalut,
                            onDismissRequest = { pokazMenuWalut = false },
                            modifier = Modifier.background(CardGradient).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                        ) {
                            DostepneWaluty.forEach { waluta ->
                                DropdownMenuItem(
                                    text = { Text(waluta, color = if (wybranaWaluta == waluta) BlueAccent else Color.White, fontSize = 13.sp, fontWeight = if (wybranaWaluta == waluta) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        wybranaWaluta = waluta
                                        pokazMenuWalut = false
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = notatki,
                    onValueChange = { notatki = it },
                    placeholder = { Text(lang.t("Notatki (opcjonalnie)", "Notes (optional)"), color = TextGray, fontSize = 14.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = CardBgColor, unfocusedContainerColor = CardBgColor, focusedBorderColor = CardBorder, unfocusedBorderColor = CardBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = BlueAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nazwa.isNotBlank() && koszt.isNotBlank()) {
                        onZapisz(nazwa, koszt, notatki, wybranaWaluta)
                    } else {
                        Toast.makeText(context, lang.t("Uzupełnij nazwę i koszt", "Fill in name and cost"), Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
            ) { Text(lang.t("Zapisz", "Save"), color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onAnuluj) { Text(lang.t("Anuluj", "Cancel"), color = TextGray) }
        }
    )
}

// === KARTA WIZYTY ===
@Composable
fun KartaWizyty(wizyta: Wizyta, onClick: () -> Unit, onEdytuj: () -> Unit, onUsun: () -> Unit) {
    val lang = LocalLanguage.current
    var rozwinMenu by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier.fillMaxWidth().background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp)).clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(32.dp).background(IconBgColor, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Build, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy((3).dp) // Zmniejszony odstęp między wierszami
            ) {
                // 1 linijka: Nazwa warsztatu zawijana w dół gdy zbliża się do daty i kwoty
                Text(
                    text = wizyta.warsztat,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                // 2 linijka: Nazwa auta + nr rejestracyjny obok siebie
                val autoTekst = if (wizyta.pojazd.rejestracja.isNotBlank()) {
                    "${wizyta.pojazd.markaModel} · ${wizyta.pojazd.rejestracja}"
                } else {
                    wizyta.pojazd.markaModel
                }
                Text(
                    text = autoTekst,
                    color = TextGray,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 3 linijka: Przebieg + "Naprawy: ilość"
                val przebiegTekst = if (wizyta.przebieg.isNotBlank()) "${wizyta.przebieg} km · " else ""
                Text(
                    text = "$przebiegTekst${lang.t("Naprawy", "Repairs")}: ${wizyta.naprawy.size}",
                    color = TextGray,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(wizyta.data, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    val sumaStr = podsumujKosztyWgWalut(wizyta.naprawy)
                    Text(sumaStr, color = BlueAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box {
                    IconButton(onClick = { rozwinMenu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opcje", tint = TextGray, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = rozwinMenu,
                        onDismissRequest = { rozwinMenu = false },
                        modifier = Modifier.background(CardGradient).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text(lang.t("Edytuj", "Edit"), color = Color.White) },
                            onClick = { rozwinMenu = false; onEdytuj() },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = BlueAccent) }
                        )
                        DropdownMenuItem(
                            text = { Text(lang.t("Usuń", "Delete"), color = RedAccent) },
                            onClick = { rozwinMenu = false; onUsun() },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = RedAccent) }
                        )
                    }
                }
            }
        }
    }
}

// === OKIENKO FORMULARZA WIZYTY ===
@Composable
fun DialogFormularzWizyty(
    wizytaPoczatkowa: Wizyta?,
    listaAut: List<Pojazd>,
    onZapisz: (Pojazd, String, String, String) -> Unit,
    onAnuluj: () -> Unit
) {
    val lang = LocalLanguage.current
    if (listaAut.isEmpty()) {
        AlertDialog(
            onDismissRequest = onAnuluj,
            modifier = Modifier.border(1.dp, CardBorder, RoundedCornerShape(9.dp)).background(CardGradient, RoundedCornerShape(9.dp)),
            shape = RoundedCornerShape(9.dp),
            containerColor = Color.Transparent,
            title = { Text(lang.t("Brak pojazdów", "No vehicles"), color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text(lang.t("Aby dodać wizytę, musisz najpierw dodać co najmniej jedno auto.", "To add a visit, you need to add at least one vehicle first."), color = TextGray) },
            confirmButton = {
                Button(onClick = onAnuluj, colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)) {
                    Text(lang.t("Rozumiem", "Got it"), color = Color.White)
                }
            }
        )
        return
    }
    val context = LocalContext.current
    val kalendarz = Calendar.getInstance()
    val formatDaty = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val dzisStr = formatDaty.format(kalendarz.time)
    var wybraneAuto by remember { mutableStateOf(wizytaPoczatkowa?.pojazd ?: listaAut.first()) }
    var nazwaWarsztatu by remember { mutableStateOf(wizytaPoczatkowa?.warsztat ?: "") }
    var przebiegWizyty by remember { mutableStateOf(wizytaPoczatkowa?.przebieg ?: "") }
    var dataWizyty by remember { mutableStateOf(wizytaPoczatkowa?.data ?: dzisStr) }
    val zapisaneWarsztaty = remember { wczytajWarsztaty(context).toList() }
    var pokazMenuWarsztatow by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused) {
        if (isFocused && zapisaneWarsztaty.isNotEmpty()) pokazMenuWarsztatow = true
    }
    val tytulOkienka = if (wizytaPoczatkowa != null) lang.t("Edytuj wizytę", "Edit visit") else lang.t("Nowa wizyta", "New visit")
    val tekstPrzycisku = if (wizytaPoczatkowa != null) lang.t("Zapisz zmiany", "Save changes") else lang.t("Zapisz wizytę", "Save visit")

    val datePickerWizyta = DatePickerDialog(
        context,
        android.R.style.Theme_Material_Dialog,
        { _, rok, miesiac, dzien -> dataWizyty = String.format("%02d.%02d.%04d", dzien, miesiac + 1, rok) },
        kalendarz.get(Calendar.YEAR),
        kalendarz.get(Calendar.MONTH),
        kalendarz.get(Calendar.DAY_OF_MONTH)
    )

    AlertDialog(
        onDismissRequest = onAnuluj,
        modifier = Modifier.border(1.dp, CardBorder, RoundedCornerShape(16.dp)).background(CardGradient, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.Transparent,
        title = { Text(tytulOkienka, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(lang.t("Wybierz pojazd:", "Select vehicle:"), color = TextGray, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listaAut) { auto ->
                        val isSelected = wybraneAuto == auto
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(id = auto.avatarRes),
                                contentDescription = auto.markaModel,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Color.Transparent)
                                    .border(width = if (isSelected) 1.5.dp else 0.dp, color = if (isSelected) BlueAccent else Color.Transparent, shape = RoundedCornerShape(9.dp))
                                    .padding(if (isSelected) 3.dp else 0.dp)
                                    .clickable { wybraneAuto = auto }
                            )
                            Text(auto.rejestracja.ifBlank { auto.markaModel }, color = if (isSelected) Color.White else TextGray, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
                var szerokoscPolaWarsztatu by remember { mutableStateOf(0) }
                val densityWarsztatu = LocalDensity.current

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = nazwaWarsztatu,
                        onValueChange = { nazwaWarsztatu = it; pokazMenuWarsztatow = true },
                        placeholder = { Text(lang.t("Nazwa warsztatu (lub adres)", "Workshop name (or address)"), color = TextGray, fontSize = 14.sp) },
                        singleLine = true,
                        interactionSource = interactionSource,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = CardBgColor, unfocusedContainerColor = CardBgColor, focusedBorderColor = CardBorder, unfocusedBorderColor = CardBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = BlueAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                szerokoscPolaWarsztatu = coords.size.width
                            }
                    )
                    val pasujaceWarsztaty = if (nazwaWarsztatu.isBlank()) zapisaneWarsztaty else zapisaneWarsztaty.filter { it.contains(nazwaWarsztatu, ignoreCase = true) }
                    if (pokazMenuWarsztatow && pasujaceWarsztaty.isNotEmpty()) {
                        val scrollState = rememberScrollState()
                        val szerokoscMenuDp = with(densityWarsztatu) { szerokoscPolaWarsztatu.toDp() }
                        val wysWiersza = 34.dp
                        val widocznePozycje = minOf(pasujaceWarsztaty.size, 5)
                        val wysListy = wysWiersza * widocznePozycje

                        DropdownMenu(
                            expanded = pokazMenuWarsztatow,
                            onDismissRequest = { pokazMenuWarsztatow = false },
                            properties = androidx.compose.ui.window.PopupProperties(focusable = false),
                            modifier = Modifier
                                .width(szerokoscMenuDp)
                                .background(CardGradient)
                                .border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().height(wysListy)) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                ) {
                                    pasujaceWarsztaty.forEachIndexed { index, warsztatH ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(wysWiersza)
                                                .clickable {
                                                    nazwaWarsztatu = warsztatH
                                                    pokazMenuWarsztatow = false
                                                }
                                                .padding(horizontal = 14.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = warsztatH,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (index < pasujaceWarsztaty.size - 1) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(1.dp)
                                                    .padding(horizontal = 8.dp)
                                                    .background(horizontalTaperingBrush)
                                            )
                                        }
                                    }
                                }
                                if (pasujaceWarsztaty.size > 5) {
                                    val ulamekWidoczny = 5f / pasujaceWarsztaty.size.toFloat()
                                    val wysSuwaka = wysListy * ulamekWidoczny
                                    val postep = if (scrollState.maxValue > 0) scrollState.value.toFloat() / scrollState.maxValue.toFloat() else 0f
                                    val przesuniecieY = (wysListy - wysSuwaka) * postep
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(end = 3.dp)
                                            .offset(y = przesuniecieY)
                                            .width(3.dp)
                                            .height(wysSuwaka)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(BlueAccent.copy(alpha = 0.8f))
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = przebiegWizyty,
                    onValueChange = { input ->
                        val czysteCyfry = input.filter { it.isDigit() }
                        przebiegWizyty = if (czysteCyfry.isNotEmpty()) czysteCyfry.reversed().chunked(3).joinToString(" ").reversed() else ""
                    },
                    placeholder = { Text(lang.t("Przebieg (np. 145 000 km)", "Mileage (e.g. 145,000 km)"), color = TextGray, fontSize = 14.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = CardBgColor, unfocusedContainerColor = CardBgColor, focusedBorderColor = CardBorder, unfocusedBorderColor = CardBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = BlueAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier.fillMaxWidth().background(CardBgColor, RoundedCornerShape(12.dp)).border(1.dp, CardBorder, RoundedCornerShape(12.dp)).clickable { datePickerWizyta.show() }.padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = TextGray, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "${lang.t("Data wizyty", "Visit date")}: $dataWizyty", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nazwaWarsztatu.isNotBlank() && dataWizyty.isNotBlank()) {
                        onZapisz(wybraneAuto, nazwaWarsztatu, przebiegWizyty, dataWizyty)
                    } else {
                        Toast.makeText(context, lang.t("Podaj nazwę warsztatu i datę wizyty", "Provide workshop name and date"), Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
            ) { Text(tekstPrzycisku, color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onAnuluj) { Text(lang.t("Anuluj", "Cancel"), color = TextGray) }
        }
    )
}

// === FORMULARZ TERMINU ===
// === FORMULARZ TERMINU ===
@Composable
fun DialogFormularzTerminu(
    listaAut: List<Pojazd>,
    onZapisz: (Pojazd, String, String, String, String, String, Boolean) -> Unit,
    onAnuluj: () -> Unit
) {
    val lang = LocalLanguage.current
    if (listaAut.isEmpty()) {
        AlertDialog(
            onDismissRequest = onAnuluj,
            modifier = Modifier.border(1.dp, CardBorder, RoundedCornerShape(9.dp)).background(CardGradient, RoundedCornerShape(9.dp)),
            shape = RoundedCornerShape(9.dp),
            containerColor = Color.Transparent,
            title = { Text(lang.t("Brak pojazdów", "No vehicles"), color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text(lang.t("Aby dodać termin, musisz najpierw dodać co najmniej jedno auto.", "To add a schedule, add at least one vehicle first."), color = TextGray) },
            confirmButton = {
                Button(onClick = onAnuluj, colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)) {
                    Text(lang.t("Rozumiem", "Got it"), color = Color.White)
                }
            }
        )
        return
    }
    val context = LocalContext.current
    val kalendarz = Calendar.getInstance()
    var wybraneAuto by remember { mutableStateOf(listaAut.first()) }
    val typyTerminow = listOf("OC", "AC", "OC/AC", "Badanie techniczne", "Wymiana opon", "Wymiana oleju/filtrów", "Serwis klimatyzacji", "Inne")
    var wybranyTyp by remember { mutableStateOf(typyTerminow[0]) }
    var menuRozwiniete by remember { mutableStateOf(false) }
    var dodatkowyOpis by remember { mutableStateOf("") }
    var wprowadzonaData by remember { mutableStateOf("") }
    var recznaDataPrzyp by remember { mutableStateOf("") }
    var recznaGodzinaPrzyp by remember { mutableStateOf("09:00") }
    var sztywnyAlertWlaczony by remember { mutableStateOf(false) }

    val datePickerWaznosc = DatePickerDialog(
        context,
        android.R.style.Theme_Material_Dialog,
        { _, rok, miesiac, dzien -> wprowadzonaData = String.format("%02d.%02d.%04d", dzien, miesiac + 1, rok) },
        kalendarz.get(Calendar.YEAR),
        kalendarz.get(Calendar.MONTH),
        kalendarz.get(Calendar.DAY_OF_MONTH)
    )
    val datePickerPrzypomnienie = DatePickerDialog(
        context,
        android.R.style.Theme_Material_Dialog,
        { _, rok, miesiac, dzien -> recznaDataPrzyp = String.format("%02d.%02d.%04d", dzien, miesiac + 1, rok) },
        kalendarz.get(Calendar.YEAR),
        kalendarz.get(Calendar.MONTH),
        kalendarz.get(Calendar.DAY_OF_MONTH)
    )

    AlertDialog(
        onDismissRequest = onAnuluj,
        modifier = Modifier.border(1.dp, CardBorder, RoundedCornerShape(16.dp)).background(CardGradient, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.Transparent,
        title = { Text(lang.t("Nowy termin", "New schedule"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(lang.t("Wybierz pojazd:", "Select vehicle:"), color = TextGray, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listaAut) { auto ->
                        val isSelected = wybraneAuto == auto
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(id = auto.avatarRes),
                                contentDescription = auto.markaModel,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Color.Transparent)
                                    .border(width = if (isSelected) 1.5.dp else 0.dp, color = if (isSelected) BlueAccent else Color.Transparent, shape = RoundedCornerShape(9.dp))
                                    .padding(if (isSelected) 3.dp else 0.dp)
                                    .clickable { wybraneAuto = auto }
                            )
                            Text(auto.rejestracja.ifBlank { auto.markaModel }, color = if (isSelected) Color.White else TextGray, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().background(CardBgColor, RoundedCornerShape(12.dp)).border(1.dp, CardBorder, RoundedCornerShape(12.dp)).clickable { menuRozwiniete = true }.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(getIkonaDlaTypu(wybranyTyp), contentDescription = null, tint = BlueAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = translateTyp(wybranyTyp, lang.isEnglish), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextGray)
                    }
                    DropdownMenu(
                        expanded = menuRozwiniete,
                        onDismissRequest = { menuRozwiniete = false },
                        modifier = Modifier.background(CardGradient).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                    ) {
                        typyTerminow.forEach { typ ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(getIkonaDlaTypu(typ), contentDescription = null, tint = if (wybranyTyp == typ) BlueAccent else TextGray, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(translateTyp(typ, lang.isEnglish), color = if (wybranyTyp == typ) BlueAccent else Color.White, fontSize = 13.sp)
                                    }
                                },
                                onClick = { wybranyTyp = typ; menuRozwiniete = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = dodatkowyOpis,
                    onValueChange = { dodatkowyOpis = it },
                    placeholder = { Text(lang.t("Dodatkowy opis", "Additional description"), color = TextGray, fontSize = 14.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = CardBgColor, unfocusedContainerColor = CardBgColor, focusedBorderColor = CardBorder, unfocusedBorderColor = CardBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = BlueAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier.fillMaxWidth().background(CardBgColor, RoundedCornerShape(12.dp)).border(1.dp, CardBorder, RoundedCornerShape(12.dp)).clickable { datePickerWaznosc.show() }.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = TextGray, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (wprowadzonaData.isBlank()) lang.t("Wybierz datę ważności terminu", "Select expiration date") else "${lang.t("Termin", "Schedule")}: $wprowadzonaData",
                            color = if (wprowadzonaData.isBlank()) TextGray else Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().background(CardBgColor, RoundedCornerShape(12.dp)).border(1.dp, CardBorder, RoundedCornerShape(12.dp)).clickable { datePickerPrzypomnienie.show() }.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = YellowAccent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (recznaDataPrzyp.isBlank()) lang.t("Wybierz datę powiadomienia", "Select notification date") else "Alert: $recznaDataPrzyp",
                            color = if (recznaDataPrzyp.isBlank()) TextGray else Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().background(CardBgColor, RoundedCornerShape(12.dp)).border(1.dp, CardBorder, RoundedCornerShape(12.dp)).clickable {
                        val parts = recznaGodzinaPrzyp.split(":")
                        val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
                        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        TimePickerDialog(context, android.R.style.Theme_Material_Dialog, { _, godz, min -> recznaGodzinaPrzyp = String.format("%02d:%02d", godz, min) }, h, m, true).show()
                    }.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = YellowAccent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "${lang.t("Godzina powiadomienia", "Notification time")}: $recznaGodzinaPrzyp", color = Color.White, fontSize = 14.sp)
                    }
                }
                // Suwak Włącz/Wyłącz dla Sztywnego Alertu (5 dni przed o 09:00)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBgColor, RoundedCornerShape(12.dp))
                        .border(1.dp, if (sztywnyAlertWlaczony) BlueAccent else CardBorder, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { sztywnyAlertWlaczony = !sztywnyAlertWlaczony }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = lang.t("Powiadomienia", "Notifications"),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = lang.t("Codziennie o 09:00 (5 dni przed)", "Daily at 09:00 (5 days before)"),
                            color = TextGray,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = sztywnyAlertWlaczony,
                        onCheckedChange = { sztywnyAlertWlaczony = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BlueAccent,
                            uncheckedThumbColor = TextGray,
                            uncheckedTrackColor = IconBgColor
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val maRecznyAlert = recznaDataPrzyp.isNotBlank() && recznaGodzinaPrzyp.isNotBlank()
                    if (wprowadzonaData.isNotBlank() && (maRecznyAlert || sztywnyAlertWlaczony)) {
                        onZapisz(wybraneAuto, wybranyTyp, wprowadzonaData, recznaDataPrzyp, recznaGodzinaPrzyp, dodatkowyOpis, sztywnyAlertWlaczony)
                    } else {
                        Toast.makeText(context, lang.t("Wybierz datę terminu oraz ustaw alert ręczny lub włącz sztywny alert", "Select expiration date and set an alert"), Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
            ) { Text(lang.t("Zapisz termin", "Save schedule"), color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onAnuluj) { Text(lang.t("Anuluj", "Cancel"), color = TextGray) }
        }
    )
}

@Composable
fun DialogFormularzEdycjiTerminu(
    termin: Termin,
    listaAut: List<Pojazd>,
    onZapisz: (Pojazd, String, String, String, String, String, Boolean) -> Unit,
    onAnuluj: () -> Unit
) {
    val lang = LocalLanguage.current
    val context = LocalContext.current
    val kalendarz = Calendar.getInstance()
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    var wybraneAuto by remember { mutableStateOf(termin.pojazd) }
    val typyTerminow = listOf("OC", "AC", "OC/AC", "Badanie techniczne", "Wymiana opon", "Wymiana oleju/filtrów", "Serwis klimatyzacji", "Inne")
    var wybranyTyp by remember { mutableStateOf(termin.typ) }
    var menuRozwiniete by remember { mutableStateOf(false) }
    var dodatkowyOpis by remember { mutableStateOf(termin.opis) }
    var wprowadzonaData by remember { mutableStateOf(termin.data) }
    var recznaDataPrzyp by remember { mutableStateOf(termin.przypomnienieData) }
    var recznaGodzinaPrzyp by remember { mutableStateOf(termin.przypomnienieGodzina.ifBlank { "09:00" }) }
    var sztywnyAlertWlaczony by remember { mutableStateOf(termin.sztywnyAlert) }

    val datePickerWaznosc = DatePickerDialog(
        context,
        android.R.style.Theme_Material_Dialog,
        { _, rok, miesiac, dzien -> wprowadzonaData = String.format("%02d.%02d.%04d", dzien, miesiac + 1, rok) },
        try { sdf.parse(termin.data)?.let { Calendar.getInstance().apply { time = it }.get(Calendar.YEAR) } ?: kalendarz.get(Calendar.YEAR) } catch(e:Exception){kalendarz.get(Calendar.YEAR)},
        try { sdf.parse(termin.data)?.let { Calendar.getInstance().apply { time = it }.get(Calendar.MONTH) } ?: kalendarz.get(Calendar.MONTH) } catch(e:Exception){kalendarz.get(Calendar.MONTH)},
        try { sdf.parse(termin.data)?.let { Calendar.getInstance().apply { time = it }.get(Calendar.DAY_OF_MONTH) } ?: kalendarz.get(Calendar.DAY_OF_MONTH) } catch(e:Exception){kalendarz.get(Calendar.DAY_OF_MONTH)}
    )
    val datePickerPrzypomnienie = DatePickerDialog(
        context,
        android.R.style.Theme_Material_Dialog,
        { _, rok, miesiac, dzien -> recznaDataPrzyp = String.format("%02d.%02d.%04d", dzien, miesiac + 1, rok) },
        kalendarz.get(Calendar.YEAR),
        kalendarz.get(Calendar.MONTH),
        kalendarz.get(Calendar.DAY_OF_MONTH)
    )

    AlertDialog(
        onDismissRequest = onAnuluj,
        modifier = Modifier.border(1.dp, CardBorder, RoundedCornerShape(16.dp)).background(CardGradient, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.Transparent,
        title = { Text(lang.t("Edytuj termin", "Edit schedule"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(lang.t("Wybierz pojazd:", "Select vehicle:"), color = TextGray, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listaAut) { auto ->
                        val isSelected = wybraneAuto.id == auto.id
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(id = auto.avatarRes),
                                contentDescription = auto.markaModel,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Color.Transparent)
                                    .border(width = if (isSelected) 1.5.dp else 0.dp, color = if (isSelected) BlueAccent else Color.Transparent, shape = RoundedCornerShape(9.dp))
                                    .padding(if (isSelected) 3.dp else 0.dp)
                                    .clickable { wybraneAuto = auto }
                            )
                            Text(auto.rejestracja.ifBlank { auto.markaModel }, color = if (isSelected) Color.White else TextGray, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().background(CardBgColor, RoundedCornerShape(12.dp)).border(1.dp, CardBorder, RoundedCornerShape(12.dp)).clickable { menuRozwiniete = true }.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(getIkonaDlaTypu(wybranyTyp), contentDescription = null, tint = BlueAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = translateTyp(wybranyTyp, lang.isEnglish), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextGray)
                    }
                    DropdownMenu(
                        expanded = menuRozwiniete,
                        onDismissRequest = { menuRozwiniete = false },
                        modifier = Modifier.background(CardGradient).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                    ) {
                        typyTerminow.forEach { typ ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(getIkonaDlaTypu(typ), contentDescription = null, tint = if (wybranyTyp == typ) BlueAccent else TextGray, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(translateTyp(typ, lang.isEnglish), color = if (wybranyTyp == typ) BlueAccent else Color.White, fontSize = 13.sp)
                                    }
                                },
                                onClick = { wybranyTyp = typ; menuRozwiniete = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = dodatkowyOpis,
                    onValueChange = { dodatkowyOpis = it },
                    placeholder = { Text(lang.t("Dodatkowy opis", "Additional description"), color = TextGray, fontSize = 14.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = CardBgColor, unfocusedContainerColor = CardBgColor, focusedBorderColor = CardBorder, unfocusedBorderColor = CardBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = BlueAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier.fillMaxWidth().background(CardBgColor, RoundedCornerShape(12.dp)).border(1.dp, CardBorder, RoundedCornerShape(12.dp)).clickable { datePickerWaznosc.show() }.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = TextGray, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${lang.t("Termin", "Schedule")}: $wprowadzonaData",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().background(CardBgColor, RoundedCornerShape(12.dp)).border(1.dp, CardBorder, RoundedCornerShape(12.dp)).clickable { datePickerPrzypomnienie.show() }.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = YellowAccent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (recznaDataPrzyp.isBlank()) lang.t("Wybierz datę powiadomienia", "Select notification date") else "Alert: $recznaDataPrzyp",
                            color = if (recznaDataPrzyp.isBlank()) TextGray else Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().background(CardBgColor, RoundedCornerShape(12.dp)).border(1.dp, CardBorder, RoundedCornerShape(12.dp)).clickable {
                        val parts = recznaGodzinaPrzyp.split(":")
                        val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
                        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        TimePickerDialog(context, android.R.style.Theme_Material_Dialog, { _, godz, min -> recznaGodzinaPrzyp = String.format("%02d:%02d", godz, min) }, h, m, true).show()
                    }.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = YellowAccent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "${lang.t("Godzina powiadomienia", "Notification time")}: $recznaGodzinaPrzyp", color = Color.White, fontSize = 14.sp)
                    }
                }
                // Suwak Powiadomienia (codziennie o 09:00 przez 5 dni przed terminem)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBgColor, RoundedCornerShape(12.dp))
                        .border(1.dp, if (sztywnyAlertWlaczony) BlueAccent else CardBorder, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { sztywnyAlertWlaczony = !sztywnyAlertWlaczony }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = lang.t("Powiadomienia", "Notifications"),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = lang.t("Codziennie o 09:00 (5 dni przed)", "Daily at 09:00 (5 days before)"),
                            color = TextGray,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = sztywnyAlertWlaczony,
                        onCheckedChange = { sztywnyAlertWlaczony = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BlueAccent,
                            uncheckedThumbColor = TextGray,
                            uncheckedTrackColor = IconBgColor
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val maRecznyAlert = recznaDataPrzyp.isNotBlank() && recznaGodzinaPrzyp.isNotBlank()
                    if (wprowadzonaData.isNotBlank() && (maRecznyAlert || sztywnyAlertWlaczony)) {
                        onZapisz(wybraneAuto, wybranyTyp, wprowadzonaData, recznaDataPrzyp, recznaGodzinaPrzyp, dodatkowyOpis, sztywnyAlertWlaczony)
                    } else {
                        Toast.makeText(context, lang.t("Wybierz datę terminu oraz ustaw alert lub włącz powiadomienia", "Select expiration date and set an alert"), Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
            ) { Text(lang.t("Zapisz zmiany", "Save changes"), color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onAnuluj) { Text(lang.t("Anuluj", "Cancel"), color = TextGray) }
        }
    )
}

// === FORMULARZ AUTA ===
// === FORMULARZ AUTA ===
@Composable
fun DialogFormularzAuta(
    autoPoczatkowe: Pojazd?,
    onZapisz: (String, String, Int) -> Unit,
    onAnuluj: () -> Unit
) {
    val lang = LocalLanguage.current
    var nazwaAuta by remember { mutableStateOf(autoPoczatkowe?.markaModel ?: "") }
    var nrRejestracyjny by remember { mutableStateOf(autoPoczatkowe?.rejestracja ?: "") }
    var wybranyAwatar by remember { mutableStateOf(autoPoczatkowe?.avatarRes ?: R.drawable.suv1) }

    // Zaktualizowana lista awatarów (SUV-y 1-3, hatchbacki, kombi, vany i przyczepki)
    val dostepneAwatary = listOf(
        R.drawable.suv1, R.drawable.suv2, R.drawable.suv3,
        R.drawable.hatchback1, R.drawable.hatchback2, R.drawable.hatchback3,
        R.drawable.kombi1, R.drawable.kombi2, R.drawable.kombi3,
        R.drawable.van1, R.drawable.van2, R.drawable.van3,
        R.drawable.przyczepka1, R.drawable.przyczepka2, R.drawable.przyczepka3
    )

    val tytulOkienka = if (autoPoczatkowe != null) lang.t("Edytuj auto", "Edit vehicle") else lang.t("Dodaj nowe auto", "Add new vehicle")
    val tekstPrzycisku = if (autoPoczatkowe != null) lang.t("Zapisz zmiany", "Save changes") else lang.t("Dodaj auto", "Add vehicle")

    AlertDialog(
        onDismissRequest = onAnuluj,
        modifier = Modifier.border(1.dp, CardBorder, RoundedCornerShape(16.dp)).background(CardGradient, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.Transparent,
        title = { Text(tytulOkienka, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = nazwaAuta,
                    onValueChange = { nazwaAuta = it },
                    label = { Text(lang.t("Nazwa auta", "Vehicle name")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BlueAccent, unfocusedBorderColor = Color(0xFF2C3E50), focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = BlueAccent, focusedLabelColor = BlueAccent, unfocusedLabelColor = TextGray),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = nrRejestracyjny,
                    onValueChange = { nrRejestracyjny = it.uppercase() },
                    label = { Text(lang.t("Nr rejestracyjny", "License plate")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BlueAccent, unfocusedBorderColor = Color(0xFF2C3E50), focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = BlueAccent, focusedLabelColor = BlueAccent, unfocusedLabelColor = TextGray),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(lang.t("Wybierz awatar pojazdu:", "Select vehicle avatar:"), color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))

                // Przewijana w pionie sekcja z awatarami (układ siatki / kafelków)
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(CardBgColor, RoundedCornerShape(12.dp))
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                        .padding(8.dp)
                        .verticalScroll(scrollState)
                ) {
                    // Wyświetlanie w rzędach po 4 ikony
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        dostepneAwatary.chunked(4).forEach { rzad ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rzad.forEach { awatarId ->
                                    val isSelected = wybranyAwatar == awatarId
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(65.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) BlueAccent.copy(alpha = 0.2f) else Color.Transparent)
                                            .border(width = if (isSelected) 2.dp else 1.dp, color = if (isSelected) BlueAccent else CardBorder, shape = RoundedCornerShape(8.dp))
                                            .clickable { wybranyAwatar = awatarId }
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = awatarId),
                                            contentDescription = "Awatar",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                // Dopełnienie pustego miejsca w ostatnim rzędzie, jeśli jest mniej niż 4 elementy
                                repeat(4 - rzad.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (nazwaAuta.isNotBlank()) onZapisz(nazwaAuta, nrRejestracyjny, wybranyAwatar) },
                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
            ) { Text(tekstPrzycisku, color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onAnuluj) { Text(lang.t("Anuluj", "Cancel"), color = TextGray) }
        }
    )
}

// === KARTA PIONOWA AUTA ===
@Composable
fun KartaPionowaAuta(auto: Pojazd, onEdytuj: () -> Unit, onUsun: () -> Unit) {
    val lang = LocalLanguage.current
    var rozwinMenu by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier.fillMaxWidth().background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = auto.avatarRes),
                contentDescription = "Auto",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(width = 110.dp, height = 75.dp).clip(RoundedCornerShape(9.dp)).background(Color.Transparent)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(auto.markaModel, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(auto.rejestracja, color = TextGray, fontSize = 13.sp)
            }
            Box {
                IconButton(onClick = { rozwinMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opcje", tint = TextGray)
                }
                DropdownMenu(
                    expanded = rozwinMenu,
                    onDismissRequest = { rozwinMenu = false },
                    modifier = Modifier.background(CardGradient).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text(lang.t("Edytuj", "Edit"), color = Color.White) },
                        onClick = { rozwinMenu = false; onEdytuj() },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = BlueAccent) }
                    )
                    DropdownMenuItem(
                        text = { Text(lang.t("Usuń", "Delete"), color = RedAccent) },
                        onClick = { rozwinMenu = false; onUsun() },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = RedAccent) }
                    )
                }
            }
        }
    }
}

// === KARUZELA POZIOMA AUT ===
// === KARUZELA POZIOMA AUT ===
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListaAutHorizontal(listaAut: List<Pojazd>, wybraneAuto: Pojazd?, onAutoKlikniete: (Pojazd?) -> Unit) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val szerokoscKarty = (screenWidth - 32.dp - 24.dp) / 3

    val ciasnyStyl = androidx.compose.ui.text.TextStyle(
        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 28.dp).fillMaxWidth()
    ) {
        items(listaAut) { auto ->
            val isSelected = wybraneAuto?.id == auto.id
            val cardBorderColor = if (isSelected) BlueAccent else CardBorder
            val borderWidth = if (isSelected) 1.5.dp else 1.dp

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier
                    .width(szerokoscKarty)
                    .height(90.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(CardGradient)
                    .border(borderWidth, cardBorderColor, RoundedCornerShape(9.dp))
                    .clickable { onAutoKlikniete(if (isSelected) null else auto) }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(start = 4.dp, top = 4.dp, end = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = auto.avatarRes),
                            contentDescription = "Zdjęcie auta",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(2.dp)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp) // Bezpieczny odstęp od bocznych ramek
                            .padding(top = 2.dp, bottom = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = auto.markaModel,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = ciasnyStyl,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(
                                    iterations = Int.MAX_VALUE, // Przewija w nieskończoność
                                    velocity = 25.dp // Płynna, spokojna prędkość przesuwania
                                )
                        )
                        Text(
                            text = auto.rejestracja,
                            color = TextGray,
                            fontSize = 9.sp,
                            style = ciasnyStyl,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// === LISTA I KARTY TERMINÓW ===
@Composable
fun ListaTerminow(listaTerminow: List<Termin>, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        for (termin in listaTerminow) {
            KartaTerminuDlaPulpitu(termin = termin)
        }
    }
}

@Composable
fun KartaTerminuDlaPulpitu(termin: Termin) {
    val lang = LocalLanguage.current
    val (etykietaTekst, etykietaKolor) = try {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val dataTerminu = sdf.parse(termin.data)
        if (dataTerminu != null) {
            val dzisCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val terminCal = Calendar.getInstance().apply {
                time = dataTerminu
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val diffMillis = terminCal.timeInMillis - dzisCal.timeInMillis
            val dniDoKonca = TimeUnit.MILLISECONDS.toDays(diffMillis)
            when {
                dniDoKonca >= 30 -> Pair(if (lang.isEnglish) "In $dniDoKonca days" else "Za $dniDoKonca dni", GreenAccent)
                dniDoKonca in 10..29 -> Pair(if (lang.isEnglish) "In $dniDoKonca days" else "Za $dniDoKonca dni", YellowAccent)
                dniDoKonca in 1..9 -> Pair(if (lang.isEnglish) "In $dniDoKonca days" else "Za $dniDoKonca dni", RedAccent)
                dniDoKonca == 0L -> Pair(lang.t("Dzisiaj", "Today"), RedAccent)
                else -> Pair(lang.t("Po terminie", "Overdue"), RedAccent)
            }
        } else {
            Pair(lang.t("Błąd daty", "Date error"), TextGray)
        }
    } catch (e: Exception) {
        Pair(lang.t("Błąd daty", "Date error"), TextGray)
    }

    val ikonaFinal = getIkonaDlaTypu(termin.typ)
    val kolorIkonyFinal = etykietaKolor

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier.fillMaxWidth().background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(30.dp).background(IconBgColor, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(ikonaFinal, contentDescription = null, tint = kolorIkonyFinal, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(translateTyp(termin.typ, lang.isEnglish), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val opisTekst = if (termin.opis.isNotBlank()) " · ${termin.opis}" else ""
                Text("${termin.pojazd.markaModel} · ${termin.pojazd.rejestracja}$opisTekst", color = TextGray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(termin.data, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Box(modifier = Modifier.offset(y = (-1).dp).background(etykietaKolor, RoundedCornerShape(9.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                    Text(text = etykietaTekst, color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun KartaTerminu(
    termin: Termin,
    onUsun: (() -> Unit)?,
    onEdytuj: (() -> Unit)? = null,
    onPrzedluz: ((Int) -> Unit)? = null
) {
    val lang = LocalLanguage.current
    var rozwinMenu by remember { mutableStateOf(false) }

    val (etykietaTekst, etykietaKolor) = try {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val dataTerminu = sdf.parse(termin.data)
        if (dataTerminu != null) {
            val dzisCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val terminCal = Calendar.getInstance().apply {
                time = dataTerminu
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val diffMillis = terminCal.timeInMillis - dzisCal.timeInMillis
            val dniDoKonca = TimeUnit.MILLISECONDS.toDays(diffMillis)
            when {
                dniDoKonca >= 30 -> Pair(if (lang.isEnglish) "In $dniDoKonca days" else "Za $dniDoKonca dni", GreenAccent)
                dniDoKonca in 10..29 -> Pair(if (lang.isEnglish) "In $dniDoKonca days" else "Za $dniDoKonca dni", YellowAccent)
                dniDoKonca in 1..9 -> Pair(if (lang.isEnglish) "In $dniDoKonca days" else "Za $dniDoKonca dni", RedAccent)
                dniDoKonca == 0L -> Pair(lang.t("Dzisiaj", "Today"), RedAccent)
                else -> Pair(lang.t("Po terminie", "Overdue"), RedAccent)
            }
        } else {
            Pair(lang.t("Błąd daty", "Date error"), TextGray)
        }
    } catch (e: Exception) {
        Pair(lang.t("Błąd daty", "Date error"), TextGray)
    }

    val ikonaFinal = getIkonaDlaTypu(termin.typ)
    val kolorIkonyFinal = etykietaKolor

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier.fillMaxWidth().background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(30.dp).background(IconBgColor, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(ikonaFinal, contentDescription = null, tint = kolorIkonyFinal, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(translateTyp(termin.typ, lang.isEnglish), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val opisTekst = if (termin.opis.isNotBlank()) " · ${termin.opis}" else ""
                Text("${termin.pojazd.markaModel} · ${termin.pojazd.rejestracja}$opisTekst", color = TextGray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(termin.data, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Box(modifier = Modifier.offset(y = (-1).dp).background(etykietaKolor, RoundedCornerShape(9.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                        Text(text = etykietaTekst, color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                Box {
                    IconButton(onClick = { rozwinMenu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opcje", tint = TextGray, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = rozwinMenu,
                        onDismissRequest = { rozwinMenu = false },
                        modifier = Modifier.background(CardGradient).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                    ) {
                        val miesiacePrzedluzenia = if (termin.typ.contains("opon", ignoreCase = true)) 6 else 12
                        val etykietaPrzedluz = if (miesiacePrzedluzenia == 12) lang.t("Przedłuż o 1 rok", "Extend by 1 year") else lang.t("Przedłuż o 6 miesięcy", "Extend by 6 months")

                        DropdownMenuItem(
                            text = { Text(etykietaPrzedluz, color = Color.White, fontSize = 12.sp) },
                            onClick = {
                                rozwinMenu = false
                                onPrzedluz?.invoke(miesiacePrzedluzenia)
                            },
                            leadingIcon = { Icon(Icons.Default.Update, contentDescription = null, tint = BlueAccent) }
                        )
                        if (onEdytuj != null) {
                            DropdownMenuItem(
                                text = { Text(lang.t("Edytuj", "Edit"), color = Color.White, fontSize = 12.sp) },
                                onClick = {
                                    rozwinMenu = false
                                    onEdytuj()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = BlueAccent) }
                            )
                        }
                        if (onUsun != null) {
                            DropdownMenuItem(
                                text = { Text(lang.t("Usuń", "Delete"), color = RedAccent, fontSize = 12.sp) },
                                onClick = {
                                    rozwinMenu = false
                                    onUsun()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = RedAccent) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// === ZAKŁADKA "WIĘCEJ" ZE STATYCZNYMI PRZYCISKAMI JĘZYKA NA DOLE ===
@Composable
fun EkranWiecej(
    paddingValues: PaddingValues,
    onPrzejdzDoDziennika: () -> Unit,
    onPrzejdzDoRaportuPDF: () -> Unit,
    onPrzejdzDoArchiwum: () -> Unit,
    onPrzejdzDoDokumentow: () -> Unit
) {
    val lang = LocalLanguage.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Text(lang.t("Więcej opcji", "More options"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(horizontalTaperingBrush))
            Spacer(modifier = Modifier.height(16.dp))
        }
        item {
            OpcjaMenuWiecej(Icons.Default.Build, lang.t("Dziennik napraw", "Repair log")) { onPrzejdzDoDziennika() }
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
            OpcjaMenuWiecej(Icons.Default.Description, lang.t("Dokumenty", "Documents")) { onPrzejdzDoDokumentow() }
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
            OpcjaMenuWiecej(Icons.Default.PictureAsPdf, lang.t("Raport PDF", "PDF Report")) { onPrzejdzDoRaportuPDF() }
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
            OpcjaMenuWiecej(Icons.Default.Archive, lang.t("Archiwum", "Archive")) { onPrzejdzDoArchiwum() }
        }
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(horizontalTaperingBrush))
            Spacer(modifier = Modifier.height(16.dp))
            Text(lang.t("Wybór języka", "Language selection"), color = TextGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Stały kafelek "Polski"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (!lang.isEnglish) CardGradient else Brush.verticalGradient(listOf(CardBgColor, CardBgColor)), RoundedCornerShape(9.dp))
                        .border(1.dp, if (!lang.isEnglish) BlueAccent else CardBorder, RoundedCornerShape(9.dp))
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { lang.isEnglish = false }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Polski", color = if (!lang.isEnglish) Color.White else TextGray, fontSize = 13.sp, fontWeight = if (!lang.isEnglish) FontWeight.Bold else FontWeight.Normal)
                }
                // Stały kafelek "English"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (lang.isEnglish) CardGradient else Brush.verticalGradient(listOf(CardBgColor, CardBgColor)), RoundedCornerShape(9.dp))
                        .border(1.dp, if (lang.isEnglish) BlueAccent else CardBorder, RoundedCornerShape(9.dp))
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { lang.isEnglish = true }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "English", color = if (lang.isEnglish) Color.White else TextGray, fontSize = 13.sp, fontWeight = if (lang.isEnglish) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
fun OpcjaMenuWiecej(ikona: ImageVector, tytul: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier.fillMaxWidth().background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp)).clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(36.dp).background(IconBgColor, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(ikona, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(tytul, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGray)
        }
    }
}

// === TOPBAR, PODSUMOWANIE I SZYBKI DOSTĘP ===
@Composable
fun TopBar(listaTerminow: List<Termin>, ukrytePowiadomienia: Set<Int>, onUkryjPowiadomienie: (Int) -> Unit) {
    val lang = LocalLanguage.current
    var pokazDialog by remember { mutableStateOf(false) }
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    val teraz = System.currentTimeMillis()
    val aktywnePowiadomienia = listaTerminow.filter { termin ->
        !ukrytePowiadomienia.contains(termin.id) && try {
            val d = sdf.parse("${termin.przypomnienieData} ${termin.przypomnienieGodzina}")
            d != null && d.time <= teraz
        } catch (e: Exception) { false }
    }.sortedByDescending {
        try { sdf.parse("${it.przypomnienieData} ${it.przypomnienieGodzina}")?.time ?: 0L } catch (e: Exception) { 0L }
    }

    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(id = R.drawable.nazwatop),
            contentDescription = "Logo AutoTermin",
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(34.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(modifier = Modifier.clickable { pokazDialog = true }) {
            Icon(Icons.Default.Notifications, contentDescription = "Powiadomienia", tint = Color.White, modifier = Modifier.size(26.dp))
            if (aktywnePowiadomienia.isNotEmpty()) {
                Box(
                    modifier = Modifier.size(10.dp).align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp).background(RedAccent, CircleShape).border(2.dp, BgColor, CircleShape)
                )
            }
        }
    }

    if (pokazDialog) {
        AlertDialog(
            onDismissRequest = { pokazDialog = false },
            modifier = Modifier.border(1.dp, CardBorder, RoundedCornerShape(9.dp)).background(CardGradient, RoundedCornerShape(9.dp)),
            shape = RoundedCornerShape(9.dp),
            containerColor = Color.Transparent,
            title = { Text(lang.t("Powiadomienia", "Notifications"), color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                if (aktywnePowiadomienia.isEmpty()) {
                    Text(lang.t("Brak nowych powiadomień.", "No new notifications."), color = TextGray)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(aktywnePowiadomienia) { termin ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                shape = RoundedCornerShape(9.dp),
                                modifier = Modifier.fillMaxWidth().background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                            ) {
                                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${translateTyp(termin.typ, lang.isEnglish)} - ${termin.pojazd.markaModel}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("${lang.t("Z zaplanowaną datą", "Scheduled date")}: ${termin.data}", color = TextGray, fontSize = 10.sp)
                                    }
                                    IconButton(onClick = { onUkryjPowiadomienie(termin.id) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Check, contentDescription = "Zrobione", tint = GreenAccent)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pokazDialog = false }) {
                    Text(lang.t("Zamknij", "Close"), color = BlueAccent, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun KartaPodsumowania(listaAut: List<Pojazd>, listaTerminow: List<Termin>, wybraneAuto: Pojazd?) {
    val lang = LocalLanguage.current
    var greenCount = 0
    var yellowCount = 0
    var redCount = 0
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val dzisCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    listaTerminow.forEach { termin ->
        try {
            val dataTerminu = sdf.parse(termin.data)
            if (dataTerminu != null) {
                val terminCal = Calendar.getInstance().apply {
                    time = dataTerminu
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val diffMillis = terminCal.timeInMillis - dzisCal.timeInMillis
                val dniDoKonca = TimeUnit.MILLISECONDS.toDays(diffMillis)
                if (dniDoKonca <= 7) redCount++
                else if (dniDoKonca <= 21) yellowCount++
                else greenCount++
            }
        } catch (e: Exception) {}
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(lang.t("Twoje pojazdy", "Your vehicles"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                if (listaAut.isEmpty()) {
                    Text(lang.t("Brak aut", "No vehicles"), color = TextGray, fontSize = 11.sp)
                } else {
                    val displayList = listaAut.take(10)
                    val avatarSize = 44.dp
                    val overlap = 8.dp
                    val step = avatarSize - overlap
                    val columns = if (displayList.size > 5) 5 else displayList.size
                    val rows = (displayList.size - 1) / 5 + 1
                    val verticalSpacing = 8.dp
                    val boxWidth = if (columns == 0) 0.dp else avatarSize + step * (columns - 1)
                    val boxHeight = if (rows == 0) 0.dp else avatarSize * rows + verticalSpacing * (rows - 1)
                    Box(modifier = Modifier.width(boxWidth).height(boxHeight)) {
                        for (i in displayList.indices.reversed()) {
                            val auto = displayList[i]
                            val isSelected = wybraneAuto?.id == auto.id
                            val obramowkaKolor = if (isSelected) BlueAccent else CardBorder
                            val rowIndex = i / 5
                            val colIndex = i % 5
                            val glowModifier = if (isSelected) {
                                Modifier.shadow(elevation = 12.dp, shape = CircleShape, ambientColor = BlueAccent, spotColor = BlueAccent)
                            } else {
                                Modifier
                            }
                            Image(
                                painter = painterResource(id = auto.avatarRes),
                                contentDescription = "Miniatura auta",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .offset(x = step * colIndex, y = (avatarSize + verticalSpacing) * rowIndex)
                                    .size(avatarSize)
                                    .then(glowModifier)
                                    .clip(CircleShape)
                                    .background(Brush.verticalGradient(listOf(Color(0xFF2A364B), Color(0xFF131926))))
                                    .border(1.dp, obramowkaKolor, CircleShape)
                                    .padding(6.dp)
                            )
                        }
                    }
                }
            }
            Box(modifier = Modifier.padding(horizontal = 12.dp).fillMaxHeight().padding(vertical = 4.dp)) {
                Box(modifier = Modifier.fillMaxHeight().width(1.8.dp).background(verticalBrightDividerBrush))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(greenCount.toString(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = YellowAccent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(yellowCount.toString(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = RedAccent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(redCount.toString(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun NaglowekTerminow(listaAut: List<Pojazd>, wybraneAuto: Pojazd?, onFiltruj: (Pojazd?) -> Unit) {
    val lang = LocalLanguage.current
    var rozwinMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(lang.t("Najbliższe terminy", "Upcoming schedules"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (listaAut.isNotEmpty()) {
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                Row(
                    modifier = Modifier
                        .width(135.dp)
                        .background(CardGradient, RoundedCornerShape(9.dp))
                        .border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { rozwinMenu = true }
                        .padding(horizontal = 15.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = wybraneAuto?.markaModel ?: lang.t("Wszystkie auta", "All vehicles"), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = rozwinMenu,
                    onDismissRequest = { rozwinMenu = false },
                    modifier = Modifier.background(CardGradient).border(1.dp, CardBorder, RoundedCornerShape(9.dp))
                ) {
                    Column(modifier = Modifier.widthIn(min = 135.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onFiltruj(null); rozwinMenu = false }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lang.t("Wszystkie auta", "All vehicles"),
                                color = if (wybraneAuto == null) BlueAccent else Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (wybraneAuto == null) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 8.dp).background(horizontalTaperingBrush))
                        listaAut.forEachIndexed { index, auto ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onFiltruj(auto); rozwinMenu = false }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = auto.markaModel,
                                    color = if (wybraneAuto?.id == auto.id) BlueAccent else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (wybraneAuto?.id == auto.id) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (index < listaAut.size - 1) {
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 8.dp).background(horizontalTaperingBrush))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SekcjaSzybkiDostep(
    onPrzejdzDoDziennika: () -> Unit,
    onPrzejdzDoRaportuPDF: () -> Unit,
    onPrzejdzDoArchiwum: () -> Unit,
    onPrzejdzDoDokumentow: () -> Unit
) {
    val lang = LocalLanguage.current
    Text(lang.t("Szybki dostęp", "Quick access"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KartaSzybkiegoDostepu(Icons.Default.Build, lang.t("Dziennik napraw", "Repair log"), lang.t("Koszty i naprawy", "Costs & repairs"), Modifier.weight(1f)) { onPrzejdzDoDziennika() }
            KartaSzybkiegoDostepu(Icons.Default.Description, lang.t("Dokumenty", "Documents"), lang.t("Polisy, faktury", "Policies, invoices"), Modifier.weight(1f)) { onPrzejdzDoDokumentow() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KartaSzybkiegoDostepu(Icons.Default.PictureAsPdf, lang.t("Raport PDF", "PDF Report"), lang.t("Raport kosztów", "Cost report"), Modifier.weight(1f)) { onPrzejdzDoRaportuPDF() }
            KartaSzybkiegoDostepu(Icons.Default.Archive, lang.t("Archiwum", "Archive"), lang.t("Usunięte dane", "Deleted data"), Modifier.weight(1f)) { onPrzejdzDoArchiwum() }
        }
    }
}

@Composable
fun KartaSzybkiegoDostepu(
    ikona: ImageVector,
    tytul: String,
    podtytul: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(9.dp),
        modifier = modifier.background(CardGradient, RoundedCornerShape(9.dp)).border(1.dp, CardBorder, RoundedCornerShape(9.dp)).clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(30.dp).background(Color(0xFF1E2B45), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(ikona, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.White)) { append(tytul) }
                    append("\n")
                    withStyle(SpanStyle(fontWeight = FontWeight.Normal, fontSize = 8.sp, color = TextGray)) { append(podtytul) }
                },
                lineHeight = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun DolnyPasekNawigacji(aktualnaZakladka: String, onZakladkaZmieniona: (String) -> Unit) {
    val lang = LocalLanguage.current
    Column(modifier = Modifier.fillMaxWidth().background(BgColor)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(colors = listOf(Color.Transparent, CardBorder, Color.Transparent)))
        )
        NavigationBar(containerColor = BgColor, contentColor = TextGray, tonalElevation = 0.dp) {
            NavigationBarItem(
                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                label = { Text(lang.t("Pulpit", "Dashboard"), fontSize = 10.sp) },
                selected = aktualnaZakladka == "Pulpit",
                onClick = { onZakladkaZmieniona("Pulpit") },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = BlueAccent, selectedTextColor = BlueAccent, indicatorColor = Color.Transparent, unselectedIconColor = TextGray, unselectedTextColor = TextGray)
            )
            NavigationBarItem(
                icon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) },
                label = { Text(lang.t("Auta", "Vehicles"), fontSize = 10.sp) },
                selected = aktualnaZakladka == "Auta",
                onClick = { onZakladkaZmieniona("Auta") },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = BlueAccent, selectedTextColor = BlueAccent, indicatorColor = Color.Transparent, unselectedIconColor = TextGray, unselectedTextColor = TextGray)
            )
            NavigationBarItem(
                icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                label = { Text(lang.t("Terminy", "Schedules"), fontSize = 10.sp) },
                selected = aktualnaZakladka == "Terminy",
                onClick = { onZakladkaZmieniona("Terminy") },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = BlueAccent, selectedTextColor = BlueAccent, indicatorColor = Color.Transparent, unselectedIconColor = TextGray, unselectedTextColor = TextGray)
            )
            NavigationBarItem(
                icon = { Icon(Icons.Default.MoreHoriz, contentDescription = null) },
                label = { Text(lang.t("Więcej", "More"), fontSize = 10.sp) },
                selected = aktualnaZakladka in listOf("Więcej", "DziennikNapraw", "SzczegolyWizyty", "RaportPDF", "Archiwum", "Dokumenty"),
                onClick = { onZakladkaZmieniona("Więcej") },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = BlueAccent, selectedTextColor = BlueAccent, indicatorColor = Color.Transparent, unselectedIconColor = TextGray, unselectedTextColor = TextGray)
            )
        }
    }
}

fun wczytajZdjecieZUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            android.graphics.ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun renderujStronyPDFZUri(context: Context, uri: Uri): List<Bitmap> {
    val bitmaps = mutableListOf<Bitmap>()
    try {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return bitmaps
        val renderer = PdfRenderer(pfd)
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val width = page.width * 3
            val height = page.height * 3
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmaps.add(bitmap)
        }
        renderer.close()
        pfd.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return bitmaps
}


// === DIALOG PODGLĄDU DOKUMENTU Z NATYWNYM PANORAMOWANIEM I ZOOMEM ===
// === DIALOG PODGLĄDU DOKUMENTU Z PŁYNNYM ZOOMEM I BRAKIEM UCIĘCIA GÓRY ===
@Composable
fun DialogPodgladuDokumentu(dokument: Dokument, onZamknij: () -> Unit) {
    val lang = LocalLanguage.current
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var bitmaps by remember { mutableStateOf<List<Bitmap>?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            when (dokument.typ) {
                "PDF" -> "application/pdf"
                "IMAGE" -> "image/*"
                else -> "*/*"
            }
        )
    ) { destUri: Uri? ->
        if (destUri != null) {
            try {
                val inputStream = if (dokument.uriString.startsWith("content://")) {
                    context.contentResolver.openInputStream(Uri.parse(dokument.uriString))
                } else {
                    FileInputStream(File(dokument.uriString))
                }
                inputStream?.use { input ->
                    context.contentResolver.openOutputStream(destUri)?.use { output ->
                        input.copyTo(output)
                    }
                }
                Toast.makeText(context, lang.t("Zapisano pomyślnie!", "Saved successfully!"), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, lang.t("Błąd zapisu pliku", "Error saving file"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(dokument) {
        isLoading = true
        val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val czyStaryPlik = dokument.uriString.startsWith("content://")
            val plikFizyczny = if (!czyStaryPlik) File(dokument.uriString) else null
            if (dokument.typ == "PDF") {
                if (czyStaryPlik) renderujStronyPDFZUri(context, Uri.parse(dokument.uriString))
                else if (plikFizyczny != null && plikFizyczny.exists()) renderujStronyPDF(plikFizyczny) else emptyList()
            } else if (dokument.typ == "IMAGE") {
                if (czyStaryPlik) {
                    val bmp = wczytajZdjecieZUri(context, Uri.parse(dokument.uriString))
                    if (bmp != null) listOf(bmp) else emptyList()
                } else {
                    if (plikFizyczny != null && plikFizyczny.exists()) {
                        android.graphics.BitmapFactory.decodeFile(plikFizyczny.absolutePath)?.let { listOf(it) } ?: emptyList()
                    } else emptyList()
                }
            } else emptyList()
        }
        bitmaps = loaded
        isLoading = false
    }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onZamknij,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Zamknij",
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable { onZamknij() }
                            )
                            Text(
                                text = dokument.nazwa,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (dokument.typ == "PDF" && bitmaps != null) {
                            Text(
                                text = "${lang.t("Stron", "Pages")}: ${bitmaps!!.size}",
                                color = TextGray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 36.dp, top = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .background(BlueAccent, RoundedCornerShape(9.dp))
                                .border(1.dp, BlueAccent, RoundedCornerShape(9.dp))
                                .clip(RoundedCornerShape(9.dp))
                                .clickable {
                                    val rozszerzenie = if (dokument.typ == "PDF") ".pdf" else if (dokument.typ == "IMAGE") ".jpg" else ""
                                    saveLauncher.launch("${dokument.nazwa}$rozszerzenie")
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(lang.t("Zapisz", "Save"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(horizontalTaperingBrush))
                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BlueAccent)
                    }
                } else if (bitmaps.isNullOrEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(lang.t("Nie udało się załadować podglądu pliku.", "Failed to load file preview."), color = TextGray)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Transparent)
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                                    scale = newScale
                                    if (newScale > 1f) {
                                        val limitX = size.width * (scale - 1f) / 2f
                                        val limitY = size.height * (scale - 1f) / 2f
                                        offset = Offset(
                                            x = (offset.x + pan.x).coerceIn(-limitX, limitX),
                                            y = (offset.y + pan.y).coerceIn(-limitY, limitY)
                                        )
                                    } else {
                                        offset = Offset.Zero
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                },
                            userScrollEnabled = scale <= 1.05f,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(bitmaps!!) { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
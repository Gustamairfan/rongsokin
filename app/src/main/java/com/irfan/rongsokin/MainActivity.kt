@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package com.irfan.rongsokin

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.net.NetworkCapabilities
import androidx.compose.ui.text.style.TextOverflow
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.widget.Button as AButton
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.FirebaseNetworkException
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val UNITS = linkedMapOf("gram" to 0.001, "Kg" to 1.0, "kwintal" to 100.0, "ton" to 1000.0)
val GREEN = Color(0xFF0F7A3E)
val GREEN2 = Color(0xFF1F9A52)
val DARK = Color(0xFF0B6B35)
val BG = Color(0xFFF3F6F4)
val SOFT = Color(0xFFF1F5F2)
val ORANGE = Color(0xFFF57C00)
val INK = Color(0xFF1B2B22)
val RED = Color(0xFFD32F2F)
val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

// ---- sumber harga pasar ----
const val PRICES_URL = "https://raw.githubusercontent.com/Gustamairfan/rongsokin/main/prices.json"
val SRC_LABEL = linkedMapOf(
    "CU" to "Tembaga", "AL" to "Aluminium", "FE" to "Besi", "ST" to "Baja", "KU" to "Kuningan", "CM" to "Tembaga campuran", "SENG" to "Seng",
    "PET" to "Plastik PET", "HDPE" to "Plastik HDPE", "PP" to "Plastik PP", "LDPE" to "Plastik LDPE", "PL" to "Plastik campur",
    "KRD" to "Kardus / Karton", "HVS" to "Kertas HVS (putih)", "KRN" to "Kertas koran", "KCP" to "Kertas campur",
    "MANUAL" to "Lainnya (input manual)"
)
val SRC_URL: Map<String, String> = run {
    val yf = "finance.yahoo.com/quote/"; val rawUrl = "raw.githubusercontent.com/Gustamairfan/rongsokin"
    mapOf("CU" to "${yf}HG=F", "AL" to "${yf}ALI=F", "FE" to "${yf}TIO=F", "ST" to "${yf}HRC=F",
        "KU" to "${yf}HG=F · estimasi", "CM" to "${yf}HG=F · estimasi") +
        listOf("SENG", "PET", "HDPE", "PP", "LDPE", "PL", "KRD", "HVS", "KRN", "KCP").associateWith { "$rawUrl · perkiraan" }
}
const val PLASTIC_REF = "Plasticker (plasticker.de), Recycling Today (recyclingtoday.com), ADUPI (Asosiasi Daur Ulang Plastik Indonesia), Bank Sampah Induk setempat"
const val PAPER_REF = "Fastmarkets RISI (fastmarkets.com), Recycling Today (recyclingtoday.com), APKI (Asosiasi Pulp dan Kertas Indonesia), pengepul/bank sampah setempat"
val REFS = mapOf(
    "KU" to "SMM / Shanghai Metals Market (metal.com, harga brass scrap), LME (lme.com), Investing.com Indonesia",
    "CM" to "SMM / Shanghai Metals Market (metal.com, harga copper scrap), LME (lme.com), Investing.com Indonesia",
    "MANUAL" to "harga pengepul/bank sampah setempat, TradingView, Investing.com Indonesia"
)

data class PItem(val key: String, val group: String, val name: String, val exchange: String, val live: Boolean = false,
                 val basis: String? = null, val factor: Double = 1.0, val price: Double = 0.0, val prev: Double = 0.0)
val DEFAULT_ITEMS = listOf(
    PItem("CU", "logam", "Tembaga", "COMEX Copper (HG=F)", live = true),
    PItem("AL", "logam", "Aluminium", "Aluminum Futures (ALI=F)", live = true),
    PItem("FE", "logam", "Besi (bijih besi)", "SGX Iron Ore 62% (TIO=F)", live = true),
    PItem("ST", "logam", "Baja (HRC)", "CME Hot-Rolled Coil (HRC=F)", live = true),
    PItem("KU", "logam", "Kuningan", "Estimasi 40% harga tembaga", basis = "CU", factor = 0.4),
    PItem("CM", "logam", "Tembaga campuran", "Estimasi 60% harga tembaga", basis = "CU", factor = 0.6),
    PItem("SENG", "logam", "Seng (lembaran bekas)", "Perkiraan pengepul Indonesia", price = 3000.0, prev = 3000.0),
    PItem("PET", "plastik", "Plastik PET (botol)", "Perkiraan pengepul Indonesia", price = 5000.0, prev = 5000.0),
    PItem("HDPE", "plastik", "Plastik HDPE", "Perkiraan pengepul Indonesia", price = 5000.0, prev = 5000.0),
    PItem("PP", "plastik", "Plastik PP (gelas)", "Perkiraan pengepul Indonesia", price = 4000.0, prev = 4000.0),
    PItem("LDPE", "plastik", "Plastik LDPE (lembaran)", "Perkiraan pengepul Indonesia", price = 3000.0, prev = 3000.0),
    PItem("PL", "plastik", "Plastik campur", "Perkiraan pengepul Indonesia", price = 2000.0, prev = 2000.0),
    PItem("KRD", "kertas", "Kardus / Karton", "Perkiraan pengepul Indonesia", price = 2000.0, prev = 2000.0),
    PItem("HVS", "kertas", "Kertas HVS (putih)", "Perkiraan pengepul Indonesia", price = 3000.0, prev = 3000.0),
    PItem("KRN", "kertas", "Kertas koran", "Perkiraan pengepul Indonesia", price = 2500.0, prev = 2500.0),
    PItem("KCP", "kertas", "Kertas campur", "Perkiraan pengepul Indonesia", price = 1500.0, prev = 1500.0)
)

fun parseItems(txt: String): List<PItem> {
    val a = JSONObject(txt).getJSONArray("items")
    return (0 until a.length()).map {
        val o = a.getJSONObject(it)
        PItem(o.getString("key"), o.optString("group", "logam"), o.optString("name", o.getString("key")), o.optString("exchange", ""),
            o.optBoolean("live", false), if (o.has("basis")) o.getString("basis") else null, o.optDouble("factor", 1.0), o.optDouble("price", 0.0), o.optDouble("prev", 0.0))
    }
}
fun mergeItems(remote: List<PItem>): List<PItem> {
    val m = LinkedHashMap<String, PItem>(); DEFAULT_ITEMS.forEach { m[it.key] = it }; remote.forEach { m[it.key] = it }; return m.values.toList()
}
fun loadItems(prefs: android.content.SharedPreferences): List<PItem> =
    prefs.getString("pj", null)?.let { runCatching { mergeItems(parseItems(it)) }.getOrNull() } ?: DEFAULT_ITEMS

/** key -> (harga sekarang, harga sebelumnya) dalam Rp/Kg */
fun buildTable(live: Map<String, Pair<Double, Double>>, items: List<PItem>): Map<String, Pair<Double, Double>> {
    val out = mutableMapOf<String, Pair<Double, Double>>()
    items.forEach { x ->
        val v: Pair<Double, Double>? = when {
            x.live -> live[x.key]
            x.basis != null -> live[x.basis]?.let { (a, b) -> (a * x.factor) to (b * x.factor) }
            else -> null
        } ?: if (x.price > 0) x.price to (if (x.prev > 0) x.prev else x.price) else null
        if (v != null) out[x.key] = v
    }
    return out
}
fun guessSource(name: String): String {
    val n = name.lowercase()
    return when {
        "tembaga" in n && "campur" in n -> "CM"
        "tembaga" in n || "copper" in n -> "CU"
        "kuningan" in n || "brass" in n -> "KU"
        "alum" in n -> "AL"
        "seng" in n -> "SENG"
        "baja" in n || "steel" in n -> "ST"
        "besi" in n -> "FE"
        Regex("\\bpet\\b").containsMatchIn(n) -> "PET"
        "hdpe" in n -> "HDPE"
        Regex("\\bpp\\b").containsMatchIn(n) -> "PP"
        "ldpe" in n -> "LDPE"
        "plastik" in n || "plastic" in n -> "PL"
        "kardus" in n || "karton" in n || "occ" in n -> "KRD"
        "hvs" in n || "putih" in n -> "HVS"
        "koran" in n -> "KRN"
        "kertas" in n || "paper" in n -> "KCP"
        else -> "MANUAL"
    }
}
fun srcOf(p: Product): String {
    val s = if (p.source in SRC_LABEL) p.source else guessSource(p.name)
    return if (s == "FE" && guessSource(p.name) == "ST") "ST" else s
}
val EDITABLE = setOf("SENG", "PET", "HDPE", "PP", "LDPE", "PL", "KRD", "HVS", "KRN", "KCP")
fun priceOf(p: Product, t: Map<String, Pair<Double, Double>>): Pair<Double, Double> {
    val s = srcOf(p)
    if (s == "MANUAL" || (s in EDITABLE && p.market > 0)) return p.market to (if (p.prev > 0) p.prev else p.market)
    return t[s] ?: (p.market to p.prev)
}

suspend fun fetchQuotes(): Map<String, Pair<Double, Double>>? = withContext(Dispatchers.IO) {
    fun q(sym: String): Pair<Double, Double> {
        val c = URL("https://query1.finance.yahoo.com/v8/finance/chart/$sym?interval=1d&range=1d").openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "Mozilla/5.0"); c.connectTimeout = 10000; c.readTimeout = 10000
        val m = JSONObject(c.inputStream.bufferedReader().readText()).getJSONObject("chart")
            .getJSONArray("result").getJSONObject(0).getJSONObject("meta")
        val now = m.getDouble("regularMarketPrice")
        return now to m.optDouble("chartPreviousClose", now)
    }
    val fx = runCatching { q("IDR=X") }.getOrNull() ?: return@withContext null
    val out = mutableMapOf<String, Pair<Double, Double>>()
    listOf(Triple("CU", "HG=F", 2.20462), Triple("AL", "ALI=F", 0.001), Triple("FE", "TIO=F", 0.001), Triple("ST", "HRC=F", 1 / 907.185)).forEach { (k, s, f) ->
        runCatching { val (a, b) = q(s); out[k] = (a * f * fx.first) to (b * f * fx.second) }
    }
    if (out.isEmpty()) null else out
}
fun loadQ(prefs: android.content.SharedPreferences): Map<String, Pair<Double, Double>> =
    (prefs.getString("q", "") ?: "").split(";").mapNotNull {
        val a = it.split(":"); val x = a.getOrNull(1)?.toDoubleOrNull(); val y = a.getOrNull(2)?.toDoubleOrNull()
        if (a.size == 3 && x != null && y != null) a[0] to (x to y) else null
    }.toMap()

data class Product(
    val id: String = "", val name: String = "", val source: String = "",
    val market: Double = 0.0, val prev: Double = 0.0,
    val sell: Long = 0, val buy: Long = 0,
    val stock: Double = 0.0, val lastIn: Double = 0.0, val lastUnit: String = "Kg", val created: Long = 0
)

fun rp(n: Number) = NumberFormat.getInstance(Locale("in", "ID")).apply { maximumFractionDigits = 2 }.format(n)

object Thousands : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val d = text.text
        val out = d.reversed().chunked(3).joinToString(".").reversed()
        val map = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val r = d.length - offset
                val dotsRight = if (r > 0) (r - 1) / 3 else 0
                return offset + (d.length - 1).coerceAtLeast(0) / 3 - dotsRight
            }
            override fun transformedToOriginal(offset: Int): Int =
                (0 until offset.coerceAtMost(out.length)).count { out[it] != '.' }
        }
        return TransformedText(AnnotatedString(out), map)
    }
}

/** true = iklan uji coba Google (aman untuk testing). Ubah ke false HANYA untuk rilis publik. */
const val ADS_TEST_MODE = true
val AD_UNIT_ID = if (ADS_TEST_MODE) "ca-app-pub-3940256099942544/2247696110" else "ca-app-pub-3598338199328764/5722393983"

const val PRIVACY_TEXT = "Data produk, stok, dan harga Anda disimpan di akun Google/Firebase milik Anda dan hanya dapat diakses oleh akun Anda.\n\n" +
    "Kami tidak menjual atau membagikan data Anda kepada pihak ketiga. Pengelolaan data pribadi mengacu pada UU No. 27 Tahun 2022 tentang Pelindungan Data Pribadi dan UU ITE.\n\n" +
    "Data dikirim melalui koneksi terenkripsi (HTTPS). Anda dapat mengekspor atau meminta penghapusan data kapan saja."

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this) {}
        window.statusBarColor = 0xFF0B6B35.toInt()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = GREEN, secondary = GREEN2, background = BG, surface = Color.White)) {
                var user by remember { mutableStateOf(auth.currentUser) }
                DisposableEffect(Unit) {
                    val l = FirebaseAuth.AuthStateListener { user = it.currentUser }
                    auth.addAuthStateListener(l); onDispose { auth.removeAuthStateListener(l) }
                }
                Surface(Modifier.fillMaxSize(), color = BG) { user?.let { Home(it) } ?: Login() }
            }
        }
    }
}

// ---------- LOGIN ----------
var credsError by mutableStateOf<String?>(null)
fun errText(e: Throwable): String = when (e) {
    is FirebaseAuthException -> when (e.errorCode) {
        "ERROR_OPERATION_NOT_ALLOWED" -> "Login Email/Password belum diaktifkan. Buka Firebase Console → Authentication → Sign-in method → Email/Password → Enable."
        "ERROR_WEAK_PASSWORD" -> "Password terlalu lemah (minimal 6 karakter)."
        else -> "${e.errorCode}: ${e.message}"
    }
    is FirebaseNetworkException -> "Koneksi ke server Google gagal atau terlalu lambat."
    is FirebaseFirestoreException -> "${e.code.name}: ${e.message}"
    is TimeoutCancellationException -> "Koneksi terlalu lambat (timeout)."
    else -> e.message ?: e.javaClass.simpleName
}
fun hintFor(m: String) = when {
    "PERMISSION_DENIED" in m -> "→ Rules Firestore menolak akses. Firebase Console → Firestore Database → Rules → tempel rules dari README → Publish."
    "NOT_FOUND" in m || "FAILED_PRECONDITION" in m -> "→ Database Firestore belum dibuat. Firebase Console → Firestore Database → Create database."
    else -> "→ Server Google tidak terjangkau/lambat dari jaringan ini. Coba ganti jaringan (Wi-Fi ↔ data seluler)."
}

fun realEmail(u: FirebaseUser): String? = u.providerData.firstOrNull { it.providerId == "google.com" }?.email ?: u.email

/** Username = email Google user. Password dibuat otomatis, bisa diganti di Profil. Juga memperbaiki akun versi lama. */
suspend fun ensureCreds(u: FirebaseUser) {
    val email = realEmail(u) ?: return
    val pwProv = u.providerData.firstOrNull { it.providerId == "password" }
    if (pwProv?.email.equals(email, true)) return
    if (pwProv != null) u.unlink("password").await()
    val pw = (1..10).map { "abcdefghjkmnpqrstuvwxyz23456789".random() }.joinToString("")
    u.linkWithCredential(EmailAuthProvider.getCredential(email, pw)).await()
    db.collection("users").document(u.uid).set(mapOf("username" to email, "password" to pw), SetOptions.merge())
}

fun gClient(ctx: Context) = GoogleSignIn.getClient(
    ctx, GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(ctx.getString(R.string.default_web_client_id)).requestEmail().build()
)

@Composable
fun Login() {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var u by remember { mutableStateOf("") }; var p by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }; var forgot by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(false) }; var showPw by remember { mutableStateOf(false) }
    fun toast(s: String?) = Toast.makeText(ctx, s ?: "Gagal", Toast.LENGTH_LONG).show()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        scope.launch {
            busy = true
            try {
                val acc = GoogleSignIn.getSignedInAccountFromIntent(r.data).await()
                auth.signInWithCredential(GoogleAuthProvider.getCredential(acc.idToken, null)).await()
            } catch (e: Exception) { toast(e.message) }
            busy = false
        }
    }
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(DARK, GREEN2))).padding(top = 32.dp, bottom = 72.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(170.dp).clip(RoundedCornerShape(32.dp)).background(Color.White))
                Spacer(Modifier.height(12.dp))
                Text("RONGSOKIN", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text("Smart Scrap Management System", color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
            }
        }
        Card(Modifier.padding(horizontal = 20.dp).offset(y = (-44).dp).fillMaxWidth(), shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(6.dp)) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Selamat datang", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = INK)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(u, { u = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, shape = shape,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(p, { p = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, shape = shape,
                    visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { showPw = !showPw }.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(showPw, { showPw = it }); Text("Tampilkan password", fontSize = 13.sp)
                    }
                    TextButton({ forgot = true }) { Text("Lupa password?") }
                }
                Button({
                    scope.launch {
                        busy = true
                        try { auth.signInWithEmailAndPassword(u.trim(), p).await() }
                        catch (e: Exception) { toast("Email/password salah, atau belum pernah login dengan Google.") }
                        busy = false
                    }
                }, Modifier.fillMaxWidth().height(52.dp), shape = shape, enabled = !busy && u.isNotBlank() && p.isNotBlank()) { Text("Masuk", fontSize = 16.sp) }
                Text("atau", Modifier.padding(10.dp), fontSize = 13.sp, color = Color.Gray)
                OutlinedButton({ launcher.launch(gClient(ctx).signInIntent) }, Modifier.fillMaxWidth().height(52.dp), shape = shape, enabled = !busy) {
                    Text("G  Lanjutkan dengan Google", fontSize = 15.sp)
                }
                TextButton({ privacy = true }) { Text("🔒 Privasi data", color = Color.Gray) }
            }
        }
    }
    if (forgot) Dlg("Lupa password", { forgot = false }, "Mengerti", null, { forgot = false }) {
        Text("Login dengan Google, lalu buka menu ⋮ → Profil untuk melihat atau mengganti password Anda.")
    }
    if (privacy) Dlg("Privasi & Keamanan Data", { privacy = false }, "Tutup", null, { privacy = false }) { Text(PRIVACY_TEXT) }
}

// ---------- DIALOG HELPERS ----------
@Composable
fun Dlg(title: String, onDismiss: () -> Unit, ok: String = "Oke", cancel: String? = "Batal", onOk: () -> Unit, content: @Composable () -> Unit) =
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(24.dp), containerColor = Color.White,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { content() } },
        confirmButton = { TextButton(onOk) { Text(ok, fontWeight = FontWeight.Bold) } },
        dismissButton = cancel?.let { { TextButton(onDismiss) { Text(it) } } })

@Composable
fun QtyDialog(title: String, units: List<String>, confirm: String?, max: Double? = null, onDismiss: () -> Unit, onKg: (Double) -> Unit) {
    val ctx = LocalContext.current
    var t by remember { mutableStateOf("") }; var unit by remember { mutableStateOf(units.first { it == "Kg" || units.size == 1 }) }
    var ask by remember { mutableStateOf<Double?>(null) }
    val kg = (t.replace(',', '.').toDoubleOrNull() ?: 0.0) * UNITS[unit]!!
    if (ask != null) {
        Dlg("Konfirmasi", { ask = null }, "Oke", "Batal", { onKg(ask!!); onDismiss() }) { Text(confirm!!.replace("{x}", "$t $unit")) }
    } else Dlg(title, onDismiss, "Simpan", "Batal", {
        if (max != null && kg > max + 1e-9) Toast.makeText(ctx, "Melebihi stok (${rp(max)} Kg)", Toast.LENGTH_SHORT).show()
        else if (kg > 0) { if (confirm != null) ask = kg else { onKg(kg); onDismiss() } }
    }) {
        OutlinedTextField(t, { s -> t = s.filter { it.isDigit() || it == '.' || it == ',' }.take(12) }, singleLine = true,
            label = { Text("Jumlah") }, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            units.forEach { k -> FilterChip(unit == k, { unit = k }, { Text(k) }) }
        }
    }
}

@Composable
fun PickSourceDialog(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val list = SRC_LABEL.filter { it.value.contains(q.trim(), true) }
    Dialog(onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(Color.White)) {
            Column(Modifier.padding(16.dp)) {
                Text("Sumber harga pasar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Cari jenis…") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(6.dp))
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    if (list.isEmpty()) Text("Tidak ditemukan", color = Color.Gray, modifier = Modifier.padding(12.dp))
                    list.forEach { (k, v) ->
                        Text(v, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (k == current) Color(0xFFD8F0E0) else Color.Transparent)
                            .clickable { onPick(k) }.padding(horizontal = 12.dp, vertical = 12.dp),
                            fontSize = 15.sp, fontWeight = if (k == current) FontWeight.Bold else FontWeight.Normal, color = INK)
                    }
                }
            }
        }
    }
}

@Composable
fun ProductDialog(initial: Product?, table: Map<String, Pair<Double, Double>>, onDismiss: () -> Unit, onSave: (String, String, Long) -> Unit) {
    var n by remember { mutableStateOf(initial?.name ?: "") }
    var chosen by remember { mutableStateOf<String?>(initial?.let { srcOf(it) }) }
    var manual by remember { mutableStateOf(if ((initial?.market ?: 0.0) > 0) initial!!.market.toLong().toString() else "") }
    var editing by remember { mutableStateOf((initial?.market ?: 0.0) > 0) }
    var open by remember { mutableStateOf(false) }
    val src = chosen ?: guessSource(n)
    val canEdit = src in EDITABLE
    val est = table[src]?.first
    Dlg(if (initial == null) "Tambah produk baru" else "Edit produk", onDismiss, "Simpan", "Batal", {
        if (n.isNotBlank()) {
            val m = if (src == "MANUAL" || (canEdit && editing)) manual.toLongOrNull() ?: 0L else 0L
            onSave(n.trim(), src, m); onDismiss()
        }
    }) {
        OutlinedTextField(n, { n = it }, label = { Text("Nama produk") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth()) {
            OutlinedTextField(SRC_LABEL[src] ?: "", {}, Modifier.fillMaxWidth(), readOnly = true, singleLine = true,
                label = { Text("Sumber harga pasar") }, trailingIcon = { Text("▾", fontSize = 18.sp, color = Color.Gray) }, shape = RoundedCornerShape(14.dp))
            Box(Modifier.matchParentSize().clickable { open = true })
        }
        Spacer(Modifier.height(6.dp))
        when {
            src == "MANUAL" -> {
                OutlinedTextField(manual, { manual = it.filter(Char::isDigit).take(12) }, singleLine = true, shape = RoundedCornerShape(14.dp),
                    label = { Text("Harga pasar manual (Rp/Kg)") }, visualTransformation = Thousands,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text("Referensi: ${REFS["MANUAL"]}", fontSize = 11.sp, color = Color.Gray)
            }
            canEdit && !editing -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (est != null) "Perkiraan: ≈ Rp ${rp(est.toLong())}/kg" else "Perkiraan: -", fontSize = 12.sp, color = INK, modifier = Modifier.weight(1f))
                TextButton({ editing = true; if (manual.isEmpty() && est != null) manual = est.toLong().toString() }) { Text("Edit harga") }
            }
            canEdit -> {
                OutlinedTextField(manual, { manual = it.filter(Char::isDigit).take(12) }, singleLine = true, shape = RoundedCornerShape(14.dp),
                    label = { Text("Harga pasar (Rp/Kg)") }, visualTransformation = Thousands,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                TextButton({ editing = false; manual = "" }) { Text("Kembali ke perkiraan") }
            }
            else -> Text("Harga pasar terisi otomatis dari bursa. Tidak cocok? Pilih “Lainnya (input manual)”.", fontSize = 11.sp, color = Color.Gray)
        }
    }
    if (open) PickSourceDialog(src, { open = false }) { chosen = it; editing = false; manual = ""; open = false }
}

@Composable
fun ProfileDialog(user: FirebaseUser, onDismiss: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }; var pw by remember { mutableStateOf("") }
    var edited by remember { mutableStateOf(false) }; var err by remember { mutableStateOf<String?>(null) }
    var show by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }
    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()
    val email = realEmail(user) ?: ""
    LaunchedEffect(Unit) {
        runCatching { withTimeoutOrNull(5000) { db.collection("users").document(user.uid).get().await() } }.getOrNull()?.let {
            old = it.getString("password") ?: ""; if (!edited) pw = old
        }
    }
    suspend fun apply(newPw: String) {
        val cu = auth.currentUser ?: throw IllegalStateException("Belum login")
        if (cu.providerData.none { it.providerId == "password" }) cu.linkWithCredential(EmailAuthProvider.getCredential(email, newPw)).await()
        else cu.updatePassword(newPw).await()
        db.collection("users").document(cu.uid).set(mapOf("username" to email, "password" to newPw), SetOptions.merge())
    }
    val gLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        scope.launch {
            busy = true; err = null
            try {
                val acc = GoogleSignIn.getSignedInAccountFromIntent(r.data).await()
                withTimeout(25_000) {
                    auth.currentUser!!.reauthenticate(GoogleAuthProvider.getCredential(acc.idToken, null)).await()
                    apply(pw)
                }
                toast("Password diperbarui"); onDismiss()
            } catch (e: Exception) { err = errText(e) }
            busy = false
        }
    }
    Dlg("Profil", onDismiss, "Simpan", "Tutup", {
        when {
            busy -> {}
            pw.length < 6 -> err = "Password minimal 6 karakter"
            pw == old -> onDismiss()
            else -> scope.launch {
                busy = true; err = null
                try { withTimeout(25_000) { apply(pw) }; toast("Password diperbarui"); onDismiss() }
                catch (e: FirebaseAuthRecentLoginRequiredException) {
                    var ok = false
                    if (old.isNotEmpty()) ok = runCatching {
                        withTimeout(25_000) { auth.currentUser!!.reauthenticate(EmailAuthProvider.getCredential(email, old)).await(); apply(pw) }; true
                    }.getOrDefault(false)
                    if (ok) { toast("Password diperbarui"); onDismiss() }
                    else { err = "Verifikasi dengan akun Google untuk melanjutkan…"; gLauncher.launch(gClient(ctx).signInIntent) }
                } catch (e: Exception) { err = errText(e) }
                busy = false
            }
        }
    }) {
        OutlinedTextField(email, {}, Modifier.fillMaxWidth(), readOnly = true, label = { Text("Username (email)") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(pw, { pw = it; edited = true }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, enabled = !busy, shape = RoundedCornerShape(14.dp),
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation())
        Row(Modifier.clickable { show = !show }, verticalAlignment = Alignment.CenterVertically) { Checkbox(show, { show = it }); Text("Tampilkan password", fontSize = 13.sp) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
        err?.let { Text(it, color = RED, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        credsError?.let { Text("Login email belum siap: $it", color = RED, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
    }
}

// ---------- KALKULATOR ----------
fun calcFmt(d: Double): String = BigDecimal(d).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
fun disp(s: String): String {
    val neg = s.startsWith("-"); val b = s.removePrefix("-")
    val i = b.substringBefore("."); val d = if ("." in b) "," + b.substringAfter(".") else ""
    return (if (neg) "-" else "") + i.reversed().chunked(3).joinToString(".").reversed() + d
}

@Composable
fun Calculator(onClose: () -> Unit) {
    var cur by remember { mutableStateOf("0") }; var acc by remember { mutableStateOf<Double?>(null) }
    var op by remember { mutableStateOf<Char?>(null) }; var fresh by remember { mutableStateOf(false) }
    fun calc(): Double { val a = acc ?: return cur.toDouble(); val b = cur.toDouble()
        return when (op) { '+' -> a + b; '-' -> a - b; '×' -> a * b; '÷' -> if (b == 0.0) 0.0 else a / b; else -> b } }
    fun press(k: String) {
        when {
            k == "C" -> { cur = "0"; acc = null; op = null; fresh = false }
            k == "⌫" -> { cur = if (fresh) "0" else cur.dropLast(1); if (cur.isEmpty() || cur == "-") cur = "0" }
            k == "000" -> { if (!fresh && cur != "0" && cur.length < 12) cur += "000" }
            k[0].isDigit() || k == "." -> {
                if (cur.length < 15 || fresh) cur = if (fresh || cur == "0" && k != ".") k else if (k == "." && "." in cur) cur else cur + k
                fresh = false
            }
            k == "=" -> { if (op != null) { cur = calcFmt(calc()); acc = null; op = null; fresh = true } }
            else -> { if (op != null && !fresh) { val r = calc(); cur = calcFmt(r); acc = r } else acc = cur.toDouble(); op = k[0]; fresh = true }
        }
    }
    @Composable fun RowScope.Key(k: String, bg: Color, fg: Color, w: Float = 1f) =
        Box(Modifier.weight(w).padding(4.dp).height(68.dp).clip(RoundedCornerShape(20.dp)).background(bg).clickable { press(k) }, contentAlignment = Alignment.Center) {
            Text(k, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = fg)
        }
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.padding(14.dp).fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(Color.White)) {
            Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Kalkulator", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 6.dp))
                    Text("✕", Modifier.clip(CircleShape).clickable { onClose() }.padding(10.dp), fontSize = 22.sp)
                }
                Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.End) {
                    Text(if (acc != null && op != null) "${disp(calcFmt(acc!!))} $op" else " ", fontSize = 16.sp, color = Color.Gray, maxLines = 1)
                    Text(disp(cur), fontSize = 46.sp, fontWeight = FontWeight.Bold, color = INK, maxLines = 1)
                }
                val num = SOFT; val opc = Color(0xFFD8F0E0)
                Row { Key("7", num, INK); Key("8", num, INK); Key("9", num, INK); Key("÷", opc, GREEN) }
                Row { Key("4", num, INK); Key("5", num, INK); Key("6", num, INK); Key("×", opc, GREEN) }
                Row { Key("1", num, INK); Key("2", num, INK); Key("3", num, INK); Key("-", opc, GREEN) }
                Row { Key("0", num, INK); Key("000", num, INK); Key(".", num, INK); Key("+", opc, GREEN) }
                Row { Key("C", Color(0xFFFDE0E0), RED); Key("⌫", num, INK); Key("=", GREEN, Color.White, 2f) }
            }
        }
    }
}

// ---------- HOME ----------
fun isOnline(ctx: Context): Boolean {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return cm.activeNetwork != null
}

@Composable
fun HeaderAction(text: String, modifier: Modifier, onClick: () -> Unit) =
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .18f)).clickable { onClick() }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }

@Composable
fun Home(user: FirebaseUser) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("r", 0) }
    val col = remember { db.collection("users").document(user.uid).collection("products") }
    var products by remember { mutableStateOf(listOf<Product>()) }
    var pending by remember { mutableStateOf(false) }; var syncing by remember { mutableStateOf(false) }; var stuck by remember { mutableStateOf(false) }
    var listenErr by remember { mutableStateOf<String?>(null) }; val diag = remember { mutableStateListOf<String>() }; var diagBusy by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(prefs.getBoolean("off", false)) }
    var online by remember { mutableStateOf(true) }
    var pinId by remember { mutableStateOf<String?>(null) }; val listState = rememberLazyListState()
    var sort by remember { mutableStateOf(0) }; var query by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf("") }; var sel by remember { mutableStateOf<Product?>(null) }
    var menu by remember { mutableStateOf(false) }; var sellStep by remember { mutableStateOf(0) }
    var quotes by remember { mutableStateOf(loadQ(prefs)) }; var quoteTime by remember { mutableStateOf(prefs.getLong("qt", 0L)) }
    var items by remember { mutableStateOf(loadItems(prefs)) }
    var fx by remember { mutableStateOf(loadFx(prefs)) }
    val table = buildTable(quotes, items)
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(pinId) { if (pinId != null) { delay(300); listState.scrollToItem(0) } }
    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
    fun upd(p: Product, vararg f: Pair<String, Any>) { col.document(p.id).update(mapOf(*f)) }
    val close = { dialog = ""; sellStep = 0 }

    suspend fun refresh(): Boolean {
        val r = fetchQuotes()
        val f = fetchFx()
        val j = withContext(Dispatchers.IO) { runCatching { httpGet(PRICES_URL) }.getOrNull() }
        if (j != null) runCatching { items = mergeItems(parseItems(j)); prefs.edit().putString("pj", j).apply() }
        if (r == null && j == null && f == null) return false
        if (f != null) { fx = f; prefs.edit().putString("fx", f.entries.joinToString(";") { "${it.key}:${it.value}" }).apply() }
        if (r != null) {
            quotes = r
            prefs.edit().putString("q", r.entries.joinToString(";") { "${it.key}:${it.value.first}:${it.value.second}" }).apply()
        }
        quoteTime = System.currentTimeMillis(); prefs.edit().putLong("qt", quoteTime).apply()
        return true
    }

    suspend fun runSync() {
        diagBusy = true; syncing = true; diag.clear()
        val t0 = System.currentTimeMillis()
        fun ms() = "${System.currentTimeMillis() - t0} ms"
        if (isOnline(ctx)) {
            diag += "Mengukur kecepatan internet…"
            diag[diag.lastIndex] = "✓ Internet terhubung (${netType(ctx)}) · ±${speedTest()}"
        } else diag += "✗ Tidak ada internet"
        try { db.enableNetwork().await() } catch (e: Exception) { }
        diag += "Membaca data dari server…"
        try {
            val n = withTimeoutOrNull(20_000) { col.get(Source.SERVER).await().size() }
            diag[diag.lastIndex] = if (n == null) "✗ Baca server: timeout >20 detik\n${hintFor("")}" else "✓ Baca server: $n produk tersimpan di server (${ms()})"
        } catch (e: Exception) { val m = errText(e); diag[diag.lastIndex] = "✗ Baca server: $m\n${hintFor(m)}" }
        diag += "Menulis ke server…"
        try {
            val ok = withTimeoutOrNull(20_000) { db.collection("users").document(user.uid).collection("diag").document("ping").set(mapOf("t" to System.currentTimeMillis())).await(); true }
            diag[diag.lastIndex] = if (ok == null) "✗ Tulis server: timeout >20 detik\n${hintFor("")}" else "✓ Tulis server berhasil (${ms()})"
        } catch (e: Exception) { val m = errText(e); diag[diag.lastIndex] = "✗ Tulis server: $m\n${hintFor(m)}" }
        diag += "Mengirim data tertunda…"
        val ok2 = try { withTimeoutOrNull(20_000) { db.waitForPendingWrites().await(); true } } catch (e: Exception) { null }
        diag[diag.lastIndex] = if (ok2 == true) { pending = false; stuck = false; "✓ Semua data sudah terkirim (${ms()})" } else "✗ Masih ada data yang belum terkirim"
        syncing = false; diagBusy = false
    }

    DisposableEffect(user.uid) {
        val l = col.addSnapshotListener(MetadataChanges.INCLUDE) { s, e ->
            if (e != null) listenErr = errText(e)
            if (s != null) { listenErr = null; products = s.documents.mapNotNull { d -> d.toObject(Product::class.java)?.copy(id = d.id) }; pending = s.metadata.hasPendingWrites() }
        }
        onDispose { l.remove() }
    }
    LaunchedEffect(Unit) { while (true) { online = isOnline(ctx); delay(3000) } }
    LaunchedEffect(user.uid) {
        try { ensureCreds(user); credsError = null } catch (e: Exception) { credsError = errText(e); if (isOnline(ctx)) toast("Login email belum siap: ${errText(e)}") }
    }
    LaunchedEffect(pending, online, offline) {   // data tertunda > 12 dtk dianggap belum terkirim
        stuck = false
        if (pending && online && !offline) {
            val ok = try { withTimeoutOrNull(12_000) { db.waitForPendingWrites().await(); true } } catch (e: Exception) { null }
            if (ok == null) stuck = true else pending = false
        }
    }
    LaunchedEffect(offline) {
        prefs.edit().putBoolean("off", offline).apply()
        if (offline) db.disableNetwork() else db.enableNetwork()
    }
    LaunchedEffect(online, offline) {   // harga pasar diperbarui tiap 30 menit saat online
        while (true) {
            if (online && !offline && System.currentTimeMillis() - quoteTime >= 1_800_000) refresh()
            delay(60_000)
        }
    }

    val busySync = syncing || (pending && !stuck)
    val bad = stuck || listenErr != null
    val statusColor = when { offline || !online -> Color(0xFFFF5252); bad -> Color(0xFFFF9800); busySync -> Color(0xFFFFC107); else -> Color(0xFF69F0AE) }
    val statusText = when { offline || !online -> "Offline"; bad -> "Tertunda"; busySync -> "Sync…"; else -> "Online" }
    val statusHint = when {
        offline -> "Mode offline aktif"; !online -> "Tidak ada koneksi internet"
        listenErr != null -> "Gagal membaca data server: $listenErr"
        stuck -> "Ada data belum terkirim ke server. Periksa Firestore & rules di Firebase Console."
        busySync -> "Sedang sinkronisasi…"; else -> "Semua data sudah tersinkron" }
    val shown = products.filter { query.isNullOrBlank() || it.name.contains(query!!, true) }.let {
        when (sort) { 1 -> it.sortedByDescending { p -> p.stock }; 2 -> it.sortedBy { p -> p.stock }; 3 -> it.sortedBy { p -> p.name.lowercase() }; else -> it.sortedByDescending { p -> priceOf(p, table).first } }
    }.let { l -> if (pinId == null) l else l.sortedBy { x -> if (x.id == pinId) 0 else 1 } }
    val totalValue = products.sumOf { it.stock * it.sell }; val totalKg = products.sumOf { it.stock }

    Column(Modifier.fillMaxSize()) {
        // HEADER
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(Brush.verticalGradient(listOf(DARK, GREEN2))).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color.White))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Total nilai stok · ${products.size} produk · ${rp(totalKg)} Kg", color = Color.White.copy(alpha = .75f), fontSize = 11.sp, maxLines = 1)
                    Text("Rp ${rp(totalValue.toLong())}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = .2f)).combinedClickable(onClick = {}, onLongClick = { toast(statusHint) }).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(statusColor)); Spacer(Modifier.width(6.dp))
                    Text(statusText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(10.dp))
                Box {
                    Box(Modifier.size(52.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                        Text("☰", color = Color.White, fontSize = 26.sp)
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Filter") }, { menu = false; dialog = "filter" })
                        DropdownMenuItem({ Text("Mode Offline") }, { offline = !offline }, trailingIcon = { Checkbox(offline, null) })
                        DropdownMenuItem({ Text("Sinkronisasi data", color = if (offline) Color.Gray else Color.Unspecified) }, {
                            menu = false
                            if (!offline && !diagBusy) { dialog = "sync"; scope.launch { runSync() } }
                        }, enabled = !offline)
                        DropdownMenuItem({ Text("Profil") }, { menu = false; dialog = "profile" })
                        DropdownMenuItem({ Text("Sumber trading") }, { menu = false; dialog = "sources" })
                        DropdownMenuItem({ Text("Tabel Trading", color = if (online && !offline) Color.Unspecified else Color.Gray) }, { menu = false; dialog = "table" }, enabled = online && !offline)
                        DropdownMenuItem({ Text("Tentang aplikasi") }, { menu = false; dialog = "about" })
                        DropdownMenuItem({ Text("Privasi & keamanan data") }, { menu = false; dialog = "privacy" })
                        DropdownMenuItem({ Text("Export") }, { menu = false; dialog = "export" })
                        DropdownMenuItem({ Text("Log out", color = RED) }, { menu = false; dialog = "logout" })
                    }
                }
            }
            AdBar(online && !offline, Modifier.padding(top = 10.dp, end = 8.dp).clip(RoundedCornerShape(14.dp)))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderAction("＋ Produk", Modifier.weight(1f)) { dialog = "add" }
                HeaderAction("🧮 Kalkulator", Modifier.weight(1.2f)) { dialog = "calc" }
                HeaderAction("🔍 Cari", Modifier.weight(1f)) { query = if (query == null) "" else null }
            }
        }
        query?.let {
            OutlinedTextField(it, { v -> query = v }, Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp), placeholder = { Text("Cari produk…") },
                singleLine = true, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White))
        }
        if (shown.isEmpty()) Column(Modifier.weight(1f).fillMaxWidth().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.logo), null, Modifier.size(140.dp).clip(RoundedCornerShape(28.dp)))
            Spacer(Modifier.height(12.dp))
            Text(if (products.isEmpty()) "Belum ada produk" else "Produk tidak ditemukan", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = INK)
            Text("Tekan “＋ Produk” untuk mulai mencatat.", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)) {
            itemsIndexed(shown, key = { _, p -> p.id }) { i, p ->
                ProductCard(i + 1, p, priceOf(p, table),
                    onLong = { sel = p; dialog = "longpress" },
                    onStock = { if (p.sell == 0L) toast("Isi harga jual dulu") else { sel = p; dialog = "stock" } },
                    onRevisi = { sel = p; dialog = "revisi" },
                    onSell = { if (p.sell == 0L) toast("Isi harga jual dulu") else if (p.stock <= 0) toast("Stok kosong") else { sel = p; dialog = "sell"; sellStep = 0 } },
                    onSave = { f, v -> upd(p, f to v) })
            }
        }
    }

    val p = sel
    when (dialog) {
        "add" -> ProductDialog(null, table, close) { n, s, m ->
            val ref = col.document(); pinId = ref.id
            ref.set(Product(name = n, source = s, market = m.toDouble(), prev = m.toDouble(), created = System.currentTimeMillis()))
        }
        "edit" -> ProductDialog(p, table, close) { n, s, m -> upd(p!!, "name" to n, "source" to s, "market" to m.toDouble(), "prev" to (if (m.toDouble() != p.market) p.market else p.prev)) }
        "calc" -> Calculator(close)
        "profile" -> ProfileDialog(user, close)
        "filter" -> Dlg("Filter", close, "Tutup", null, close) {
            listOf("Harga pasar tertinggi (default)", "Stok terbanyak", "Stok tersedikit", "Abjad A–Z").forEachIndexed { i, t ->
                Row(Modifier.clickable { sort = i; pinId = null; close() }, verticalAlignment = Alignment.CenterVertically) { RadioButton(sort == i, { sort = i; pinId = null; close() }); Text(t) } }
        }
        "table" -> TradingTable(items, table, fx, quoteTime, refreshing, {
            scope.launch { refreshing = true; toast(if (refresh()) "Harga diperbarui" else "Gagal, cek koneksi internet"); refreshing = false }
        }, close)
        "sources" -> Dlg("Sumber trading", close, "Tutup", null, close) {
            Text("Harga pasar adalah patokan/perkiraan, bukan harga transaksi pengepul. Dikonversi ke Rupiah per Kg dan diperbarui tiap 30 menit saat online atau saat Anda menekan Perbarui.", fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Text("Otomatis dari bursa (live)", fontWeight = FontWeight.Bold)
            items.filter { it.live }.forEach { Text("• ${it.name}: ${it.exchange}", fontSize = 13.sp) }
            Text("• Kurs USD/IDR (IDR=X), data via Yahoo Finance", fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Text("Estimasi dari harga bursa", fontWeight = FontWeight.Bold)
            items.filter { it.basis != null }.forEach { Text("• ${it.name}: ${it.exchange}", fontSize = 13.sp) }
            Spacer(Modifier.height(10.dp))
            Text("Perkiraan pengepul (daftar harga aplikasi)", fontWeight = FontWeight.Bold)
            Text("Seng, plastik, dan kertas tidak punya bursa publik gratis. Angkanya diambil dari daftar harga aplikasi yang diperbarui berkala.", fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Text("Referensi untuk mengecek / input manual", fontWeight = FontWeight.Bold)
            Text("• Kuningan: ${REFS["KU"]}", fontSize = 13.sp)
            Text("• Tembaga campuran: ${REFS["CM"]}", fontSize = 13.sp)
            Text("• Plastik: $PLASTIC_REF", fontSize = 13.sp)
            Text("• Kertas & kardus: $PAPER_REF", fontSize = 13.sp)
            Text("• Lainnya: ${REFS["MANUAL"]}", fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Text(if (quoteTime > 0) "Terakhir diperbarui: " + SimpleDateFormat("dd MMM yyyy HH:mm", Locale("in", "ID")).format(Date(quoteTime)) else "Belum pernah diperbarui", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            TextButton({ scope.launch { toast(if (refresh()) "Harga diperbarui" else "Gagal, cek koneksi internet") } }) { Text("⟳ Perbarui sekarang") }
        }
        "sync" -> Dlg("Sinkronisasi", { if (!diagBusy) close() }, "Tutup", null, { if (!diagBusy) close() }) {
            diag.forEach { Text(it, fontSize = 13.sp); Spacer(Modifier.height(6.dp)) }
            if (diagBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        "about" -> Dlg("Tentang Rongsokin", close, "Tutup", null, close) {
            Text("Rongsokin – Smart Scrap Management System\nVersi 3.1\n\nAplikasi pencatatan stok, harga beli/jual, dan nilai barang rongsok/scrap secara praktis, bisa dipakai online maupun offline.")
            Text("Dibuat oleh: Irfan Gustama", Modifier.padding(top = 12.dp))
            Text("www.gustama.my.id", fontSize = 11.sp, color = Color.Gray) }
        "privacy" -> Dlg("Privasi & Keamanan Data", close, "Tutup", null, close) { Text(PRIVACY_TEXT) }
        "export" -> Dlg("Export", close, "Oke", "Batal", {
            close(); runCatching {
                val sb = StringBuilder("No;Produk;Harga Pasar (Rp/Kg);Harga Jual;Harga Beli;Stok Masuk Terakhir;Total Stok (Kg);Total Jual (Rp)\n")
                products.forEachIndexed { i, x -> sb.append("${i + 1};${x.name};${priceOf(x, table).first.toLong()};${x.sell};${x.buy};${x.lastIn} ${x.lastUnit};${x.stock};${(x.stock * x.sell).toLong()}\n") }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "Rongsokin_${System.currentTimeMillis()}.csv"); put(MediaStore.Downloads.MIME_TYPE, "text/csv") })!!
                ctx.contentResolver.openOutputStream(uri)!!.use { it.write(("\uFEFF" + sb).toByteArray()) }
                toast("Tersimpan di folder Download")
            }.onFailure { toast("Export gagal") }
        }) { Text("Export semua produk ke file Excel (.csv)?") }
        "logout" -> Dlg("Log out", close, "Keluar", "Batal", { close(); gClient(ctx).signOut(); auth.signOut() }) { Text(if (pending || stuck) "⚠ Ada data yang belum terkirim ke server. Jika keluar atau uninstall sekarang, data itu bisa hilang. Jalankan Sinkronisasi dulu.\n\nTetap keluar?" else "Yakin ingin keluar?") }
        "longpress" -> Dlg(p?.name ?: "", close, "Edit", "Tutup", { dialog = "edit" }) {
            TextButton({ dialog = "delete" }) { Text("Hapus produk", color = RED) } }
        "delete" -> Dlg("Hapus produk", close, "Hapus", "Batal", { col.document(p!!.id).delete(); close() }) { Text("Hapus '${p?.name}' beserta datanya?") }
        "stock" -> QtyDialog("Tambah stok", UNITS.keys.toList(), null, null, close) { kg ->
            upd(p!!, "stock" to p.stock + kg, "lastIn" to kg, "lastUnit" to "Kg"); toast("Stok ditambah") }
        "revisi" -> QtyDialog("Revisi (kurangi stok)", listOf("gram", "Kg"), "Kurangi stok sebesar {x}? Total stok & total jual akan terpotong.", p?.stock, close) { kg ->
            upd(p!!, "stock" to (p.stock - kg).coerceAtLeast(0.0)) }
        "sell" -> when (sellStep) {
            0 -> AlertDialog(onDismissRequest = close, shape = RoundedCornerShape(24.dp), containerColor = Color.White, title = { Text("Jual ${p?.name}", fontWeight = FontWeight.Bold) },
                text = { Text("Jual seluruh stok (${rp(p!!.stock)} Kg) atau sebagian?") },
                confirmButton = { TextButton({ sellStep = 2 }) { Text("Seluruhnya") } }, dismissButton = { TextButton({ sellStep = 1 }) { Text("Sebagian") } })
            1 -> QtyDialog("Jual sebagian", listOf("Kg", "kwintal", "ton"), "Jual {x}?", p?.stock, close) { kg -> upd(p!!, "stock" to (p.stock - kg).coerceAtLeast(0.0)) }
            else -> Dlg("Konfirmasi", close, "Oke", "Batal", { upd(p!!, "stock" to 0.0); close() }) { Text("Jual seluruh ${rp(p!!.stock)} Kg senilai Rp ${rp((p.stock * p.sell).toLong())}?") }
        }
    }
}

@Composable
fun PriceField(label: String, value: Long, onSave: (Long) -> Unit) {
    val focus = LocalFocusManager.current
    var t by remember(value) { mutableStateOf(if (value == 0L) "" else value.toString()) }
    LaunchedEffect(t) { delay(700); val n = t.toLongOrNull() ?: 0L; if (n != value) onSave(n) }
    Column {
        Text(label, fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        BasicTextField(t, { t = it.filter(Char::isDigit).take(12) }, Modifier.fillMaxWidth(), singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = INK),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }), visualTransformation = Thousands,
            decorationBox = { inner ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SOFT).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Rp ", color = Color.Gray, fontSize = 13.sp)
                    Box(Modifier.weight(1f)) { if (t.isEmpty()) Text("0", color = Color.LightGray, fontSize = 16.sp); inner() }
                }
            })
    }
}

@Composable
fun ProductCard(no: Int, p: Product, price: Pair<Double, Double>, onLong: () -> Unit, onStock: () -> Unit, onRevisi: () -> Unit, onSell: () -> Unit, onSave: (String, Long) -> Unit) {
    val (now, prev) = price
    val diff = if (prev > 0) (now - prev) / prev * 100 else 0.0
    val c = if (diff >= 0) GREEN else RED
    Card(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLong),
        shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFD8F0E0)), contentAlignment = Alignment.Center) {
                    Text("$no", fontWeight = FontWeight.Bold, color = GREEN, fontSize = 13.sp)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = INK, maxLines = 1)
                    Text(if (srcOf(p) in EDITABLE && p.market > 0) "harga diedit manual" else SRC_URL[srcOf(p)] ?: "input manual", fontSize = 10.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (now > 0) Column(Modifier.clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = .1f)).padding(horizontal = 8.dp, vertical = 3.dp), horizontalAlignment = Alignment.End) {
                    Text("Rp ${rp(now.toLong())}/kg", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = INK)
                    Text("${if (diff >= 0) "▲" else "▼"} ${"%.2f".format(Math.abs(diff))}%", fontSize = 10.sp, color = c, fontWeight = FontWeight.SemiBold)
                } else Text("Pasar: -", fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { PriceField("Harga jual (wajib)", p.sell) { onSave("sell", it) } }
                Box(Modifier.weight(1f)) { PriceField("Harga beli", p.buy) { onSave("buy", it) } }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Stok total ${rp(p.stock)} Kg", fontSize = 11.sp, color = INK, fontWeight = FontWeight.SemiBold)
                    Text("Masuk terakhir ${rp(p.lastIn)} Kg", fontSize = 11.sp, color = Color.Gray)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total jual", fontSize = 10.sp, color = Color.Gray)
                    Text("Rp ${rp((p.stock * p.sell).toLong())}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = GREEN)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onStock, Modifier.weight(1f), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("＋ Stok", fontSize = 13.sp) }
                OutlinedButton(onRevisi, Modifier.weight(1f), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Revisi", fontSize = 13.sp) }
                Button(onSell, Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(ORANGE), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Jual ➜", fontSize = 13.sp) }
            }
        }
    }
}


// ---------- IKLAN (AdMob Native Advanced, tampilan banner kecil) ----------
fun buildAdView(c: Context): NativeAdView {
    val d = c.resources.displayMetrics.density
    fun dp(x: Int) = (x * d).toInt()
    val root = NativeAdView(c)
    val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(8)) }
    val icon = ImageView(c).apply { layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)) }
    val col = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10); marginEnd = dp(8) }
    }
    val top = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
    val badge = TextView(c).apply { text = "Iklan"; textSize = 9f; setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xFFF9A825.toInt()); setPadding(dp(4), 0, dp(4), 0) }
    val headline = TextView(c).apply {
        textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD); maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END; setTextColor(0xFF1B2B22.toInt()); setPadding(dp(6), 0, 0, 0)
    }
    val body = TextView(c).apply { textSize = 11f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setTextColor(0xFF777777.toInt()) }
    val cta = AButton(c).apply {
        textSize = 12f; isAllCaps = false; minHeight = 0; minimumHeight = 0; setPadding(dp(12), 0, dp(12), 0)
        setTextColor(0xFFFFFFFF.toInt())
        background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFF0F7A3E.toInt()); cornerRadius = dp(17).toFloat() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34))
    }
    top.addView(badge); top.addView(headline); col.addView(top); col.addView(body)
    row.addView(icon); row.addView(col); row.addView(cta); root.addView(row)
    root.iconView = icon; root.headlineView = headline; root.bodyView = body; root.callToActionView = cta
    return root
}

fun bindAd(v: NativeAdView, a: NativeAd) {
    (v.headlineView as TextView).text = a.headline
    (v.bodyView as TextView).apply { text = a.body ?: a.advertiser ?: ""; visibility = if (text.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE }
    (v.callToActionView as AButton).apply { text = a.callToAction ?: "Buka"; visibility = if (a.callToAction == null) android.view.View.GONE else android.view.View.VISIBLE }
    (v.iconView as ImageView).apply { setImageDrawable(a.icon?.drawable); visibility = if (a.icon == null) android.view.View.GONE else android.view.View.VISIBLE }
    v.setNativeAd(a)
}

/** Slot iklan kecil di bawah layar. Tidak memakan ruang jika iklan belum/tidak termuat. */
@Composable
fun AdBar(enabled: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var ad by remember { mutableStateOf<NativeAd?>(null) }
    val holder = remember { arrayOfNulls<NativeAd>(1) }
    DisposableEffect(Unit) { onDispose { holder[0]?.destroy(); holder[0] = null } }
    LaunchedEffect(enabled) {
        if (!enabled) { ad = null; return@LaunchedEffect }
        var tries = 0
        while (ad == null && tries < 3) {
            tries++
            AdLoader.Builder(ctx, AD_UNIT_ID)
                .forNativeAd { a -> holder[0]?.destroy(); holder[0] = a; ad = a }
                .withAdListener(object : AdListener() { override fun onAdFailedToLoad(e: LoadAdError) {} })
                .withNativeAdOptions(NativeAdOptions.Builder().setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT).build())
                .build().loadAd(AdRequest.Builder().build())
            delay(300_000)
        }
    }
    ad?.let { a ->
        AndroidView(factory = { c -> buildAdView(c) }, update = { v -> if (v.tag !== a) { bindAd(v, a); v.tag = a } }, modifier = modifier.fillMaxWidth().background(Color.White))
    }
}


fun httpGet(u: String): String {
    val c = URL(u).openConnection() as HttpURLConnection
    c.connectTimeout = 10000; c.readTimeout = 10000
    return c.inputStream.bufferedReader().use { it.readText() }
}
fun netType(ctx: Context): String {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val c = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "-"
    return when { c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"; c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Seluler"; else -> "Lainnya" }
}
suspend fun speedTest(): String = withContext(Dispatchers.IO) {
    try {
        val t0 = System.nanoTime()
        val c = URL("https://speed.cloudflare.com/__down?bytes=400000").openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        val n = c.inputStream.use { it.readBytes().size }
        val sec = (System.nanoTime() - t0) / 1e9
        "%.1f Mbps".format(n * 8 / sec / 1e6)
    } catch (e: Exception) { "kecepatan tidak terukur" }
}

data class Cur(val flag: String, val abbr: String, val code: String)
val CURRENCIES = listOf(
    Cur("🇺🇸", "USA", "USD"), Cur("🇪🇺", "EU", "EUR"), Cur("🇯🇵", "JPN", "JPY"), Cur("🇸🇬", "SGP", "SGD"), Cur("🇲🇾", "MYS", "MYR"),
    Cur("🇦🇺", "AUS", "AUD"), Cur("🇬🇧", "GBR", "GBP"), Cur("🇨🇳", "CHN", "CNY"), Cur("🇭🇰", "HKG", "HKD"), Cur("🇸🇦", "SAU", "SAR"),
    Cur("🇰🇷", "KOR", "KRW"), Cur("🇹🇭", "THA", "THB"), Cur("🇨🇭", "CHE", "CHF"), Cur("🇨🇦", "CAN", "CAD")
)
const val KURS_SPREAD = 0.005
fun fmtIdr(v: Double): String = NumberFormat.getInstance(Locale("in", "ID")).apply {
    val d = if (v >= 1000) 0 else 2; minimumFractionDigits = d; maximumFractionDigits = d
}.format(v)
fun loadFx(prefs: android.content.SharedPreferences): Map<String, Double> =
    (prefs.getString("fx", "") ?: "").split(";").mapNotNull {
        val a = it.split(":"); val v = a.getOrNull(1)?.toDoubleOrNull(); if (a.size == 2 && v != null) a[0] to v else null
    }.toMap()
/** kode mata uang -> harga tengah dalam IDR per 1 unit */
suspend fun fetchFx(): Map<String, Double>? = withContext(Dispatchers.IO) {
    runCatching {
        val r = JSONObject(httpGet("https://open.er-api.com/v6/latest/USD")).getJSONObject("rates")
        val idr = r.getDouble("IDR")
        val out = mutableMapOf<String, Double>()
        CURRENCIES.forEach { c -> if (r.has(c.code) && r.getDouble(c.code) > 0) out[c.code] = idr / r.getDouble(c.code) }
        if (out.isEmpty()) null else out
    }.getOrNull()
}

@Composable
fun TradingTable(items: List<PItem>, table: Map<String, Pair<Double, Double>>, fx: Map<String, Double>, quoteTime: Long,
                 refreshing: Boolean, onRefresh: () -> Unit, onClose: () -> Unit) {
    var tab by remember { mutableStateOf("logam") }
    val stamp = if (quoteTime > 0) " Diperbarui: " + SimpleDateFormat("dd MMM yyyy HH:mm", Locale("in", "ID")).format(Date(quoteTime)) else ""
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.padding(10.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(Color.White)) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Tabel Trading", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(start = 4.dp).weight(1f))
                    TextButton(onRefresh, enabled = !refreshing) { Text(if (refreshing) "Memuat…" else "⟳ Perbarui", fontSize = 13.sp) }
                    Text("✕", Modifier.clip(CircleShape).clickable { onClose() }.padding(10.dp), fontSize = 20.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(tab == "logam", { tab = "logam" }, { Text("Logam") })
                    FilterChip(tab == "kurs", { tab = "kurs" }, { Text("KURS IDR") })
                }
                Spacer(Modifier.height(8.dp))
                if (tab == "logam") {
                    Row(Modifier.fillMaxWidth().background(Color(0xFFD8F0E0)).padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf("JENIS" to 1.1f, "BURSA/ACUAN PASAR" to 1.5f, "HARGA" to 1.1f, "PERUBAHAN" to 1f, "SATUAN" to .8f).forEach { (t, w) ->
                            Text(t, Modifier.weight(w), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = INK)
                        }
                    }
                    Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                        items.filter { it.group == "logam" && (it.live || it.basis != null) }.forEach { x ->
                            val v = table[x.key]
                            val d = if (v != null && v.second > 0) (v.first - v.second) / v.second * 100 else null
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(x.name, Modifier.weight(1.1f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = INK)
                                Text(x.exchange, Modifier.weight(1.5f), fontSize = 9.sp, color = Color.Gray)
                                Text(if (v == null) "-" else (if (!x.live) "≈ " else "") + rp(v.first.toLong()), Modifier.weight(1.1f), fontSize = 11.sp, color = INK)
                                Text(if (d == null) "-" else "${if (d >= 0) "▲" else "▼"} ${"%.2f".format(Math.abs(d))}%", Modifier.weight(1f), fontSize = 10.sp,
                                    color = if (d == null) Color.Gray else if (d >= 0) GREEN else RED)
                                Text("Rp/kg", Modifier.weight(.8f), fontSize = 10.sp, color = Color.Gray)
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEEEEE)))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("≈ = perkiraan/estimasi, bukan harga transaksi pengepul. Harga logam bursa dikonversi dengan kurs USD/IDR. Data diperbarui otomatis tiap 30 menit atau saat Anda menekan Perbarui.$stamp",
                        fontSize = 10.sp, color = Color.Gray)
                } else {
                    Row(Modifier.fillMaxWidth().background(Color(0xFFD8F0E0)).padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf("MATA UANG" to 1.4f, "KODE" to .7f, "BELI (IDR)" to 1.1f, "JUAL (IDR)" to 1.1f).forEach { (t, w) ->
                            Text(t, Modifier.weight(w), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = INK)
                        }
                    }
                    Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                        if (fx.isEmpty()) Text("Belum ada data kurs. Tekan Perbarui (perlu internet).", Modifier.padding(12.dp), fontSize = 12.sp, color = Color.Gray)
                        CURRENCIES.forEach { c ->
                            val mid = fx[c.code]
                            if (mid != null) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${c.flag} ${c.abbr}", Modifier.weight(1.4f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = INK)
                                    Text(c.code, Modifier.weight(.7f), fontSize = 11.sp, color = Color.Gray)
                                    Text(fmtIdr(mid * (1 - KURS_SPREAD)), Modifier.weight(1.1f), fontSize = 11.sp, color = INK)
                                    Text(fmtIdr(mid * (1 + KURS_SPREAD)), Modifier.weight(1.1f), fontSize = 11.sp, color = INK)
                                }
                                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEEEEE)))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Kurs indikatif: kurs tengah pasar dengan selisih ±0,5% untuk beli/jual. Bukan kurs resmi bank atau money changer. Sumber: open.er-api.com (ExchangeRate-API).$stamp",
                        fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}

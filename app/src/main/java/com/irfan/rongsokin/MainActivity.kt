@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
package com.irfan.rongsokin

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
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
val SRC_LABEL = linkedMapOf("CU" to "Tembaga", "AL" to "Aluminium", "FE" to "Besi/Baja", "MANUAL" to "Manual")
val SRC_FULL = mapOf(
    "CU" to "Tembaga – COMEX Copper Futures (HG=F)",
    "AL" to "Aluminium – Aluminum Futures (ALI=F)",
    "FE" to "Besi/Baja – CME Hot-Rolled Coil Steel (HRC=F)"
)
fun guessSource(name: String): String {
    val n = name.lowercase()
    return when {
        "tembaga" in n || "copper" in n -> "CU"
        "alum" in n -> "AL"
        "besi" in n || "baja" in n || "steel" in n -> "FE"
        else -> "MANUAL"
    }
}
fun srcOf(p: Product) = if (p.source in SRC_LABEL) p.source else guessSource(p.name)
/** (harga sekarang, harga sebelumnya) dalam Rp/Kg */
fun priceOf(p: Product, q: Map<String, Pair<Double, Double>>): Pair<Double, Double> {
    val s = srcOf(p)
    if (s == "MANUAL") return p.market to (if (p.prev > 0) p.prev else p.market)
    return q[s] ?: (p.market to p.prev)
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
    listOf(Triple("CU", "HG=F", 2.20462), Triple("AL", "ALI=F", 0.001), Triple("FE", "HRC=F", 1 / 907.185)).forEach { (k, s, f) ->
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

const val PRIVACY_TEXT = "Data produk, stok, dan harga Anda disimpan di akun Google/Firebase milik Anda dan hanya dapat diakses oleh akun Anda.\n\n" +
    "Kami tidak menjual atau membagikan data Anda kepada pihak ketiga. Pengelolaan data pribadi mengacu pada UU No. 27 Tahun 2022 tentang Pelindungan Data Pribadi dan UU ITE.\n\n" +
    "Data dikirim melalui koneksi terenkripsi (HTTPS). Anda dapat mengekspor atau meminta penghapusan data kapan saja."

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
/** Username = email Google user. Password dibuat otomatis, bisa diganti di Profil. */
suspend fun ensureCreds(u: FirebaseUser) {
    val email = u.email ?: return
    val doc = db.collection("users").document(u.uid)
    val snap = runCatching { doc.get().await() }.getOrNull()
    if (snap != null && snap.exists() && snap.getString("username")?.contains("@") == true) return
    runCatching { if (u.providerData.any { it.providerId == "password" }) u.unlink("password").await() }
    val pw = (1..10).map { "abcdefghjkmnpqrstuvwxyz23456789".random() }.joinToString("")
    u.linkWithCredential(EmailAuthProvider.getCredential(email, pw)).await()
    doc.set(mapOf("username" to email, "password" to pw)).await()
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
    var privacy by remember { mutableStateOf(false) }
    fun toast(s: String?) = Toast.makeText(ctx, s ?: "Gagal", Toast.LENGTH_LONG).show()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        scope.launch {
            busy = true
            try {
                val acc = GoogleSignIn.getSignedInAccountFromIntent(r.data).await()
                val res = auth.signInWithCredential(GoogleAuthProvider.getCredential(acc.idToken, null)).await()
                ensureCreds(res.user!!)
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
                    visualTransformation = PasswordVisualTransformation())
                TextButton({ forgot = true }, Modifier.align(Alignment.End)) { Text("Lupa password?") }
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
fun ProductDialog(initial: Product?, onDismiss: () -> Unit, onSave: (String, String, Long) -> Unit) {
    var n by remember { mutableStateOf(initial?.name ?: "") }
    var chosen by remember { mutableStateOf<String?>(initial?.let { srcOf(it) }) }
    var manual by remember { mutableStateOf(if ((initial?.market ?: 0.0) > 0) initial!!.market.toLong().toString() else "") }
    val src = chosen ?: guessSource(n)
    Dlg(if (initial == null) "Tambah produk baru" else "Edit produk", onDismiss, "Simpan", "Batal", {
        if (n.isNotBlank()) { onSave(n.trim(), src, manual.toLongOrNull() ?: 0L); onDismiss() }
    }) {
        OutlinedTextField(n, { n = it }, label = { Text("Nama produk") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(12.dp))
        Text("Sumber harga pasar", fontSize = 12.sp, color = Color.Gray)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SRC_LABEL.forEach { (k, v) -> FilterChip(src == k, { chosen = k }, { Text(v) }) }
        }
        Spacer(Modifier.height(6.dp))
        if (src == "MANUAL") OutlinedTextField(manual, { manual = it.filter(Char::isDigit).take(12) }, singleLine = true, shape = RoundedCornerShape(14.dp),
            label = { Text("Harga pasar manual (Rp/Kg)") }, visualTransformation = Thousands,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        else Text("Diambil otomatis saat online dari ${SRC_FULL[src]}.", fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
fun ProfileDialog(user: FirebaseUser, onDismiss: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }; var pw by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }
    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()
    LaunchedEffect(Unit) {
        runCatching { val d = db.collection("users").document(user.uid).get().await(); old = d.getString("password") ?: ""; pw = old }
    }
    Dlg("Profil", onDismiss, "Simpan", "Tutup", {
        when {
            pw.length < 6 -> toast("Password minimal 6 karakter")
            pw == old -> onDismiss()
            else -> scope.launch {
                busy = true
                try {
                    val cu = auth.currentUser!!
                    if (old.isNotEmpty()) runCatching { cu.reauthenticate(EmailAuthProvider.getCredential(cu.email!!, old)).await() }
                    cu.updatePassword(pw).await()
                    db.collection("users").document(cu.uid).set(mapOf("username" to cu.email, "password" to pw), SetOptions.merge()).await()
                    toast("Password diperbarui"); onDismiss()
                } catch (e: Exception) { toast("Gagal mengganti password (butuh internet). ${e.message ?: ""}") }
                busy = false
            }
        }
    }) {
        OutlinedTextField(user.email ?: "", {}, Modifier.fillMaxWidth(), readOnly = true, label = { Text("Username (email)") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, enabled = !busy, shape = RoundedCornerShape(14.dp),
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation())
        Row(Modifier.clickable { show = !show }, verticalAlignment = Alignment.CenterVertically) { Checkbox(show, { show = it }); Text("Tampilkan password", fontSize = 13.sp) }
        Text("Gunakan email & password ini untuk login di HP lain.", fontSize = 11.sp, color = Color.Gray)
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
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .18f)).clickable { onClick() }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }

@Composable
fun Home(user: FirebaseUser) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("r", 0) }
    val col = remember { db.collection("users").document(user.uid).collection("products") }
    var products by remember { mutableStateOf(listOf<Product>()) }
    var pending by remember { mutableStateOf(false) }; var syncing by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(prefs.getBoolean("off", false)) }
    var online by remember { mutableStateOf(true) }
    var sort by remember { mutableStateOf(0) }; var query by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf("") }; var sel by remember { mutableStateOf<Product?>(null) }
    var menu by remember { mutableStateOf(false) }; var sellStep by remember { mutableStateOf(0) }
    var quotes by remember { mutableStateOf(loadQ(prefs)) }; var quoteTime by remember { mutableStateOf(prefs.getLong("qt", 0L)) }
    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
    fun upd(p: Product, vararg f: Pair<String, Any>) { col.document(p.id).update(mapOf(*f)) }
    val close = { dialog = ""; sellStep = 0 }

    suspend fun refresh(): Boolean {
        val r = fetchQuotes() ?: return false
        quotes = r; quoteTime = System.currentTimeMillis()
        prefs.edit().putString("q", r.entries.joinToString(";") { "${it.key}:${it.value.first}:${it.value.second}" }).putLong("qt", quoteTime).apply()
        return true
    }

    DisposableEffect(user.uid) {
        val l = col.addSnapshotListener(MetadataChanges.INCLUDE) { s, _ ->
            if (s != null) { products = s.documents.mapNotNull { d -> d.toObject(Product::class.java)?.copy(id = d.id) }; pending = s.metadata.hasPendingWrites() }
        }
        onDispose { l.remove() }
    }
    LaunchedEffect(Unit) { while (true) { online = isOnline(ctx); delay(3000) } }
    LaunchedEffect(offline) {
        prefs.edit().putBoolean("off", offline).apply()
        if (offline) db.disableNetwork() else db.enableNetwork()
    }
    LaunchedEffect(online, offline) {   // harga pasar diperbarui tiap 1 jam saat online
        while (true) { if (online && !offline) refresh(); delay(3_600_000) }
    }

    val statusColor = when { offline || !online -> Color(0xFFFF5252); syncing || pending -> Color(0xFFFFC107); else -> Color(0xFF69F0AE) }
    val statusText = when { offline || !online -> "Offline"; syncing || pending -> "Sync…"; else -> "Online" }
    val shown = products.filter { query.isNullOrBlank() || it.name.contains(query!!, true) }.let {
        when (sort) { 1 -> it.sortedByDescending { p -> p.stock }; 2 -> it.sortedByDescending { p -> priceOf(p, quotes).first }; else -> it.sortedBy { p -> p.created } }
    }
    val totalValue = products.sumOf { it.stock * it.sell }; val totalKg = products.sumOf { it.stock }

    Column(Modifier.fillMaxSize()) {
        // HEADER
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.verticalGradient(listOf(DARK, GREEN2))).padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.White))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Rongsokin", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("Smart Scrap Management", color = Color.White.copy(alpha = .75f), fontSize = 11.sp)
                }
                Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = .2f)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(statusColor)); Spacer(Modifier.width(6.dp))
                    Text(statusText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Box {
                    Text("⋮", Modifier.clip(CircleShape).clickable { menu = true }.padding(horizontal = 12.dp, vertical = 4.dp), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Filter") }, { menu = false; dialog = "filter" })
                        DropdownMenuItem({ Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(offline, null); Text("Mode Offline") } }, { offline = !offline })
                        DropdownMenuItem({ Text("Sinkronisasi data", color = if (offline) Color.Gray else Color.Unspecified) }, {
                            menu = false
                            if (!offline) scope.launch { syncing = true
                                try { db.enableNetwork().await(); db.waitForPendingWrites().await(); toast("Sinkronisasi selesai") } catch (e: Exception) { toast("Sinkronisasi gagal") }
                                syncing = false }
                        }, enabled = !offline)
                        DropdownMenuItem({ Text("Profil") }, { menu = false; dialog = "profile" })
                        DropdownMenuItem({ Text("Sumber trading") }, { menu = false; dialog = "sources" })
                        DropdownMenuItem({ Text("Tentang aplikasi") }, { menu = false; dialog = "about" })
                        DropdownMenuItem({ Text("Privasi & keamanan data") }, { menu = false; dialog = "privacy" })
                        DropdownMenuItem({ Text("Export") }, { menu = false; dialog = "export" })
                        DropdownMenuItem({ Text("Log out", color = RED) }, { menu = false; dialog = "logout" })
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.padding(end = 8.dp)) {
                Text("Total nilai stok", color = Color.White.copy(alpha = .75f), fontSize = 12.sp)
                Text("Rp ${rp(totalValue.toLong())}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("${products.size} produk · ${rp(totalKg)} Kg", color = Color.White.copy(alpha = .75f), fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderAction("＋ Produk", Modifier.weight(1f)) { dialog = "add" }
                    HeaderAction("🧮 Kalkulator", Modifier.weight(1.2f)) { dialog = "calc" }
                    HeaderAction("🔍 Cari", Modifier.weight(1f)) { query = if (query == null) "" else null }
                }
            }
        }
        query?.let {
            OutlinedTextField(it, { v -> query = v }, Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp), placeholder = { Text("Cari produk…") },
                singleLine = true, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White))
        }
        if (shown.isEmpty()) Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.logo), null, Modifier.size(140.dp).clip(RoundedCornerShape(28.dp)))
            Spacer(Modifier.height(12.dp))
            Text(if (products.isEmpty()) "Belum ada produk" else "Produk tidak ditemukan", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = INK)
            Text("Tekan “＋ Produk” untuk mulai mencatat.", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)) {
            itemsIndexed(shown, key = { _, p -> p.id }) { i, p ->
                ProductCard(i + 1, p, priceOf(p, quotes),
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
        "add" -> ProductDialog(null, close) { n, s, m -> col.add(Product(name = n, source = s, market = m.toDouble(), prev = m.toDouble(), created = System.currentTimeMillis())) }
        "edit" -> ProductDialog(p, close) { n, s, m -> upd(p!!, "name" to n, "source" to s, "market" to m.toDouble(), "prev" to (if (m.toDouble() != p.market) p.market else p.prev)) }
        "calc" -> Calculator(close)
        "profile" -> ProfileDialog(user, close)
        "filter" -> Dlg("Filter", close, "Tutup", null, close) {
            listOf("Default (urutan input)", "Stok terbanyak", "Harga pasar tertinggi").forEachIndexed { i, t ->
                Row(Modifier.clickable { sort = i; close() }, verticalAlignment = Alignment.CenterVertically) { RadioButton(sort == i, { sort = i; close() }); Text(t) } }
        }
        "sources" -> Dlg("Sumber trading", close, "Tutup", null, close) {
            Text("Harga pasar adalah patokan harga logam di bursa internasional (bukan harga lapak/pengepul), dikonversi ke Rupiah per Kg memakai kurs USD/IDR. Diperbarui tiap 1 jam saat online.", fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            SRC_FULL.values.forEach { Text("• $it", fontSize = 13.sp) }
            Text("• Kurs USD/IDR (IDR=X)", fontSize = 13.sp)
            Text("Data diambil lewat Yahoo Finance.", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Text("Produk di luar logam di atas (kardus, plastik, kertas, dll) tidak punya harga bursa — pilih sumber “Manual” dan isi sendiri.", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Text(if (quoteTime > 0) "Terakhir diperbarui: " + SimpleDateFormat("dd MMM yyyy HH:mm", Locale("in", "ID")).format(Date(quoteTime)) else "Belum pernah diperbarui", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            TextButton({ scope.launch { toast(if (refresh()) "Harga diperbarui" else "Gagal, cek koneksi internet") } }) { Text("⟳ Perbarui sekarang") }
            TextButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://id.investing.com/commodities/copper"))) }) { Text("Buka Investing.com (referensi)") }
            TextButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tradingview.com/symbols/COMEX-HG1!/"))) }) { Text("Buka TradingView (referensi)") }
        }
        "about" -> Dlg("Tentang Rongsokin", close, "Tutup", null, close) {
            Text("Rongsokin – Smart Scrap Management System\nVersi 1.0\n\nAplikasi pencatatan stok, harga beli/jual, dan nilai barang rongsok/scrap secara praktis, bisa dipakai online maupun offline.\n\nDibuat oleh: Irfan Gustama") }
        "privacy" -> Dlg("Privasi & Keamanan Data", close, "Tutup", null, close) { Text(PRIVACY_TEXT) }
        "export" -> Dlg("Export", close, "Oke", "Batal", {
            close(); runCatching {
                val sb = StringBuilder("No;Produk;Harga Pasar (Rp/Kg);Harga Jual;Harga Beli;Stok Masuk Terakhir;Total Stok (Kg);Total Jual (Rp)\n")
                products.forEachIndexed { i, x -> sb.append("${i + 1};${x.name};${priceOf(x, quotes).first.toLong()};${x.sell};${x.buy};${x.lastIn} ${x.lastUnit};${x.stock};${(x.stock * x.sell).toLong()}\n") }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "Rongsokin_${System.currentTimeMillis()}.csv"); put(MediaStore.Downloads.MIME_TYPE, "text/csv") })!!
                ctx.contentResolver.openOutputStream(uri)!!.use { it.write(("\uFEFF" + sb).toByteArray()) }
                toast("Tersimpan di folder Download")
            }.onFailure { toast("Export gagal") }
        }) { Text("Export semua produk ke file Excel (.csv)?") }
        "logout" -> Dlg("Log out", close, "Keluar", "Batal", { close(); gClient(ctx).signOut(); auth.signOut() }) { Text("Yakin ingin keluar?") }
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
        Text(label, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(start = 4.dp, bottom = 3.dp))
        BasicTextField(t, { t = it.filter(Char::isDigit).take(12) }, Modifier.fillMaxWidth(), singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = INK),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }), visualTransformation = Thousands,
            decorationBox = { inner ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SOFT).padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
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
    Card(Modifier.padding(horizontal = 16.dp, vertical = 7.dp).fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLong),
        shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFD8F0E0)), contentAlignment = Alignment.Center) {
                    Text("$no", fontWeight = FontWeight.Bold, color = GREEN)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = INK, maxLines = 1)
                    Text("Pasar: ${SRC_LABEL[srcOf(p)]}", fontSize = 11.sp, color = Color.Gray)
                }
                if (now > 0) Column(Modifier.clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = .1f)).padding(horizontal = 10.dp, vertical = 5.dp), horizontalAlignment = Alignment.End) {
                    Text("Rp ${rp(now.toLong())}/kg", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = INK)
                    Text("${if (diff >= 0) "▲" else "▼"} ${"%.2f".format(Math.abs(diff))}%", fontSize = 11.sp, color = c, fontWeight = FontWeight.SemiBold)
                } else Text("Pasar: -", fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { PriceField("Harga jual (wajib)", p.sell) { onSave("sell", it) } }
                Box(Modifier.weight(1f)) { PriceField("Harga beli", p.buy) { onSave("buy", it) } }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF7FAF8)).padding(12.dp)) {
                Column(Modifier.weight(1f)) { Text("Stok total", fontSize = 11.sp, color = Color.Gray); Text("${rp(p.stock)} Kg", fontWeight = FontWeight.Bold, color = INK) }
                Column(Modifier.weight(1f)) { Text("Masuk terakhir", fontSize = 11.sp, color = Color.Gray); Text("${rp(p.lastIn)} Kg", fontWeight = FontWeight.Bold, color = INK) }
                Column(Modifier.weight(1.3f), horizontalAlignment = Alignment.End) { Text("Total jual", fontSize = 11.sp, color = Color.Gray); Text("Rp ${rp((p.stock * p.sell).toLong())}", fontWeight = FontWeight.Black, color = GREEN) }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onStock, Modifier.weight(1f), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("＋ Stok", fontSize = 13.sp) }
                OutlinedButton(onRevisi, Modifier.weight(1f), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Revisi", fontSize = 13.sp) }
                Button(onSell, Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(ORANGE), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Jual ➜", fontSize = 13.sp) }
            }
        }
    }
}

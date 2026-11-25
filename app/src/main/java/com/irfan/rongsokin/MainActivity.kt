@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
package com.irfan.rongsokin

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

/** Isi dengan URL JSON harga pasar: {"Kardus":2500,"Besi":4000} (Rp per Kg). Kosong = harga pasar manual. */
const val PRICE_URL = ""
val UNITS = linkedMapOf("gram" to 0.001, "Kg" to 1.0, "kwintal" to 100.0, "ton" to 1000.0)
val GREEN = Color(0xFF1F8A41)
val BG = Color(0xFFEEF3EF)
val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = GREEN)) {
                var user by remember { mutableStateOf(auth.currentUser) }
                DisposableEffect(Unit) {
                    val l = FirebaseAuth.AuthStateListener { user = it.currentUser }
                    auth.addAuthStateListener(l); onDispose { auth.removeAuthStateListener(l) }
                }
                Surface(Modifier.fillMaxSize(), color = BG) {
                    Box(Modifier.statusBarsPadding().navigationBarsPadding()) {
                        user?.let { Home(it) } ?: Login()
                    }
                }
            }
        }
    }
}

// ---------- LOGIN ----------
suspend fun ensureCreds(u: FirebaseUser) {
    val doc = db.collection("users").document(u.uid)
    if (runCatching { doc.get().await().exists() }.getOrDefault(false)) return
    val base = (u.email?.substringBefore("@") ?: "user").lowercase().filter { it.isLetterOrDigit() }.ifEmpty { "user" }
    val pw = (1..10).map { "abcdefghjkmnpqrstuvwxyz23456789".random() }.joinToString("")
    var name = base
    repeat(5) {
        try {
            u.linkWithCredential(EmailAuthProvider.getCredential("$name@rongsokin.app", pw)).await()
            doc.set(mapOf("username" to name, "password" to pw)).await(); return
        } catch (e: Exception) { name = base + (100..999).random() }
    }
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
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Card(Modifier.padding(20.dp).fillMaxWidth(), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(Color.White)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(150.dp))
                Text("RONGSOKIN", fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("Smart Scrap Management System", fontSize = 14.sp)
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(u, { u = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(p, { p = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                TextButton({ forgot = true }, Modifier.align(Alignment.End)) { Text("Forgot password?") }
                Button({
                    scope.launch {
                        busy = true
                        try { auth.signInWithEmailAndPassword("${u.trim().lowercase()}@rongsokin.app", p).await() }
                        catch (e: Exception) { toast("Username/password salah, atau belum pernah login Google.") }
                        busy = false
                    }
                }, Modifier.fillMaxWidth(), enabled = !busy && u.isNotBlank() && p.isNotBlank()) { Text("Sign in") }
                Text("atau", Modifier.padding(8.dp), fontSize = 13.sp)
                OutlinedButton({ launcher.launch(gClient(ctx).signInIntent) }, enabled = !busy) { Text("G  Continue with Google") }
                Text("🔒 Privasi data", Modifier.padding(top = 8.dp), color = Color.Gray)
            }
        }
    }
    if (forgot) Dlg("Lupa password", { forgot = false }, "Mengerti", null, { forgot = false }) {
        Text("Login sekali dengan Google, lalu buka menu ⋮ → Profil untuk melihat username & password Anda.")
    }
}

// ---------- DIALOG HELPERS ----------
@Composable
fun Dlg(title: String, onDismiss: () -> Unit, ok: String = "Oke", cancel: String? = "Batal", onOk: () -> Unit, content: @Composable () -> Unit) =
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column { content() } },
        confirmButton = { TextButton(onOk) { Text(ok) } },
        dismissButton = cancel?.let { { TextButton(onDismiss) { Text(it) } } })

@Composable
fun QtyDialog(title: String, units: List<String>, confirm: String?, onDismiss: () -> Unit, onKg: (Double) -> Unit) {
    var t by remember { mutableStateOf("") }; var unit by remember { mutableStateOf(units.first { it == "Kg" || units.size == 1 }) }
    var ask by remember { mutableStateOf<Double?>(null) }
    val kg = (t.replace(',', '.').toDoubleOrNull() ?: 0.0) * UNITS[unit]!!
    if (ask != null) {
        Dlg("Konfirmasi", { ask = null }, "Oke", "Batal", { onKg(ask!!); onDismiss() }) { Text(confirm!!.replace("{x}", "$t $unit")) }
    } else Dlg(title, onDismiss, if (confirm != null) "Simpan" else "Simpan", "Batal", {
        if (kg > 0) { if (confirm != null) ask = kg else { onKg(kg); onDismiss() } }
    }) {
        OutlinedTextField(t, { s -> t = s.filter { it.isDigit() || it == '.' || it == ',' }.take(12) }, singleLine = true,
            label = { Text("Jumlah") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Row(verticalAlignment = Alignment.CenterVertically) {
            units.forEach { k -> RadioButton(unit == k, { unit = k }); Text(k, fontSize = 12.sp) }
        }
    }
}

@Composable
fun ProductDialog(initial: Product?, online: Boolean, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var n by remember { mutableStateOf(initial?.name ?: "") }
    var s by remember { mutableStateOf(initial?.source ?: if (online) "Pasar scrap internasional (otomatis)" else "") }
    Dlg(if (initial == null) "Tambah produk baru" else "Edit produk", onDismiss, "Simpan", "Batal", { if (n.isNotBlank()) { onSave(n.trim(), s); onDismiss() } }) {
        OutlinedTextField(n, { n = it }, label = { Text("Nama produk") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(s, { s = it }, label = { Text("Sumber harga pasar") }, singleLine = true)
    }
}

@Composable
fun Calculator(onClose: () -> Unit) {
    var cur by remember { mutableStateOf("0") }; var acc by remember { mutableStateOf<Double?>(null) }
    var op by remember { mutableStateOf<Char?>(null) }; var fresh by remember { mutableStateOf(false) }
    fun fmt(d: Double) = if (d % 1.0 == 0.0 && Math.abs(d) < 1e12) d.toLong().toString() else d.toString()
    fun calc(): Double { val a = acc ?: return cur.toDouble(); val b = cur.toDouble()
        return when (op) { '+' -> a + b; '-' -> a - b; '×' -> a * b; '÷' -> if (b == 0.0) 0.0 else a / b; else -> b } }
    fun press(k: String) {
        when {
            k == "C" -> { cur = "0"; acc = null; op = null; fresh = false }
            k[0].isDigit() || k == "." -> { cur = if (fresh || cur == "0" && k != ".") k else if (k == "." && "." in cur) cur else cur + k; fresh = false }
            k == "=" -> { if (op != null) { cur = fmt(calc()); acc = null; op = null; fresh = true } }
            else -> { if (op != null && !fresh) { val r = calc(); cur = fmt(r); acc = r } else acc = cur.toDouble(); op = k[0]; fresh = true }
        }
    }
    Dialog(onClose) {
        Card(colors = CardDefaults.cardColors(Color.White)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kalkulator", fontWeight = FontWeight.Bold); Text("✕", Modifier.clickable { onClose() }, fontSize = 20.sp)
                }
                Text(cur, Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = TextAlign.End, fontSize = 34.sp, maxLines = 1)
                listOf("C÷×-", "789+", "456=", "123.", "0").forEach { row ->
                    Row { row.forEach { c -> Button({ press(c.toString()) }, Modifier.padding(2.dp).weight(1f)) { Text(c.toString()) } } }
                }
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
    var creds by remember { mutableStateOf("memuat…") }
    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
    fun upd(p: Product, vararg f: Pair<String, Any>) { col.document(p.id).update(mapOf(*f)) }
    val close = { dialog = ""; sellStep = 0 }

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
    val latest by rememberUpdatedState(products)
    LaunchedEffect(online, offline) {   // refresh harga pasar tiap 1 jam (mode online)
        while (online && !offline && PRICE_URL.isNotBlank()) {
            runCatching {
                val j = withContext(Dispatchers.IO) { JSONObject(URL(PRICE_URL).readText()) }
                latest.forEach { p ->
                    val k = j.keys().asSequence().firstOrNull { it.equals(p.name, true) } ?: return@forEach
                    val nv = j.getDouble(k); if (nv != p.market) col.document(p.id).update(mapOf("prev" to p.market, "market" to nv))
                }
            }
            delay(3_600_000)
        }
    }

    val statusColor = when { offline || !online -> Color.Red; syncing || pending -> Color(0xFFFFC107); else -> Color(0xFF2ECC40) }
    val statusText = when { offline || !online -> "offline"; syncing || pending -> "sync"; else -> "online" }
    val shown = products.filter { query.isNullOrBlank() || it.name.contains(query!!, true) }.let {
        when (sort) { 1 -> it.sortedByDescending { p -> p.stock }; 2 -> it.sortedByDescending { p -> p.market }; else -> it.sortedBy { p -> p.created } }
    }

    Column(Modifier.fillMaxSize()) {
        // HEADER
        Row(Modifier.fillMaxWidth().background(Color.White).padding(6.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            HeaderBtn("＋", "Tambah produk baru") { dialog = "add" }
            HeaderBtn("🧮", "kalkulator") { dialog = "calc" }
            HeaderBtn("🔍", "Cari Produk") { query = if (query == null) "" else null }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(width = 44.dp, height = 28.dp).clip(RoundedCornerShape(8.dp)).background(statusColor))
                Text(statusText, fontSize = 10.sp)
            }
            Box {
                Text("⋮", Modifier.clickable { menu = true }.padding(8.dp), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Filter") }, { menu = false; dialog = "filter" })
                    DropdownMenuItem({ Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(offline, null); Text("Mode Offline") } }, { offline = !offline })
                    DropdownMenuItem({ Text("Sinkronisasi data", color = if (offline) Color.Gray else Color.Unspecified) }, {
                        menu = false
                        if (!offline) scope.launch { syncing = true
                            try { db.enableNetwork().await(); db.waitForPendingWrites().await(); toast("Sinkronisasi selesai") } catch (e: Exception) { toast("Sinkronisasi gagal") }
                            syncing = false }
                    }, enabled = !offline)
                    DropdownMenuItem({ Text("Profil") }, { menu = false; dialog = "profile"
                        scope.launch { creds = runCatching { val d = db.collection("users").document(user.uid).get().await()
                            "Username: ${d.getString("username")}\nPassword: ${d.getString("password")}" }.getOrDefault("Belum tersedia (butuh koneksi)") } })
                    DropdownMenuItem({ Text("Tentang aplikasi") }, { menu = false; dialog = "about" })
                    DropdownMenuItem({ Text("Privasi & keamanan data") }, { menu = false; dialog = "privacy" })
                    DropdownMenuItem({ Text("Export") }, { menu = false; dialog = "export" })
                    DropdownMenuItem({ Text("Log out") }, { menu = false; dialog = "logout" })
                }
            }
        }
        query?.let { OutlinedTextField(it, { v -> query = v }, Modifier.fillMaxWidth().background(Color.White), placeholder = { Text("Cari produk…") }, singleLine = true) }
        // LIST
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(shown, key = { _, p -> p.id }) { i, p ->
                ProductRow(i + 1, p, if (i % 2 == 0) BG else Color.White,
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
        "add" -> ProductDialog(null, online && !offline, close) { n, s -> col.add(Product(name = n, source = s, created = System.currentTimeMillis())) }
        "edit" -> ProductDialog(p, online && !offline, close) { n, s -> upd(p!!, "name" to n, "source" to s) }
        "calc" -> Calculator(close)
        "filter" -> Dlg("Filter", close, "Tutup", null, close) {
            listOf("Default (trafik pasar)", "Stok terbanyak", "Harga pasar tertinggi").forEachIndexed { i, t ->
                Row(Modifier.clickable { sort = i; close() }, verticalAlignment = Alignment.CenterVertically) { RadioButton(sort == i, { sort = i; close() }); Text(t) } }
        }
        "profile" -> Dlg("Profil", close, "Tutup", null, close) { Text("Email: ${user.email}\n$creds") }
        "about" -> Dlg("Tentang Rongsokin", close, "Tutup", null, close) {
            Text("Rongsokin – Smart Scrap Management System\nVersi 1.0\n\nAplikasi pencatatan stok, harga beli/jual, dan nilai barang rongsok/scrap secara praktis, bisa dipakai online maupun offline.\n\nDibuat oleh: Irfan Gustama") }
        "privacy" -> Dlg("Privasi & Keamanan Data", close, "Tutup", null, close) {
            Text("Data produk, stok, dan harga Anda disimpan di akun Google/Firebase milik Anda dan hanya dapat diakses oleh akun Anda.\n\n" +
                "Kami tidak menjual atau membagikan data Anda kepada pihak ketiga. Pengelolaan data pribadi mengacu pada UU No. 27 Tahun 2022 tentang Pelindungan Data Pribadi dan UU ITE.\n\n" +
                "Data dikirim melalui koneksi terenkripsi (HTTPS). Anda dapat mengekspor atau meminta penghapusan data kapan saja.") }
        "export" -> Dlg("Export", close, "Oke", "Batal", {
            close(); runCatching {
                val sb = StringBuilder("No;Produk;Harga Pasar;Harga Jual;Harga Beli;Stok Masuk Terakhir;Total Stok (Kg);Total Jual (Rp)\n")
                products.forEachIndexed { i, x -> sb.append("${i + 1};${x.name};${x.market};${x.sell};${x.buy};${x.lastIn} ${x.lastUnit};${x.stock};${(x.stock * x.sell).toLong()}\n") }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "Rongsokin_${System.currentTimeMillis()}.csv"); put(MediaStore.Downloads.MIME_TYPE, "text/csv") })!!
                ctx.contentResolver.openOutputStream(uri)!!.use { it.write(("\uFEFF" + sb).toByteArray()) }
                toast("Tersimpan di folder Download")
            }.onFailure { toast("Export gagal") }
        }) { Text("Export semua produk ke file Excel (.csv)?") }
        "logout" -> Dlg("Log out", close, "Keluar", "Batal", { close(); gClient(ctx).signOut(); auth.signOut() }) { Text("Yakin ingin keluar?") }
        "longpress" -> Dlg(p?.name ?: "", close, "Edit", "Tutup", { dialog = "edit" }) {
            TextButton({ dialog = "delete" }) { Text("Hapus produk", color = Color.Red) } }
        "delete" -> Dlg("Hapus produk", close, "Hapus", "Batal", { col.document(p!!.id).delete(); close() }) { Text("Hapus '${p?.name}' beserta datanya?") }
        "stock" -> QtyDialog("Tambah stok", UNITS.keys.toList(), null, close) { kg ->
            upd(p!!, "stock" to p.stock + kg, "lastIn" to kg, "lastUnit" to "Kg"); toast("Stok ditambah") }
        "revisi" -> QtyDialog("Revisi (kurangi stok)", listOf("gram", "Kg"), "Kurangi stok sebesar {x}? Total stok & total jual akan terpotong.", close) { kg ->
            upd(p!!, "stock" to (p.stock - kg).coerceAtLeast(0.0)) }
        "sell" -> when (sellStep) {
            0 -> AlertDialog(onDismissRequest = close, title = { Text("Jual ${p?.name}") }, text = { Text("Jual seluruh stok (${rp(p!!.stock)} Kg) atau sebagian?") },
                confirmButton = { TextButton({ sellStep = 2 }) { Text("Seluruhnya") } }, dismissButton = { TextButton({ sellStep = 1 }) { Text("Sebagian") } })
            1 -> QtyDialog("Jual sebagian", listOf("Kg"), "Jual {x}?", close) { kg -> upd(p!!, "stock" to (p.stock - kg).coerceAtLeast(0.0)) }
            else -> Dlg("Konfirmasi", close, "Oke", "Batal", { upd(p!!, "stock" to 0.0); close() }) { Text("Jual seluruh ${rp(p!!.stock)} Kg senilai Rp ${rp((p.stock * p.sell).toLong())}?") }
        }
    }
}

@Composable
fun HeaderBtn(icon: String, label: String, onClick: () -> Unit) =
    Column(Modifier.clickable { onClick() }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 28.sp); Text(label, fontSize = 10.sp, color = Color.Gray)
    }

@Composable
fun PriceField(label: String, value: Long, onSave: (Long) -> Unit) {
    var t by remember(value) { mutableStateOf(if (value == 0L) "" else value.toString()) }
    var had by remember { mutableStateOf(false) }
    Text(label, fontSize = 10.sp, color = Color.Gray)
    BasicTextField(t, { t = it.filter(Char::isDigit).take(12) },
        Modifier.fillMaxWidth().border(1.dp, Color.Gray, RoundedCornerShape(3.dp)).padding(4.dp)
            .onFocusChanged { if (it.isFocused) had = true else if (had) { had = false; onSave(t.toLongOrNull() ?: 0L) } },
        singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, textAlign = TextAlign.Center, color = Color.DarkGray),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = Thousands)
}

@Composable
fun ProductRow(no: Int, p: Product, bg: Color, onLong: () -> Unit, onStock: () -> Unit, onRevisi: () -> Unit, onSell: () -> Unit, onSave: (String, Long) -> Unit) {
    val up = p.market >= p.prev
    val c = if (up) Color(0xFF2E9E3E) else Color.Red
    Row(Modifier.fillMaxWidth().background(bg).height(IntrinsicSize.Min).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1.3f).combinedClickable(onClick = {}, onLongClick = onLong).padding(horizontal = 4.dp)) {
            Text("$no. ${p.name}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (p.market > 0) "${rp(p.market)}/kg" else "-", Modifier.border(1.5.dp, c, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 12.sp)
                if (p.market > 0) Text(if (up) " ▲" else " ▼", color = c, fontSize = 18.sp)
            }
            Text("Harga pasar (Rp)", fontSize = 10.sp, color = Color.Gray)
        }
        Column(Modifier.weight(1f).padding(horizontal = 3.dp)) {
            PriceField("harga jual", p.sell) { onSave("sell", it) }
            PriceField("Harga beli", p.buy) { onSave("buy", it) }
        }
        Column(Modifier.weight(.85f).clickable { onStock() }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("＋", Modifier.border(1.5.dp, Color.DarkGray, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 4.dp), fontSize = 26.sp)
            Text("Tambah stok", fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1.3f).padding(horizontal = 4.dp)) {
            Text("Stok masuk terakhir: ${rp(p.lastIn)} kg", fontSize = 9.sp)
            Text("Total Stok: ${rp(p.stock)} kg", fontSize = 9.sp)
            Text("Total Jual: Rp.${rp((p.stock * p.sell).toLong())}", fontSize = 9.sp)
            Text("REVISI", Modifier.background(Color(0xFF5B3A7E)).clickable { onRevisi() }.padding(horizontal = 3.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(.7f).clickable { onSell() }, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.Red), contentAlignment = Alignment.Center) { Text("➜", color = Color.White, fontSize = 20.sp) }
            Text("Jual", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

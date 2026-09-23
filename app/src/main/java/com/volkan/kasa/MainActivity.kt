package com.volkan.kasa

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class VaultItem(
    val id: Long,
    val title: String,
    val username: String,
    val password: String,
    val note: String
)

class VaultStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)
    private val alias = "KasaAESKey"

    private fun key(): SecretKey {
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(alias)) {
            val kg = KeyGenerator.getInstance("AES", "AndroidKeyStore")
            kg.init(256)
            kg.generateKey()
        }
        return (ks.getEntry(alias, null) as java.security.KeyStore.SecretKeyEntry).secretKey
    }

    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val combined = cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = data.copyOfRange(0, 12)
        val body = data.copyOfRange(12, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    fun load(): List<VaultItem> = runCatching {
        val raw = prefs.getString("data", null) ?: return emptyList()
        val arr = JSONArray(decrypt(raw))
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(VaultItem(o.getLong("id"), o.getString("title"),
                    o.getString("username"), o.getString("password"), o.getString("note")))
            }
        }
    }.getOrDefault(emptyList())

    fun save(items: List<VaultItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("title", it.title); put("username", it.username)
                put("password", it.password); put("note", it.note)
            })
        }
        prefs.edit().putString("data", encrypt(arr.toString())).apply()
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var store: VaultStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = VaultStore(this)
        setContent { KasaApp(store) }
    }

    fun authenticate(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Kasa Kilidi")
            .setSubtitle("Kimliğinizi doğrulayın")
            .setNegativeButtonText("İptal")
            .build()
        prompt.authenticate(info)
    }
}

@Composable
fun KasaApp(store: VaultStore) {
    var unlocked by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf(store.load()) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        if (!unlocked) {
            LockScreen {
                unlocked = true
            }
        } else {
            VaultScreen(
                items = items,
                onAdd = { item ->
                    items = items + item
                    store.save(items)
                },
                onDelete = { item ->
                    items = items.filterNot { it.id == item.id }
                    store.save(items)
                }
            )
        }
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    var passcode by remember { mutableStateOf("") }
    var firstRun by remember { mutableStateOf(true) }
    val prefs = remember { context.getSharedPreferences("setup", Context.MODE_PRIVATE) }
    val saved = prefs.getString("pin", null)
    firstRun = saved == null

    Surface(Modifier.fillMaxSize(), color = Color(0xFF101114)) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Lock, null, Modifier.size(72.dp), tint = Color(0xFF8AB4F8))
            Spacer(Modifier.height(18.dp))
            Text("KASA", style = MaterialTheme.typography.headlineLarge)
            Text(
                if (firstRun) "Ana PIN'inizi oluşturun" else "Kasanız kilitli",
                color = Color.LightGray
            )
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = passcode,
                onValueChange = { if (it.length <= 8) passcode = it },
                label = { Text("4–8 haneli PIN") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                if (firstRun) {
                    if (passcode.length >= 4) {
                        prefs.edit().putString("pin", passcode).apply()
                        onUnlock()
                    }
                } else if (passcode == saved) onUnlock()
                else Toast.makeText(context, "PIN hatalı", Toast.LENGTH_SHORT).show()
            }) {
                Text(if (firstRun) "KASA OLUŞTUR" else "KİLİDİ AÇ")
            }
            Spacer(Modifier.height(12.dp))
            if (!firstRun) {
                TextButton(onClick = {
                    (context as? MainActivity)?.authenticate(onUnlock)
                }) { Text("Parmak izi ile aç") }
            }
        }
    }
}

@Composable
fun VaultScreen(
    items: List<VaultItem>,
    onAdd: (VaultItem) -> Unit,
    onDelete: (VaultItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val filtered = items.filter {
        it.title.contains(query, true) ||
        it.username.contains(query, true) ||
        it.note.contains(query, true)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Kasa")
                    Text("${items.size} kayıt", style = MaterialTheme.typography.labelSmall)
                }
            }, actions = {
                IconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, "Yeni kayıt")
                }
            })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, "Ekle")
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Kayıtlarda ara...") },
                singleLine = true
            )
            Spacer(Modifier.height(14.dp))
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (items.isEmpty()) "Henüz kayıt yok.\n+ ile ilk kaydı ekleyin." else "Sonuç bulunamadı.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.id }) { item ->
                        VaultCard(item, onDelete, context)
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddDialog(
            onDismiss = { showAdd = false },
            onSave = {
                onAdd(it)
                showAdd = false
            }
        )
    }
}

@Composable
fun VaultCard(item: VaultItem, onDelete: (VaultItem) -> Unit, context: Context) {
    var reveal by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, null, Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    if (item.username.isNotBlank())
                        Text(item.username, color = Color.Gray)
                }
                IconButton(onClick = { onDelete(item) }) {
                    Icon(Icons.Default.Delete, "Sil")
                }
            }
            if (item.password.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (reveal) item.password else "••••••••••••",
                        Modifier.weight(1f)
                    )
                    IconButton(onClick = { reveal = !reveal }) {
                        Icon(if (reveal) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                    IconButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Şifre", item.password))
                        Toast.makeText(context, "Şifre kopyalandı", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.ContentCopy, "Kopyala") }
                }
            }
            if (item.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(item.note, color = Color.Gray)
            }
        }
    }
}

@Composable
fun AddDialog(onDismiss: () -> Unit, onSave: (VaultItem) -> Unit) {
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni kayıt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Başlık") }, singleLine = true)
                OutlinedTextField(username, { username = it }, label = { Text("Kullanıcı / e-posta") }, singleLine = true)
                OutlinedTextField(
                    password, { password = it }, label = { Text("Şifre") }, singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton({ visible = !visible }) {
                            Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    }
                )
                OutlinedTextField(note, { note = it }, label = { Text("Not") })
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(VaultItem(System.currentTimeMillis(), title, username, password, note))
                }
            ) { Text("KAYDET") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İPTAL") } }
    )
}

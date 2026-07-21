package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFFEF7FF), contentColor = Color(0xFF1D1B20)) {
          Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent
          ) { innerPadding ->
            SOSApp(Modifier.padding(innerPadding))
          }
        }
      }
    }
  }
}

enum class AppState {
  CHECKING,
  SETUP,
  COUNTDOWN,
  EXECUTED
}

@Composable
fun SOSApp(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val sharedPrefs = remember { context.getSharedPreferences("sos_prefs", Context.MODE_PRIVATE) }
  
  var webhookUrl by remember { mutableStateOf(sharedPrefs.getString("webhook_url", "") ?: "") }
  var senderName by remember { mutableStateOf(sharedPrefs.getString("sender_name", "") ?: "") }
  var appState by remember { mutableStateOf(AppState.CHECKING) }

  val requiredPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
  )

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val allGranted = permissions.entries.all { it.value }
    if (allGranted && webhookUrl.isNotBlank() && senderName.isNotBlank()) {
      appState = AppState.COUNTDOWN
    } else if (!allGranted) {
      Toast.makeText(context, "Permissões necessárias para funcionar.", Toast.LENGTH_LONG).show()
      appState = AppState.SETUP
    }
  }

  LaunchedEffect(Unit) {
    if (webhookUrl.isBlank() || senderName.isBlank()) {
      appState = AppState.SETUP
    } else {
      val hasPermissions = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
      }
      if (hasPermissions) {
        appState = AppState.COUNTDOWN
      } else {
        permissionLauncher.launch(requiredPermissions)
      }
    }
  }

  when (appState) {
    AppState.CHECKING -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    }
    AppState.SETUP -> {
      SetupScreen(
        modifier = modifier,
        initialWebhookUrl = webhookUrl,
        initialSenderName = senderName,
        onSave = { url, name ->
          sharedPrefs.edit()
            .putString("webhook_url", url)
            .putString("sender_name", name)
            .apply()
          webhookUrl = url
          senderName = name
          permissionLauncher.launch(requiredPermissions)
        }
      )
    }
    AppState.COUNTDOWN -> {
      CountdownScreen(
        modifier = modifier,
        onCancel = { appState = AppState.SETUP },
        onTrigger = {
          executeSOS(context, webhookUrl, senderName)
          appState = AppState.EXECUTED
        }
      )
    }
    AppState.EXECUTED -> {
      ExecutedScreen(
        modifier = modifier,
        onReset = { appState = AppState.SETUP }
      )
    }
  }
}

@Composable
fun SetupScreen(
  modifier: Modifier = Modifier, 
  initialWebhookUrl: String, 
  initialSenderName: String, 
  onSave: (String, String) -> Unit
) {
  var url by remember { mutableStateOf(initialWebhookUrl) }
  var name by remember { mutableStateOf(initialSenderName) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Box(
      modifier = Modifier
        .size(80.dp)
        .background(Color(0xFFE8DEF8), CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = Icons.Default.Warning,
        contentDescription = "Alerta",
        tint = Color(0xFF21005D),
        modifier = Modifier.size(40.dp)
      )
    }
    Spacer(modifier = Modifier.height(24.dp))
    Text(
      text = "Configuração do SOS",
      fontSize = 24.sp,
      fontWeight = FontWeight.SemiBold,
      color = Color(0xFF1D1B20)
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = "Configure seu webhook do Microsoft Teams e seu nome para enviar alertas de emergência.",
      fontSize = 14.sp,
      color = Color(0xFF49454F),
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(32.dp))
    OutlinedTextField(
      value = name,
      onValueChange = { name = it },
      label = { Text("Seu Nome") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp)
    )
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
      value = url,
      onValueChange = { url = it },
      label = { Text("URL do Webhook do Teams") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp)
    )
    Spacer(modifier = Modifier.height(32.dp))
    Button(
      onClick = {
        if (url.isNotBlank() && name.isNotBlank()) {
          onSave(url, name)
        }
      },
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp),
      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D1B20), contentColor = Color.White),
      shape = CircleShape
    ) {
      Text("Salvar e Ativar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
  }
}

@Composable
fun CountdownScreen(modifier: Modifier = Modifier, onCancel: () -> Unit, onTrigger: () -> Unit) {
  var countdown by remember { mutableIntStateOf(3) }

  LaunchedEffect(countdown) {
    if (countdown > 0) {
      delay(1000L)
      countdown -= 1
    } else {
      onTrigger()
    }
  }

  Column(
    modifier = modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Header Section
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.Start
    ) {
      Text(
        text = "SOS Emergência",
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF1D1B20)
      )
      Text(
        text = "Ação automática iniciada",
        fontSize = 14.sp,
        color = Color(0xFF49454F),
        modifier = Modifier.padding(top = 4.dp)
      )
    }

    // Main Trigger Area
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(horizontal = 24.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Animated SOS Ring
        Box(contentAlignment = Alignment.Center) {
          Box(
            modifier = Modifier
              .size(256.dp)
              .scale(1.1f)
              .background(Color(0xFFB3261E).copy(alpha = 0.1f), CircleShape)
          )
          Box(
            modifier = Modifier
              .size(208.dp)
              .scale(1.05f)
              .background(Color(0xFFB3261E).copy(alpha = 0.2f), CircleShape)
          )
          Box(
            modifier = Modifier
              .size(160.dp)
              .background(Color(0xFFB3261E), CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = String.format("%02d", countdown),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp
              )
              Text(
                text = "SEGUNDOS",
                color = Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.5.sp
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
          text = "Enviando alerta para o Teams",
          fontSize = 20.sp,
          fontWeight = FontWeight.Medium,
          color = Color(0xFFB3261E)
        )
        
        Spacer(modifier = Modifier.height(40.dp))

        // Status Indicators
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          StatusCard(
            icon = "📍",
            title = "Localização GPS",
            subtitle = "Sendo capturada...",
            statusText = "PRONTO",
            statusColor = Color(0xFF16A34A)
          )
          StatusCard(
            icon = "💬",
            title = "Alerta do Teams",
            subtitle = "Canal de emergência",
            statusText = "AGUARDANDO",
            statusColor = Color(0xFFD97706)
          )
        }
      }
    }

    // Bottom Action Bar
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp)
    ) {
      Button(
        onClick = onCancel,
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D1B20), contentColor = Color.White),
        shape = CircleShape
      ) {
        Text("CANCELAR SOS", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

@Composable
fun StatusCard(icon: String, title: String, subtitle: String, statusText: String, statusColor: Color) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color(0xFFF2F0F4), RoundedCornerShape(16.dp))
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .background(Color(0xFFE8DEF8), CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Text(icon, fontSize = 18.sp)
    }
    Spacer(modifier = Modifier.width(16.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1D1B20))
      Text(subtitle, fontSize = 12.sp, color = Color(0xFF49454F))
    }
    Text(statusText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = statusColor)
  }
}

@Composable
fun ExecutedScreen(modifier: Modifier = Modifier, onReset: () -> Unit) {
  Column(
    modifier = modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Header Section
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.Start
    ) {
      Text(
        text = "SOS Emergência",
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF1D1B20)
      )
      Text(
        text = "Ação automática concluída",
        fontSize = 14.sp,
        color = Color(0xFF49454F),
        modifier = Modifier.padding(top = 4.dp)
      )
    }

    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(horizontal = 24.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
          modifier = Modifier
            .size(120.dp)
            .background(Color(0xFFE8DEF8), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Text("✓", fontSize = 48.sp, color = Color(0xFF21005D), fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
          text = "SOS Enviado",
          fontSize = 20.sp,
          fontWeight = FontWeight.Medium,
          color = Color(0xFF1D1B20)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
          text = "O alerta foi enviado para o Teams com sua localização.",
          fontSize = 14.sp,
          color = Color(0xFF49454F),
          textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(40.dp))

        // Status Indicators
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          StatusCard(
            icon = "📍",
            title = "Localização GPS",
            subtitle = "Capturada com sucesso",
            statusText = "CONCLUÍDO",
            statusColor = Color(0xFF16A34A)
          )
          StatusCard(
            icon = "💬",
            title = "Alerta do Teams",
            subtitle = "Mensagem recebida pelo webhook",
            statusText = "ENVIADO",
            statusColor = Color(0xFF16A34A)
          )
        }
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp)
    ) {
      Button(
        onClick = onReset,
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF2F0F4), contentColor = Color(0xFF1D1B20)),
        shape = CircleShape
      ) {
        Text("Voltar para Configurações", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

@SuppressLint("MissingPermission")
fun executeSOS(context: Context, webhookUrl: String, senderName: String) {
  try {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    
    // Obtain last location quickly
    fusedLocationClient.lastLocation.addOnCompleteListener { task ->
      val location = if (task.isSuccessful) task.result else null
      val mapsLink = if (location != null) {
        "https://maps.google.com/?q=${location.latitude},${location.longitude}"
      } else {
        "Localização indisponível"
      }
      
      sendTeamsWebhook(context, webhookUrl, senderName, mapsLink)
    }

  } catch (e: Exception) {
    e.printStackTrace()
    Toast.makeText(context, "Erro ao obter localização: ${e.message}", Toast.LENGTH_LONG).show()
  }
}

fun sendTeamsWebhook(context: Context, webhookUrl: String, senderName: String, mapsLink: String) {
  val client = OkHttpClient()
  val jsonType = "application/json; charset=utf-8".toMediaType()

  val factsArray = JSONArray().apply {
    put(JSONObject().apply {
      put("name", "Localização:")
      put("value", if (mapsLink.startsWith("http")) "[Ver no Mapa]($mapsLink)" else mapsLink)
    })
  }

  val section = JSONObject().apply {
    put("activityTitle", "🚨 SOS Emergência - Alerta 🚨")
    put("activitySubtitle", "Enviado por: **$senderName**")
    put("facts", factsArray)
  }

  val json = JSONObject().apply {
    put("@type", "MessageCard")
    put("@context", "http://schema.org/extensions")
    put("themeColor", "B3261E")
    put("summary", "Alerta de SOS Emergência")
    put("sections", JSONArray().put(section))
  }

  val body = json.toString().toRequestBody(jsonType)
  val request = Request.Builder()
    .url(webhookUrl)
    .post(body)
    .build()

  CoroutineScope(Dispatchers.IO).launch {
    try {
      val response = client.newCall(request).execute()
      if (!response.isSuccessful) {
        throw java.io.IOException("Unexpected code $response")
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }
}

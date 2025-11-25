package com.codepalace.accelerometer

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.tasks.await
import com.codepalace.accelerometer.PontosFixosRepository
import com.codepalace.accelerometer.PontoFixo
import android.location.Location

@Composable
fun MapsScreen() {
    val context = LocalContext.current

    var hasLocation by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasLocation =
            (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                    (grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
    }

    var pontosFixos by remember { mutableStateOf<List<PontoFixo>>(emptyList()) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }

    // Carrega os pontos fixos do Firebase
    LaunchedEffect(Unit) {
        PontosFixosRepository.criarPontosFixosSeNaoExistirem()
        pontosFixos = PontosFixosRepository.getPontosFixos()
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    val cameraPositionState = rememberCameraPositionState()

    // Atualiza a localização do usuário
    LaunchedEffect(hasLocation) {
        if (hasLocation) {
            val cur = LocationUpdates.getCurrentLatLng(context).await()
            cur?.let {
                val latLng = LatLng(it.latitude, it.longitude)
                userLocation = latLng
                cameraPositionState.position =
                    CameraPosition.fromLatLngZoom(latLng, 16f)
            }

            LocationUpdates.locationFlow(context).collectLatest { loc ->
                val latLng = LatLng(loc.latitude, loc.longitude)
                userLocation = latLng
                cameraPositionState.animate(
                    update = CameraUpdateFactory.newLatLng(latLng),
                    durationMs = 500
                )
            }
        }
    }

    val mapProps by remember(hasLocation) {
        mutableStateOf(MapProperties(isMyLocationEnabled = hasLocation))
    }
    val uiSettings = remember {
        MapUiSettings(myLocationButtonEnabled = true, zoomControlsEnabled = false)
    }

    val zoomMinimoParaMostrar = 13f

    // Estado do diálogo
    var selectedPonto by remember { mutableStateOf<PontoFixo?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProps,
            uiSettings = uiSettings
        ) {
            if (cameraPositionState.position.zoom >= zoomMinimoParaMostrar) {
                pontosFixos.forEach { ponto ->
                    Marker(
                        state = rememberMarkerState(
                            position = LatLng(ponto.latitude, ponto.longitude)
                        ),
                        title = ponto.nome,
                        snippet = "Ponto fixo",
                        onClick = {
                            // Mostra o diálogo apenas se estiver dentro de 500 metros
                            userLocation?.let { userLoc ->
                                val dist = calculateDistance(
                                    userLoc.latitude,
                                    userLoc.longitude,
                                    ponto.latitude,
                                    ponto.longitude
                                )
                                if (dist <= 500) {
                                    selectedPonto = ponto
                                    showDialog = true
                                }
                            }
                            true
                        }
                    )
                }
            }
        }

        if (showDialog && selectedPonto != null) {
            val ponto = selectedPonto!!
            AlertDialog(
                onDismissRequest = {
                    showDialog = false
                    selectedPonto = null
                },
                title = { Text(text = "Esse buraco ainda existe?") },
                text = {
                    Text(
                        text = "${ponto.nome}\nLatitude: ${ponto.latitude}\nLongitude: ${ponto.longitude}"
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            // ✅ Sim: buraco ainda existe
                            showDialog = false
                            selectedPonto = null
                        }
                    ) { Text("Sim") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            // ❌ Não: buraco não existe mais
                            showDialog = false
                            selectedPonto = null
                        }
                    ) { Text("Não") }
                }
            )
        }
    }
}

// Função para calcular distância em metros entre dois pontos
fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val loc1 = Location("").apply { latitude = lat1; longitude = lon1 }
    val loc2 = Location("").apply { latitude = lat2; longitude = lon2 }
    return loc1.distanceTo(loc2)
}

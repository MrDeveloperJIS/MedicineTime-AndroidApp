package com.mrjis.medicinetime.alarm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mrjis.medicinetime.AppContainer
import com.mrjis.medicinetime.MedicineTimeApplication
import com.mrjis.medicinetime.ui.theme.MedicineTimeTheme
import kotlinx.coroutines.launch

/** Opens over the lock screen when a dose is due. Closes itself once the dose is answered. */
class AlarmActivity : ComponentActivity() {

    private var request by mutableStateOf<AlarmRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        request = AlarmRequest.from(intent)
        val container = (application as MedicineTimeApplication).container

        setContent {
            MedicineTimeTheme {
                AlarmRoute(request = request, container = container, onClose = { finish() })
            }
        }
    }

    /** Another alarm (or a merged one) was opened while this screen was already showing. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        request = AlarmRequest.from(intent)
    }

    companion object {
        fun createIntent(context: Context, profileId: String, logIds: List<String>): Intent =
            Intent(context, AlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // A different data Uri per profile keeps their pending intents separate.
                data = Uri.parse("medicinetime://ring/$profileId")
                putExtra(AlarmScheduler.EXTRA_PROFILE_ID, profileId)
                putExtra(AlarmService.EXTRA_LOG_IDS, logIds.toTypedArray())
            }
    }
}

private data class AlarmRequest(val profileId: String, val logIds: List<String>) {
    companion object {
        fun from(intent: Intent): AlarmRequest? {
            val profileId = intent.getStringExtra(AlarmScheduler.EXTRA_PROFILE_ID) ?: return null
            val logIds = intent.getStringArrayExtra(AlarmService.EXTRA_LOG_IDS)?.toList().orEmpty()
            return if (logIds.isEmpty()) null else AlarmRequest(profileId, logIds)
        }
    }
}

@Composable
private fun AlarmRoute(request: AlarmRequest?, container: AppContainer, onClose: () -> Unit) {
    if (request == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    var group by remember(request) { mutableStateOf<RingGroup?>(null) }
    LaunchedEffect(request) {
        RingLoader.observe(container, request.profileId, request.logIds).collect {
            group = it
            // Nothing is waiting any more (answered from the notification, or the medicine was deleted).
            if (it == null) onClose()
        }
    }

    val current = group
    if (current == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    AlarmScreen(
        group = current,
        onTaken = { selectedIds ->
            // appScope, so the answer is saved even though this screen closes right away.
            container.appScope.launch { container.doseActions.taken(current.profileId, selectedIds) }
            onClose()
        },
        onSnooze = { minutes ->
            container.appScope.launch {
                container.doseActions.snooze(current.profileId, current.logIds, minutes)
            }
            onClose()
        }
    )
}
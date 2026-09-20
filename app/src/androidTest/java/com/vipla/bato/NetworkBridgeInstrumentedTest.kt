package com.vipla.bato

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipla.bato.ai.HttpProviderBridge
import com.vipla.bato.ai.ProviderResult
import com.vipla.bato.ai.ProviderContext
import com.vipla.bato.data.StenoEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NetworkBridgeInstrumentedTest {
    @Test
    fun installedManifestAndLiveProviderPostWorkOnAndroid() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.packageManager.checkPermission(Manifest.permission.INTERNET, context.packageName)
        )

        val result = HttpProviderBridge("https://bato-sigma.vercel.app/api/bato")
            .respond("Odgovori samo: ANDROID BATO LIVE OK", ProviderContext(emptyList(), emptyList(), emptyList(), emptyList()))
        assertTrue("Expected a real provider response, got $result", result is ProviderResult.Response)
        assertTrue(
            "Unexpected provider text: $result",
            (result as ProviderResult.Response).text.contains("ANDROID BATO LIVE OK", ignoreCase = true)
        )

        val date = HttpProviderBridge("https://bato-sigma.vercel.app/api/bato")
            .respond("Koji je danas dan? U odgovoru obavezno navedi datum kao YYYY-MM-DD.", ProviderContext(emptyList(), emptyList(), emptyList(), emptyList()))
        assertTrue("Date request failed: $date", date is ProviderResult.Response)
        assertTrue("Backend date was not authoritative: $date", (date as ProviderResult.Response).text.contains(LocalDate.now().toString()))

        val prior = event("USER", "Moje test ime je ORION-742.", "STENO_FIRST:TEXT")
        val memory = HttpProviderBridge("https://bato-sigma.vercel.app/api/bato")
            .respond("Koje je moje test ime?", ProviderContext(listOf(prior), emptyList(), emptyList(), emptyList()))
        assertTrue("Conversation memory failed: $memory", memory is ProviderResult.Response && memory.text.contains("ORION-742"))

        val stored = event("USER", "Sačuvana Riznica činjenica: kod trezora je NEBULA-913.", "STENO_FIRST:TEXT")
        val retrieval = HttpProviderBridge("https://bato-sigma.vercel.app/api/bato")
            .respond("Koji je kod trezora?", ProviderContext(emptyList(), listOf(stored), emptyList(), emptyList()))
        assertTrue("STENO retrieval failed: $retrieval", retrieval is ProviderResult.Response && retrieval.text.contains("NEBULA-913"))
        assertTrue("Retrieval provenance missing: $retrieval", (retrieval as ProviderResult.Response).retrievalUsed && "STENO_RETRIEVAL" in retrieval.retrievalSources)
    }

    private fun event(role: String, text: String, provenance: String): StenoEvent {
        val now = System.currentTimeMillis()
        return StenoEvent(1, role, text, now, now, UUID.randomUUID().toString(), UUID.randomUUID().toString(), provenance)
    }
}

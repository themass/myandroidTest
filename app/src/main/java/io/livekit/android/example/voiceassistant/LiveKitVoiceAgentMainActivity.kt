package io.livekit.android.example.voiceassistant

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class LiveKitVoiceAgentMainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val fragment = LivekitVoiceAgentFragment()
            val transaction = supportFragmentManager.beginTransaction()
            transaction.add(android.R.id.content, fragment)
            transaction.commit()
        }
    }
}

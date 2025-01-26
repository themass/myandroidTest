package com.livekit.voiceassistant

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentTransaction
import io.livekit.android.annotations.Beta
import io.livekit.android.example.voiceassistant.R

class MainActivity : AppCompatActivity() {
    @OptIn(Beta::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requireToken { url, token ->
            val fragment = LivekitVoiceAgentFragment.newInstance(url, token)
            val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
            transaction.add(R.id.fragment_container, fragment)
            transaction.commit()
        }
    }
}

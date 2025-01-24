package test;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.openapi.ks.chat.R;

import org.firezenk.audiowaves.Visualizer;

public class AudioWavesMainActivity extends AppCompatActivity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_audiowaves_main);
            ((Visualizer) findViewById(R.id.visualizer)).startListening();
    }

}

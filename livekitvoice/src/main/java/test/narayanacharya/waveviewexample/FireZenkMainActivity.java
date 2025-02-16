package test.narayanacharya.waveviewexample;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.narayanacharya.waveview.WaveView;

import io.livekit.android.example.voiceassistant.R;

public class FireZenkMainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firezenk_main);

        final WaveView sine = (WaveView) findViewById(R.id.waveView);
        sine.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
        sine.setWaveColor(ContextCompat.getColor(this, R.color.colorAccent));

        sine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sine.isPlaying()) {
                    sine.pause();
                } else {
                    sine.play();
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_firezenk_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.next_menu) {
            Intent intent = new Intent(this, BuilderExampleActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

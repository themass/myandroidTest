package test.audiovisualizeview;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import io.livekit.android.example.voiceassistant.R;

public class AudioVisualizeViewMainActivity extends AppCompatActivity implements View.OnClickListener {

    private TextView vReflect, vCircle, vSingle, vWave, vNet, vGrain;
    private static final int RECORD_AUDIO = 10001;
    private static final int READ_EXTERNAL_STORAGE = 10002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audiovisualize_main);

        vReflect = findViewById(R.id.reflect_entrance);
        vCircle = findViewById(R.id.circle_entrance);
        vSingle = findViewById(R.id.single_entrance);
        vWave = findViewById(R.id.wave_entrance);
        vNet = findViewById(R.id.net_entrance);
        vGrain = findViewById(R.id.grain_entrance);
        vSingle.setOnClickListener(this);
        vReflect.setOnClickListener(this);
        vCircle.setOnClickListener(this);
        vWave.setOnClickListener(this);
        vNet.setOnClickListener(this);
        vGrain.setOnClickListener(this);

    }

    @Override
    public void onClick(View view) {
        Intent intent = null;
        int id = view.getId();
        if (id == R.id.single_entrance) {
            intent = new Intent(AudioVisualizeViewMainActivity.this, SingleVisualizeActivity.class);
        } else if (id == R.id.reflect_entrance) {
            intent = new Intent(AudioVisualizeViewMainActivity.this, ReflectVisualizeActivity.class);
        } else if (id == R.id.circle_entrance) {
            intent = new Intent(AudioVisualizeViewMainActivity.this, CircleVisualizeActivity.class);
        } else if (id == R.id.wave_entrance) {
            intent = new Intent(AudioVisualizeViewMainActivity.this, WaveVisualizeActivity.class);
        } else if (id == R.id.net_entrance) {
            intent = new Intent(AudioVisualizeViewMainActivity.this, NetVisualizeActivity.class);
        } else if (id == R.id.grain_entrance) {
            intent = new Intent(AudioVisualizeViewMainActivity.this, GrainVisualizeActivity.class);
        }
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case RECORD_AUDIO:
            case READ_EXTERNAL_STORAGE:
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
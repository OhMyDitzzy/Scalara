package id.ditzzy.scalara; 

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import id.ditzzy.scalara.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inflate and get instance of binding
        binding = ActivityMainBinding.inflate(getLayoutInflater());

        // set content view to binding's root
        setContentView(binding.getRoot());
        throw new RuntimeException("Scalara test crash — verifying CrashHandler captures uncaught exceptions");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.binding = null;
    }
}

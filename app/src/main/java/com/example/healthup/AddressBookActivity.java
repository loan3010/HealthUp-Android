package com.example.healthup;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.example.models.Address;

public class AddressBookActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_address_book);
        
        View container = findViewById(R.id.fragment_container);
        UIUtils.applyStatusBarInsets(container);

        if (savedInstanceState == null) {
            AddressBookFragment fragment = new AddressBookFragment();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, fragment)
                    .commit();
        }

        // Lắng nghe kết quả từ Fragment để trả về cho Activity gọi nó
        getSupportFragmentManager().setFragmentResultListener("address_result", this, (requestKey, result) -> {
            Address address = (Address) result.getSerializable("selected_address");
            if (address != null) {
                Intent data = new Intent();
                data.putExtra("selected_address", address);
                setResult(RESULT_OK, data);
                finish();
            }
        });
    }
}

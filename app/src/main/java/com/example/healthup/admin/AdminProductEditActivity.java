package com.example.healthup.admin;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.healthup.R;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.Product;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdminProductEditActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_ID = "product_id";

    private static final List<String> CATEGORIES = Arrays.asList(
            "Hạt dinh dưỡng", "Granola", "Trái cây sấy", "Đồ ăn vặt", "Trà thảo mộc", "Combo"
    );

    private final AdminRepository repository = new AdminRepository();
    private String productId;
    private boolean isEdit;
    private TextInputEditText etName, etPrice, etOriginalPrice, etStock, etImage, etShortDesc, etDescription;
    private Spinner spinnerCategory;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_product_edit);

        productId = getIntent().getStringExtra(EXTRA_PRODUCT_ID);
        isEdit = !TextUtils.isEmpty(productId);

        MaterialToolbar toolbar = findViewById(R.id.toolbarProductEdit);
        toolbar.setTitle(isEdit ? R.string.admin_edit_product : R.string.admin_add_product);
        toolbar.setNavigationOnClickListener(v -> finish());

        etName = findViewById(R.id.etProductName);
        etPrice = findViewById(R.id.etProductPrice);
        etOriginalPrice = findViewById(R.id.etProductOriginalPrice);
        etStock = findViewById(R.id.etProductStock);
        etImage = findViewById(R.id.etProductImage);
        etShortDesc = findViewById(R.id.etProductShortDesc);
        etDescription = findViewById(R.id.etProductDescription);
        spinnerCategory = findViewById(R.id.spinnerProductCategory);
        MaterialButton btnSave = findViewById(R.id.btnSaveProduct);

        spinnerCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, CATEGORIES));
        btnSave.setOnClickListener(v -> saveProduct());

        if (isEdit) {
            loadProduct();
        }
    }

    private void loadProduct() {
        FirestoreManager.getInstance().getProductsCollection().document(productId).get()
                .addOnSuccessListener(this::bindProduct)
                .addOnFailureListener(e -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void bindProduct(DocumentSnapshot doc) {
        Product product = Product.fromDocument(doc);
        if (product == null) return;
        etName.setText(product.getName());
        etPrice.setText(String.valueOf((long) product.getPrice()));
        etOriginalPrice.setText(String.valueOf((long) product.getOriginalPrice()));
        etStock.setText(String.valueOf(product.getStock()));
        etShortDesc.setText(product.getShortDesc());
        etDescription.setText(product.getDescription());
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            String image = product.getImages().get(0);
            if (image.contains("/")) {
                image = image.substring(image.lastIndexOf('/') + 1);
            }
            etImage.setText(image);
        }
        int index = CATEGORIES.indexOf(product.getCategory());
        if (index >= 0) spinnerCategory.setSelection(index);
    }

    private void saveProduct() {
        String name = textOf(etName);
        String priceText = textOf(etPrice);
        String stockText = textOf(etStock);
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(priceText)) {
            Toast.makeText(this, "Vui lòng nhập tên và giá", Toast.LENGTH_SHORT).show();
            return;
        }

        Product product = new Product();
        if (isEdit) product.setId(productId);
        product.setName(name);
        product.setPrice(Double.parseDouble(priceText));
        String originalPriceText = textOf(etOriginalPrice);
        product.setOriginalPrice(TextUtils.isEmpty(originalPriceText) ? product.getPrice() : Double.parseDouble(originalPriceText));
        product.setStock(TextUtils.isEmpty(stockText) ? 0 : Integer.parseInt(stockText));
        product.setCat((String) spinnerCategory.getSelectedItem());
        product.setShortDesc(textOf(etShortDesc));
        product.setDescription(textOf(etDescription));

        String imageName = textOf(etImage);
        List<String> images = new ArrayList<>();
        if (!TextUtils.isEmpty(imageName)) {
            images.add(imageName);
        }
        product.setImages(images);

        repository.saveProduct(product, !isEdit, new AdminRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(AdminProductEditActivity.this, R.string.admin_saved, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(AdminProductEditActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String textOf(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }
}

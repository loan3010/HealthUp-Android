package com.example.healthup.admin;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.healthup.FirebaseManager;
import com.example.healthup.R;
import com.example.healthup.firebase.FirestoreManager;
import com.example.healthup.util.ImageLoadHelper;
import com.example.models.Product;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
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
    private final List<Product.ProductVariant> variantRows = new ArrayList<>();

    private String productId;
    private boolean isEdit;
    private boolean isHidden;
    private boolean isDraft;

    private TextInputEditText etName, etPrice, etOriginalPrice, etStock, etImage, etShortDesc, etDescription;
    private TextInputEditText etProductCode;
    private TextInputEditText etIngredients, etNutrition, etUsage, etOrigin;
    private TextInputEditText etVariantFlavors, etVariantSizes;
    private TextView tvProductSoldReadonly, tvProductRatingReadonly;
    private View cardProductPreview;
    private ImageView imgPreview;
    private Spinner spinnerCategory;
    private SwitchMaterial switchHidden;
    private LinearLayout layoutVariantRows;
    private TextView tvVariantEmpty;
    private MaterialButton btnPickProductImage;

    private String productImageUrl = "";
    private boolean imageProcessing;
    private AdminProductImagePicker imagePicker;

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
        etProductCode = findViewById(R.id.etProductCode);
        etPrice = findViewById(R.id.etProductPrice);
        etOriginalPrice = findViewById(R.id.etProductOriginalPrice);
        etStock = findViewById(R.id.etProductStock);
        etImage = findViewById(R.id.etProductImage);
        etShortDesc = findViewById(R.id.etProductShortDesc);
        etDescription = findViewById(R.id.etProductDescription);
        etIngredients = findViewById(R.id.etProductIngredients);
        etNutrition = findViewById(R.id.etProductNutrition);
        etUsage = findViewById(R.id.etProductUsage);
        etOrigin = findViewById(R.id.etProductOrigin);
        etVariantFlavors = findViewById(R.id.etVariantFlavors);
        etVariantSizes = findViewById(R.id.etVariantSizes);
        tvProductSoldReadonly = findViewById(R.id.tvProductSoldReadonly);
        tvProductRatingReadonly = findViewById(R.id.tvProductRatingReadonly);
        cardProductPreview = findViewById(R.id.cardProductPreview);
        imgPreview = findViewById(R.id.imgProductPreview);
        spinnerCategory = findViewById(R.id.spinnerProductCategory);
        switchHidden = findViewById(R.id.switchProductHidden);
        layoutVariantRows = findViewById(R.id.layoutVariantRows);
        tvVariantEmpty = findViewById(R.id.tvVariantEmpty);
        MaterialButton btnSave = findViewById(R.id.btnSaveProduct);
        MaterialButton btnSaveDraft = findViewById(R.id.btnSaveDraft);
        btnPickProductImage = findViewById(R.id.btnPickProductImage);
        MaterialButton btnGenerateVariants = findViewById(R.id.btnGenerateVariants);

        imagePicker = new AdminProductImagePicker(this);

        spinnerCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, CATEGORIES));
        btnSave.setOnClickListener(v -> saveProduct(false));
        btnSaveDraft.setOnClickListener(v -> saveProduct(true));
        btnPickProductImage.setOnClickListener(v -> pickProductImage());
        btnGenerateVariants.setOnClickListener(v -> generateVariantCombos());

        if (isEdit) {
            loadProduct();
        } else {
            updateReadonlyStats(0, 0f, 0);
            updateVariantSectionVisibility();
            updatePreview();
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
        isHidden = product.isHidden();
        isDraft = product.isDraft();
        switchHidden.setChecked(isHidden);
        switchHidden.setOnCheckedChangeListener((buttonView, isChecked) -> isHidden = isChecked);
        etName.setText(product.getName());
        if (!TextUtils.isEmpty(product.getProductCode())) {
            etProductCode.setText(product.getProductCode());
        } else {
            etProductCode.setText(R.string.admin_product_code_pending);
        }
        etPrice.setText(String.valueOf((long) product.getPrice()));
        etOriginalPrice.setText(String.valueOf((long) product.getOriginalPrice()));
        etStock.setText(String.valueOf(product.getStock()));
        etShortDesc.setText(product.getShortDesc());
        etDescription.setText(product.getDescription());
        etIngredients.setText(product.getIngredients());
        etUsage.setText(product.getUsage());
        etOrigin.setText(product.getOrigin());
        etNutrition.setText(AdminProductContentHelper.formatNutritionForEdit(product, doc));
        updateReadonlyStats(product.getSoldCount(), product.getRating(), product.getReviewCount());
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            productImageUrl = product.getImages().get(0);
            etImage.setText(shortImageLabel(productImageUrl));
            updatePreview();
        }
        int index = CATEGORIES.indexOf(product.getCategory());
        if (index >= 0) spinnerCategory.setSelection(index);

        if (product.getVariants() != null && !product.getVariants().isEmpty()) {
            variantRows.clear();
            variantRows.addAll(product.getVariants());
            inferOptionInputsFromVariants();
            renderVariantRows();
        }
    }

    private void updateReadonlyStats(int sold, float rating, int reviewCount) {
        if (tvProductSoldReadonly != null) {
            tvProductSoldReadonly.setText(getString(R.string.admin_product_sold_readonly, sold));
        }
        if (tvProductRatingReadonly != null) {
            if (reviewCount > 0 || rating > 0) {
                tvProductRatingReadonly.setText(getString(R.string.admin_product_rating_readonly, rating, reviewCount));
            } else {
                tvProductRatingReadonly.setText(R.string.admin_product_rating_empty);
            }
        }
    }

    private void inferOptionInputsFromVariants() {
        List<String> flavors = new ArrayList<>();
        List<String> sizes = new ArrayList<>();
        for (Product.ProductVariant variant : variantRows) {
            String name = variant.getName();
            if (name == null || !name.contains(" · ")) continue;
            String[] parts = name.split(" · ");
            if (parts.length != 2) continue;
            if (!flavors.contains(parts[0].trim())) flavors.add(parts[0].trim());
            if (!sizes.contains(parts[1].trim())) sizes.add(parts[1].trim());
        }
        if (!flavors.isEmpty()) {
            etVariantFlavors.setText(TextUtils.join(", ", flavors));
        }
        if (!sizes.isEmpty()) {
            etVariantSizes.setText(TextUtils.join(", ", sizes));
        }
    }

    private void generateVariantCombos() {
        List<String> flavors = AdminVariantComboHelper.parseOptionList(textOf(etVariantFlavors));
        List<String> sizes = AdminVariantComboHelper.parseOptionList(textOf(etVariantSizes));
        if (flavors.isEmpty() && sizes.isEmpty()) {
            Toast.makeText(this, R.string.admin_variant_no_combos, Toast.LENGTH_SHORT).show();
            return;
        }

        double basePrice = parseDouble(textOf(etPrice), 0);
        double baseOriginal = parseDouble(textOf(etOriginalPrice), basePrice);
        int baseStock = parseInt(textOf(etStock), 0);
        List<Product.ProductVariant> existing = new ArrayList<>(variantRows);
        List<Product.ProductVariant> generated = AdminVariantComboHelper.generateCombinations(
                flavors, sizes, basePrice, baseOriginal, baseStock);
        variantRows.clear();
        variantRows.addAll(AdminVariantComboHelper.mergeWithExisting(generated, existing));

        String productName = textOf(etName);
        for (int i = 0; i < variantRows.size(); i++) {
            Product.ProductVariant variant = variantRows.get(i);
            if (TextUtils.isEmpty(variant.getSku())) {
                variant.setSku(AdminVariantComboHelper.suggestSku(productName, variant.getName(), i));
            }
        }
        renderVariantRows();
    }

    private void renderVariantRows() {
        layoutVariantRows.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < variantRows.size(); i++) {
            Product.ProductVariant variant = variantRows.get(i);
            View row = inflater.inflate(R.layout.item_admin_variant_row, layoutVariantRows, false);
            TextView tvName = row.findViewById(R.id.tvVariantComboName);
            TextInputEditText etVariantPrice = row.findViewById(R.id.etVariantPrice);
            TextInputEditText etVariantStock = row.findViewById(R.id.etVariantStock);
            TextInputEditText etVariantSku = row.findViewById(R.id.etVariantSku);
            TextInputEditText etVariantImage = row.findViewById(R.id.etVariantImage);
            ImageView imgVariantPreview = row.findViewById(R.id.imgVariantPreview);
            MaterialButton btnVariantPickImage = row.findViewById(R.id.btnVariantPickImage);

            tvName.setText(variant.getName());
            etVariantPrice.setText(String.valueOf((long) variant.getPrice()));
            etVariantStock.setText(String.valueOf(variant.getStock()));
            if (!TextUtils.isEmpty(variant.getSku())) {
                etVariantSku.setText(variant.getSku());
            }
            if (!TextUtils.isEmpty(variant.getImageUrl())) {
                etVariantImage.setText(variant.getImageUrl());
                ImageLoadHelper.loadInto(imgVariantPreview, variant.getImageUrl());
            }

            final int rowIndex = i;
            btnVariantPickImage.setOnClickListener(v -> pickVariantImage(rowIndex, etVariantImage, imgVariantPreview));

            layoutVariantRows.addView(row);
        }
        updateVariantSectionVisibility();
    }

    private void updateVariantSectionVisibility() {
        tvVariantEmpty.setVisibility(variantRows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void collectVariantsFromUi() {
        for (int i = 0; i < layoutVariantRows.getChildCount(); i++) {
            View row = layoutVariantRows.getChildAt(i);
            if (i >= variantRows.size()) break;
            Product.ProductVariant variant = variantRows.get(i);
            TextInputEditText etVariantPrice = row.findViewById(R.id.etVariantPrice);
            TextInputEditText etVariantStock = row.findViewById(R.id.etVariantStock);
            TextInputEditText etVariantSku = row.findViewById(R.id.etVariantSku);
            TextInputEditText etVariantImage = row.findViewById(R.id.etVariantImage);
            variant.setPrice(parseDouble(textOf(etVariantPrice), variant.getPrice()));
            variant.setOriginalPrice(variant.getPrice());
            variant.setStock(parseInt(textOf(etVariantStock), variant.getStock()));
            variant.setSku(textOf(etVariantSku));
            // Image URL is set only via picker into variantRows — do not overwrite with short label text.
        }
    }

    private void pickProductImage() {
        if (imageProcessing) return;
        showImageSourceMenu((imageUrl, previewView, triggerButton) -> {
            productImageUrl = imageUrl;
            etImage.setText(shortImageLabel(imageUrl));
            updatePreview();
        }, imgPreview, btnPickProductImage);
    }

    private void pickVariantImage(int rowIndex,
                                    TextInputEditText etVariantImage,
                                    ImageView imgVariantPreview) {
        if (imageProcessing) return;
        showImageSourceMenu((imageUrl, previewView, triggerButton) -> {
            if (rowIndex < variantRows.size()) {
                variantRows.get(rowIndex).setImageUrl(imageUrl);
            }
            etVariantImage.setText(shortImageLabel(imageUrl));
            ImageLoadHelper.loadInto(imgVariantPreview, imageUrl);
        }, imgVariantPreview, null);
    }

    private void showImageSourceMenu(@NonNull ImageApplyCallback callback,
                                     @NonNull ImageView previewView,
                                     @Nullable MaterialButton triggerButton) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_pick_image)
                .setItems(new CharSequence[]{
                        getString(R.string.admin_pick_image_assets),
                        getString(R.string.admin_pick_image_device)
                }, (dialog, which) -> {
                    if (which == 0) {
                        showAssetImagePicker(imageUrl -> callback.onApply(imageUrl, previewView, triggerButton));
                    } else {
                        imagePicker.setListener(localUri -> processImageUri(localUri, previewView, url ->
                                callback.onApply(url, previewView, triggerButton), triggerButton));
                        imagePicker.showSourceChooser();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showAssetImagePicker(@NonNull ImageUrlCallback callback) {
        List<String> images = AdminAssetImageHelper.listProductImages(this);
        if (images.isEmpty()) {
            Toast.makeText(this, R.string.admin_pick_image_assets_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_image_picker, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.rvAssetImages);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.admin_pick_image_assets)
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        recyclerView.setAdapter(new AssetImageAdapter(images, fileName -> {
            callback.onReady(fileName);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void processImageUri(@NonNull Uri localUri,
                                 @NonNull ImageView previewView,
                                 @NonNull ImageUrlCallback callback,
                                 @Nullable MaterialButton triggerButton) {
        imageProcessing = true;
        if (triggerButton != null) {
            triggerButton.setEnabled(false);
        }
        Toast.makeText(this, R.string.admin_image_processing, Toast.LENGTH_SHORT).show();
        Glide.with(this).load(localUri).centerCrop().into(previewView);

        FirebaseManager.getInstance().uploadImage(localUri)
                .addOnSuccessListener(downloadUri -> {
                    imageProcessing = false;
                    if (triggerButton != null) {
                        triggerButton.setEnabled(true);
                    }
                    callback.onReady(downloadUri.toString());
                })
                .addOnFailureListener(e -> {
                    imageProcessing = false;
                    if (triggerButton != null) {
                        triggerButton.setEnabled(true);
                    }
                    Toast.makeText(this,
                            getString(R.string.admin_image_process_error) + ": " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void updatePreview() {
        if (cardProductPreview != null) {
            cardProductPreview.setVisibility(TextUtils.isEmpty(productImageUrl) ? View.GONE : View.VISIBLE);
        }
        if (TextUtils.isEmpty(productImageUrl)) {
            if (imgPreview != null) {
                imgPreview.setImageDrawable(null);
            }
            return;
        }
        ImageLoadHelper.loadInto(imgPreview, productImageUrl);
    }

    private static String shortImageLabel(@Nullable String imageUrl) {
        if (TextUtils.isEmpty(imageUrl)) return "";
        if (imageUrl.startsWith("data:")) return "Ảnh đã chọn";
        if (imageUrl.length() > 48) {
            return imageUrl.substring(0, 45) + "…";
        }
        return imageUrl;
    }

    private void saveProduct(boolean asDraft) {
        String name = textOf(etName);
        String priceText = textOf(etPrice);
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(priceText)) {
            Toast.makeText(this, "Vui lòng nhập tên và giá", Toast.LENGTH_SHORT).show();
            return;
        }
        if (imageProcessing) {
            Toast.makeText(this, R.string.admin_image_processing, Toast.LENGTH_SHORT).show();
            return;
        }

        collectVariantsFromUi();

        Product product = new Product();
        if (isEdit) product.setId(productId);
        product.setDraft(asDraft);
        product.setHidden(asDraft ? false : isHidden);
        product.setName(name);
        product.setPrice(Double.parseDouble(priceText));
        String originalPriceText = textOf(etOriginalPrice);
        product.setOriginalPrice(TextUtils.isEmpty(originalPriceText) ? product.getPrice() : Double.parseDouble(originalPriceText));

        if (!variantRows.isEmpty()) {
            product.setVariants(new ArrayList<>(variantRows));
            product.setHasVariants(true);
            int totalStock = 0;
            for (Product.ProductVariant variant : variantRows) {
                totalStock += Math.max(0, variant.getStock());
            }
            product.setStock(totalStock);
        } else {
            String stockText = textOf(etStock);
            product.setStock(TextUtils.isEmpty(stockText) ? 0 : Integer.parseInt(stockText));
        }

        product.setCat((String) spinnerCategory.getSelectedItem());
        product.setShortDesc(textOf(etShortDesc));
        product.setDescription(textOf(etDescription));
        product.setIngredients(textOf(etIngredients));
        product.setUsage(textOf(etUsage));
        product.setOrigin(textOf(etOrigin));
        product.setNutritionText(textOf(etNutrition));

        String imageName = productImageUrl;
        List<String> images = new ArrayList<>();
        if (!TextUtils.isEmpty(imageName)) {
            images.add(imageName);
        }
        product.setImages(images);

        AdminProductCodeHelper.ensureProductCode(
                FirestoreManager.getInstance().getFirestore(),
                product.getProductCode()
        ).addOnSuccessListener(code -> {
            product.setProductCode(code);
            if (etProductCode != null) {
                etProductCode.setText(code);
            }
            persistProduct(product, asDraft);
        }).addOnFailureListener(e ->
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void persistProduct(Product product, boolean asDraft) {
        repository.saveProduct(product, !isEdit, new AdminRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(AdminProductEditActivity.this,
                        asDraft ? R.string.admin_draft_saved : R.string.admin_saved, Toast.LENGTH_SHORT).show();
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

    private double parseDouble(String value, double fallback) {
        if (TextUtils.isEmpty(value)) return fallback;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int parseInt(String value, int fallback) {
        if (TextUtils.isEmpty(value)) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private interface ImageUrlCallback {
        void onReady(@NonNull String imageUrl);
    }

    private interface ImageApplyCallback {
        void onApply(@NonNull String imageUrl,
                     @NonNull ImageView previewView,
                     @Nullable MaterialButton triggerButton);
    }

    private static class AssetImageAdapter extends RecyclerView.Adapter<AssetImageAdapter.ViewHolder> {

        interface Listener {
            void onPick(String fileName);
        }

        private final List<String> images;
        private final Listener listener;

        AssetImageAdapter(List<String> images, Listener listener) {
            this.images = images;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_admin_asset_image, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String fileName = images.get(position);
            holder.tvName.setText(fileName);
            ImageLoadHelper.loadInto(holder.img, fileName);
            holder.itemView.setOnClickListener(v -> listener.onPick(fileName));
        }

        @Override
        public int getItemCount() {
            return images.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView img;
            TextView tvName;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                img = itemView.findViewById(R.id.imgAssetPreview);
                tvName = itemView.findViewById(R.id.tvAssetName);
            }
        }
    }
}

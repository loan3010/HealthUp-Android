package com.example.healthup.admin;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.example.healthup.BaseAppCompatActivity;
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

public class AdminProductEditActivity extends BaseAppCompatActivity {

    public static final String EXTRA_PRODUCT_ID = "product_id";

    private static final List<String> CATEGORIES = Arrays.asList(
            "Hạt dinh dưỡng", "Granola", "Trái cây sấy", "Đồ ăn vặt", "Trà thảo mộc", "Combo"
    );

    private final AdminRepository repository = new AdminRepository();
    private final List<Product.ProductVariant> variantRows = new ArrayList<>();
    private final List<Product.VariantDimension> dimensionDrafts = new ArrayList<>();

    private String productId;
    private boolean isEdit;
    private boolean isHidden;
    private boolean isDraft;
    private int loadedReviewCount;
    private String loadedProductCode = "";

    private TextInputEditText etName, etPrice, etOriginalPrice, etStock, etImage, etDescription;
    private TextInputEditText etProductCode;
    private TextInputEditText etIngredients, etNutrition, etUsage, etOrigin;
    private TextView tvProductSoldReadonly, tvProductRatingReadonly, tvVariantTotals;
    private View cardProductPreview;
    private ImageView imgPreview;
    private ImageButton btnRemoveProductImage;
    private Spinner spinnerCategory;
    private SwitchMaterial switchHidden;
    private LinearLayout layoutVariantRows;
    private LinearLayout layoutVariantDimensions;
    private TextView tvVariantEmpty;
    private View tilProductStock;
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
        tilProductStock = findViewById(R.id.tilProductStock);
        etImage = findViewById(R.id.etProductImage);
        etDescription = findViewById(R.id.etProductDescription);
        etIngredients = findViewById(R.id.etProductIngredients);
        etNutrition = findViewById(R.id.etProductNutrition);
        etUsage = findViewById(R.id.etProductUsage);
        etOrigin = findViewById(R.id.etProductOrigin);
        tvProductSoldReadonly = findViewById(R.id.tvProductSoldReadonly);
        tvProductRatingReadonly = findViewById(R.id.tvProductRatingReadonly);
        tvVariantTotals = findViewById(R.id.tvVariantTotals);
        cardProductPreview = findViewById(R.id.cardProductPreview);
        imgPreview = findViewById(R.id.imgProductPreview);
        btnRemoveProductImage = findViewById(R.id.btnRemoveProductImage);
        spinnerCategory = findViewById(R.id.spinnerProductCategory);
        switchHidden = findViewById(R.id.switchProductHidden);
        layoutVariantRows = findViewById(R.id.layoutVariantRows);
        layoutVariantDimensions = findViewById(R.id.layoutVariantDimensions);
        tvVariantEmpty = findViewById(R.id.tvVariantEmpty);
        MaterialButton btnSave = findViewById(R.id.btnSaveProduct);
        MaterialButton btnSaveDraft = findViewById(R.id.btnSaveDraft);
        btnPickProductImage = findViewById(R.id.btnPickProductImage);
        MaterialButton btnGenerateVariants = findViewById(R.id.btnGenerateVariants);
        MaterialButton btnAddVariantDimension = findViewById(R.id.btnAddVariantDimension);
        MaterialButton btnSortVariantsBySold = findViewById(R.id.btnSortVariantsBySold);

        imagePicker = new AdminProductImagePicker(this);

        spinnerCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, CATEGORIES));
        btnSave.setOnClickListener(v -> saveProduct(false));
        btnSaveDraft.setOnClickListener(v -> saveProduct(true));
        btnPickProductImage.setOnClickListener(v -> pickProductImage());
        if (btnRemoveProductImage != null) {
            btnRemoveProductImage.setOnClickListener(v -> clearProductImage());
        }
        btnGenerateVariants.setOnClickListener(v -> generateVariantCombos());
        if (btnAddVariantDimension != null) {
            btnAddVariantDimension.setOnClickListener(v -> addDimensionDraft(null));
        }
        if (btnSortVariantsBySold != null) {
            btnSortVariantsBySold.setOnClickListener(v -> sortVariantRowsBySold());
        }

        if (isEdit) {
            loadProduct();
        } else {
            updateReadonlyStats(0, 0f, 0);
            ensureDefaultDimensions();
            renderDimensionRows();
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
        loadedProductCode = product.getProductCode() != null ? product.getProductCode().trim() : "";
        if (!TextUtils.isEmpty(loadedProductCode)) {
            etProductCode.setText(loadedProductCode);
        } else {
            etProductCode.setText(R.string.admin_product_code_pending);
        }
        etPrice.setText(String.valueOf((long) product.getPrice()));
        etOriginalPrice.setText(String.valueOf((long) product.getOriginalPrice()));
        etStock.setText(String.valueOf(product.getStock()));
        etDescription.setText(product.getDescription());
        etIngredients.setText(product.getIngredients());
        etUsage.setText(product.getUsage());
        etOrigin.setText(product.getOrigin());
        etNutrition.setText(AdminProductContentHelper.formatNutritionForEdit(product, doc));
        loadedReviewCount = Math.max(0, product.getReviewCount());
        updateReadonlyStats(product.getTotalSold(), product.getRating(), loadedReviewCount);
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            productImageUrl = product.getImages().get(0);
            etImage.setText(shortImageLabel(productImageUrl));
            updatePreview();
        }
        int index = CATEGORIES.indexOf(product.getCategory());
        if (index >= 0) spinnerCategory.setSelection(index);

        variantRows.clear();
        if (product.getVariants() != null) {
            variantRows.addAll(product.getVariants());
        }

        dimensionDrafts.clear();
        hydrateDimensionsForEdit(product);

        renderDimensionRows();
        renderVariantRows();
    }

    /**
     * Rebuild dimension UI so it matches actual SKU rows.
     * Never wipe variantRows (would lose price/stock of old products).
     */
    private void hydrateDimensionsForEdit(@NonNull Product product) {
        List<Product.VariantDimension> dims = product.resolveVariantDimensions();
        boolean aligned = AdminVariantComboHelper.dimensionsAlignWithVariantRows(dims, variantRows);

        if (!dims.isEmpty() && aligned) {
            for (Product.VariantDimension dim : dims) {
                Product.VariantDimension copy = new Product.VariantDimension();
                copy.setId(dim.getId());
                copy.setName(dim.getName());
                copy.setOptions(dim.getOptions() != null
                        ? new ArrayList<>(dim.getOptions()) : new ArrayList<>());
                dimensionDrafts.add(copy);
            }
            return;
        }

        // Rebuild from SKU names — flat SKUs become one "Phân loại" group.
        List<Product.VariantDimension> rebuilt =
                AdminVariantComboHelper.rebuildDimensionsFromSkus(variantRows);
        if (!rebuilt.isEmpty()) {
            dimensionDrafts.addAll(rebuilt);
            Toast.makeText(this,
                    "Đã khớp lại nhóm phân loại theo biến thể đang có. "
                            + "Kiểm tra rồi bấm Lưu (hoặc Tạo tổ hợp nếu cần nhóm mới).",
                    Toast.LENGTH_LONG).show();
            return;
        }

        ensureDefaultDimensions();
    }

    private void ensureDefaultDimensions() {
        if (!dimensionDrafts.isEmpty()) return;
        addDimensionDraft(dimDraft("Khối lượng", ""));
        addDimensionDraft(dimDraft("Loại đóng gói", ""));
    }

    private Product.VariantDimension dimDraft(String name, String optionsCsv) {
        Product.VariantDimension dim = new Product.VariantDimension();
        dim.setId("dim_" + dimensionDrafts.size());
        dim.setName(name);
        dim.setOptions(AdminVariantComboHelper.parseOptionList(optionsCsv));
        return dim;
    }

    private void addDimensionDraft(@Nullable Product.VariantDimension existing) {
        collectDimensionsFromUi();
        if (dimensionDrafts.size() >= AdminVariantComboHelper.MAX_DIMENSIONS) {
            Toast.makeText(this, "Tối đa 3 nhóm phân loại", Toast.LENGTH_SHORT).show();
            return;
        }
        if (existing != null) {
            dimensionDrafts.add(existing);
        } else {
            Product.VariantDimension dim = new Product.VariantDimension();
            dim.setId("dim_" + System.currentTimeMillis());
            dim.setName("");
            dim.setOptions(new ArrayList<>());
            dimensionDrafts.add(dim);
        }
        renderDimensionRows();
    }

    private void collectDimensionsFromUi() {
        if (layoutVariantDimensions == null) return;
        for (int i = 0; i < layoutVariantDimensions.getChildCount() && i < dimensionDrafts.size(); i++) {
            View row = layoutVariantDimensions.getChildAt(i);
            TextInputEditText etName = row.findViewById(R.id.etDimensionName);
            TextInputEditText etOptions = row.findViewById(R.id.etDimensionOptions);
            Product.VariantDimension dim = dimensionDrafts.get(i);
            dim.setName(textOf(etName));
            dim.setOptions(AdminVariantComboHelper.parseOptionList(textOf(etOptions)));
        }
    }

    private void renderDimensionRows() {
        if (layoutVariantDimensions == null) return;
        layoutVariantDimensions.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < dimensionDrafts.size(); i++) {
            Product.VariantDimension dim = dimensionDrafts.get(i);
            View row = inflater.inflate(R.layout.item_admin_variant_dimension, layoutVariantDimensions, false);
            TextView tvIndex = row.findViewById(R.id.tvDimensionIndex);
            TextInputEditText etDimName = row.findViewById(R.id.etDimensionName);
            TextInputEditText etDimOptions = row.findViewById(R.id.etDimensionOptions);
            ImageButton btnRemove = row.findViewById(R.id.btnRemoveDimension);
            tvIndex.setText("Nhóm " + (i + 1));
            etDimName.setText(dim.getName() != null ? dim.getName() : "");
            etDimOptions.setText(dim.getOptions() != null ? TextUtils.join(", ", dim.getOptions()) : "");
            final int rowIndex = i;
            btnRemove.setOnClickListener(v -> {
                collectDimensionsFromUi();
                if (rowIndex < dimensionDrafts.size()) {
                    dimensionDrafts.remove(rowIndex);
                    renderDimensionRows();
                }
            });
            layoutVariantDimensions.addView(row);
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

    private void generateVariantCombos() {
        collectDimensionsFromUi();
        List<Product.VariantDimension> usable = new ArrayList<>();
        for (Product.VariantDimension dim : dimensionDrafts) {
            if (dim.getName() == null || dim.getName().trim().isEmpty()) continue;
            if (dim.getOptions() == null || dim.getOptions().isEmpty()) continue;
            usable.add(dim);
        }
        if (usable.isEmpty()) {
            Toast.makeText(this, R.string.admin_variant_no_combos, Toast.LENGTH_SHORT).show();
            return;
        }

        double basePrice = parseDouble(textOf(etPrice), 0);
        double baseOriginal = parseDouble(textOf(etOriginalPrice), basePrice);
        List<Product.ProductVariant> existing = new ArrayList<>(variantRows);
        List<Product.ProductVariant> generated = AdminVariantComboHelper.generateCombinations(
                usable, basePrice, baseOriginal);
        variantRows.clear();
        variantRows.addAll(AdminVariantComboHelper.mergeWithExisting(generated, existing));

        String productName = textOf(etName);
        for (int i = 0; i < variantRows.size(); i++) {
            Product.ProductVariant variant = variantRows.get(i);
            if (TextUtils.isEmpty(variant.getSku())) {
                variant.setSku(AdminVariantComboHelper.suggestSku(productName, variant.getName(), i));
            }
        }
        Toast.makeText(this,
                "Đã tạo " + variantRows.size() + " tổ hợp. Tắt «Đang bán» với tổ hợp không có thật.",
                Toast.LENGTH_LONG).show();
        renderVariantRows();
    }

    private void sortVariantRowsBySold() {
        collectVariantsFromUi();
        variantRows.sort((a, b) -> Integer.compare(b.getSold(), a.getSold()));
        renderVariantRows();
    }

    private void renderVariantRows() {
        layoutVariantRows.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int totalStock = 0;
        int totalSold = 0;
        for (int i = 0; i < variantRows.size(); i++) {
            Product.ProductVariant variant = variantRows.get(i);
            View row = inflater.inflate(R.layout.item_admin_variant_row, layoutVariantRows, false);
            TextView tvName = row.findViewById(R.id.tvVariantComboName);
            TextView tvSold = row.findViewById(R.id.tvVariantSold);
            SwitchMaterial switchEnabled = row.findViewById(R.id.switchVariantEnabled);
            TextInputEditText etVariantPrice = row.findViewById(R.id.etVariantPrice);
            TextInputEditText etVariantStock = row.findViewById(R.id.etVariantStock);
            TextInputEditText etVariantSku = row.findViewById(R.id.etVariantSku);
            TextInputEditText etVariantImage = row.findViewById(R.id.etVariantImage);
            ImageView imgVariantPreview = row.findViewById(R.id.imgVariantPreview);
            MaterialButton btnVariantPickImage = row.findViewById(R.id.btnVariantPickImage);
            ImageButton btnRemoveVariantCombo = row.findViewById(R.id.btnRemoveVariantCombo);
            ImageButton btnRemoveVariantImage = row.findViewById(R.id.btnRemoveVariantImage);

            tvName.setText(variant.getName());
            if (tvSold != null) {
                tvSold.setText(getString(R.string.admin_variant_sold_label, variant.getSold()));
            }
            if (switchEnabled != null) {
                switchEnabled.setChecked(variant.isEnabled());
                final int enabledIndex = i;
                switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (enabledIndex < variantRows.size()) {
                        variantRows.get(enabledIndex).setEnabled(isChecked);
                        updateVariantTotals();
                    }
                });
            }
            etVariantPrice.setText(String.valueOf((long) variant.getPrice()));
            etVariantStock.setText(String.valueOf(variant.getStock()));
            if (!TextUtils.isEmpty(variant.getSku())) {
                etVariantSku.setText(variant.getSku());
            }
            if (!TextUtils.isEmpty(variant.getImageUrl())) {
                etVariantImage.setText(variant.getImageUrl());
                ImageLoadHelper.loadInto(imgVariantPreview, variant.getImageUrl());
                btnRemoveVariantImage.setVisibility(View.VISIBLE);
            } else {
                btnRemoveVariantImage.setVisibility(View.GONE);
            }

            if (variant.isEnabled()) {
                totalStock += Math.max(0, variant.getStock());
            }
            totalSold += Math.max(0, variant.getSold());

            final int rowIndex = i;
            btnVariantPickImage.setOnClickListener(v -> pickVariantImage(rowIndex, etVariantImage, imgVariantPreview, btnRemoveVariantImage));
            btnRemoveVariantCombo.setOnClickListener(v -> confirmRemoveVariantCombo(rowIndex));
            btnRemoveVariantImage.setOnClickListener(v -> clearVariantImage(rowIndex, etVariantImage, imgVariantPreview, btnRemoveVariantImage));

            layoutVariantRows.addView(row);
        }
        if (tvVariantTotals != null) {
            tvVariantTotals.setText(getString(R.string.admin_variant_totals, totalStock, totalSold));
        }
        updateVariantSectionVisibility();
    }

    private void updateVariantTotals() {
        int totalStock = 0;
        int totalSold = 0;
        for (Product.ProductVariant variant : variantRows) {
            if (variant.isEnabled()) totalStock += Math.max(0, variant.getStock());
            totalSold += Math.max(0, variant.getSold());
        }
        if (tvVariantTotals != null) {
            tvVariantTotals.setText(getString(R.string.admin_variant_totals, totalStock, totalSold));
        }
    }

    private void updateVariantSectionVisibility() {
        tvVariantEmpty.setVisibility(variantRows.isEmpty() ? View.VISIBLE : View.GONE);
        if (tilProductStock != null) {
            tilProductStock.setVisibility(variantRows.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void collectVariantsFromUi() {
        for (int i = 0; i < layoutVariantRows.getChildCount(); i++) {
            View row = layoutVariantRows.getChildAt(i);
            if (i >= variantRows.size()) break;
            Product.ProductVariant variant = variantRows.get(i);
            TextInputEditText etVariantPrice = row.findViewById(R.id.etVariantPrice);
            TextInputEditText etVariantStock = row.findViewById(R.id.etVariantStock);
            TextInputEditText etVariantSku = row.findViewById(R.id.etVariantSku);
            SwitchMaterial switchEnabled = row.findViewById(R.id.switchVariantEnabled);
            variant.setPrice(parseDouble(textOf(etVariantPrice), 0));
            variant.setOriginalPrice(variant.getPrice());
            variant.setStock(parseInt(textOf(etVariantStock), 0));
            variant.setSku(textOf(etVariantSku));
            if (switchEnabled != null) {
                variant.setEnabled(switchEnabled.isChecked());
            }
        }
    }

    private void confirmRemoveVariantCombo(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= variantRows.size()) return;
        String name = variantRows.get(rowIndex).getName();
        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_remove_variant_combo)
                .setMessage(name != null ? name : "")
                .setPositiveButton(R.string.admin_confirm, (d, w) -> {
                    variantRows.remove(rowIndex);
                    renderVariantRows();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void clearProductImage() {
        productImageUrl = "";
        if (etImage != null) {
            etImage.setText("");
        }
        updatePreview();
    }

    private void clearVariantImage(int rowIndex,
                                   TextInputEditText etVariantImage,
                                   ImageView imgVariantPreview,
                                   ImageButton btnRemoveVariantImage) {
        if (rowIndex < variantRows.size()) {
            variantRows.get(rowIndex).setImageUrl(null);
        }
        etVariantImage.setText("");
        imgVariantPreview.setImageDrawable(null);
        btnRemoveVariantImage.setVisibility(View.GONE);
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
                                    ImageView imgVariantPreview,
                                    ImageButton btnRemoveVariantImage) {
        if (imageProcessing) return;
        showImageSourceMenu((imageUrl, previewView, triggerButton) -> {
            if (rowIndex < variantRows.size()) {
                variantRows.get(rowIndex).setImageUrl(imageUrl);
            }
            etVariantImage.setText(shortImageLabel(imageUrl));
            ImageLoadHelper.loadInto(imgVariantPreview, imageUrl);
            btnRemoveVariantImage.setVisibility(View.VISIBLE);
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
        boolean hasImage = !TextUtils.isEmpty(productImageUrl);
        if (cardProductPreview != null) {
            cardProductPreview.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        }
        if (btnRemoveProductImage != null) {
            btnRemoveProductImage.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        }
        if (!hasImage) {
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

        collectDimensionsFromUi();
        collectVariantsFromUi();

        if (!variantRows.isEmpty()) {
            for (Product.ProductVariant variant : variantRows) {
                if (variant.isEnabled() && variant.getPrice() <= 0) {
                    Toast.makeText(this,
                            "Mỗi tổ hợp đang bán cần giá > 0: " + variant.getName(),
                            Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }

        Product product = new Product();
        if (isEdit) {
            product.setId(productId);
            // Preserve the existing product code so re-saving an edit never
            // consumes a new sequence number from meta/productCodes.
            product.setProductCode(loadedProductCode);
        }
        product.setDraft(asDraft);
        product.setHidden(asDraft ? false : isHidden);
        product.setName(name);
        product.setPrice(Double.parseDouble(priceText));
        String originalPriceText = textOf(etOriginalPrice);
        product.setOriginalPrice(TextUtils.isEmpty(originalPriceText) ? product.getPrice() : Double.parseDouble(originalPriceText));

        if (!variantRows.isEmpty()) {
            product.setVariants(new ArrayList<>(variantRows));
            product.setHasVariants(true);
            product.setVariantDimensions(new ArrayList<>(dimensionDrafts));
            // Keep product-level price aligned with card/detail display (min enabled SKU).
            double synced = product.getDisplayPrice();
            if (synced > 0) {
                product.setPrice(synced);
                double syncedOriginal = product.getDisplayOriginalPrice();
                if (syncedOriginal > 0) {
                    product.setOriginalPrice(syncedOriginal);
                }
            }
            int totalStock = 0;
            int totalSold = 0;
            for (Product.ProductVariant variant : variantRows) {
                if (variant.isEnabled()) {
                    totalStock += Math.max(0, variant.getStock());
                }
                totalSold += Math.max(0, variant.getSold());
            }
            product.setStock(totalStock);
            // New products start at 0 sold; edits keep their real recorded sold.
            product.setSold(Math.max(0, totalSold));
            product.setSoldCount(Math.max(0, totalSold));

            // Keep legacy fields in sync for older clients
            List<String> legacyFlavors = new ArrayList<>();
            List<String> legacyWeights = new ArrayList<>();
            List<String> legacyPkg = new ArrayList<>();
            for (Product.VariantDimension dim : dimensionDrafts) {
                if (dim.getName() == null || dim.getOptions() == null) continue;
                String n = dim.getName().toLowerCase();
                if (n.contains("hương") || n.contains("flavor") || n.contains("vị")) {
                    legacyFlavors.addAll(dim.getOptions());
                } else if (n.contains("khối") || n.contains("weight") || n.contains("thể tích")
                        || n.contains("size") || n.contains("dung tích")) {
                    legacyWeights.addAll(dim.getOptions());
                } else if (n.contains("đóng") || n.contains("pack") || n.contains("gói")) {
                    legacyPkg.addAll(dim.getOptions());
                } else if (legacyPkg.isEmpty()) {
                    legacyPkg.addAll(dim.getOptions());
                } else {
                    legacyFlavors.addAll(dim.getOptions());
                }
            }
            product.setFlavors(AdminVariantComboHelper.toOptionFirestoreList(legacyFlavors));
            product.setWeights(AdminVariantComboHelper.toOptionFirestoreList(legacyWeights));
            product.setPackagingTypes(AdminVariantComboHelper.toOptionFirestoreList(legacyPkg));
        } else {
            String stockText = textOf(etStock);
            product.setStock(TextUtils.isEmpty(stockText) ? 0 : Integer.parseInt(stockText));
            product.setFlavors(new ArrayList<>());
            product.setWeights(new ArrayList<>());
            product.setPackagingTypes(new ArrayList<>());
            product.setVariantDimensions(new ArrayList<>());
        }

        product.setCat((String) spinnerCategory.getSelectedItem());
        product.setShortDesc("");
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

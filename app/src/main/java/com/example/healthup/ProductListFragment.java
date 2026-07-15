package com.example.healthup;








import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.healthup.WishlistManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;
import com.example.healthup.R;
import com.example.healthup.ProductAdapter;
import com.example.healthup.firebase.FirestoreManager;
import com.example.healthup.util.GuestWishlistUiHelper;
import com.example.healthup.util.ReviewStatsHelper;
import com.example.healthup.util.ToastUtils;
import com.example.models.Product;
import com.bumptech.glide.Glide;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;








public class ProductListFragment extends Fragment implements ProductAdapter.OnProductClickListener {

    private static final int REQUEST_CODE_SPEECH_INPUT = 1001;

    private RecyclerView rvProducts;
    private ProductAdapter productAdapter;
    private List<Product> productList = new ArrayList<>();
    private List<Product> allProductsForSearch = new ArrayList<>();
    private EditText etSearch;
    private ExtendedFloatingActionButton fabFilter;
    private ChipGroup chipGroupCategories;
    private View layoutEmpty;
    private android.widget.ProgressBar progressBar;
    private View ivClearSearch;
    private View containerImageSearch;
    private ImageView ivSearchPreview;
    private android.net.Uri photoUri;
    private boolean isImageSearch = false;

    private final androidx.activity.result.ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> pickImageLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    processImageForSearch(uri);
                }
            });

    private final androidx.activity.result.ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK) {
                    processImageForSearch(photoUri);
                }
            });

    private final androidx.activity.result.ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    launchCamera();
                } else {
                    ToastUtils.show(getContext(), "Cần quyền Camera để tìm kiếm bằng hình ảnh");
                }
            });








    private List<String> categoryNames = new ArrayList<>();
    private String selectedCategory = "Tất cả";








    private String currentSort = "Phổ biến";
    private double minPrice = 0;
    private double maxPrice = 1000000;
    private float minRating = 0;








    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_product_list, container, false);
        initViews(view);








        if (getArguments() != null) {
            selectedCategory = getArguments().getString("category", "Tất cả");
        }








        setupRecyclerViews();
        fetchCategoriesFromFirestore();
        fetchProducts();
        fetchAllProductsForSearch();
        setupSearch(view);
        return view;
    }

    private void fetchAllProductsForSearch() {
        FirestoreManager.getInstance().getProductsCollection().get().addOnSuccessListener(snapshots -> {
            allProductsForSearch.clear();
            for (DocumentSnapshot doc : snapshots) {
                if (Product.isVisibleToBuyers(doc)) {
                    Product p = Product.fromDocument(doc);
                    if (p != null) {
                        allProductsForSearch.add(p);
                    }
                }
            }
        });
    }

    private void setupSearch(View view) {
        ivClearSearch = view.findViewById(R.id.ivClearSearch);
        containerImageSearch = view.findViewById(R.id.containerImageSearch);
        ivSearchPreview = view.findViewById(R.id.ivSearchPreview);

        if (ivClearSearch != null) {
            ivClearSearch.setOnClickListener(v -> {
                isImageSearch = false;
                if (containerImageSearch != null) containerImageSearch.setVisibility(View.GONE);
                if (etSearch != null) {
                    etSearch.setText("");
                    etSearch.setVisibility(View.VISIBLE);
                }
                ivClearSearch.setVisibility(View.GONE);
                fetchProducts(); // Quay lại danh sách theo filter hiện tại
            });
        }

        view.findViewById(R.id.btn_voice_search).setOnClickListener(v -> startVoiceRecognition());
        view.findViewById(R.id.btn_camera_search).setOnClickListener(v -> startCameraSearch());
    }

    private void startVoiceRecognition() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 100);
            return;
        }

        Intent intent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Nói tên sản phẩm muốn tìm...");

        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
        } catch (Exception e) {
            ToastUtils.show(getContext(), "Máy bạn không hỗ trợ tìm kiếm giọng nói");
        }
    }

    private void startCameraSearch() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.layout_bottom_sheet_image_source, null);
        dialog.setContentView(view);

        TextView tvHeader = view.findViewById(R.id.tvHeader);
        if (tvHeader != null) tvHeader.setText("Tìm kiếm bằng hình ảnh");

        view.findViewById(R.id.btnCamera).setOnClickListener(v -> {
            dialog.dismiss();
            launchCamera();
        });

        view.findViewById(R.id.btnGallery).setOnClickListener(v -> {
            dialog.dismiss();
            launchGallery();
        });

        View btnCancel = view.findViewById(R.id.btnCancel);
        if (btnCancel != null) btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void launchCamera() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {

            Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            java.io.File photoFile = null;
            try {
                String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new java.util.Date());
                photoFile = java.io.File.createTempFile("SEARCH_" + timeStamp + "_", ".jpg", requireContext().getExternalFilesDir(null));
            } catch (IOException ex) {
                Log.e("ProductList", "Lỗi tạo file ảnh: " + ex.getMessage());
            }

            if (photoFile != null) {
                photoUri = androidx.core.content.FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", photoFile);
                intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoUri);
                cameraLauncher.launch(intent);
            }
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA);
        }
    }

    private void launchGallery() {
        pickImageLauncher.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
                .setMediaType(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startVoiceRecognition();
        } else if (requestCode == 200 && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == android.app.Activity.RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                String voiceQuery = result.get(0);
                etSearch.setText(voiceQuery);
            }
        }
    }

    private void processImageForSearch(android.net.Uri uri) {
        if (uri == null || getContext() == null) return;
        ToastUtils.show(getContext(), "Đang phân tích dấu vân tay thị giác...");

        try {
            if (containerImageSearch != null && ivSearchPreview != null && etSearch != null) {
                isImageSearch = true;
                etSearch.setVisibility(View.GONE);
                containerImageSearch.setVisibility(View.VISIBLE);
                Glide.with(this).load(uri).into(ivSearchPreview);
                if (ivClearSearch != null) ivClearSearch.setVisibility(View.VISIBLE);
            }

            android.graphics.Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), uri);
            VisualFingerprint inputFingerprint = extractVisualFingerprint(bitmap);

            InputImage image = InputImage.fromBitmap(bitmap, 0);
            ImageLabeler labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);

            labeler.process(image)
                    .addOnSuccessListener(labels -> {
                        if (labels.isEmpty()) {
                            ToastUtils.show(getContext(), "Không nhận diện được đặc trưng vật thể");
                            return;
                        }

                        List<ProductScore> scoredProducts = new ArrayList<>();
                        for (Product p : allProductsForSearch) {
                            double similarity = calculateAdvancedVisualSimilarity(p, labels, inputFingerprint);
                            if (similarity > 0.3) {
                                scoredProducts.add(new ProductScore(p, similarity));
                            }
                        }

                        Collections.sort(scoredProducts, (a, b) -> Double.compare(b.score, a.score));

                        List<Product> results = new ArrayList<>();
                        for (ProductScore ps : scoredProducts) {
                            results.add(ps.product);
                        }

                        displayVisualSearchResults(results);
                    })
                    .addOnFailureListener(e -> {
                        if (getContext() != null)
                            ToastUtils.show(getContext(), "Lỗi: " + e.getMessage());
                    });
        } catch (IOException e) {
            Log.e("ProductList", "Visual search error", e);
        }
    }

    private VisualFingerprint extractVisualFingerprint(android.graphics.Bitmap bitmap) {
        if (bitmap == null) return new VisualFingerprint(0, 0);
        int centerX = bitmap.getWidth() / 2;
        int centerY = bitmap.getHeight() / 2;
        int centerColor = bitmap.getPixel(centerX, centerY);
        android.graphics.Bitmap small = android.graphics.Bitmap.createScaledBitmap(bitmap, 1, 1, true);
        int avgColor = small.getPixel(0, 0);
        small.recycle();
        return new VisualFingerprint(centerColor, avgColor);
    }

    private double calculateAdvancedVisualSimilarity(Product p, List<ImageLabel> labels, VisualFingerprint inputFP) {
        double score = 0;
        String pName = removeAccents(p.getName().toLowerCase());
        String pCat = p.getCategory() != null ? removeAccents(p.getCategory().toLowerCase()) : "";

        int pTargetColor = getCategoryColor(p.getCategory());
        if (isColorSimilar(inputFP.centerColor, pTargetColor)) score += 0.8;
        if (isColorSimilar(inputFP.avgColor, pTargetColor)) score += 0.4;

        for (ImageLabel label : labels) {
            String concept = translateLabel(label.getText()).toLowerCase();
            String conceptNorm = removeAccents(concept);
            float confidence = label.getConfidence();

            if (pName.contains(conceptNorm) || pCat.contains(conceptNorm)) {
                score += (confidence * 1.5);
            }
            if (isPhysicalMatch(label.getText(), pName)) {
                score += (confidence * 0.5);
            }
        }
        return score;
    }

    private boolean isPhysicalMatch(String label, String pName) {
        if (label.contains("Liquid") && (pName.contains("nuoc") || pName.contains("mat ong"))) return true;
        if (label.contains("Granular") && (pName.contains("hat") || pName.contains("ngu coc"))) return true;
        if (label.contains("Leaf") && pName.contains("tra")) return true;
        if (label.contains("Container") && (pName.contains("chai") || pName.contains("hu") || pName.contains("tui"))) return true;
        return false;
    }

    private static class VisualFingerprint {
        int centerColor;
        int avgColor;
        VisualFingerprint(int centerColor, int avgColor) {
            this.centerColor = centerColor;
            this.avgColor = avgColor;
        }
    }

    private int getCategoryColor(String category) {
        if (category == null) return 0;
        String cat = category.toLowerCase();
        if (cat.contains("tra") || cat.contains("rau") || cat.contains("thao moc")) return android.graphics.Color.GREEN;
        if (cat.contains("hat") || cat.contains("granola") || cat.contains("ngu coc")) return android.graphics.Color.parseColor("#8B4513");
        if (cat.contains("trai cay") || cat.contains("do an vat")) return android.graphics.Color.RED;
        return 0;
    }

    private boolean isColorSimilar(int c1, int c2) {
        if (c1 == 0 || c2 == 0) return false;
        float[] hsv1 = new float[3];
        float[] hsv2 = new float[3];
        android.graphics.Color.colorToHSV(c1, hsv1);
        android.graphics.Color.colorToHSV(c2, hsv2);
        return Math.abs(hsv1[0] - hsv2[0]) < 45;
    }

    private void displayVisualSearchResults(List<Product> results) {
        if (!isAdded() || getView() == null) return;

        List<Product> filteredResults = new ArrayList<>();
        for (Product p : results) {
            if (selectedCategory.equals("Tất cả") || (p.getCategory() != null && p.getCategory().equalsIgnoreCase(selectedCategory))) {
                filteredResults.add(p);
            }
        }

        productList.clear();
        productList.addAll(filteredResults);
        productAdapter.updateData(new ArrayList<>(productList));
        updateEmptyState();
        if (filteredResults.isEmpty()) {
            ToastUtils.show(getContext(), "Không tìm thấy sản phẩm phù hợp trong danh mục này");
        }
    }

    private String removeAccents(String str) {
        if (str == null) return "";
        return str.toLowerCase()
                .replaceAll("[àáạảãâầấậẩẫăằắặẳẵ]", "a")
                .replaceAll("[èéẹẻẽêềếệểễ]", "e")
                .replaceAll("[ìíịỉĩ]", "i")
                .replaceAll("[òóọỏõôồốộổỗơờớợởỡ]", "o")
                .replaceAll("[ùúụủũưừứựửữ]", "u")
                .replaceAll("[ỳýỵỷỹ]", "y")
                .replaceAll("đ", "d")
                .trim();
    }

    private static class ProductScore {
        Product product;
        double score;
        ProductScore(Product product, double score) {
            this.product = product;
            this.score = score;
        }
    }

    private String translateLabel(String label) {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("nut", "Hạt");
        mapping.put("seed", "Hạt");
        mapping.put("fruit", "Trái cây");
        mapping.put("granola", "Granola");
        mapping.put("tea", "Trà");
        mapping.put("snack", "Ăn vặt");
        String lowLabel = label.toLowerCase();
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (lowLabel.contains(entry.getKey())) return entry.getValue();
        }
        return label;
    }








    private void initViews(View view) {
        rvProducts = view.findViewById(R.id.rv_products);
        etSearch = view.findViewById(R.id.et_search);
        UIUtils.bindFocusBorder(view.findViewById(R.id.searchBarLayout), etSearch);
        fabFilter = view.findViewById(R.id.fab_filter);
        chipGroupCategories = view.findViewById(R.id.chip_group_categories);
        layoutEmpty = view.findViewById(R.id.layout_empty);
        progressBar = view.findViewById(R.id.progressBar);








        if (layoutEmpty != null) {
            View btnClear = layoutEmpty.findViewById(R.id.btn_clear_filter);
            if (btnClear != null) btnClear.setOnClickListener(v -> resetFilters());
        }








        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() != null) getActivity().onBackPressed();
        });








        fabFilter.setOnClickListener(v -> showFilterBottomSheet());








        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isImageSearch) return;
                String query = s.toString().trim();
                if (ivClearSearch != null) {
                    ivClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                }
                filterLocal(query);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }








    private void setupRecyclerViews() {
        productAdapter = new ProductAdapter(productList, this);
        GuestWishlistUiHelper.applyTo(productAdapter);
        rvProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvProducts.setAdapter(productAdapter);
        rvProducts.setNestedScrollingEnabled(false);
        rvProducts.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN
                    && getActivity() != null
                    && etSearch != null
                    && etSearch.hasFocus()) {
                UIUtils.hideKeyboard(getActivity());
            }
            return false;
        });
    }








    private void fetchCategoriesFromFirestore() {
        categoryNames.clear();
        categoryNames.addAll(Arrays.asList("Tất cả", "Hạt dinh dưỡng", "Granola", "Trái cây sấy", "Đồ ăn vặt", "Trà thảo mộc", "Combo"));
        setupCategoryChips();


        FirestoreManager.getInstance().getFirestore().collection("categories").get().addOnSuccessListener(snapshots -> {
            if (!snapshots.isEmpty()) {
                // Có thể đồng bộ tên từ server ở đây nếu cần
            }
        });
    }








    private void setupCategoryChips() {
        chipGroupCategories.removeAllViews();
        for (String name : categoryNames) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_category_chip, chipGroupCategories, false);
            chip.setText(name);
            chip.setCheckable(true);








            boolean isSelected = name.equals(selectedCategory);
            chip.setChecked(isSelected);
            updateChipStyle(chip, isSelected);








            chip.setOnClickListener(v -> {
                selectedCategory = name;
                refreshChipGroupUI();
                fetchProducts();
            });
            chipGroupCategories.addView(chip);
        }
    }








    private void refreshChipGroupUI() {
        for (int i = 0; i < chipGroupCategories.getChildCount(); i++) {
            Chip chip = (Chip) chipGroupCategories.getChildAt(i);
            boolean isSelected = chip.getText().toString().equals(selectedCategory);
            chip.setChecked(isSelected);
            updateChipStyle(chip, isSelected);
        }
    }








    private void updateChipStyle(Chip chip, boolean isSelected) {
        if (isSelected) {
            chip.setChipBackgroundColorResource(R.color.primary_green);
            chip.setTextColor(getResources().getColor(R.color.white));
            chip.setChipStrokeWidth(0f);
        } else {
            chip.setChipBackgroundColorResource(R.color.white);
            chip.setTextColor(getResources().getColor(R.color.text_dark));
            chip.setChipStrokeWidth(2f);
            chip.setChipStrokeColorResource(R.color.border_color);
        }
    }








    private void fetchProducts() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
        rvProducts.setVisibility(View.GONE);


        Query query = FirestoreManager.getInstance().getFilteredProductsQuery(selectedCategory);








        query.get().addOnSuccessListener(snapshots -> {
            List<Product> result = FirestoreManager.getInstance()
                    .processProductSnapshots(snapshots, currentSort, minPrice, maxPrice, minRating);








            productList.clear();
            productList.addAll(result);
            productAdapter.updateData(new ArrayList<>(productList));
            applyWishlistState();
            enrichProductStats();
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            updateEmptyState();
            // FIX (yêu cầu): hiển thị số lượng sản phẩm phù hợp với bộ lọc/sắp xếp hiện tại
            // ngay trên nút "Bộ lọc", cập nhật lại mỗi lần fetchProducts() chạy (cả lần đầu
            // vào trang lẫn sau khi bấm Áp dụng ở bottom sheet).
            updateFilterButtonLabel(productList.size());
        }).addOnFailureListener(e -> {
            Log.e("ProductList", "Error fetching products: " + e.getMessage());
            ToastUtils.show(getContext(), "Lỗi tải sản phẩm: " + e.getMessage());
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            updateEmptyState();
            updateFilterButtonLabel(0);
        });
    }








    // FIX (yêu cầu): cập nhật chữ trên nút "Bộ lọc" thành "Bộ lọc (N)" với N là số sản phẩm
    // đang khớp với danh mục/sắp xếp/khoảng giá/đánh giá hiện tại.
    private void updateFilterButtonLabel(int count) {
        if (fabFilter != null) {
            fabFilter.setText(String.format(Locale.getDefault(), "Bộ lọc (%d)", count));
        }
    }




    private void enrichProductStats() {
        ReviewStatsHelper.enrichProducts(productList, () -> {
            if (isAdded() && productAdapter != null) {
                productAdapter.notifyDataSetChanged();
            }
        });
    }

    // FIX (yêu cầu #4): xem giải thích chi tiết trong HomeFragment.applyWishlistToHomeLists().
    // Nguyên nhân giống hệt: applyFavoriteState() mutate product object đang được adapter giữ
    // tham chiếu, nên DiffUtil trong updateData() không phát hiện thay đổi -> đổi sang
    // notifyDataSetChanged() để chắc chắn RecyclerView vẽ lại đúng trạng thái trái tim.
    private void applyWishlistState() {
        String uid = WishlistManager.currentUserId();
        if (uid == null) {
            return;
        }
        WishlistManager.loadFavoriteIds(uid, ids -> {
            if (!isAdded()) {
                return;
            }
            WishlistManager.applyFavoriteState(productList, ids);
            productAdapter.notifyDataSetChanged();
        });
    }




    private void updateEmptyState() {
        if (productList.isEmpty()) {
            rvProducts.setVisibility(View.GONE);
            if (layoutEmpty != null) {
                layoutEmpty.setVisibility(View.VISIBLE);
            }
        } else {
            rvProducts.setVisibility(View.VISIBLE);
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
        }
    }








    private void resetFilters() {
        selectedCategory = "Tất cả";
        currentSort = "Phổ biến";
        minPrice = 0;
        maxPrice = 1000000;
        minRating = 0;
        setupCategoryChips();
        fetchProducts();
    }








    private void filterLocal(String query) {
        if (query.isEmpty()) {
            fetchProducts();
            return;
        }
        List<Product> filtered = new ArrayList<>();
        String lowQuery = query.toLowerCase();
        for (Product p : allProductsForSearch) {
            boolean matchesCategory = selectedCategory.equals("Tất cả") ||
                    (p.getCategory() != null && p.getCategory().equalsIgnoreCase(selectedCategory));

            if (matchesCategory && p.getName().toLowerCase().contains(lowQuery)) {
                filtered.add(p);
            }
        }
        productList.clear();
        productList.addAll(filtered);
        productAdapter.updateData(new ArrayList<>(productList));
        updateEmptyState();
    }








    private void showFilterBottomSheet() {
        try {
            FilterBottomSheetFragment filterSheet = FilterBottomSheetFragment.newInstance(
                    selectedCategory, currentSort, minPrice, maxPrice, minRating
            );
            filterSheet.setFilterListener((category, sort, min, max, rating) -> {
                this.selectedCategory = category;
                this.currentSort = sort;
                this.minPrice = min;
                this.maxPrice = max;
                this.minRating = rating;
                setupCategoryChips();
                fetchProducts();
            });
            filterSheet.show(getChildFragmentManager(), "FilterBottomSheet");
        } catch (Exception e) {
            ToastUtils.show(getContext(), "Lỗi mở bộ lọc");
        }
    }








    @Override
    public void onProductClick(Product product) {
        if (product == null || product.getId() == null) return;
        Intent intent = new Intent(getContext(), ProductDetailActivity.class);
        intent.putExtra("productId", product.getId());
        startActivity(intent);
    }








    // FIX (yêu cầu #3): luôn hiển thị popup chọn số lượng/phân loại, bất kể có phân loại hay không.
    @Override
    public void onAddToCart(Product product) {
        showVariantSheet(product);
    }




    private void showVariantSheet(Product product) {
        VariantBottomSheetFragment sheet = VariantBottomSheetFragment.newInstance(product, (variant, quantity, selections) ->
                com.example.healthup.util.CartHelper.addToCart(requireContext(), product, variant, quantity, selections));
        sheet.show(getChildFragmentManager(), "VariantSelection");
    }








    @Override
    public void onResume() {
        super.onResume();
        GuestWishlistUiHelper.applyTo(productAdapter);
    }

    @Override
    public void onFavoriteClick(Product product) {
        WishlistManager.toggle(requireContext(), product, success -> {
            if (success && isAdded()) {
                productAdapter.notifyDataSetChanged();
            }
        });
    }
}
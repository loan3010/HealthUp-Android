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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.healthup.util.GuestWishlistUiHelper;
import com.example.healthup.util.LocaleHelper;
import com.example.healthup.util.ReviewStatsHelper;
import com.example.healthup.util.ToastUtils;
import com.example.healthup.util.TranslationManager;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.firestore.DocumentSnapshot;
import com.example.healthup.R;
import com.example.healthup.ProductAdapter;
import com.example.healthup.BlogAdapter;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.Blog;
import com.example.models.Category;
import com.example.models.Product;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeFragment extends Fragment implements ProductAdapter.OnProductClickListener, CategoryAdapter.OnCategoryClickListener, BlogAdapter.OnBlogClickListener {

    private static final int REQUEST_CODE_SPEECH_INPUT = 1001;
    private static final int REQUEST_CODE_CAMERA_INPUT = 1002;
    private static final int REQUEST_CODE_GALLERY_INPUT = 1003;

    private RecyclerView rvNewProducts, rvFeaturedProducts, rvCategories, rvFlashSale, rvBlogs;
    private ProductAdapter newProductAdapter, featuredProductAdapter, flashSaleAdapter;
    private CategoryAdapter categoryAdapter;
    private BlogAdapter blogAdapter;

    private List<Product> newProductList = new ArrayList<>();
    private List<Product> featuredProductList = new ArrayList<>();
    private List<Product> flashSaleList = new ArrayList<>();
    private List<Category> categoryList = new ArrayList<>();
    private List<Blog> blogList = new ArrayList<>();
    private List<Product> allProductsForSearch = new ArrayList<>();
    private List<String> bannerImages = new ArrayList<>();
    private int currentBannerIndex = 0;
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

    private android.os.Handler timerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private long endTime;

    private Runnable bannerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!bannerImages.isEmpty()) {
                View view = getView();
                if (view != null) {
                    ImageView ivBanner = view.findViewById(R.id.ivBanner);
                    if (ivBanner != null) {
                        Glide.with(HomeFragment.this)
                                .load("file:///android_asset/images/banners/" + bannerImages.get(currentBannerIndex))
                                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade())
                                .into(ivBanner);

                        currentBannerIndex = (currentBannerIndex + 1) % bannerImages.size();
                    }
                }
            }
            timerHandler.postDelayed(this, 2000);
        }
    };

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            long millis = endTime - System.currentTimeMillis();
            if (millis > 0) {
                int seconds = (int) (millis / 1000);
                int minutes = seconds / 60;
                int hours = minutes / 60;
                seconds = seconds % 60;
                minutes = minutes % 60;

                View view = getView();
                if (view != null) {
                    TextView tvH = view.findViewById(R.id.tvTimerHour);
                    TextView tvM = view.findViewById(R.id.tvTimerMinute);
                    TextView tvS = view.findViewById(R.id.tvTimerSecond);
                    if (tvH != null) tvH.setText(String.format("%02d", hours));
                    if (tvM != null) tvM.setText(String.format("%02d", minutes));
                    if (tvS != null) tvS.setText(String.format("%02d", seconds));
                }
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        updateWelcomeMessage(view);

        bannerImages.clear();
        bannerImages.addAll(Arrays.asList(
                "banner01.jpg", "banner02.jpg", "banner03.jpg",
                "banner04.jpg", "banner05.jpg", "banner06.jpg", "banner07.jpg",
                "freshfood.jpg", "goodfood.jpg", "healthyfood.jpg"
        ));
        Collections.shuffle(bannerImages);

        initViews(view);
        setupSearch(view);
        fetchCategories();
        fetchProducts();
        fetchBlogs();

        endTime = System.currentTimeMillis() + (2 * 60 * 60 * 1000);
        timerHandler.post(timerRunnable);
        timerHandler.post(bannerRunnable);

        return view;
    }

    private void initViews(View view) {
        rvCategories = view.findViewById(R.id.rvCategories);
        categoryAdapter = new CategoryAdapter(categoryList, this);
        rvCategories.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        rvCategories.setAdapter(categoryAdapter);

        rvFlashSale = view.findViewById(R.id.rvFlashSale);
        flashSaleAdapter = new ProductAdapter(flashSaleList, this, true);
        LinearLayoutManager flashSaleLM = new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false);
        rvFlashSale.setLayoutManager(flashSaleLM);
        rvFlashSale.setAdapter(flashSaleAdapter);

        View btnLeft = view.findViewById(R.id.btnFlashSaleLeft);
        View btnRight = view.findViewById(R.id.btnFlashSaleRight);

        if (btnLeft != null) {
            btnLeft.setOnClickListener(v -> {
                int pos = flashSaleLM.findFirstVisibleItemPosition();
                if (pos > 0) rvFlashSale.smoothScrollToPosition(pos - 1);
            });
        }

        if (btnRight != null) {
            btnRight.setOnClickListener(v -> {
                int pos = flashSaleLM.findLastVisibleItemPosition();
                if (pos < flashSaleAdapter.getItemCount() - 1) rvFlashSale.smoothScrollToPosition(pos + 1);
            });
        }

        rvFlashSale.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (btnLeft != null) btnLeft.setVisibility(flashSaleLM.findFirstVisibleItemPosition() > 0 ? View.VISIBLE : View.GONE);
                if (btnRight != null) btnRight.setVisibility(flashSaleLM.findLastVisibleItemPosition() < flashSaleAdapter.getItemCount() - 1 ? View.VISIBLE : View.GONE);
            }
        });

        rvNewProducts = view.findViewById(R.id.rvNewProducts);
        newProductAdapter = new ProductAdapter(newProductList, this, false);
        rvNewProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvNewProducts.setAdapter(newProductAdapter);

        rvFeaturedProducts = view.findViewById(R.id.rvFeaturedProducts);
        featuredProductAdapter = new ProductAdapter(featuredProductList, this, false);
        rvFeaturedProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvFeaturedProducts.setAdapter(featuredProductAdapter);

        applyGuestWishlistUi();

        rvBlogs = view.findViewById(R.id.rvBlogs);
        blogAdapter = new BlogAdapter(blogList, this, true);
        rvBlogs.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        rvBlogs.setAdapter(blogAdapter);

        view.findViewById(R.id.tvViewAllNew).setOnClickListener(v -> navigateToCategory(null));
        view.findViewById(R.id.tvViewAllFeatured).setOnClickListener(v -> navigateToCategory(null));

        View tvViewAllBlogs = view.findViewById(R.id.tvViewAllBlogs);
        if (tvViewAllBlogs != null) {
            tvViewAllBlogs.setOnClickListener(v -> openBlogList());
        }

        setupChipListeners(view);

        View dietCard = view.findViewById(R.id.cardDietEntry);
        if (dietCard != null) {
            dietCard.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), DietLandingActivity.class)));
        }
    }

    private void openBlogList() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new BlogFragment())
                    .addToBackStack(null)
                    .commit();
        }
    }

    private void setupChipListeners(View view) {
        int[] chipIds = {
                R.id.chipHatDinhDuong, R.id.chipGranola, R.id.chipTraiCaySay,
                R.id.chipDoAnVat, R.id.chipTraThaoMoc, R.id.chipCombo
        };
        String[] categoryNames = {
                "Hạt dinh dưỡng", "Granola", "Trái cây sấy",
                "Đồ ăn vặt", "Trà thảo mộc", "Combo"
        };

        String currentLang = LocaleHelper.getLanguage(requireContext());

        for (int i = 0; i < chipIds.length; i++) {
            final String categoryName = categoryNames[i];
            TextView chip = view.findViewById(chipIds[i]);
            if (chip != null) {
                if ("en".equals(currentLang)) {
                    TranslationManager.translate(categoryName, "en", translated -> {
                        if (translated != null) chip.setText(translated);
                    });
                }
                chip.setOnClickListener(v -> navigateToCategory(categoryName));
            }
        }
    }

    private void setupSearch(View view) {
        EditText etSearch = view.findViewById(R.id.etSearch);
        View containerImageSearch = view.findViewById(R.id.containerImageSearch);
        View ivClearSearch = view.findViewById(R.id.ivClearSearch);
        View mainContent = view.findViewById(R.id.mainContent);
        View rvRealtimeSearch = view.findViewById(R.id.rvRealtimeSearch);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isImageSearch) return;

                String query = s.toString().trim();
                if (ivClearSearch != null) {
                    ivClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                }

                if (query.isEmpty()) {
                    mainContent.setVisibility(View.VISIBLE);
                    rvRealtimeSearch.setVisibility(View.GONE);
                } else {
                    mainContent.setVisibility(View.GONE);
                    rvRealtimeSearch.setVisibility(View.VISIBLE);

                    List<Product> searchResults = new ArrayList<>();
                    String lowQuery = query.toLowerCase();
                    for (Product p : allProductsForSearch) {
                        if (p.getName().toLowerCase().contains(lowQuery)) {
                            searchResults.add(p);
                        }
                    }

                    RecyclerView rvSearch = view.findViewById(R.id.rvRealtimeSearch);
                    ProductAdapter searchAdapter = new ProductAdapter(searchResults, HomeFragment.this);
                    GuestWishlistUiHelper.applyTo(searchAdapter);
                    rvSearch.setLayoutManager(new GridLayoutManager(getContext(), 2));
                    rvSearch.setAdapter(searchAdapter);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        if (ivClearSearch != null) {
            ivClearSearch.setOnClickListener(v -> {
                isImageSearch = false;
                if (containerImageSearch != null) containerImageSearch.setVisibility(View.GONE);
                if (etSearch != null) {
                    etSearch.setText("");
                    etSearch.setVisibility(View.VISIBLE);
                }
                ivClearSearch.setVisibility(View.GONE);
                mainContent.setVisibility(View.VISIBLE);
                rvRealtimeSearch.setVisibility(View.GONE);
            });
        }

        // Voice search click
        view.findViewById(R.id.ivMic).setOnClickListener(v -> startVoiceRecognition());

        // Camera search click
        view.findViewById(R.id.ivCamera).setOnClickListener(v -> startCameraSearch());
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
                Log.e("HomeFragment", "Lỗi tạo file ảnh: " + ex.getMessage());
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
        } else if (requestCode == 100) {
            ToastUtils.show(getContext(), "Bạn cần cấp quyền Micro để sử dụng tính năng này");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == android.app.Activity.RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                String voiceQuery = result.get(0);
                performSearch(voiceQuery);
            }
        } else if (requestCode == REQUEST_CODE_CAMERA_INPUT && resultCode == android.app.Activity.RESULT_OK) {
            processImageForSearch(photoUri);
        } else if (requestCode == REQUEST_CODE_GALLERY_INPUT && resultCode == android.app.Activity.RESULT_OK && data != null) {
            processImageForSearch(data.getData());
        }
    }

    private void performSearch(String query) {
        EditText etSearch = getView().findViewById(R.id.etSearch);
        if (etSearch != null) {
            etSearch.setText(query);
        }
    }

    private void processImageForSearch(android.net.Uri uri) {
        if (uri == null || getContext() == null) return;
        ToastUtils.show(getContext(), "Đang phân tích dấu vân tay thị giác...");

        try {
            // Hiển thị ảnh xem trước TRONG thanh search (thay thế text)
            View view = getView();
            if (view != null) {
                View containerImageSearch = view.findViewById(R.id.containerImageSearch);
                ImageView ivSearchPreview = view.findViewById(R.id.ivSearchPreview);
                EditText etSearch = view.findViewById(R.id.etSearch);
                View ivClearSearch = view.findViewById(R.id.ivClearSearch);
                
                if (containerImageSearch != null && ivSearchPreview != null && etSearch != null) {
                    isImageSearch = true;
                    etSearch.setVisibility(View.GONE); // Ẩn ô nhập liệu
                    containerImageSearch.setVisibility(View.VISIBLE); // Hiện ảnh
                    Glide.with(this).load(uri).into(ivSearchPreview);
                    
                    if (ivClearSearch != null) ivClearSearch.setVisibility(View.VISIBLE);
                }
            }

            // 1. Phân tích Dấu vân tay màu sắc đa điểm (Multi-point Color Fingerprint)
            android.graphics.Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), uri);
            VisualFingerprint inputFingerprint = extractVisualFingerprint(bitmap);
            
            // 2. Sử dụng ML Kit để trích xuất Vector đặc trưng cấu trúc
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            ImageLabeler labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);

            labeler.process(image)
                    .addOnSuccessListener(labels -> {
                        if (labels.isEmpty()) {
                            ToastUtils.show(getContext(), "Không nhận diện được đặc trưng vật thể");
                            return;
                        }

                        // 3. Tính toán độ tương đồng dựa trên tổ hợp Màu sắc + Cấu trúc + Đặc tính
                        List<ProductScore> scoredProducts = new ArrayList<>();
                        for (Product p : allProductsForSearch) {
                            double similarity = calculateAdvancedVisualSimilarity(p, labels, inputFingerprint);
                            if (similarity > 0.3) { // Ngưỡng tương đồng tinh chỉnh
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
            Log.e("HomeFragment", "Visual search error", e);
        }
    }

    private VisualFingerprint extractVisualFingerprint(android.graphics.Bitmap bitmap) {
        if (bitmap == null) return new VisualFingerprint(0, 0);
        
        // Lấy màu tại tâm (vật thể chính)
        int centerX = bitmap.getWidth() / 2;
        int centerY = bitmap.getHeight() / 2;
        int centerColor = bitmap.getPixel(centerX, centerY);
        
        // Lấy màu trung bình tổng thể
        android.graphics.Bitmap small = android.graphics.Bitmap.createScaledBitmap(bitmap, 1, 1, true);
        int avgColor = small.getPixel(0, 0);
        small.recycle();
        
        return new VisualFingerprint(centerColor, avgColor);
    }

    private double calculateAdvancedVisualSimilarity(Product p, List<ImageLabel> labels, VisualFingerprint inputFP) {
        double score = 0;
        String pName = removeAccents(p.getName().toLowerCase());
        String pCat = removeAccents(p.getCategory().toLowerCase());

        // A. Khớp màu sắc (Color Matching - 40% trọng số)
        int pTargetColor = getCategoryColor(p.getCategory());
        if (isColorSimilar(inputFP.centerColor, pTargetColor)) score += 0.8;
        if (isColorSimilar(inputFP.avgColor, pTargetColor)) score += 0.4;

        // B. Khớp đặc trưng cấu trúc (Structural Matching - 60% trọng số)
        for (ImageLabel label : labels) {
            String concept = translateLabel(label.getText()).toLowerCase();
            String conceptNorm = removeAccents(concept);
            float confidence = label.getConfidence();

            if (pName.contains(conceptNorm) || pCat.contains(conceptNorm)) {
                score += (confidence * 1.5);
            }
            
            // So khớp đặc tính vật lý (Texture/Container)
            if (isPhysicalMatch(label.getText(), pName)) {
                score += (confidence * 0.5);
            }
        }

        return score;
    }

    private boolean isPhysicalMatch(String label, String pName) {
        // Ánh xạ các nhãn thị giác sang đặc tính vật lý của sản phẩm
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
        View view = getView();

        view.findViewById(R.id.mainContent).setVisibility(View.GONE);
        view.findViewById(R.id.rvRealtimeSearch).setVisibility(View.VISIBLE);

        RecyclerView rvSearch = view.findViewById(R.id.rvRealtimeSearch);
        ProductAdapter searchAdapter = new ProductAdapter(results, this);
        GuestWishlistUiHelper.applyTo(searchAdapter);
        rvSearch.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvSearch.setAdapter(searchAdapter);

        if (results.isEmpty()) {
            ToastUtils.show(getContext(), "Không tìm thấy sản phẩm tương đồng");
        } else {
            ToastUtils.show(getContext(), "Đã tìm thấy " + results.size() + " sản phẩm phù hợp nhất");
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
        // Cập nhật từ điển chuyên sâu cho HealthUp
        mapping.put("nut", "Hạt");
        mapping.put("seed", "Hạt");
        mapping.put("cashew", "Điều");
        mapping.put("almond", "Hạnh nhân");
        mapping.put("walnut", "Óc chó");
        mapping.put("macadamia", "Macca");
        mapping.put("peanut", "Lạc");
        mapping.put("pistachio", "Dẻ cười");
        mapping.put("hazelnut", "Phỉ");
        
        mapping.put("fruit", "Trái cây");
        mapping.put("dried fruit", "Sấy");
        mapping.put("raisin", "Nho khô");
        mapping.put("berry", "Dâu");
        mapping.put("strawberry", "Dâu tây");
        mapping.put("date fruit", "Chà là");
        mapping.put("apricot", "Mơ");
        
        mapping.put("granola", "Granola");
        mapping.put("cereal", "Ngũ cốc");
        mapping.put("muesli", "Ngũ cốc");
        mapping.put("oat", "Yến mạch");
        mapping.put("honey", "Mật ong");
        
        mapping.put("tea", "Trà");
        mapping.put("matcha", "Trà xanh");
        mapping.put("drink", "Nước");
        mapping.put("beverage", "Đồ uống");
        mapping.put("bottle", "Chai");
        mapping.put("jar", "Hũ");
        
        mapping.put("snack", "Ăn vặt");
        mapping.put("cookie", "Bánh");
        mapping.put("biscuit", "Bánh");
        mapping.put("food", "Thực phẩm");
        mapping.put("cuisine", "Món ăn");
        mapping.put("produce", "Nông sản");
        mapping.put("vegetable", "Rau");
        
        String lowLabel = label.toLowerCase();
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (lowLabel.contains(entry.getKey())) return entry.getValue();
        }
        return label;
    }

    private void fetchCategories() {
        List<String> brandCategories = Arrays.asList("Hạt dinh dưỡng", "Granola", "Trái cây sấy", "Đồ ăn vặt", "Trà thảo mộc", "Combo");
        categoryList.clear();
        for (int i = 0; i < brandCategories.size(); i++) {
            String name = brandCategories.get(i);
            String icon = "fruit.png";

            if (name.contains("Hạt")) icon = "grain.png";
            else if (name.contains("Granola")) icon = "dry.png";
            else if (name.contains("Trái cây")) icon = "fruit.png";
            else if (name.contains("vặt")) icon = "cooking.png";
            else if (name.contains("Trà")) icon = "coffee.png";
            else if (name.contains("Combo")) icon = "multiple.png";

            categoryList.add(new Category(String.valueOf(i + 1), name, icon));
        }
        categoryAdapter.notifyDataSetChanged();

        FirestoreManager.getInstance().getFirestore().collection("categories")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    boolean hasChanges = false;
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        String name = doc.getString("name");
                        String iconUrl = doc.getString("iconUrl");
                        if (name != null && iconUrl != null && !iconUrl.isEmpty()) {
                            // Chỉ ghi đè nếu iconUrl không phải là fruit.png hoặc là link http thực tế
                            // Điều này ngăn chặn việc tất cả bị reset về fruit.png từ DB cũ
                            if (iconUrl.startsWith("http") || !iconUrl.equals("fruit.png")) {
                                for (Category cat : categoryList) {
                                    if (cat.getName().equalsIgnoreCase(name)) {
                                        cat.setIconUrl(iconUrl);
                                        hasChanges = true;
                                    }
                                }
                            }
                        }
                    }
                    if (hasChanges) {
                        categoryAdapter.notifyDataSetChanged();
                    }
                });
    }

    private void fetchProducts() {
        FirestoreManager.getInstance().getProductsCollection()
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Product> allFetched = new ArrayList<>();
                    List<Product> flashSales = new ArrayList<>();
                    Map<String, Long> createdAtById = new HashMap<>();

                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        if (!Product.isVisibleToBuyers(doc)) {
                            continue;
                        }
                        Product product = doc.toObject(Product.class);
                        if (product != null) {
                            product.setId(doc.getId());
                            createdAtById.put(doc.getId(), Product.readCreatedAtMillis(doc));
                            allFetched.add(product);

                            Object isFlash = doc.get("isFlashSale");
                            if (isFlash instanceof Boolean && (Boolean)isFlash) {
                                product.setFlashSale(true);
                                flashSales.add(product);
                            }

                            Object isNewObj = doc.get("isNew");
                            if (isNewObj instanceof Boolean && (Boolean)isNewObj) {
                                product.setNew(true);
                            }
                        }
                    }

                    allProductsForSearch.clear();
                    allProductsForSearch.addAll(allFetched);

                    flashSaleList.clear();
                    if (!flashSales.isEmpty()) {
                        Collections.shuffle(flashSales);
                        flashSaleList.addAll(flashSales.subList(0, Math.min(flashSales.size(), 5)));
                    } else if (!allFetched.isEmpty()) {
                        flashSaleList.addAll(allFetched.subList(0, Math.min(allFetched.size(), 5)));
                    }
                    flashSaleAdapter.updateData(new ArrayList<>(flashSaleList));

                    // Newest: 6 products added most recently (by createdAt)
                    List<Product> newest = new ArrayList<>(allFetched);
                    newest.sort((a, b) -> Long.compare(
                            createdAtById.getOrDefault(b.getId(), 0L),
                            createdAtById.getOrDefault(a.getId(), 0L)));
                    newProductList.clear();
                    if (!newest.isEmpty()) {
                        newProductList.addAll(newest.subList(0, Math.min(newest.size(), 6)));
                    }
                    newProductAdapter.updateData(new ArrayList<>(newProductList));

                    // Featured: best-selling first
                    List<Product> featured = new ArrayList<>(allFetched);
                    featured.sort((a, b) -> Integer.compare(b.getSoldCount(), a.getSoldCount()));
                    featuredProductList.clear();
                    if (!featured.isEmpty()) {
                        featuredProductList.addAll(featured.subList(0, Math.min(featured.size(), 10)));
                    } else {
                        featuredProductList.addAll(Product.getDummyProducts());
                    }
                    featuredProductAdapter.updateData(new ArrayList<>(featuredProductList));
                    applyWishlistToHomeLists();
                    enrichHomeProductStats();
                })
                .addOnFailureListener(e -> {
                    newProductList.clear();
                    featuredProductList.clear();
                    featuredProductList.addAll(Product.getDummyProducts());
                    newProductAdapter.notifyDataSetChanged();
                    featuredProductAdapter.notifyDataSetChanged();
                });
    }

    private void applyGuestWishlistUi() {
        GuestWishlistUiHelper.applyTo(flashSaleAdapter);
        GuestWishlistUiHelper.applyTo(newProductAdapter);
        GuestWishlistUiHelper.applyTo(featuredProductAdapter);
    }

    private void enrichHomeProductStats() {
        if (!isAdded()) return;
        List<Product> all = new ArrayList<>();
        all.addAll(flashSaleList);
        all.addAll(newProductList);
        all.addAll(featuredProductList);
        ReviewStatsHelper.enrichProducts(all, () -> {
            if (!isAdded()) return;
            if (flashSaleAdapter != null) flashSaleAdapter.notifyDataSetChanged();
            if (newProductAdapter != null) newProductAdapter.notifyDataSetChanged();
            if (featuredProductAdapter != null) featuredProductAdapter.notifyDataSetChanged();
        });
    }

    private void applyWishlistToHomeLists() {
        String uid = WishlistManager.currentUserId();
        if (uid == null) return;
        WishlistManager.loadFavoriteIds(uid, ids -> {
            if (!isAdded()) return;
            WishlistManager.applyFavoriteState(flashSaleList, ids);
            WishlistManager.applyFavoriteState(newProductList, ids);
            WishlistManager.applyFavoriteState(featuredProductList, ids);
            if (flashSaleAdapter != null) flashSaleAdapter.notifyDataSetChanged();
            if (newProductAdapter != null) newProductAdapter.notifyDataSetChanged();
            if (featuredProductAdapter != null) featuredProductAdapter.notifyDataSetChanged();
        });
    }

    private void fetchBlogs() {
        FirestoreManager.getInstance().getFirestore().collection("blogs")
                .limit(5)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    blogList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Blog blog = doc.toObject(Blog.class);
                        if (blog != null) {
                            blog.setId(doc.getId());
                            blogList.add(blog);
                        }
                    }
                    blogAdapter.notifyDataSetChanged();
                });
    }

    @Override
    public void onCategoryClick(Category category) {
        navigateToCategory(category.getName());
    }

    private void navigateToCategory(String categoryName) {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            BottomNavigationView navView = mainActivity.findViewById(R.id.bottom_navigation);
            MainActivity.skipNextCategoryNavLoad = true;
            navView.setSelectedItemId(R.id.nav_category);

            ProductListFragment fragment = new ProductListFragment();
            Bundle args = new Bundle();
            args.putString("category", categoryName != null ? categoryName : "Tất cả");
            fragment.setArguments(args);

            mainActivity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    @Override
    public void onProductClick(Product product) {
        Intent intent = new Intent(getContext(), ProductDetailActivity.class);
        intent.putExtra("productId", product.getId());
        startActivity(intent);
    }

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
    public void onFavoriteClick(Product product) {
        WishlistManager.toggle(requireContext(), product, success -> {
            if (success && isAdded()) {
                newProductAdapter.notifyDataSetChanged();
                if (featuredProductAdapter != null) featuredProductAdapter.notifyDataSetChanged();
                if (flashSaleAdapter != null) flashSaleAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        applyGuestWishlistUi();
    }

    @Override
    public void onBlogClick(Blog blog) {
        Intent intent = new Intent(getContext(), BlogDetailActivity.class);
        intent.putExtra("blog", blog);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.removeCallbacks(bannerRunnable);
    }

    private void updateWelcomeMessage(View view) {
        TextView tvWelcome = view.findViewById(R.id.tvWelcome);
        if (tvWelcome == null) return;
        
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String uid = user.getUid();
            FirestoreManager.getInstance().getFirestore().collection("users").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            String name = doc.getString("fullName");
                            if (name == null || name.isEmpty()) name = doc.getString("name");
                            if (name != null && !name.isEmpty()) {
                                String welcomeText = getString(R.string.welcome_back) + ", " + name + "!";
                                tvWelcome.setText(welcomeText);
                            }
                        }
                    });
        }
    }
}

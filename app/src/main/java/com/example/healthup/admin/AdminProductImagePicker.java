package com.example.healthup.admin;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.healthup.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class AdminProductImagePicker {

    public interface Listener {
        void onImagePicked(@NonNull Uri localUri);
    }

    private final ComponentActivity activity;
    private final ActivityResultLauncher<PickVisualMediaRequest> galleryLauncher;
    private final ActivityResultLauncher<Intent> cameraLauncher;
    private final ActivityResultLauncher<String> cameraPermissionLauncher;
    private Uri cameraPhotoUri;
    @Nullable
    private Listener listener;

    public AdminProductImagePicker(@NonNull ComponentActivity activity) {
        this.activity = activity;
        galleryLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null && listener != null) {
                        listener.onImagePicked(uri);
                    }
                });
        cameraLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == ComponentActivity.RESULT_OK
                            && cameraPhotoUri != null
                            && listener != null) {
                        listener.onImagePicked(cameraPhotoUri);
                    }
                });
        cameraPermissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        launchCameraIntent();
                    } else {
                        Toast.makeText(activity, R.string.admin_image_camera_denied, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void showSourceChooser() {
        BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.BottomSheetDialogTheme);
        View view = activity.getLayoutInflater().inflate(R.layout.layout_bottom_sheet_image_source, null);
        dialog.setContentView(view);

        view.findViewById(R.id.btnCamera).setOnClickListener(v -> {
            dialog.dismiss();
            launchCamera();
        });
        view.findViewById(R.id.btnGallery).setOnClickListener(v -> {
            dialog.dismiss();
            galleryLauncher.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });
        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void launchCamera() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCameraIntent();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCameraIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile;
        try {
            photoFile = createImageFile();
        } catch (IOException e) {
            Toast.makeText(activity, R.string.admin_image_process_error, Toast.LENGTH_SHORT).show();
            return;
        }
        cameraPhotoUri = FileProvider.getUriForFile(
                activity, activity.getPackageName() + ".fileprovider", photoFile);
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
        takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        cameraLauncher.launch(takePictureIntent);
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return File.createTempFile("PRODUCT_" + timeStamp + "_", ".jpg", activity.getExternalFilesDir(null));
    }
}

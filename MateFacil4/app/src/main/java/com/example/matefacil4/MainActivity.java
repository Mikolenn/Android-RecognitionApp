package com.example.matefacil4;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private ImageView img;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ImageView para mostrar la imagen capturada
        img = (ImageView)findViewById(R.id.imageView);

        // Permisos iniciales para acceder a la cámara y almacenamiento
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.CAMERA}, 1000);
        }
    }

    // Variable para guardar la dirección de memoria de la imagen capturada
    String currentPhotoPath;

    // Se crea el nombre único y detergente, para la imagen actual
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "Backup_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",   /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }


    static final int REQUEST_IMAGE_CAPTURE = 1;

    public void takePicture(View view) {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;

            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
            }


            // Continue only if the File was successfully created
            if (photoFile != null) {

                // Dirección completa donde se guarda la imagen
                Uri photoURI = FileProvider.getUriForFile(this,
                                                        "com.example.android.fileprovider",
                                                        photoFile);

                // Intent para añadir el nombre completo y la dirección de la imagen
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);

                // Se guarda la imagen en galería y se inicia la función impícita onActivityResult
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {

            File file = new File(currentPhotoPath);
            Bitmap bitmap = null;

            // Matriz para realizar el giro de la imagen
            Matrix matrix = new Matrix();
            matrix.postRotate(90);

            try {
                // Se recupera la captura de la galería, para mostrarla como un bitmap en la app
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(),
                        Uri.fromFile(file));
            } catch (Exception error){
                // Placeholder
            }

            // Se ajusta el tamaño
            Bitmap sBitmap = Bitmap.createScaledBitmap(bitmap,
                    750, 750, false);

            // Se rota, porque si no, sale girada a la izquiera
            Bitmap rBitmap = Bitmap.createBitmap(sBitmap, 0, 0,
                    sBitmap.getWidth(), sBitmap.getHeight(), matrix, true);

            // Se muestra en la vista previa del layout
            img.setImageBitmap(rBitmap);
        }
    }

}

package com.tonyodev.fetchapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;
import android.widget.Toast;

import com.tonyodev.fetch2.AbstractFetchListener;
import com.tonyodev.fetch2.Download;
import com.tonyodev.fetch2.Downloader;
import com.tonyodev.fetch2.Fetch;
import com.tonyodev.fetch2.FetchConfiguration;
import com.tonyodev.fetch2.FetchListener;
import com.tonyodev.fetch2.Request;
import com.tonyodev.fetch2fileserver.ContentFile;
import com.tonyodev.fetch2fileserver.FetchFileServer;
import com.tonyodev.fetch2fileserver.FetchFileServerDownloader;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import timber.log.Timber;


public class FileServerActivity extends AppCompatActivity {

    private static final int STORAGE_PERMISSION_CODE = 100;
    public static final String FETCH_NAMESPACE = "fileServerActivity";

    private TextView textView;
    private FetchFileServer fetchFileServer;
    private Fetch fetch;
    private boolean interrupted = false;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_server);
        textView = findViewById(R.id.textView);
        final FetchConfiguration fetchConfiguration = new FetchConfiguration.Builder(this)
                .setFileServerDownloader(new FetchFileServerDownloader(Downloader.FileDownloaderType.PARALLEL))
                .setNamespace(FETCH_NAMESPACE)
                .build();
        fetch = Fetch.Impl.getInstance(fetchConfiguration);
        fetchFileServer = new FetchFileServer.Builder(this)
                .setClearDatabaseOnShutdown(true)
                .setAuthenticator((authorization, fileRequest) -> authorization.equals("password"))
                .build();
        fetchFileServer.start();
        checkStoragePermission();
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
        } else {
            copyRawFileToFilesDir();
        }
    }

    private void copyRawFileToFilesDir() {
        new Thread(() -> {
            try {
                final String testFilePath = getFilesDir().getAbsolutePath() + "/FileServerActivity/test_file.db";
                final File file = new File(testFilePath);
                if (!file.exists()) {
                    file.exists();
                }
                final InputStream inputStream = new BufferedInputStream(getResources().openRawResource(R.raw.test_file));
                final OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file));
                final byte[] buffer = new byte[1024];
                int read;
                long readBytes = 0;
                while (((read = inputStream.read(buffer, 0, 1024)) != -1) && !interrupted) {
                    readBytes += read;
                    outputStream.write(buffer, 0, read);
                }
                inputStream.close();
                outputStream.flush();
                outputStream.close();
                addContentFileToServer(file, readBytes);
            } catch (IOException exception) {
                Timber.e(exception);
            }
        }).start();
    }

    private void addContentFileToServer(File file, long fileLength) {
        final ContentFile contentFile = new ContentFile();
        contentFile.setFile(file.getAbsolutePath());
        contentFile.setName(file.getName());
        contentFile.setId(file.getAbsolutePath().hashCode());
        contentFile.setLength(fileLength);
        if (!interrupted) {
            fetchFileServer.addContentFile(contentFile);
            downloadContentFileUsingFetch(contentFile.getName());
        }
    }

    private void downloadContentFileUsingFetch(String fileName) {
        final String url = new Uri.Builder()
                .scheme("fetchlocal")
                .encodedAuthority(fetchFileServer.getAddress() + ":" + fetchFileServer.getPort())
                .appendPath(fileName)
                .toString();
        final Request request = new Request(url, Data.getFilePath(url));
        request.addHeader("Authorization", "password");
        if (!interrupted) {
            fetch.addListener(fetchListener)
                    .enqueue(request, request1 -> {
                        Timber.d(request1.toString());
                    }, error -> {
                        Timber.d(error.toString());
                    });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetch.addListener(fetchListener);
        if (interrupted) {
            interrupted = false;
            copyRawFileToFilesDir();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        fetch.removeListener(fetchListener);
        interrupted = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        fetch.close();
        fetchFileServer.shutDown(false);
    }

    private FetchListener fetchListener = new AbstractFetchListener() {

        @Override
        public void onCompleted(@NotNull Download download) {
            Timber.d("Download From FileServer completed");
            final String completed = "Completed Download";
            textView.setText(completed);
        }

        @Override
        public void onProgress(@NotNull Download download, long etaInMilliSeconds, long downloadedBytesPerSecond) {
            Timber.d("Download From FileServer Progress: " + download.getProgress() + "%");
            final String progress = "Progress: " + download.getProgress() + "%";
            textView.setText(progress);
        }

        @Override
        public void onError(@NotNull Download download) {
            final String error = "Download From FileServer Error " + download.getError().toString();
            Timber.d(error);
            textView.setText(error);
        }

    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == STORAGE_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            copyRawFileToFilesDir();
        } else {
            Toast.makeText(this, R.string.permission_not_enabled, Toast.LENGTH_LONG).show();
        }
    }
}
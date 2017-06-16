package com.hzy.maxmind.demo;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.hzy.maxmind.MaxMindApi;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    private String mmdbPath;
    private SharedPreferences prefrence;
    private String datPath;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mmdbPath = getExternalFilesDir("mmdb").getPath();
        prefrence = getPreferences(MODE_PRIVATE);
        ensureDatabase();
    }

    private void ensureDatabase() {
        boolean needDownload = true;
        if (prefrence.contains(Constants.PREF_KEY_DB_PATH)) {
            datPath = prefrence.getString(Constants.PREF_KEY_DB_PATH, "");
            File dbFile = new File(datPath);
            if (dbFile.exists() && dbFile.isFile()) {
                needDownload = false;
            }
        }
        if (needDownload) {
            downloadAndUntarDat();
        } else{
            MaxMindApi.open(datPath);
            MaxMindApi.lookupIpString("1.1.1.1");
            MaxMindApi.getMetaData();
            MaxMindApi.close();
        }
    }

    private void downloadAndUntarDat() {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setCancelable(false);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setTitle("Preparing Data...");
        }
        progressDialog.show();
        Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> e) throws Exception {
                String dbPath = downloadDatFileReady();
                e.onNext(dbPath);
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String s) throws Exception {
                        if (s != null) {
                            datPath = s;
                            prefrence.edit().putString(Constants.PREF_KEY_DB_PATH, datPath).apply();
                            progressDialog.dismiss();
                        }
                    }
                });
    }

    private String downloadDatFileReady() {
        try {
            URL url = new URL(Constants.URL_GEOLITE2_COUNTRY);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            InputStream inputStream = connection.getInputStream();
            GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream);
            TarArchiveInputStream tarInputStream = new TarArchiveInputStream(gzipInputStream);
            TarArchiveEntry entry;
            String filePath = null;
            while ((entry = tarInputStream.getNextTarEntry()) != null) {
                String tarFileName = entry.getName();
                if (entry.isFile() && tarFileName.endsWith(".mmdb")) {
                    String fileName = tarFileName.split("/")[1];
                    filePath = mmdbPath + File.separator + fileName;
                    FileOutputStream fileOutputStream = new FileOutputStream(filePath);
                    byte[] buffer = new byte[1024 * 1024 * 4];
                    int length;
                    int total = (int) entry.getSize();
                    int already = 0;
                    while ((length = tarInputStream.read(buffer)) > 0) {
                        already += length;
                        fileOutputStream.write(buffer, 0, length);
                        int percent = already * 100 / total;
                        progressDialog.setProgress(percent);
                    }
                    fileOutputStream.close();
                    break;
                }
            }
            tarInputStream.close();
            gzipInputStream.close();
            inputStream.close();
            return filePath;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


}

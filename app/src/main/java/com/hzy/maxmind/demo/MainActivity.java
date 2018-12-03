package com.hzy.maxmind.demo;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.hzy.maxmind.MaxMindApi;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
    private TextView textDatInfo;
    private TextView textResult;
    private EditText editIpAddr;
    private Button buttonQuery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textDatInfo = findViewById(R.id.text_meta_info);
        editIpAddr = findViewById(R.id.edit_input_ip);
        buttonQuery = findViewById(R.id.button_query);
        textResult = findViewById(R.id.text_result_info);
        fillWithLocalIp();
        mmdbPath = getExternalFilesDir("mmdb").getPath();
        prefrence = getPreferences(MODE_PRIVATE);
        ensureDatabase();
        buttonQuery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                queryIpAddr(editIpAddr.getText().toString());
            }
        });
    }

    private void queryIpAddr(final String ipAddr) {
        Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> e) throws Exception {
                String result;
                result = MaxMindApi.lookupIpString(ipAddr);
                e.onNext(result);
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String s) throws Exception {
                        textResult.setText(s);
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MaxMindApi.close();
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
        } else {
            initMaxMind();
        }
    }

    private void initMaxMind() {
        Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> e) throws Exception {
                String result;
                MaxMindApi.open(datPath);
                result = MaxMindApi.getMetaData();
                e.onNext(result);
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String s) throws Exception {
                        textDatInfo.setText(s);
                    }
                });
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
                            initMaxMind();
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

    private void fillWithLocalIp() {
        Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> e) throws Exception {
                e.onNext(getLocalOriginIp());
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String o) throws Exception {
                        editIpAddr.setText(o);
                    }
                });
    }

    private String getLocalOriginIp() {
        try {
            URL url = new URL("http://httpbin.org/ip");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            InputStream inputStream = connection.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            JSONObject jsonObject = new JSONObject(stringBuilder.toString());
            return jsonObject.getString("origin");
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        return "";
    }


}

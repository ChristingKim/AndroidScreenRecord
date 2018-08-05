package com.example.egova.screenrecord;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int RECORD_REQUEST_CODE  = 101;
    private static final int STORAGE_REQUEST_CODE = 102;
    private static final int AUDIO_REQUEST_CODE   = 103;
    private static final int REQUEST_SELECT_VIDEO = 104;


    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private RecordService recordService;

    private Button startBtn;
    private TextView selectVideo;
    private TextView tip;

    private String filePath;
    private enum State {INIT, READY, BUILDING, COMPLETE}
    private State state = State.INIT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        setContentView(R.layout.activity_main);

        startBtn = (Button) findViewById(R.id.start_record);
        startBtn.setEnabled(false);
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (recordService.isRunning()) {
                    recordService.stopRecord();
                    startBtn.setText(R.string.start_record);
                } else {
                    Intent captureIntent = projectionManager.createScreenCaptureIntent();
                    startActivityForResult(captureIntent, RECORD_REQUEST_CODE);
                }
            }
        });

        selectVideo = (TextView)findViewById(R.id.select_video);
        selectVideo.setOnClickListener(clickListener);

        tip = (TextView)findViewById(R.id.tip);

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_REQUEST_CODE);
        }

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] {Manifest.permission.RECORD_AUDIO}, AUDIO_REQUEST_CODE);
        }

        Intent intent = new Intent(this, RecordService.class);
        bindService(intent, connection, BIND_AUTO_CREATE);
    }

    private View.OnClickListener clickListener = new View.OnClickListener(){

        @Override
        public void onClick(View view) {
            if(state == State.INIT || state == State.COMPLETE){
                Intent intent = new Intent();
                intent.setType("video/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Select Video"), REQUEST_SELECT_VIDEO);
            }else if(state == State.READY){
                new AsyncTask<Void, Void, Void>(){

                    @Override
                    protected void onPreExecute(){
                        state = State.BUILDING;
                        tip.setText(R.string.building_gif);
                    }

                    @Override
                    protected Void doInBackground(Void... voids) {

                        List<Bitmap> bitmaps = new ArrayList<Bitmap>();

                        try {
                            BitmapExtractor extractor = new BitmapExtractor();
                            extractor.setFPS(4);
                            extractor.setScope(0, 5);
                            extractor.setSize(540, 960);
                            bitmaps = extractor.createBitmaps(filePath);
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                        String fileName = String.valueOf(System.currentTimeMillis()) + ".gif";
                        String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + fileName;
                        GIFEncoder encoder = new GIFEncoder();
                        encoder.init(bitmaps.get(0));
                        encoder.start(filePath);
                        for (int i = 1; i <bitmaps.size(); i++){
                            encoder.addFrame(bitmaps.get(i));
                        }
                        encoder.finish();
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void aVoid){
                        state = State.COMPLETE;
                        tip.setText(R.string.building_complete);
                        selectVideo.setText(R.string.select_video);
                        Toast.makeText(getApplicationContext(), "存储路径" + filePath, Toast.LENGTH_LONG).show();
                    }
                }.execute();
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(connection);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RECORD_REQUEST_CODE && resultCode == RESULT_OK) {
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            recordService.setMediaProject(mediaProjection);
            recordService.startRecord();
            startBtn.setText(R.string.stop_record);
        }

        if(requestCode == REQUEST_SELECT_VIDEO && resultCode == RESULT_OK){
            Uri videoUri = data.getData();
            filePath = getRealFilePath(videoUri);
            state = State.READY;
            selectVideo.setText(R.string.create_gif);
            tip.setText(R.string.building_init);
        }

    }

    public String getRealFilePath(Uri uri){
        String path = uri.getPath();
        String[] pathArray = path.split(":");
        String fileName = pathArray[pathArray.length - 1];
        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + fileName;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_REQUEST_CODE || requestCode == AUDIO_REQUEST_CODE) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                finish();
            }
        }
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            RecordService.RecordBinder binder = (RecordService.RecordBinder) service;
            recordService = binder.getRecordService();
            recordService.setConfig(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi);
            startBtn.setEnabled(true);
            startBtn.setText(recordService.isRunning() ? R.string.stop_record : R.string.start_record);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {}
    };
}

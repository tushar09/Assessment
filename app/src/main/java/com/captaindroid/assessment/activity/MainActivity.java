package com.captaindroid.assessment.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Network;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.widget.Toast;

import com.captaindroid.assessment.R;
import com.captaindroid.assessment.databinding.ActivityMainBinding;
import com.captaindroid.assessment.utils.Constants;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSink;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private DefaultTrackSelector trackSelector;
    private DefaultTrackSelector.Parameters trackSelectorParameters;
    private TrackGroupArray lastSeenTrackGroupArray;
    private SimpleExoPlayer player;

    private LocationListener locationCallback;
    private LocationManager fusedLocationClient;
    private Location lastLocation;
    private int timer;
    private Thread thread;
    private boolean isLocationUpdateEnabled;

    private SensorManager sensorManager;
    private SensorEventListener sensorEventListener;
    private long lastUpdate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        lastUpdate = System.currentTimeMillis();
        sensorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                getAccelerometer(event);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 100);
                return;
            } else {
                initialize();
            }
        } else {
            initialize();
        }
    }

    private void initialize() {
        initializeLocation();
        initializePlayer(Constants.VIDEO_URL);
    }

    private void initializePlayer(String videoUrl) {
        TrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory();
        RenderersFactory renderersFactory = buildRenderersFactory(false);
        trackSelector = new DefaultTrackSelector(/* context= */ this, trackSelectionFactory);
        DefaultTrackSelector.ParametersBuilder builder = new DefaultTrackSelector.ParametersBuilder(/* context= */ this);

        trackSelectorParameters = builder.build();
        trackSelector.setParameters(trackSelector
                .buildUponParameters().setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setMaxVideoSizeSd()
                .setPreferredTextLanguage("en")
                .setPreferredAudioLanguage("en"));
        lastSeenTrackGroupArray = null;
        player = new SimpleExoPlayer.Builder(this, renderersFactory).setTrackSelector(trackSelector).build();
        player.setAudioAttributes(AudioAttributes.DEFAULT);
        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this, Util.getUserAgent(this, getString(R.string.app_name)));
        //MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(videoUrl));
        MediaSource videoSource = new ExtractorMediaSource(Uri.parse(videoUrl),
                new CacheDataSourceFactory(this, 100 * 1024 * 1024, 5 * 1024 * 1024), new DefaultExtractorsFactory(), null, null);

        player.prepare(videoSource);
        player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
        player.setPlayWhenReady(true);


        player.addListener(new Player.EventListener() {
            @Override
            public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
                if (trackGroups != lastSeenTrackGroupArray) {
                    MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
                    if (mappedTrackInfo != null) {
                        if (mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_VIDEO) == MappingTrackSelector.MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
                            Toast.makeText(MainActivity.this, R.string.error_unsupported_video, Toast.LENGTH_LONG).show();
                        }
                        if (mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_AUDIO) == MappingTrackSelector.MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
                            Toast.makeText(MainActivity.this, R.string.error_unsupported_audio, Toast.LENGTH_LONG).show();
                        }
                    }
                    lastSeenTrackGroupArray = trackGroups;
                }
            }
        });

        binding.player.setPlayer(player);
    }

    private void initializeLocation() {

        locationCallback = new LocationListener() {

            @Override
            public void onLocationChanged(@NonNull Location location) {
                double distance = 0;
                if (lastLocation == null) {
                    lastLocation = location;
                    binding.tvLocation.setText("Last location update: " + System.currentTimeMillis() + "\nLAT: " + location.getLatitude() + "\nLNG: " + location.getLongitude());
                } else {
                    distance = lastLocation.distanceTo(location);
                    if (distance > 9) {
                        lastLocation = location;
                    }
                    binding.tvLocation.setText("Last location update: " + System.currentTimeMillis() + "\nLAT: " + location.getLatitude() + "\nLNG: " + location.getLongitude() + "\nDistance: " + distance);
                }
                fusedLocationClient.removeUpdates(locationCallback);
                Toast.makeText(MainActivity.this, getString(R.string.location_updated), Toast.LENGTH_SHORT).show();
                Log.e("location", location.getLatitude() + " " + location.getLongitude());
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(@NonNull String provider) {

            }

            @Override
            public void onProviderDisabled(@NonNull String provider) {

            }
        };

        fusedLocationClient = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        thread = new Thread(new Runnable() {
            @SuppressLint("MissingPermission")
            @Override
            public void run() {
                timer = 10;
                while (timer > 0) {
                    timer--;
                    if (isLocationUpdateEnabled) {
                        fusedLocationClient.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 1F, locationCallback, Looper.getMainLooper());
                    }

                    if (timer == 0) {
                        timer = 10;
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            binding.tvTimer.setText("Next Location update in: " + timer + " seconds");
                        }
                    });

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        thread.start();

    }

    private RenderersFactory buildRenderersFactory(boolean preferExtensionRenderer) {
        @DefaultRenderersFactory.ExtensionRendererMode
        int extensionRendererMode =
                true
                        ? (preferExtensionRenderer
                        ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                        : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                        : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;
        return new DefaultRenderersFactory(/* context= */ this)
                .setExtensionRendererMode(extensionRendererMode);
    }

    private void getAccelerometer(SensorEvent event) {
        float[] values = event.values;
        float x = values[0];
        float y = values[1];
        float z = values[2];

        String message = String.format("x = %f, y = %f, z = %f", x, y, z);
        if(Math.abs(z) > 1f){
            player.seekTo((long) (player.getCurrentPosition() + (z * -1000f)));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 100: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initialize();
                    Toast.makeText(this, getString(R.string.permissoin_granted), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, getString(R.string.permissoin_not_granted), Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isLocationUpdateEnabled = false;
        if (player != null) {
            player.setPlayWhenReady(false);
            player.stop();
            player.release();
        }

        if (locationCallback != null) {
            fusedLocationClient.removeUpdates(locationCallback);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(sensorEventListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isLocationUpdateEnabled = true;
        if (player != null) {
            player.setPlayWhenReady(true);
        }
        sensorManager.registerListener(sensorEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onStop() {
        super.onStop();
        isLocationUpdateEnabled = false;
        if (player != null) {
            player.setPlayWhenReady(false);
        }

        if (locationCallback != null) {
            fusedLocationClient.removeUpdates(locationCallback);
        }
    }

    public class CacheDataSourceFactory implements DataSource.Factory {
        private final Context context;
        private final DefaultDataSourceFactory defaultDatasourceFactory;
        private final long maxFileSize, maxCacheSize;

        public CacheDataSourceFactory(Context context, long maxCacheSize, long maxFileSize) {
            super();
            this.context = context;
            this.maxCacheSize = maxCacheSize;
            this.maxFileSize = maxFileSize;
            String userAgent = Util.getUserAgent(context, context.getString(R.string.app_name));
            DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
            defaultDatasourceFactory = new DefaultDataSourceFactory(this.context,
                    bandwidthMeter,
                    new DefaultHttpDataSourceFactory(userAgent, bandwidthMeter));
        }

        @Override
        public DataSource createDataSource() {
            LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(maxCacheSize);
            SimpleCache simpleCache = new SimpleCache(new File(context.getCacheDir(), "media"), evictor);
            return new CacheDataSource(simpleCache, defaultDatasourceFactory.createDataSource(),
                    new FileDataSource(), new CacheDataSink(simpleCache, maxFileSize),
                    CacheDataSource.FLAG_BLOCK_ON_CACHE | CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR, null);
        }
    }
}
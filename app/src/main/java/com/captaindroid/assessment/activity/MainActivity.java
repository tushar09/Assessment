package com.captaindroid.assessment.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
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
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 100);
                return;
            }else {
                initialize();
            }
        }else {
            initialize();
        }
    }

    private void initialize(){
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
        MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(videoUrl));

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

    private void initializeLocation(){

        locationCallback = new LocationListener(){

            @Override
            public void onLocationChanged(@NonNull Location location) {
                double distance = 0;
                if(lastLocation == null){
                    lastLocation = location;
                    binding.tvLocation.setText("Last location update: " + System.currentTimeMillis()  + "\nLAT: " + location.getLatitude() + "\nLNG: " + location.getLongitude());
                }else{
                    distance = lastLocation.distanceTo(location);
                    if(distance > 9){
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
            @Override
            public void run() {
                timer = 10;
                while(timer > 0){
                    timer--;
                    fusedLocationClient.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 1F, locationCallback, Looper.getMainLooper());
                    if(timer == 0){
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
}
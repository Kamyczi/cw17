package com.example.gpsmaps;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private static final String TAG = "Ks4g";
    private static final int MY_PERMISSION_ACCESS_FINE_LOCATION = 1;

    private LinearLayout linearLayout;
    private TextView text_net, text_gps, bestProvider, longitude, latitude, archivalData;
    private MapView oms;
    private MapController mapController;

    private LocationManager locationManager;
    private Criteria criteria;
    private Location location;
    private String bp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);


        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        Context context = getApplicationContext();
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context));

        linearLayout = findViewById(R.id.main);
        text_net = findViewById(R.id.network);
        text_gps = findViewById(R.id.gps);
        bestProvider = findViewById(R.id.bestProvider);
        longitude = findViewById(R.id.longitude);
        latitude = findViewById(R.id.latitude);
        archivalData = findViewById(R.id.archivalData);
        oms = findViewById(R.id.osm_map);

        oms.setTileSource(TileSourceFactory.MAPNIK);
        oms.setBuiltInZoomControls(true);
        oms.setMultiTouchControls(true);
        mapController = (MapController) oms.getController();
        mapController.setZoom(12);


        criteria = new Criteria();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        bp = locationManager.getBestProvider(criteria, true);
        if (bp == null) bp = LocationManager.GPS_PROVIDER;
        bestProvider.setText("Best provider: " + bp);


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSION_ACCESS_FINE_LOCATION
            );
            return;
        }


        location = locationManager.getLastKnownLocation(bp);
        if (location != null) {
            setMapToLocation(location);
        }

        locationManager.requestLocationUpdates(bp, 500, 0.5f, this);


        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = false;
        try {
            gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (gpsEnabled) {
            text_gps.setText("GPS Enabled");
            text_gps.setTextColor(Color.GREEN);
        } else {
            text_gps.setText("GPS Disabled");
            text_gps.setTextColor(Color.RED);
        }

        if (isNetworkAvailable()) {
            text_net.setText("Internet Connected");
            text_net.setTextColor(Color.GREEN);
        } else {
            text_net.setText("No Internet");
            text_net.setTextColor(Color.RED);
        }

        archivalData.setText("Measurement readings:\n\n Longitude: " + location.getLongitude() +"|| Latitude: " + location.getLatitude());
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.send_sms) {
            sendSMSWithCoordinates();
            return true;
        } else if (item.getItemId() == R.id.save_screenshot) {
            captureMapScreenshot();
            return true;
        } else if (item.getItemId() == R.id.share_results) {
            shareResults();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    private void setMapToLocation(Location loc) {
        GeoPoint point = new GeoPoint(loc.getLatitude(), loc.getLongitude());
        mapController.setCenter(point);
        addMarkerToMap(point);
    }

    private void addMarkerToMap(GeoPoint center) {
        Marker marker = new Marker(oms);
        marker.setPosition(center);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle("My position");
        marker.setIcon(getResources().getDrawable(R.drawable.marker));
        oms.getOverlays().clear();
        oms.getOverlays().add(marker);
        oms.invalidate();
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        this.location = location;
        archivalData.append("Lon: " + location.getLongitude() + " | Lat: " + location.getLatitude() + "\n");
        setMapToLocation(location);
        Log.d(TAG, "Updated: " + location.getLongitude() + " " + location.getLatitude());
    }


    private void sendSMSWithCoordinates() {
        if (location == null) {
            Toast.makeText(this, "Brak lokalizacji", Toast.LENGTH_SHORT).show();
            return;
        }
        String message = "Moje koordynaty: \nLat: " + location.getLatitude() +
                "\nLon: " + location.getLongitude();
        Intent smsIntent = new Intent(Intent.ACTION_SEND);
        smsIntent.setType("text/plain");
        smsIntent.putExtra(Intent.EXTRA_TEXT, message);
        startActivity(smsIntent);
    }

    private void captureMapScreenshot() {
        try {
            oms.setDrawingCacheEnabled(true);
            Bitmap b = Bitmap.createBitmap(oms.getDrawingCache());
            oms.setDrawingCacheEnabled(false);

            String fileName = "map_screenshot_" + System.currentTimeMillis() + ".png";
            OutputStream fos;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/MapScreenshots");

                Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                fos = resolver.openOutputStream(imageUri);
            } else {
                File dir = new File(Environment.getExternalStorageDirectory() + "/DCIM/MapScreenshots");
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, fileName);
                fos = new FileOutputStream(file);
            }

            b.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();

            Toast.makeText(this, "Zapisano screenshot!", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Błąd zapisu screena", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareResults() {
        String text = archivalData.getText().toString();
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(shareIntent, "Udostępnij wyniki"));
    }


    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }



    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); // ✔ konieczne

        if (requestCode == MY_PERMISSION_ACCESS_FINE_LOCATION) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                recreate();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}

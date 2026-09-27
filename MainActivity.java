package com.miletrace.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity implements LocationListener {
    private static final int REQ_LOCATION = 1001;
    private static final String PREFS = "miletrace";
    private static final String HISTORY = "history";

    private LocationManager locationManager;
    private SharedPreferences prefs;

    private LinearLayout root, content;
    private TextView status, distanceView, modeView, liveDistance, liveMode, gpsView, speedView, elapsedView, accuracyView, coordsView, todayView, historyView;
    private Button startBtn, pauseBtn, endBtn;
    private String mode = "Walking";
    private boolean tracking = false, paused = false;
    private long startElapsed = 0L, pausedAccumulated = 0L, pauseStarted = 0L;
    private double totalMeters = 0.0, lastSpeedKmh = 0.0;
    private Location lastLocation;
    private final ArrayList<Location> route = new ArrayList<>();
    private Timer uiTimer;

    private int bg = Color.rgb(11,18,32), panel = Color.rgb(18,28,45), text = Color.rgb(238,242,247), muted = Color.rgb(148,163,184);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        buildShell();
        showHome();
    }

    private TextView tv(String s, float sp) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(text); t.setTextSize(sp); t.setPadding(0,6,0,6);
        return t;
    }

    private Button btn(String s) {
        Button b = new Button(this);
        b.setText(s); b.setTextColor(text); b.setAllCaps(false);
        return b;
    }

    private LinearLayout box() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL); l.setPadding(20,20,20,20);
        l.setBackgroundColor(panel);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0,10,0,0); l.setLayoutParams(p);
        return l;
    }

    private void buildShell() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(18,18,18,18); root.setBackgroundColor(bg);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = tv("MileTrace\nEvery distance, traced live.", 22); header.addView(brand, new LinearLayout.LayoutParams(0,-2,1));
        status = tv("Ready", 12); status.setTextColor(muted); header.addView(status);
        root.addView(header);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private void clear() { content.removeAllViews(); }

    private TextView distanceText(String s) {
        TextView t = tv(s, 46); t.setGravity(Gravity.CENTER); t.setTextColor(text); t.setPadding(0,24,0,10); return t;
    }

    private void showHome() {
        clear();
        LinearLayout hero = box();
        hero.addView(tv("LIVE DISTANCE", 12));
        distanceView = distanceText(formatKm(0)); hero.addView(distanceView);
        modeView = tv("🚶  Walking", 16); modeView.setGravity(Gravity.CENTER); hero.addView(modeView);
        startBtn = btn("START JOURNEY"); hero.addView(startBtn);
        startBtn.setOnClickListener(v -> startJourney());
        content.addView(hero);

        LinearLayout modes = new LinearLayout(this); modes.setGravity(Gravity.CENTER);
        String[] names = {"Walking","Running","Cycling","Bike"};
        for (String n : names) {
            Button m = btn(n); modes.addView(m, new LinearLayout.LayoutParams(0,70,1));
            m.setOnClickListener(v -> { if(!tracking){ mode=n; modeView.setText(icon(n)+"  "+n); } });
        }
        content.addView(modes);

        LinearLayout today = box(); today.addView(tv("Today's Distance",13));
        todayView = tv(formatKm(todayDistance()),28); today.addView(todayView); content.addView(today);

        LinearLayout recent = box(); recent.addView(tv("Recent Journeys",16));
        historyView = tv(renderHistory(3),14); recent.addView(historyView); content.addView(recent);
    }

    private void showLive() {
        clear();
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = btn("←"); top.addView(back, new LinearLayout.LayoutParams(70,60)); back.setOnClickListener(v -> { stopLocationUpdates(); showHome(); });
        liveMode = tv(icon(mode)+"  "+mode,18); top.addView(liveMode);
        content.addView(top);

        LinearLayout hero = box(); hero.addView(tv("LIVE DISTANCE",12));
        liveDistance = distanceText(formatKm(totalMeters)); hero.addView(liveDistance);
        LinearLayout stats = new LinearLayout(this); stats.setGravity(Gravity.CENTER);
        speedView = tv("0.0\nkm/h",16); speedView.setGravity(Gravity.CENTER);
        elapsedView = tv("00:00:00\nTIME",16); elapsedView.setGravity(Gravity.CENTER);
        accuracyView = tv("—\nGPS ACC.",16); accuracyView.setGravity(Gravity.CENTER);
        stats.addView(speedView,new LinearLayout.LayoutParams(0,80,1)); stats.addView(elapsedView,new LinearLayout.LayoutParams(0,80,1)); stats.addView(accuracyView,new LinearLayout.LayoutParams(0,80,1));
        hero.addView(stats); content.addView(hero);

        LinearLayout gps = box(); gpsView = tv("Waiting for GPS…",14); coordsView = tv("Waiting for a position…",13); coordsView.setTextColor(muted); gps.addView(gpsView); gps.addView(coordsView); content.addView(gps);

        LinearLayout controls = new LinearLayout(this);
        pauseBtn = btn("PAUSE"); endBtn = btn("END JOURNEY");
        controls.addView(pauseBtn,new LinearLayout.LayoutParams(0,70,1)); controls.addView(endBtn,new LinearLayout.LayoutParams(0,70,1));
        pauseBtn.setOnClickListener(v -> togglePause()); endBtn.setOnClickListener(v -> endJourney());
        content.addView(controls);
    }

    private void startJourney() {
        if (!hasLocationPermission()) { requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION); return; }
        tracking=true; paused=false; totalMeters=0; lastSpeedKmh=0; lastLocation=null; route.clear();
        startElapsed=SystemClock.elapsedRealtime(); pausedAccumulated=0; status.setText("GPS starting…");
        showLive(); startLocationUpdates(); startUiTimer();
    }

    private void startLocationUpdates() {
        if (!hasLocationPermission()) return;
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0.0f, this);
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 0.0f, this);
        } catch (SecurityException e) { gpsView.setText("Location permission is required."); }
    }

    private void stopLocationUpdates() {
        try { locationManager.removeUpdates(this); } catch (SecurityException ignored) {}
        if (uiTimer != null) { uiTimer.cancel(); uiTimer=null; }
    }

    private void togglePause() {
        if (!tracking) return;
        if (!paused) {
            paused=true; pauseStarted=SystemClock.elapsedRealtime(); pauseBtn.setText("RESUME"); status.setText("Paused");
        } else {
            paused=false; pausedAccumulated += SystemClock.elapsedRealtime()-pauseStarted; lastLocation=null; pauseBtn.setText("PAUSE"); status.setText("Tracking");
        }
    }

    private void endJourney() {
        if (!tracking) return;
        long duration = elapsedMs();
        stopLocationUpdates();
        tracking=false; paused=false;
        saveJourney(duration);
        status.setText("Saved");
        showHome();
    }

    @Override public void onLocationChanged(Location loc) {
        if (!tracking || paused) return;
        if (loc == null) return;
        float acc = loc.hasAccuracy() ? loc.getAccuracy() : 999f;
        gpsView.setText("GPS: " + (acc < 25 ? "Good" : acc < 50 ? "Fair" : "Weak"));
        accuracyView.setText(String.format(Locale.US,"%.0f m\nGPS ACC.", acc));
        coordsView.setText(String.format(Locale.US,"%.6f, %.6f",loc.getLatitude(),loc.getLongitude()));
        if (acc > 50f) { status.setText("Filtering low-accuracy GPS"); return; }

        if (lastLocation != null) {
            float[] out = new float[1];
            Location.distanceBetween(lastLocation.getLatitude(),lastLocation.getLongitude(),loc.getLatitude(),loc.getLongitude(),out);
            double d = out[0];
            long dt = Math.max(1, loc.getTime()-lastLocation.getTime());
            double impliedKmh = d / dt * 3.6;
            double max = maxSpeed(mode);
            if (impliedKmh <= max && d <= 2000) {
                totalMeters += d;
                lastSpeedKmh = loc.hasSpeed() ? loc.getSpeed()*3.6 : impliedKmh;
            } else {
                status.setText("Filtering GPS jump");
            }
        }
        lastLocation = loc;
        route.add(loc);
        liveDistance.setText(formatKm(totalMeters));
    }

    @Override public void onProviderEnabled(String provider) { if(tracking) gpsView.setText("GPS provider enabled"); }
    @Override public void onProviderDisabled(String provider) { if(tracking) gpsView.setText("GPS provider disabled"); }
    @Override public void onStatusChanged(String provider,int status,Bundle extras) {}

    private void startUiTimer() {
        uiTimer = new Timer();
        uiTimer.scheduleAtFixedRate(new TimerTask(){ public void run(){ runOnUiThread(() -> {
            if(!tracking) return;
            elapsedView.setText(formatTime(elapsedMs())+"\nTIME");
            speedView.setText(String.format(Locale.US,"%.1f\nkm/h",lastSpeedKmh));
            if(liveDistance!=null) liveDistance.setText(formatKm(totalMeters));
        });}},0,500);
    }

    private long elapsedMs() {
        long now=SystemClock.elapsedRealtime();
        long endPause = paused ? now-pauseStarted : 0;
        return Math.max(0, now-startElapsed-pausedAccumulated-endPause);
    }

    private void saveJourney(long duration) {
        try {
            JSONArray a = new JSONArray(prefs.getString(HISTORY,"[]"));
            JSONObject o = new JSONObject();
            o.put("mode",mode); o.put("distanceMeters",totalMeters); o.put("durationMs",duration);
            o.put("averageKmh",duration>0 ? (totalMeters/1000.0)/(duration/3600000.0) : 0);
            o.put("startedAt",System.currentTimeMillis()-duration); o.put("endedAt",System.currentTimeMillis());
            JSONArray points = new JSONArray();
            for(Location l:route){ JSONObject p=new JSONObject(); p.put("lat",l.getLatitude()); p.put("lon",l.getLongitude()); p.put("time",l.getTime()); p.put("accuracy",l.getAccuracy()); points.put(p); }
            o.put("route",points); a.put(o);
            prefs.edit().putString(HISTORY,a.toString()).apply();
        } catch(Exception ignored){}
    }

    private double todayDistance() {
        double sum=0; String day=new SimpleDateFormat("yyyyMMdd",Locale.US).format(new Date());
        try { JSONArray a=new JSONArray(prefs.getString(HISTORY,"[]")); for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i); String d=new SimpleDateFormat("yyyyMMdd",Locale.US).format(new Date(o.optLong("endedAt"))); if(day.equals(d)) sum+=o.optDouble("distanceMeters");}} catch(Exception ignored){}
        return sum;
    }

    private String renderHistory(int limit) {
        StringBuilder s=new StringBuilder(); try {
            JSONArray a=new JSONArray(prefs.getString(HISTORY,"[]")); int start=Math.max(0,a.length()-limit);
            if(a.length()==0) return "No journeys yet.";
            for(int i=a.length()-1;i>=start;i--){ JSONObject o=a.getJSONObject(i); s.append(o.optString("mode")).append(" • ").append(formatKm(o.optDouble("distanceMeters"))).append(" • ").append(formatTime(o.optLong("durationMs"))).append("\n"); }
        } catch(Exception e){return "History unavailable.";} return s.toString().trim();
    }

    private void showHistory() {
        clear(); TextView h=tv("Journeys",28); content.addView(h);
        TextView all=tv(renderHistory(100),15); content.addView(all);
        Button clearBtn=btn("Clear local history"); content.addView(clearBtn); clearBtn.setOnClickListener(v -> {prefs.edit().remove(HISTORY).apply(); showHistory();});
    }

    private boolean hasLocationPermission(){ return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED; }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){ super.onRequestPermissionsResult(r,p,g); if(r==REQ_LOCATION && hasLocationPermission()) startJourney(); else status.setText("Location permission not granted"); }

    private String formatKm(double m){return String.format(Locale.US,"%.2f km",m/1000.0);}
    private String formatTime(long ms){long sec=ms/1000; return String.format(Locale.US,"%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60);}
    private double maxSpeed(String m){if(m.equals("Walking"))return 15;if(m.equals("Running"))return 35;if(m.equals("Cycling"))return 70;return 180;}
    private String icon(String m){if(m.equals("Walking"))return "🚶";if(m.equals("Running"))return "🏃";if(m.equals("Cycling"))return "🚴";return "🏍️";}

    @Override public void onBackPressed(){ if(tracking) { Toast.makeText(this,"End or pause the journey before leaving the live screen.",Toast.LENGTH_SHORT).show(); } else { showHome(); } }
}

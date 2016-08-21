package com.oindril.dutta.aersense;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private OkHttpClient client = new OkHttpClient();
    private SharedPreferences.Editor prefEditor;
    private final String CONT_ID = "CONTAINERID";
    private final String DEV_ID = "DEVICEID";
    private final String DELAY_MS = "DELAYMS";
    private final String DATA_SELC = "JSONOPTION";
    private final String API_ENTRY = "APIENTRY";
    private final String ACC_ENTRY = "ACCENTRY";
    private final String IGN_ENTRY = "IGNITION";
    private final String FLV_ENTRY = "FUELLEVL";
    private Spinner dropdown;
    private Spinner ignition;
    private EditText fuelLevel;
    private EditText devId;
    private EditText contId;
    private EditText delayInMs;
    private EditText apiKeyEntry;
    private EditText accIdEntry;
    private TextView dataDisplay;
    private LocationListener gpsListener = new GpsLocationListener();
    private LocationListener networkListener = new NetworkLocationListener();
    private static final String[] ignitions = {"Off", "On"};
    private static final String[] options = {"AerTrak V1 Data", "Bruno's Signature Data", "Alt AerTrak V2 Data"};
    private static final String[][] jsonOptions = new String[][]{
            {"mobileId", "mobileIdType", "serviceType", "messageType", "sequenceNumber", "updateTime", "timeOfFix", "latitude", "longitude", "altitude", "speed", "heading"},
            {"lastUpdated", "latitude", "longitude", "altitude", "speed"},
            {"mobileId", "mobileIdType", "serviceType", "messageType", "sequenceNumber", "updateTime", "timeOfFix", "latitude", "longitude", "altitude", "speed", "heading", "satellites", "fitStatus", "carrier", "rssi", "commState", "hdop", "inputs", "unitStatus", "eventIndex", "eventCode", "accums", "append", "accumulator0", "accumulator1", "accumulator2", "accumulator3", "accumulator4", "accumulator5", "accumulator6", "accumulator7", "accumulator8", "accumulator9", "accumulator10", "accumulator11", "accumulator12", "accumulator13", "accumulator14", "accumulator15"}};
    private int jsonOption;
    private Context appContext;
    private JSONObject data;
    private LocationManager locationManager;
    private Location mostRecentLocation;
    private boolean checked;
    private int msDelay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Basic Setup Code
        super.onCreate(savedInstanceState);
        appContext = getApplicationContext();
        setContentView(R.layout.activity_main);
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);
        //Local UI Components setup
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ToggleButton startStop = (ToggleButton) findViewById(R.id.toggleButton);
        assert startStop != null;
        startStop.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                checked = isChecked;
                try {
                    msDelay = Integer.parseInt(delayInMs.getText().toString());
                    if(msDelay < 0)
                        toast("Please enter time > 0!");
                } catch (Exception e) {
                    toast("Please enter a proper millisecond time!");
                    msDelay = -1;
                }
                Double fuel;
                try {
                    fuel = Double.parseDouble(fuelLevel.getText().toString());
                    if(fuel < 0 || fuel > 100)
                        toast("Please enter fuel within 0 - 100!");
                } catch (Exception e) {
                    toast("Please enter a proper fuel level!");
                    fuel = -1.0;
                }
                if(msDelay < 0) {
                    startStop.setChecked(false);
                    return;
                }
                if(fuel < 0 || fuel > 100) {
                    startStop.setChecked(false);
                    return;
                }
                fuelLevel.setEnabled(!isChecked);
                ignition.setEnabled(!isChecked);
                if(jsonOption != 2) {
                    fuelLevel.setEnabled(false);
                    ignition.setEnabled(false);
                }
                devId.setEnabled(!isChecked);
                contId.setEnabled(!isChecked);
                delayInMs.setEnabled(!isChecked);
                dropdown.setEnabled(!isChecked);
                accIdEntry.setEnabled(!isChecked);
                apiKeyEntry.setEnabled(!isChecked);
                send(isChecked, true);
            }
        });
        //Global UI Components Setup
        dataDisplay = (TextView) findViewById(R.id.jsonDataDisplay);
        devId = (EditText) findViewById(R.id.deviceID);
        contId = (EditText) findViewById(R.id.containerID);
        delayInMs = (EditText) findViewById(R.id.delayms);
        fuelLevel = (EditText) findViewById(R.id.fuelLvl);
        ignition = (Spinner) findViewById(R.id.ignition);
        dropdown = (Spinner) findViewById(R.id.profilePicker);
        apiKeyEntry = (EditText) findViewById(R.id.apienter);
        accIdEntry = (EditText) findViewById(R.id.accId);
        ignition.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ignitions));
        dropdown.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
        dropdown.setOnItemSelectedListener(this);
        jsonOption = dropdown.getSelectedItemPosition();
        //Get data from SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("com.dutta.aersense.preferences", Context.MODE_PRIVATE);
        prefEditor = sharedPreferences.edit();
        prefEditor.apply();
        devId.setText(sharedPreferences.getString(DEV_ID, devId.getText().toString()));
        contId.setText(sharedPreferences.getString(CONT_ID, contId.getText().toString()));
        delayInMs.setText(sharedPreferences.getString(DELAY_MS, delayInMs.getText().toString()));
        apiKeyEntry.setText(sharedPreferences.getString(API_ENTRY, apiKeyEntry.getText().toString()));
        accIdEntry.setText(sharedPreferences.getString(ACC_ENTRY, accIdEntry.getText().toString()));
        fuelLevel.setText(sharedPreferences.getString(FLV_ENTRY, fuelLevel.getText().toString()));
        ignition.setSelection(sharedPreferences.getInt(IGN_ENTRY, ignition.getSelectedItemPosition()));
        dropdown.setSelection(sharedPreferences.getInt(DATA_SELC, jsonOption));
        //Get data from savedInstanceState
        if(savedInstanceState != null) {
            devId.setText(sharedPreferences.getString(DEV_ID, devId.getText().toString()));
            devId.setText(sharedPreferences.getString(CONT_ID, devId.getText().toString()));
            delayInMs.setText(sharedPreferences.getString(DELAY_MS, delayInMs.getText().toString()));
            apiKeyEntry.setText(sharedPreferences.getString(API_ENTRY, apiKeyEntry.getText().toString()));
            accIdEntry.setText(sharedPreferences.getString(ACC_ENTRY, accIdEntry.getText().toString()));
            fuelLevel.setText(sharedPreferences.getString(FLV_ENTRY, fuelLevel.getText().toString()));
            ignition.setSelection(sharedPreferences.getInt(IGN_ENTRY, ignition.getSelectedItemPosition()));
            dropdown.setSelection(sharedPreferences.getInt(DATA_SELC, dropdown.getSelectedItemPosition()));
        }
        //Get Application Working
        registerLocationListeners();
    }

    public void send(boolean sending, final boolean user) {
        if(!isConnected()) {
            toast("No Connectivity Acquired...");
            return;
        }
        if(mostRecentLocation == null) {
            toast("No Location Acquired...");
            Intent intent = getIntent();
            finish();
            startActivity(intent);
            return;
        }
        if (user && sending)
            toast("Sending data to container " + contId.getText().toString() + " for device " + devId.getText().toString() + " using the "+options[jsonOption]+" JSON payload format...");
        else if(user)
            toast("Stopping sending.");
        dataFromInputs();
        locationToJson(sending);
        updateDataDisplay();
        post("https://api.aercloud.aeris.com/v1/"+accIdEntry.getText().toString()+"/scls/"+
                devId.getText().toString()+"/containers/"+
                contId.getText().toString()+"/contentInstances?apiKey="+apiKeyEntry.getText().toString(), getJSON().toString(), new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { toast("Sending Failed..."); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    //final String responseStr = response.body().string();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if(checked)
                                (new Handler()).postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        send(false, false);
                                    }
                                }, msDelay);
                        }
                    });
                } else
                    toast("Response Unsuccessful...");
            }
        });
    }

    public Call post(String url, String json, Callback callback) {
        RequestBody body = RequestBody.create(JSON, json);
        Request request = new Request.Builder().url(url).post(body).build();
        Call call = client.newCall(request);
        call.enqueue(callback);
        return call;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        jsonOption = position;
        if(jsonOption != 2) {
            fuelLevel.setEnabled(false);
            ignition.setEnabled(false);
        } else {
            fuelLevel.setEnabled(true);
            ignition.setEnabled(true);
        }
        data = new JSONObject();
        for (int i = 0; i < jsonOptions[jsonOption].length; i++) {
            try {
                data.put(jsonOptions[jsonOption][i], "");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (position == 0) {
            try {
                data.put("mobileId", "456203765" + devId.getText().toString().charAt(devId.getText().toString().length() - 1));
                data.put("mobileIdType", 1);
                data.put("serviceType", 0);
                data.put("messageType", 2);
                data.put("sequenceNumber", 9804);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else //noinspection StatementWithEmptyBody
        if (position == 1) {}
        else if (position == 2) {
            try {
                data.put("mobileId", "456203765" + devId.getText().toString().charAt(devId.getText().toString().length() - 1));
                data.put("mobileIdType", 0);
                data.put("serviceType", 0);
                data.put("messageType", 0);
                data.put("sequenceNumber", 0);
                data.put("satellites", 7);
                data.put("fitStatus", 0);
                data.put("carrier", 260);
                data.put("rssi", 81);
                data.put("commState", 111);
                data.put("hdop", 1.1);
                data.put("inputs", 17);
                data.put("unitStatus", 0);
                data.put("eventIndex", 17);
                data.put("eventCode", "a");
                data.put("accums", 16);
                data.put("append", 0);
                data.put("accumulator0", "00000000");
                data.put("accumulator1", "00000000");
                data.put("accumulator2", "00000000");
                data.put("accumulator3", "00000000");
                data.put("accumulator4", "00000000");
                data.put("accumulator5", "00000000");
                data.put("accumulator6", "00000000");
                data.put("accumulator7", "00000000");
                data.put("accumulator8", "00000000");
                data.put("accumulator9", "00000000");
                data.put("accumulator10", "00000000");
                data.put("accumulator11", "00000000");
                data.put("accumulator12", "00000000");
                data.put("accumulator13", "00000000");
                data.put("accumulator14", "00000000");
                data.put("accumulator15", "00000000");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        updateDataDisplay();
    }

    public JSONObject getJSON() {
        JSONObject jsonData = new JSONObject();
        try {
            for (int i = 0; i < jsonOptions[jsonOption].length; i++)
                jsonData.put(jsonOptions[jsonOption][i], data.get(jsonOptions[jsonOption][i]));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonData;
    }

    public String getData() {
        JSONObject sendingData = getJSON();
        String dataStringDisplay = "{\n";
        try {
            for (int i = 0; i < jsonOptions[jsonOption].length; i++)
                dataStringDisplay += "  \"" + jsonOptions[jsonOption][i] + "\": \"" + sendingData.get(jsonOptions[jsonOption][i]).toString() + "\",\n";
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return dataStringDisplay.substring(0, dataStringDisplay.length() - 2) + dataStringDisplay.substring(dataStringDisplay.length() - 1) + "}";
    }

    public void updateDataDisplay() {
        dataDisplay.setText(getData());
    }

    public void dataFromInputs() {
        try {
            String hex = Integer.toHexString((int)(Double.parseDouble(fuelLevel.getText().toString())*100)).toUpperCase();
            data.put("accumulator4", ("00000000"+hex).substring(hex.length()));
            data.put("accumulator8", "0000000"+ignition.getSelectedItemPosition());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void locationToJson(boolean log) {
        if (mostRecentLocation != null) {
            try {
                data.put("updateTime", (long)(Math.round(System.currentTimeMillis()/1000)));
                data.put("latitude", mostRecentLocation.getLatitude());
                data.put("longitude", mostRecentLocation.getLongitude());
                data.put("timeOfFix", (long)(Math.round(mostRecentLocation.getTime()/1000)));
                data.put("lastUpdated", (long)(Math.round(mostRecentLocation.getTime()/1000)));
                if (mostRecentLocation.hasAltitude()) data.put("altitude", mostRecentLocation.getAltitude());
                else data.put("altitude", 0);
                if (mostRecentLocation.hasSpeed()) data.put("speed", mostRecentLocation.getSpeed());
                else data.put("speed", 0);
                if (mostRecentLocation.hasBearing()) data.put("heading", mostRecentLocation.getBearing());
                else data.put("heading", 5);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if(log)
            toast("Provide Location Access!");
    }

    //Posting Data Functions
    public boolean isConnected(){
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Activity.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    //Location Functions
    private void registerLocationListeners() {
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        boolean enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!enabled) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }
        Criteria gpsLocation = new Criteria();
        Criteria networkLocation = new Criteria();
        gpsLocation.setAccuracy(Criteria.ACCURACY_FINE);
        networkLocation.setAccuracy(Criteria.ACCURACY_COARSE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            toast("Provide Location Access!");
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0, gpsListener);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 0, networkListener);
    }

    @SuppressWarnings("unused")
    private void unRegisterLocationListeners() {
        if (locationManager != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                toast("Provide Location Access!");
                return;
            }
            locationManager.removeUpdates(gpsListener);
            locationManager.removeUpdates(networkListener);
        }
    }

    public class GpsLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(Location location) {
            if (isBetterLocation(location, mostRecentLocation)) mostRecentLocation = location;
        }
        @Override
        public void onProviderDisabled(String Provider) {}
        @Override
        public void onProviderEnabled(String Provider) {}
        @Override
        public void onStatusChanged(String provider, int status, Bundle Extras) {}
    }

    public class NetworkLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(Location location) {
            if (isBetterLocation(location, mostRecentLocation)) mostRecentLocation = location;
        }
        @Override
        public void onProviderDisabled(String Provider) {}
        @Override
        public void onProviderEnabled(String Provider) {}
        @Override
        public void onStatusChanged(String provider, int status, Bundle Extras) {}
    }

    private boolean isBetterLocation(Location location, Location mostRecentLocation) {
        if (mostRecentLocation == null) return true;
        long timeDelta = location.getTime() - mostRecentLocation.getTime();
        boolean extremelyNew = timeDelta > 120000;
        boolean extremelyOld = timeDelta > -120000;
        boolean newer = timeDelta > 0;
        if (extremelyNew) return true;
        else if (extremelyOld) return false;
        else {
            int accuracyDelta = (int) (location.getAccuracy() - mostRecentLocation.getAccuracy());
            boolean lessAccurate = accuracyDelta > 0;
            boolean moreAccurate = accuracyDelta < 0;
            boolean fromSameProvider = isSameProvider(location.getProvider(), mostRecentLocation.getProvider());
            return moreAccurate || newer && !lessAccurate || newer && fromSameProvider;
        }
    }

    private boolean isSameProvider(String providerLocation, String providerCurrentLocation) {
        if (providerLocation == null) return providerCurrentLocation == null;
        return providerLocation.equals(providerCurrentLocation);
    }

    //Miscellaneous Functions
    public void toast(String message) {
        toast(message, false);
    }
    public void toast(String message, boolean length) {
        int duration = Toast.LENGTH_SHORT;
        if(length)
            duration = Toast.LENGTH_LONG;
        if(appContext != null)
            Toast.makeText(appContext, message, duration).show();
    }
        @Override
        protected void onPause() {
            super.onPause();
            prefEditor.putString(CONT_ID, contId.getText().toString());
            prefEditor.putString(DEV_ID, devId.getText().toString());
            prefEditor.putString(DELAY_MS, delayInMs.getText().toString());
            prefEditor.putString(API_ENTRY, apiKeyEntry.getText().toString());
            prefEditor.putString(ACC_ENTRY, accIdEntry.getText().toString());
            prefEditor.putString(FLV_ENTRY, fuelLevel.getText().toString());
            prefEditor.putInt(IGN_ENTRY, ignition.getSelectedItemPosition());
            prefEditor.putInt(DATA_SELC, jsonOption);
            prefEditor.apply();
        }
        @Override
        protected void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putString(CONT_ID, contId.getText().toString());
            outState.putString(DEV_ID, devId.getText().toString());
            outState.putString(DELAY_MS, delayInMs.getText().toString());
            outState.putString(API_ENTRY, apiKeyEntry.getText().toString());
            outState.putString(ACC_ENTRY, accIdEntry.getText().toString());
            outState.putString(FLV_ENTRY, fuelLevel.getText().toString());
            outState.putInt(IGN_ENTRY, ignition.getSelectedItemPosition());
            outState.putInt(DATA_SELC, jsonOption);
        }
    @Override
    public void onNothingSelected(AdapterView<?> parent) { dropdown.setSelection(0); }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) { return true; }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) { return super.onOptionsItemSelected(item); }
}
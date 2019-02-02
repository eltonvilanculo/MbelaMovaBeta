package mmconsultoria.co.mz.mbelamova.activity;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.AutocompleteFilter;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.SquareCap;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.annotations.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentActivity;
import mmconsultoria.co.mz.mbelamova.Common.Common;
import mmconsultoria.co.mz.mbelamova.R;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


@RequiresApi(api = Build.VERSION_CODES.P)
public class ClientMapActivity extends FragmentActivity implements NavigationView.OnNavigationItemSelectedListener,
        OnMapReadyCallback,GoogleApiClient.OnConnectionFailedListener,
        GoogleApiClient.ConnectionCallbacks {

    private static final String TAG = "MapsActivity";

    private static final String FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    private static final String COURSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1234;
    private static final float DEFAULT_ZOOM = 15f;

    private static final LatLngBounds LAT_LNG_BOUNDS =new LatLngBounds(
            new LatLng(-40,-168), new LatLng(71,136));

    //Widget
    private ImageView mGps;
    private DrawerLayout mDrawerLayout;
    private ImageView mPerfilFoto;
    private  ImageView navigation_menu;
    private CardView mSettings;

    //vars
    private Boolean mLocationPermissionGranted = false;
    private GoogleMap mMap;
    private FusedLocationProviderClient mFusedLocationProviderClient; // Voce me deu Problemas
    private Location lastLocation;
    private LocationRequest locationRequest;
    private GoogleApiClient mGoogleApiClient;
    DatabaseReference driver;
    GeoFire geoFire;
    Marker currentMarker;


    private Polyline currentPolyline;

    // Animacao do carro
    private List<LatLng> polyLineList;
    private Marker carMarker;
    private float v;
    private  double lat,lng;
    private Handler handler;
    private LatLng startPosition,endPosition,currentPosition;
    private int index,next;
    private Button tracarRota;
    private  String destination;
    private PolylineOptions polylineOptions,blackPolylineOptions;
    private Polyline blackPolyline,greyPolyline;
    private IGoogleAPI mService;

    public interface OnArticleSelectedListener {
        public void onArticleSelected(Uri articleUri);
    }
    Runnable drawPathRunnable =new Runnable() {
        @Override
        public void run() {
            if(index<polyLineList.size()-1){
                index++;
                next=index+1;
            }
            if(index<polyLineList.size()-1){
                startPosition= polyLineList.get(index);
                endPosition=polyLineList.get(next);

            }

            final ValueAnimator valueAnimator= ValueAnimator.ofInt(0,1);
            valueAnimator.setDuration(3000);
            valueAnimator.setInterpolator(new LinearInterpolator());
            valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    v=valueAnimator.getAnimatedFraction();
                    lng=v*endPosition.longitude+(1-v)*startPosition.longitude;
                    lat=v*endPosition.latitude+(1-v)*startPosition.latitude;
                    LatLng newPos=new LatLng(lat,lng);
                    carMarker.setPosition(newPos);
                    carMarker.setAnchor(0.5f,5f);
                    carMarker.setRotation(getBearing(startPosition,newPos));
                    mMap.moveCamera(CameraUpdateFactory.newCameraPosition(
                            new CameraPosition.Builder()
                                    .target(newPos)
                                    .zoom(15.5f)
                                    .build()
                    ));
                    valueAnimator.start();
                    handler.postDelayed(drawPathRunnable,3000); // Problema, tinha que ser "this"
                }
            });
        }
    };

    private float getBearing(LatLng startPosition, LatLng endPosition) {
        double lat=Math.abs(startPosition.latitude-endPosition.latitude);
        double lng=Math.abs(startPosition.longitude-endPosition.longitude);

        if (startPosition.latitude< endPosition.latitude && startPosition.longitude< endPosition.longitude)
            return (float) (Math.toDegrees(Math.atan(lng/lat)));
        else  if (startPosition.latitude>= endPosition.latitude && startPosition.longitude< endPosition.longitude)
            return (float) ((90-Math.toDegrees(Math.atan(lng/lat)))+90);
        else  if (startPosition.latitude>= endPosition.latitude && startPosition.longitude>= endPosition.longitude)
            return (float) (Math.toDegrees(Math.atan(lng/lat))+180);
        else  if (startPosition.latitude< endPosition.latitude && startPosition.longitude>= endPosition.longitude)
            return (float) ((90-Math.toDegrees(Math.atan(lng/lat)))+270);
        return -1;
    }


    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_map);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
//        mSearchText= (AutoCompleteTextView)findViewById(R.id.input_search);
        navigation_menu= (ImageView) findViewById(R.id.navigation_menu_client);

        mGps= (ImageView) findViewById(R.id.ic_gps);

        mDrawerLayout = findViewById(R.id.drawer_layout_client);
        mPerfilFoto=(ImageView) findViewById(R.id.perfil_foto) ;
        Toast.makeText(this, "Rider Mode", Toast.LENGTH_SHORT).show();
        PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);

        // Crindo circunferencia na foto de perfil
        // Bitmap bitmap=new BitmapFactory.decodeResource(getResources(),R.drawable.stlsm);


        // AutoComplite CardView
        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                // TODO: Get info about the selected place.
                destination=place.getName().toString();
                geoLocate();
                Toast.makeText(ClientMapActivity.this, "Pesquisa", Toast.LENGTH_SHORT).show();
                Log.i(TAG, "Place: " + place.getName());
                Toast.makeText(ClientMapActivity.this, place.getName().toString(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Status status) {
                // TODO: Handle the error.
                Log.i(TAG, "An error occurred: " + status);
            }
        });

        // Filtro para Mocambique
        AutocompleteFilter typeFilter = new AutocompleteFilter.Builder()
                .setCountry("MZ")
                .build();

        autocompleteFragment.setFilter(typeFilter);

        // Nevegation Drawer
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {
                        // set item as selected to persist highlight
                        menuItem.setChecked(true);
                        // close drawer when item is tapped
                        mDrawerLayout.closeDrawers();

                        // Add code here to update the UI based on the item selected
                        // For example, swap UI fragments here
                        int id = menuItem.getItemId();

                        if (id == R.id.nav_dar_boleia) {
                            Intent homeIntent= new Intent(ClientMapActivity.this,MapsActivity.class);
                            startActivity(homeIntent);
                            finish();



                        } else if (id == R.id.nav_termos_de_uso) {
                            Toast.makeText(ClientMapActivity.this, "Termos de Uso", Toast.LENGTH_SHORT).show();
                        } else if (id == R.id.nav_definicoes) {
                            Toast.makeText(ClientMapActivity.this, "Definicoes", Toast.LENGTH_SHORT).show();
                        } else if (id == R.id.nav_promocao) {
                            Toast.makeText(ClientMapActivity.this, "Promocao", Toast.LENGTH_SHORT).show();
                        } else if (id == R.id.nav_pagamento) {
                            Toast.makeText(ClientMapActivity.this, "Pagamento", Toast.LENGTH_SHORT).show();
                        } else if (id == R.id.nav_feedback) {
                            Toast.makeText(ClientMapActivity.this, "Feedback", Toast.LENGTH_SHORT).show();
                        } else if (id == R.id.nav_historico) {

                            Toast.makeText(ClientMapActivity.this, "Historico", Toast.LENGTH_SHORT).show();
                        }

                        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout_client);
                        drawer.closeDrawer(GravityCompat.START);
                        return true;
                    }
                });

        // Botao get direction Funcionalidade
        tracarRota = findViewById(R.id.btnGetDirection);
        tracarRota.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getDirection();

               //  myHome();


            }
        });

        //coope -25.9527746,32.58862495
        // Alto mae -25.95634688,32.56630898

        polyLineList =new ArrayList<>();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        getLocationPermission();
        mService=Common.geIGoogleAPI();

    }

    private void getDirection() {
        currentPosition=new LatLng(lastLocation.getAltitude(),lastLocation.getLongitude());
        String requestApi=null;
        try {
            requestApi="https://maps.googleapis.com/maps/api/directions/json?"+
                    "mode=driving&"+
                    "transit_routing_preference=less_driving&"+
                    "origin="+currentPosition.latitude+","+currentPosition.longitude+"&"+
                    "destination="+destination+"&"+
                    "key="+getResources().getString(R.string.google_direction_api);
            Log.d(TAG, "getDirection: "+requestApi);// Print Url for Debug
            mService.getPath(requestApi).enqueue(new Callback<String>() {
                @Override
                public void onResponse(Call<String> call, Response<String> response) {

                    try {
                        JSONObject jsonObject =new JSONObject(response.body().toString());
                        JSONArray jsonArray=jsonObject.getJSONArray("routes");
                        for (int i = 0; i< jsonArray.length()  ; i++) {
                            JSONObject route=jsonArray.getJSONObject(i);
                            JSONObject poly=route.getJSONObject("overview_polyline");
                            String polyline=poly.getString("points");
                            polyLineList=decodePoly(polyline);

                            //Adjusting Bounds

                            LatLngBounds.Builder builder=new LatLngBounds.Builder();
                            for (LatLng latLng:polyLineList)
                                builder.include(latLng);
                            LatLngBounds bounds=builder.build();
                            CameraUpdate mCameraUpdate= CameraUpdateFactory.newLatLngBounds(bounds,2);
                            mMap.animateCamera(mCameraUpdate);

                            polylineOptions=new PolylineOptions();
                            polylineOptions.color(Color.GRAY);
                            polylineOptions.width(5);
                            polylineOptions.startCap(new SquareCap());
                            polylineOptions.endCap(new SquareCap());
                            polylineOptions.jointType(JointType.ROUND);
                            polylineOptions.addAll(polyLineList);
                            greyPolyline=mMap.addPolyline(polylineOptions);

                            blackPolylineOptions=new PolylineOptions();
                            blackPolylineOptions.color(Color.BLACK);
                            blackPolylineOptions.width(5);
                            blackPolylineOptions.startCap(new SquareCap());
                            blackPolylineOptions.endCap(new SquareCap());
                            blackPolylineOptions.jointType(JointType.ROUND);
                            blackPolyline=mMap.addPolyline(blackPolylineOptions);

                            mMap.addMarker(new MarkerOptions()
                                    .position(polyLineList.get(polyLineList.size()-1))
                                    .title("Local de encontro"));
                            //pickup Location

                            //Animacao
                            ValueAnimator polylineAnimator=ValueAnimator.ofInt(0,100);
                            polylineAnimator.setDuration(2000);
                            polylineAnimator.setInterpolator(new LinearInterpolator());
                            polylineAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                @Override
                                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                                    List<LatLng> points =greyPolyline.getPoints();
                                    int percentValue=(int)valueAnimator.getAnimatedValue();
                                    int size=points.size();
                                    int newPoits= (int)(size * (percentValue/100.0f));
                                    List<LatLng> p=points.subList(0,newPoits);
                                    blackPolyline.setPoints(p);


                                }
                            });polylineAnimator.start();
                            carMarker=mMap.addMarker(new MarkerOptions().position(currentPosition)
                                    .flat(true)
                                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.carro)));

                            handler =new Handler();
                            index=-1;
                            next=1;

                            handler.postDelayed(drawPathRunnable,300);
                        }
                    }catch (Exception e){

                    }

                }

                @Override
                public void onFailure(Call<String> call, Throwable t) {
                    Toast.makeText(ClientMapActivity.this, t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });

        }catch (Exception e){

        }


    }


    /*
     * Limpar usando switch off
     *
     * stopLocationUpdate();
     * mCurrent.remove();
     * mMap.clear();
     * handler.removeCallbacks(drawPath...);
     * Toast You are offline
     *
     * */

    private List decodePoly(String encoded) {

        List poly = new ArrayList();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)),
                    (((double) lng / 1E5)));
            poly.add(p);
        }

        return poly;
    }

// Metodos para Rota
    //TODO: Rotas

    // Fim metodos para rotas

    private void init(){
        Log.d(TAG, "init: inicializando Search");
        mGps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: icone do gps pressionado");
                getDeviceLocation();
                Toast.makeText(ClientMapActivity.this, "Chefe estou aqui", Toast.LENGTH_SHORT).show();
            }
        });

    }

    private void geoLocate() {
        Log.d(TAG, "geoLocate: Localizacao Geografica");


        Geocoder geocoder=new Geocoder(ClientMapActivity.this);
        List<Address> list=new ArrayList<>();

        try {
            list=geocoder.getFromLocationName(destination,1);

        }catch (IOException e){
            Log.d(TAG, "geoLocate: IOException"+e.getMessage());

        }

        if (list.size()>0){
            Address address=list.get(0); // Localizacao pesquisada
            Log.d(TAG, "geoLocate: Localizacao Localizada "+address.toString());
            Toast.makeText(this, "Localizacao Localizada "+address.toString(), Toast.LENGTH_SHORT).show();
            moveCamera(new LatLng(address.getLatitude(),address.getLongitude()),DEFAULT_ZOOM,address.getAddressLine(0));

        }
    }

    private void getDeviceLocation() {
        Log.d(TAG, "getDeviceLocation: Buscando a localizacao Actual");
        Toast.makeText(this, "Buscando a localizacao Actual", Toast.LENGTH_SHORT).show();
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        try {
            if (mLocationPermissionGranted) {
                Task location = mFusedLocationProviderClient.getLastLocation();

                ((Task) location).addOnCompleteListener(new OnCompleteListener() {
                    @Override
                    public void onComplete(@NonNull Task task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "onComplete: Localizacao encontrada");
                            Location currentLocation = (Location) task.getResult();
                            if (currentLocation != null) {
                                lastLocation=currentLocation;
                                moveCamera(new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude()), DEFAULT_ZOOM,"My location");
                            }

                        } else {
                            Log.d(TAG, "onComplete: Local actual nulo");
                            Toast.makeText(ClientMapActivity.this, "Nao foi possivel carregar a localizacao actual", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        } catch (SecurityException e) {
            Log.d(TAG, "getDeviceLocation: Excepcao de Seguranca" + e.getMessage());
        }
    }



    private void moveCamera(LatLng latLng, float zoom, String title) {// mover a camera
        Log.d(TAG, "moveCamera: movendo a camera para lattude:" + latLng.latitude + "longitude:" + latLng.longitude);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));

        //Opcoes de marcador
        if (!title.equals("My location")){

            MarkerOptions options=new MarkerOptions()
                    .position(latLng)
                    .title(title);
            mMap.addMarker(options); // Adicionando o marcador ao mapa

        }
        // hideSoftKeyboard();
    }

    private void initMap() {// inicializar o mapa
        Log.d(TAG, "initMap: inicializando o mapa");
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(ClientMapActivity.this);

    }


    private void getLocationPermission() {
        Log.d(TAG, "getLocationPermission: buscando permissao de localizacao");
        String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            if (ContextCompat.checkSelfPermission(this.getApplicationContext(), COURSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mLocationPermissionGranted = true;
                initMap();
            } else {
                ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_REQUEST_CODE);
            }
        } else {
            ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST_CODE: {
                if (grantResults.length > 0) {
                    for (int i = 0; i < grantResults.length; i++) {
                        if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                            mLocationPermissionGranted = false;
                            Log.d(TAG, "onRequestPermissionsResult: permissao falhou");
                            return;
                        }
                    }
                    mLocationPermissionGranted = true;
                    Log.d(TAG, "onRequestPermissionsResult: permissao consedida");
                    // Inicializa o mapa pois tudo esta bem
                    initMap();
                }
            }
        }
    }

    public void myHome (){
        // Add a marker in Maputo and move the camera
//        LatLng mpt = new LatLng(-25.85578649, 32.62132376);
//        mMap.addMarker(new MarkerOptions().position(mpt).title("Gimo Casa"));
//        mMap.moveCamera(CameraUpdateFactory.newLatLng(mpt));

        Polyline polyline1 = mMap.addPolyline(new PolylineOptions()
                .clickable(true)
                .add(
                        new LatLng(-25.97367572, 32.5707829),
                        new LatLng(-25.97362749, 32.57067561),
                        new LatLng(-25.97340083, 32.57016599),
                        new LatLng(-25.9733092, 32.56994605),
                        new LatLng(-25.97262921, 32.5685753),
                        new LatLng(-25.97202638, 32.5674355),
                        new LatLng(-25.97126922, 32.5659281),
                        new LatLng(-25.97220482, 32.56536484)));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(-25.97220482, 32.56536484)));


    }
    private void displayLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
//medi bang
        lastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (lastLocation != null) {
//            if (location_swhitch.isChecked()) {
            final double latitude = lastLocation.getLatitude();
            final double longitude = lastLocation.getLongitude();

            geoFire.setLocation(FirebaseAuth.getInstance().getCurrentUser().getUid(), new GeoLocation(latitude, longitude), new GeoFire.CompletionListener() {
                @Override
                public void onComplete(String key, DatabaseError error) {
                    //Add Marker
                    if (currentMarker != null) {
                        currentMarker.remove();
                        currentMarker = mMap.addMarker(new MarkerOptions()
                                .icon(BitmapDescriptorFactory
                                        .fromResource(R.drawable.carro))
                                .position(new LatLng(latitude, longitude))
                                .title("Voce"));

                        //Move Camera to this position
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude, longitude), 15.0f));

//                            // Animacao do carro
//                            rotateMarker(currentMarker, -360, mMap);

                    }
                }
            });
//            } else {
//                Log.d(TAG, "displayLocation: " + "cannot get the location");
//                Toast.makeText(this, "cannot get the location", Toast.LENGTH_SHORT).show();
//
//            }
        }

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

        Toast.makeText(this, "Mapa inicializado com Sucesso!", Toast.LENGTH_LONG).show();

        mMap = googleMap;
        if (mLocationPermissionGranted) {
            getDeviceLocation();

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            mMap.setMyLocationEnabled(true);

            mMap.getUiSettings().setMyLocationButtonEnabled(false);// desabilitar localizacao My location

            // Tutorial Retrofit
            mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            mMap.setTrafficEnabled(false);
            mMap.setIndoorEnabled(false);
            mMap.setBuildingsEnabled(false);
            mMap.getUiSettings().setZoomControlsEnabled(true); // Habilitar Zoom

            init();
            // displayLocation();
            // startLocationUpdates();

        }


    }



    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        return true;
    }

    public void openDrawer(View view) {
        mDrawerLayout.openDrawer(GravityCompat.START,true);
    }




    private void stopLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, (com.google.android.gms.location.LocationListener) this);
        currentMarker.remove();
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,locationRequest, (com.google.android.gms.location.LocationListener) this);
        // LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(locationRequest,new LocationCallback(),Looper.getMainLooper());


    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Toast.makeText(this, "Conectado", Toast.LENGTH_SHORT).show();
        displayLocation();
        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }
}

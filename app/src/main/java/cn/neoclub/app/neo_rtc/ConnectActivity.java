package cn.neoclub.app.neo_rtc;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * Handles the initial setup where the user selects which room to join.
 */
public class ConnectActivity extends AppCompatActivity {
    private static final String TAG = "ConnectActivity";
    private static final int CONNECTION_REQUEST = 1;
    private static boolean commandLineRun = false;

    private ImageButton connectButton;
    private ImageButton addFavoriteButton;
    private EditText roomEditText;

    private EditText etClient;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        roomEditText = (EditText) findViewById(R.id.room_edittext);
        connectButton = (ImageButton) findViewById(R.id.connect_button);
        connectButton.setOnClickListener(connectListener);
        addFavoriteButton = (ImageButton) findViewById(R.id.add_favorite_button);
        addFavoriteButton.setOnClickListener(addFavoriteListener);

        etClient = (EditText) findViewById(R.id.et_client);
        // If an implicit VIEW intent is launching the app, go directly to that URL.
        final Intent intent = getIntent();
        if ("android.intent.action.VIEW".equals(intent.getAction())
                && !commandLineRun) {
            int runTimeMs = intent.getIntExtra(
                    CallActivity.EXTRA_RUNTIME, 0);
            String room = roomEditText.getText().toString();
            String client = etClient.getText().toString();
            connectToRoom(room, true, runTimeMs, client);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        ContentManager.setContentClientid(this, etClient.getText().toString());
        ContentManager.setContentRoomid(this, roomEditText.getText().toString());
    }

    @Override
    public void onResume() {
        super.onResume();
        roomEditText.setText(ContentManager.getContentRoomid(this));
        etClient.setText(ContentManager.getContentClientid(this));
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        if (requestCode == CONNECTION_REQUEST && commandLineRun) {
            Log.d(TAG, "Return: " + resultCode);
            setResult(resultCode);
            commandLineRun = false;
            finish();
        }
    }

    private void connectToRoom(
            String roomId, boolean commandLineRun, int runTimeMs, String clientId) {
        ConnectActivity.commandLineRun = commandLineRun;

        String roomUrl = ContentManager.getContentRoomid(this);

        // Check capture quality slider flag.
        boolean captureQualitySlider = false;

        // Check statistics display option.
        boolean displayHud = false;

        // Start AppRTCDemo activity.
        Log.d(TAG, "Connecting to room " + roomId + " at URL " + roomUrl);
        Uri uri = Uri.parse(roomUrl);
        Intent intent = new Intent(this, CallActivity.class);
        intent.setData(uri);
        intent.putExtra(CallActivity.EXTRA_ROOMID, roomId);
        intent.putExtra(CallActivity.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED,
                captureQualitySlider);
        intent.putExtra(CallActivity.EXTRA_VIDEO_CALL, true);
        intent.putExtra(CallActivity.EXTRA_DISPLAY_HUD, displayHud);
        intent.putExtra(CallActivity.EXTRA_CMDLINE, commandLineRun);
        intent.putExtra(CallActivity.EXTRA_RUNTIME, runTimeMs);

        intent.putExtra("CLIENTID", clientId);

        startActivityForResult(intent, CONNECTION_REQUEST);
    }

    private final View.OnClickListener addFavoriteListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            join();
        }
    };

    private final View.OnClickListener connectListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            create();
        }
    };

    private void create() {
        String url = ContentManager.CREATE_URL;

        AsyncHttpURLConnection httpConnection = new AsyncHttpURLConnection(
                "POST", url, getContent(), new AsyncHttpURLConnection.AsyncHttpEvents() {
            @Override
            public void onHttpError(String errorMessage) {
                Log.e(TAG, "GAE POST error: " + errorMessage);
            }

            @Override
            public void onHttpComplete(String response) {
                try {
                    Log.e(TAG, response);
                    JSONObject json = new JSONObject(response);
                    String ret = json.optString("ret");
                    Log.e(TAG, "ret " + ret);
                    if (ret.equals("SUCCESS")) {
                        String id = json.getString("id");
                        String room = json.getString("room");
                        String host = json.getString("host");
                        String character = json.getString("character");
                        connectToRoom(room, false, 0, id);
                    } else {
                        Log.e(TAG, ret);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        httpConnection.send();
    }

    private void showToast(final String ret) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ConnectActivity.this, ret, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void join() {
        String url = ContentManager.JOIN_URL;

        AsyncHttpURLConnection httpConnection = new AsyncHttpURLConnection(
                "POST", url, getContent(), new AsyncHttpURLConnection.AsyncHttpEvents() {
            @Override
            public void onHttpError(String errorMessage) {
                Log.e(TAG, "GAE POST error: " + errorMessage);
            }

            @Override
            public void onHttpComplete(String response) {
                try {
                    Log.e(TAG, response);
                    JSONObject json = new JSONObject(response);
                    String ret = json.optString("ret");
                    Log.e(TAG, "ret " + ret);
                    if (ret.equals("SUCCESS")) {
                        String id = json.getString("id");
                        String room = json.getString("room");
                        String host = json.getString("host");
                        String character = json.getString("character");
                        connectToRoom(room, false, 0, id);
                    } else {
                        Log.e(TAG, ret);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        httpConnection.send();
    }

    private String getContent() {
        String roomId = roomEditText.getText().toString();
        String clientId = etClient.getText().toString();
        String timestamp = String.valueOf(System.currentTimeMillis());
        return "{\"roomId\":\"" + roomId + "\",\"clientId\":\"" + clientId + "\",\"timestamp\":\"" + timestamp + "\"}";
    }
}

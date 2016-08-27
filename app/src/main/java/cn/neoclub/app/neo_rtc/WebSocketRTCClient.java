package cn.neoclub.app.neo_rtc;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class WebSocketRTCClient implements AppRTCClient,
        WebSocketChannelClient.WebSocketChannelEvents {
    private static final String TAG = "WSRTCClient";

    private String remote_id = "";
    public String client_id = "";

    private enum ConnectionState {
        NEW, CONNECTED, CLOSED, ERROR
    }

    private final LooperExecutor executor;
    private SignalingEvents events;
    private WebSocketChannelClient wsClient;
    private ConnectionState roomState;
    private String roomId;

    private List<String> members = new ArrayList<>();
    private LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();

    public WebSocketRTCClient(SignalingEvents events, LooperExecutor executor, String roomId, String client_id) {
        this.events = events;
        this.executor = executor;
        this.roomId = roomId;
        this.client_id = client_id;
        roomState = ConnectionState.NEW;
        executor.requestStart();
    }

    // --------------------------------------------------------------------
    // AppRTCClient interface implementation.
    @Override
    public void connectToRoom() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                connectToRoomInternal();
            }
        });
    }

    @Override
    public void disconnectFromRoom() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                disconnectFromRoomInternal();
            }
        });
        executor.requestStop();
    }

    // Connects to room - function runs on a local looper thread.
    private void connectToRoomInternal() {
        final String connectionUrl = getConnectionUrl();
        Log.d(TAG, "Connect to room: " + connectionUrl);
        roomState = ConnectionState.NEW;
        wsClient = new WebSocketChannelClient(executor, this, roomId, client_id);

        String url = ContentManager.XIRSYS_URL;
        AsyncHttpURLConnection httpConnection = new AsyncHttpURLConnection(
                "GET", url, "", new AsyncHttpURLConnection.AsyncHttpEvents() {
            @Override
            public void onHttpError(String errorMessage) {
                reportError("GAE POST error: " + errorMessage);
            }

            @Override
            public void onHttpComplete(String response) {
                Log.e(TAG, "ICE servers  " + response);
                try {
                    JSONObject roomJson = new JSONObject(response);
                    JSONObject ice = new JSONObject(roomJson.getString("d"));
                    JSONArray jsonArray = ice.getJSONArray("iceServers");
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject m = new JSONObject(jsonArray.getString(i));
                        String url = m.getString("url");
                        String username = m.optString("username");
                        String password = m.optString("credential");
                        if (username.equals("")) {
                            iceServers.add(new PeerConnection.IceServer(url));
                        } else {
                            iceServers.add(new PeerConnection.IceServer(url, username, password));
                        }
                    }
                    connect();
                } catch (JSONException e) {
                    reportError("GAE POST JSON error: " + e.toString());
                }
            }
        });
        httpConnection.send();
    }

    private void connect() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                wsClient.connect(getConnectionUrl());
            }
        });
    }

    // Disconnect from room and send bye messages - runs on a local looper thread.
    private void disconnectFromRoomInternal() {
        Log.d(TAG, "Disconnect. Room state: " + roomState);
        roomState = ConnectionState.CLOSED;
        if (wsClient != null) {
            wsClient.disconnect(true);
        }
    }

    // Helper functions to get connection, post message and leave message URLs
    private String getConnectionUrl() {
        return ContentManager.WSS_URL;
    }

    // Send local offer SDP to the other participant.
    @Override
    public void sendOfferSdp(final SessionDescription sdp) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending offer SDP in non connected state.");
                    return;
                }

                Log.e(TAG, "SEND OFFER SDP  " + members.size());
                if (members.size() == 1) {
                    String remoteId = members.get(0);
                    Log.e("TAG", client_id);
                    JSONObject json = new JSONObject();
                    jsonPut(json, "sdp", sdp.description);
                    jsonPut(json, "type", "offer");
                    JSONObject mjson = new JSONObject();
                    jsonPut(mjson, "cmd", "offer");
                    jsonPut(mjson, "msg", json);
                    jsonPut(mjson, "toId", remoteId);
                    wsClient.send(mjson.toString());
                    Log.e("SEND OFFER", mjson.toString());
                } else if (members.size() > 1) {
                    events.onChannelClose();
                }
            }
        });
    }

    // Send local answer SDP to the other participant.
    @Override
    public void sendAnswerSdp(final SessionDescription sdp) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "sdp", sdp.description);
                jsonPut(json, "type", "answer");
                JSONObject mjson = new JSONObject();
                jsonPut(mjson, "cmd", "answer");
                jsonPut(mjson, "msg", json);
                jsonPut(mjson, "toId", remote_id);
                wsClient.send(mjson.toString());

                Log.e("SEND ANSWER ", mjson.toString());
            }
        });
    }

    // Send Ice candidate to the other participant.
    @Override
    public void sendLocalIceCandidate(final IceCandidate candidate) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "sdpMLineIndex", candidate.sdpMLineIndex);
                jsonPut(json, "sdpMid", candidate.sdpMid);
                jsonPut(json, "candidate", candidate.sdp);
                JSONObject mjson = new JSONObject();
                jsonPut(mjson, "cmd", "ice");
                jsonPut(mjson, "msg", json);
                jsonPut(mjson, "toId", remote_id);
                wsClient.send(mjson.toString());

                Log.e("SEND ICE ", mjson.toString());
            }
        });
    }

    // Send removed Ice candidates to the other participant.
    @Override
    public void sendLocalIceCandidateRemovals(final IceCandidate[] candidates) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "type", "remove-candidates");
                JSONArray jsonArray = new JSONArray();
                for (final IceCandidate candidate : candidates) {
                    jsonArray.put(toJsonCandidate(candidate));
                }
                jsonPut(json, "candidates", jsonArray);
                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending ICE candidate removals in non connected state.");
                    return;
                }
                wsClient.send(json.toString());
            }
        });
    }

    // --------------------------------------------------------------------
    // WebSocketChannelEvents interface implementation.
    // All events are called by WebSocketChannelClient on a local looper thread
    // (passed to WebSocket client constructor).
    @Override
    public void onWebSocketMessage(final String msg) {
        if (wsClient.getState() != WebSocketChannelClient.WebSocketConnectionState.REGISTERED) {
            Log.e(TAG, "Got WebSocket message in non registered state.");
            return;
        }
        try {
            Log.e(TAG, "message   " + msg);
            JSONObject json = new JSONObject(msg);
            String type = json.optString("cmd");
            String errorText = json.optString("error");
            String id = json.optString("from");
            if (!id.equals("")) {
                remote_id = id;
                Log.e(TAG, "from ID  " + remote_id);
            }
            Log.d(TAG, type);
            if (type.length() > 0) {
                if (type.equals("ice")) {
                    String message = json.getString("msg");
                    JSONObject m = new JSONObject(message);
                    events.onRemoteIceCandidate(toJavaCandidate(m));
                } else if (type.equals("remove-candidates")) {
                    JSONArray candidateArray = json.getJSONArray("candidates");
                    IceCandidate[] candidates = new IceCandidate[candidateArray.length()];
                    for (int i = 0; i < candidateArray.length(); ++i) {
                        candidates[i] = toJavaCandidate(candidateArray.getJSONObject(i));
                    }
                    events.onRemoteIceCandidatesRemoved(candidates);
                } else if (type.equals("answer")) {
                    String message = json.getString("msg");
                    JSONObject m = new JSONObject(message);
                    String s = m.getString("sdp");
                    SessionDescription sdp = new SessionDescription(
                            SessionDescription.Type.fromCanonicalForm(type), s);
                    events.onRemoteDescription(sdp);
                } else if (type.equals("offer")) {
                    String message = json.getString("msg");
                    JSONObject m = new JSONObject(message);
                    String s = m.getString("sdp");
                    SessionDescription sdp = new SessionDescription(
                            SessionDescription.Type.fromCanonicalForm(type), s);
                    members.add(remote_id);
                    if (members.size() == 1) {
                        events.onRemoteDescription(sdp);
                    }
                } else if (type.equals("leave")) {
                    if (remote_id.equals(members.get(0))) {
                        events.onChannelClose();
                    }
                } else if (type.equals("loginack")) {
                    Log.d(TAG, "login  " + msg);
                    try {
                        JSONObject object = new JSONObject(msg);
                        String list = object.optString("members");
                        if (!list.equals("")) {
                            JSONArray messages = new JSONArray(list);
                            for (int i = 0; i < messages.length(); ++i) {
                                if (!client_id.equals(messages.get(i))) {
                                    members.add(messages.getString(i));
                                }
                            }
                        }
                        onWebSocketRegister(members.size());
                        Log.d(TAG, "members num  " + members.size());
                    } catch (JSONException e) {
                        reportError("Unexpected WebSocket message: " + e.toString());
                    }
                } else if (type.equals("browser")) {
                    Log.e(TAG, "BROWSER " + msg);
                } else {
                    reportError("Unexpected WebSocket message: " + msg);
                }
            } else {
                if (errorText != null && errorText.length() > 0) {
                    reportError("WebSocket error message: " + errorText);
                } else {
                    reportError("Unexpected WebSocket message: " + msg);
                }
            }
        } catch (JSONException e) {
            reportError("WebSocket message JSON parsing error: " + e.toString());
        }
    }

    @Override
    public void onWebSocketClose() {
        events.onChannelClose();
    }

    @Override
    public void onWebSocketError(String description) {
        reportError("WebSocket error: " + description);
    }

    private void onWebSocketRegister(int num) {
        roomState = ConnectionState.CONNECTED;
        events.onConnectedToRoom(isFirstIn(num), iceServers);
    }

    private boolean isFirstIn(int num) {
        Log.e(TAG, "first in " + num);
        return num == 0;
    }

    // --------------------------------------------------------------------
    // Helper functions.
    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.ERROR) {
                    roomState = ConnectionState.ERROR;
                    events.onChannelError(errorMessage);
                }
            }
        });
    }

    // Put a |key|->|value| mapping in |json|.
    private static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    // Converts a Java candidate to a JSONObject.
    private JSONObject toJsonCandidate(final IceCandidate candidate) {
        JSONObject json = new JSONObject();
        jsonPut(json, "sdpMLineIndex", candidate.sdpMLineIndex);
        jsonPut(json, "sdpMid", candidate.sdpMid);
        jsonPut(json, "candidate", candidate.sdp);
        return json;
    }

    // Converts a JSON candidate to a Java object.
    IceCandidate toJavaCandidate(JSONObject json) throws JSONException {
        return new IceCandidate(json.getString("sdpMid"),
                json.getInt("sdpMLineIndex"),
                json.getString("candidate"));
    }
}

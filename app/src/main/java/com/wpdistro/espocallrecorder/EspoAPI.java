package com.wpdistro.espocallrecorder;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public final class EspoAPI {
    private static final String apiPath = "api/v1";

    protected Uri url;
    protected String username;
    protected String token;
    protected String userId = null;

    public EspoAPI(Uri url, String username, String token) {
        this.url = Uri.withAppendedPath(url, apiPath);
        this.username = username;
        this.token = token;
    }

    public static EspoAPI fromConfig(SharedPreferences config) {
        String url = config.getString("url", "");
        String username = config.getString("username", "");
        String token = config.getString("token", "");

        return new EspoAPI(Uri.parse(url), username, token);
    }

    public static void fetchToken(Context ctx, Uri uri, String username, String password,
                                  Response.Listener<String> responseListener, Response.ErrorListener errorListener)
            throws ExecutionException, InterruptedException, TimeoutException {
        RequestQueue queue = Volley.newRequestQueue(ctx);
        uri = Uri.withAppendedPath(uri, apiPath);
        uri = Uri.withAppendedPath(uri, "App/user");

        JsonObjectRequest tokenRequest = new JsonObjectRequest(uri.toString(), response -> {
            try {
                String token = response.getString("token");
                responseListener.onResponse(token);
            } catch (JSONException e) {
                errorListener.onErrorResponse(new Exceptions.TokenMissingException());
            }
        }, error -> {
            Log.d("callRecordService", error.toString());
            errorListener.onErrorResponse(error);
        }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> params = new HashMap<>();
                String authString = username + ":" + password;
                params.put("Espo-Authorization", Base64.encodeToString(authString.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT));

                return params;
            }
        };

        queue.add(tokenRequest);
    }

    public void checkConnection(Context ctx, Response.Listener<JSONObject> successListener, Response.ErrorListener errorListener) {
        espoRequest(ctx, "App/user", response -> {
            try {
                response.getString("token");
                successListener.onResponse(response);
            } catch (JSONException e) {
                errorListener.onErrorResponse(new Exceptions.TokenMissingException());
            }
        }, errorListener);
    }

    public void checkPhoneNumber(Context ctx, String phoneNumber, Response.Listener<JSONObject> successListener, Response.ErrorListener errorListener) {
        if (phoneNumber.startsWith("+420")) {
            phoneNumber = phoneNumber.substring(4);
        } else if (phoneNumber.startsWith("00420")) {
            phoneNumber = phoneNumber.substring(6);
        }

        String path = "Contact?maxSize=1&select=id,name&where[0][type]=endsWith&where[0][field]=phoneNumber&where[0][value]=" + phoneNumber;

        espoRequest(ctx, path, response -> {
            try {
                int total = response.getInt("total");

                if (total == 0) {
                    errorListener.onErrorResponse(new Exceptions.ContactNotFoundException());
                } else {
                    successListener.onResponse(response);
                }
            } catch (JSONException e) {
                errorListener.onErrorResponse(new Exceptions.ContactNotFoundException());
            }
        }, errorListener);
    }

    public void uploadCall(Context ctx, JSONObject obj, boolean isIncoming, long duration, long dateStart, File audioFile) {
        if (TextUtils.isEmpty(userId)) {
            fetchUserId(ctx, userId -> {
                this.userId = userId;
                handleCallUpload(ctx, obj, isIncoming, duration, dateStart, userId, audioFile);
            });
        } else {
            handleCallUpload(ctx, obj, isIncoming, duration, dateStart, userId, audioFile);
        }
    }

    private void handleCallUpload(Context ctx, JSONObject obj, boolean isIncoming, long duration, long dateStart, String userId, File audioFile) {
        try {
            uploadCall(ctx, obj, isIncoming, duration, dateStart, userId, audioFile);
        } catch (JSONException | Exceptions.NullFileBytesException e) {
            audioFile.delete();
            e.printStackTrace();
        }
    }

    private void uploadCall(Context ctx, JSONObject obj, boolean isIncoming, long duration, long dateStart, String userId, File audioFile)
            throws JSONException, Exceptions.NullFileBytesException {
        String id = obj.getString("id");
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY);

        JSONObject callData = new JSONObject();
        callData.put("name", obj.getString("name"));
        callData.put("assignedUserId", userId);

        if (obj.has("accountId")) {
            String accountId = obj.getString("accountId");
            if (!TextUtils.isEmpty(accountId)) {
                JSONArray contacts = new JSONArray();
                contacts.put(id);

                callData.put("parentType", "Account");
                callData.put("parentId", obj.getString("accountId"));
                callData.put("contactsIds", contacts.toString());
            } else {
                callData.put("parentType", "Contact");
                callData.put("parentId", id);
            }
        }

        TimeZone tz = TimeZone.getDefault();
        dateStart += tz.getRawOffset() + (tz.useDaylightTime() ? tz.getDSTSavings() : 0);

        callData.put("direction", isIncoming ? "Inbound" : "Outbound");
        callData.put("status", "Held");
        callData.put("dateStart", formatter.format(dateStart));
        callData.put("dateEnd", formatter.format(dateStart + duration * 1000));

        JSONObject attData = new JSONObject();
        int audioFileLength = (int) audioFile.length();
        String fileBytes = null;
        String mimeType = ctx.getContentResolver().getType(Uri.fromFile(audioFile));

        try {
            byte[] bytes = new byte[audioFileLength];
            BufferedInputStream buffer = new BufferedInputStream(new FileInputStream(audioFile));
            buffer.read(bytes, 0, bytes.length);
            buffer.close();

            fileBytes = Base64.encodeToString(bytes, Base64.DEFAULT);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (fileBytes == null) {
            throw new Exceptions.NullFileBytesException();
        }

        attData.put("field", "recordedcall");
        attData.put("assignedUserId", userId);
        attData.put("file", "data:" + mimeType + ";base64," + fileBytes);
        attData.put("name", audioFile.getName());
        attData.put("relatedType", "Call");
        attData.put("role", "Attachment");
        attData.put("size", Long.toString(audioFileLength));
        attData.put("type", mimeType);

        espoRequest(ctx, Request.Method.POST, "Attachment", attData, attResult -> {
            try {
                callData.put("recordedcallId", attResult.getString("id"));

                espoRequest(ctx, Request.Method.POST, "Call", callData, callResult -> {
                }, error -> {
                    try {
                        Log.d("callRecordService", new String(error.networkResponse.data, HttpHeaderParser.parseCharset(error.networkResponse.headers, "utf-8")));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                });
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }, error -> {
            try {
                Log.d("callRecordService", new String(error.networkResponse.data, HttpHeaderParser.parseCharset(error.networkResponse.headers, "utf-8")));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        });
    }

    private void fetchUserId(Context ctx, Response.Listener<String> listener) {
        espoRequest(ctx, "App/user", response -> {
            try {
                listener.onResponse(response.getJSONObject("user").getString("id"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }, null);
    }

    protected void espoRequest(Context ctx, String path, Response.Listener<JSONObject> listener, @Nullable Response.ErrorListener errorListener) {
        espoRequest(ctx, Request.Method.GET, path, new JSONObject(), listener, errorListener);
    }

    protected void espoRequest(Context ctx, int method, String path, JSONObject data,
                               Response.Listener<JSONObject> listener, @Nullable Response.ErrorListener errorListener) {
        RequestQueue queue = Volley.newRequestQueue(ctx);

        JsonObjectRequest request = new JsonObjectRequest(method, Uri.withAppendedPath(url, path).toString(), data, listener, errorListener) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> params = new HashMap<>();

                if (method == Request.Method.POST) {
                    params.put("Content-Type", "application/json");
                }

                String authString = username + ":" + token;
                params.put("Espo-Authorization", Base64.encodeToString(authString.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT));

                return params;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(
                10000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        queue.add(request);
    }

    public static class Exceptions {
        public static class TokenMissingException extends VolleyError {
            public TokenMissingException() {
                super("Token is missing.");
            }
        }

        public static class NullFileBytesException extends Exception {
            public NullFileBytesException() {
                super("Couldn't read any audio file bytes.");
            }
        }

        public static class ContactNotFoundException extends VolleyError {
            public ContactNotFoundException() {
                super("Contact not found.");
            }
        }
    }
}

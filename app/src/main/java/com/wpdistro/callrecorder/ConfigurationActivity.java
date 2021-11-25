package com.wpdistro.callrecorder;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.URLUtil;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.wpdistro.callrecorder.databinding.ConfigurationBinding;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class ConfigurationActivity extends AppCompatActivity {
    private ConfigurationBinding binding;
    private EditText urlInput;
    private EditText usernameInput;
    private EditText passwordInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ConfigurationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        urlInput = findViewById(R.id.urlInput);
        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
    }

    @Override
    public void onBackPressed() {
    }

    public void onClickAuthorize(View v) {
        String urlString = urlInput.getText().toString();
        String usernameString = usernameInput.getText().toString();
        String passwordString = passwordInput.getText().toString();

        if (TextUtils.isEmpty(urlString)) {
            urlInput.setError("Please fill in the url");
            return;
        }

        if (TextUtils.isEmpty(usernameString)) {
            usernameInput.setError("Please fill in the username");
            return;
        }

        if (TextUtils.isEmpty(passwordString)) {
            passwordInput.setError("Please fill in the password");
            return;
        }


        if (!urlString.startsWith("https://") && !urlString.startsWith("http://")) {
            urlString = "http://" + urlString;
        }

        if (!URLUtil.isValidUrl(urlString)) {
            urlInput.setError("Please enter valid URL");
            return;
        }

        try {
            CustomProgressDialog.show(this, "Requesting token...");
            String finalUrlString = urlString; // lambda

            EspoAPI.fetchToken(this, Uri.parse(finalUrlString), usernameString, passwordString, token -> {
                SharedPreferences.Editor apiDetails = getSharedPreferences("apiDetails", Context.MODE_PRIVATE).edit();
                apiDetails.putString("url", finalUrlString);
                apiDetails.putString("username", usernameString);
                apiDetails.putString("token", token);
                apiDetails.apply();

                CustomProgressDialog.close();
                setResult(Activity.RESULT_OK);
                finish();
            }, errorResponse -> {
                CustomProgressDialog.close();
                if (errorResponse.getClass() == AuthFailureError.class) {
                    new AlertDialog.Builder(this)
                            .setTitle("Authentication error")
                            .setMessage("Check your username and password.")
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            })
                            .show();
                } else if (errorResponse.getClass() == EspoAPI.Exceptions.TokenMissingException.class) {
                    new AlertDialog.Builder(this)
                            .setTitle("Token missing")
                            .setMessage("Token not found in the API response. Check your URL.")
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            })
                            .show();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Error occurred")
                            .setMessage(R.string.api_token_unknown_error)
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            })
                            .show();
                }
            });
        } catch (InterruptedException | ExecutionException |
                TimeoutException e) {
            e.printStackTrace();
            new AlertDialog.Builder(this)
                    .setTitle("Error occurred")
                    .setMessage(R.string.api_token_unknown_error + e.getMessage())
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    })
                    .show();
        }
    }
}
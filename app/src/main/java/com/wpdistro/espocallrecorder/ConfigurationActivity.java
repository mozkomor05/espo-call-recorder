package com.wpdistro.espocallrecorder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.text.TextUtils;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.EditText;

import com.android.volley.AuthFailureError;
import com.wpdistro.espocallrecorder.databinding.ConfigurationBinding;

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
            CustomProgressDialog.show(this, getString(R.string.requesting_token));
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
                            .setTitle(R.string.authentication_error_title)
                            .setMessage(R.string.authentication_error_message)
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            })
                            .show();
                } else if (errorResponse.getClass() == EspoAPI.Exceptions.TokenMissingException.class) {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.token_missing_title)
                            .setMessage(R.string.token_missing_message)
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
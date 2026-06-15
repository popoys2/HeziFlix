package com.hezi.flix;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class SplashActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        final EditText etKey = (EditText) findViewById(R.id.et_access_key);
        Button btnSubmit = (Button) findViewById(R.id.btn_submit_key);
        TextView tvForgotKey = (TextView) findViewById(R.id.tv_forgot_key);

        btnSubmit.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (etKey.getText().toString().equals("FLIXERFOREVERYONE")) {
						BaseActivity.isAppInBackground = false;
						startActivity(new Intent(SplashActivity.this, MainActivity.class));
						finish();
					} else {
						etKey.setText("");
						Toast.makeText(SplashActivity.this, "Access Denied, Wrong Access Key!", Toast.LENGTH_SHORT).show();
					}
				}
			});

        if (tvForgotKey != null) {
            tvForgotKey.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						String lookupUrl = "https://pastebin.com/vXh1M5HV";
						Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(lookupUrl));
						startActivity(browserIntent);
					}
				});
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}


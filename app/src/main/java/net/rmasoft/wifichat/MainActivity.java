package net.rmasoft.wifichat;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "WiFiDirectChatHome";
    public SharedPreferences sharedpreferences;
    public static final String MyPREFERENCES = "MyPrefs" ;
    public static final String Name = "nameKey";
    public static String userName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.AppTheme);
        setContentView(R.layout.activity_main);
        sharedpreferences = getSharedPreferences(MyPREFERENCES, Context.MODE_PRIVATE);

        userName = sharedpreferences.getString(Name, null);
        if(userName != null) {
            // Old user
            Log.i(TAG, "Username is: " + sharedpreferences.getString(Name, null));
            gotoMainScreen();
        }

        Button saveUsername = findViewById(R.id.saveUsername);
        saveUsername.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveUsername();
            }
        });

    }

    private void saveUsername() {
        EditText userNameView = findViewById(R.id.userName);
        userName = userNameView.getText().toString();

        if (userName.isEmpty()){
            Toast.makeText(this, R.string.enter_username, Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences.Editor editor = sharedpreferences.edit();
        editor.putString(Name, userName);
        editor.apply();
        Log.i(TAG, "Username changed to: " + userName);
        Toast.makeText(this, R.string.username_saved, Toast.LENGTH_LONG).show();
        gotoMainScreen();
    }

    private void gotoMainScreen() {
        Intent intent = new Intent(this, WiFiServiceDiscoveryActivity.class);
        startActivity(intent);
    }
}

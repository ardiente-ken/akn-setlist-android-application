package com.aknevs.set;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/** App entry point. Hosts SongListFragment via the layout's FragmentContainerView. */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}

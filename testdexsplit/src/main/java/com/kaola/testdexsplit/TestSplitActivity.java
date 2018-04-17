package com.kaola.testdexsplit;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;

/**
 * @author sunchuanwen
 * @time 2018/4/17.
 */
public class TestSplitActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.split_activity_main);
    }
}

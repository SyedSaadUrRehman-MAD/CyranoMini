package com.company.cyranomini;

import static android.app.Activity.RESULT_OK;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "Minimize")
public class MinimizePlugin extends Plugin {
    private Minimize implementation = new Minimize();

    @PluginMethod
    public void echo(PluginCall call) {
        String value = call.getString("value");

        JSObject ret = new JSObject();
        ret.put("value", implementation.echo(value));
        call.resolve(ret);
    }

    @PluginMethod
    public void minimize(PluginCall call) {

        //region "System alert window"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(getActivity())) {
                saveCall(call);
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getActivity().getPackageName()));
                startActivityForResult(call,intent, "overlayCallback");
            } else {
                overlay(call);
            }
        }

        //endregion
    }

    public void startScreenCapture(PluginCall call) {
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent intent = mediaProjectionManager.createScreenCaptureIntent();
        saveCall(call);
        startActivityForResult(call, intent, "mediaProjectionCallback");
    }

    @ActivityCallback
    public void mediaProjectionCallback(PluginCall savedCall, ActivityResult result)
    {
        if (savedCall == null) {

            Log.d("cyrano","savedCall is null");
            return;
        }

            Log.d("cyrano", "REQUEST_CODE_MP");
            if (result.getResultCode() == RESULT_OK) {
                Log.d("cyrano", "inside result ok");

                Log.d("cyrano","result code is "+ result.getResultCode());
                Log.d("cyrano", "mp data is "+ result.getData().toString());
                Log.d("cyrano", "starting service");
                Intent serviceIntent = new Intent(getContext(), FloatingButtonService.class);
                serviceIntent.putExtra("resultCode", result.getResultCode());
                serviceIntent.putExtra("data", result.getData());
                getActivity().startService(serviceIntent);

                getActivity().moveTaskToBack(false);
                Toast.makeText(getContext(), "Minimize call", Toast.LENGTH_LONG).show();

                // Handle the successful result
                JSObject res = new JSObject();
                res.put("value", "Success");
                savedCall.resolve(res);
            } else {
                // Handle the failure
                Log.d("cyrano", "not ok");
                savedCall.reject("Activity failed or was cancelled");
            }

    }

    @ActivityCallback
    public void overlayCallback(PluginCall savedCall, ActivityResult result)
    {
        if (savedCall == null) {

            Log.d("cyrano","savedCall is null");
            return;
        }

            Log.d("cyrano", "REQUEST_CODE_MP");
            if (result.getResultCode() == RESULT_OK) {
                Log.d("cyrano", "inside result ok");
                overlay(savedCall);
                // Handle the successful result
                JSObject res = new JSObject();
                res.put("value", "Success");
                savedCall.resolve(res);
            } else {
                // Handle the failure
                Log.d("cyrano", "not ok");
                savedCall.reject("Activity failed or was cancelled");
            }

    }

    private void overlay(PluginCall call) {
        startScreenCapture(call);
    }

}

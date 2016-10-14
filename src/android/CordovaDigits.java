package com.jimmymakesthings.plugins.digits;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.digits.sdk.android.AuthCallback;
import com.digits.sdk.android.Digits;
import com.digits.sdk.android.DigitsAuthConfig;
import com.digits.sdk.android.DigitsClient;
import com.digits.sdk.android.DigitsException;
import com.digits.sdk.android.DigitsOAuthSigning;
import com.digits.sdk.android.DigitsSession;
import com.twitter.sdk.android.core.TwitterAuthConfig;
import com.twitter.sdk.android.core.TwitterAuthToken;
import com.twitter.sdk.android.core.TwitterCore;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

import io.fabric.sdk.android.Fabric;

public class CordovaDigits extends CordovaPlugin {
    volatile DigitsClient digitsClient;
    private static final String META_DATA_KEY = "io.fabric.ConsumerKey";
    private static final String META_DATA_SECRET = "io.fabric.ConsumerSecret";
    private static final String TAG = "CORDOVA PLUGIN DIGITS";

    private AuthCallback authCallback;
    private String phoneNumber;
    private int styleId;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        TwitterAuthConfig authConfig = getTwitterConfig();
        Fabric.with(cordova.getActivity().getApplicationContext(), new Crashlytics(), new TwitterCore(authConfig), new Digits());
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        Log.i(TAG, "executing action " + action);

        styleId = cordova.getActivity().getResources().getIdentifier("CustomDigitsTheme", "style", cordova.getActivity().getPackageName());
        Log.i("Tag", "styleId "+styleId);

        if ("authenticate".equals(action)) {
            phoneNumber = args.getJSONObject(0).getString("phoneNumber");
            authenticate(callbackContext);
        } else if ("logout".equals(action)) {
            logout(callbackContext);
        } else {
            Log.w(TAG, "unknown action `" + action + "`");
            return false;
        }

        return true;
    }

    public void authenticate(final CallbackContext callbackContext) {
        authCallback = new AuthCallback() {
            @Override
            public void success(DigitsSession session, String phoneNumber) {
                // Do something with the session and phone number
                Log.i(TAG, "authentication successful");

                TwitterAuthConfig authConfig = TwitterCore.getInstance().getAuthConfig();
                TwitterAuthToken authToken = (TwitterAuthToken) session.getAuthToken();
                DigitsOAuthSigning oauthSigning = new DigitsOAuthSigning(authConfig, authToken);
                Map<String, String> authHeaders = oauthSigning.getOAuthEchoHeadersForVerifyCredentials();

                JSONObject object = new JSONObject();
                try {
                    object.put("phoneNumber", session.getPhoneNumber());
                    object.put("authHeaders", authHeaders);
                    Log.i(TAG, session.getPhoneNumber());

                    PluginResult registerUser = new PluginResult(PluginResult.Status.OK, object.toString());
                    registerUser.setKeepCallback(true);
                    callbackContext.sendPluginResult(registerUser);

                } catch (JSONException e) {
                    e.printStackTrace();
                }

                String result = new JSONObject(authHeaders).toString();
                callbackContext.success(result);
            }

            @Override
            public void failure(DigitsException exception) {
                // Do something on failure
                Log.e(TAG, "error " + exception.getMessage());
                callbackContext.error(exception.getMessage());
            }
        };

        DigitsAuthConfig.Builder digitsAuthConfigBuilder = new DigitsAuthConfig.Builder()
                .withAuthCallBack(authCallback)
                .withPhoneNumber(phoneNumber)
                .withThemeResId(styleId);

        Digits.authenticate(digitsAuthConfigBuilder.build());

    }

    public void logout(final CallbackContext callbackContext) {
        Digits.getSessionManager().clearActiveSession();
    }

    private TwitterAuthConfig getTwitterConfig() {
        String key = getMetaData(META_DATA_KEY);
        String secret = getMetaData(META_DATA_SECRET);

        return new TwitterAuthConfig(key, secret);
    }

    private String getMetaData(String name) {
        try {
            Context context = cordova.getActivity().getApplicationContext();
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);

            Bundle metaData = ai.metaData;
            if (metaData == null) {
                Log.w(TAG, "metaData is null. Unable to get meta data for " + name);
            } else {
                String value = metaData.getString(name);
                return value;
            }
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}

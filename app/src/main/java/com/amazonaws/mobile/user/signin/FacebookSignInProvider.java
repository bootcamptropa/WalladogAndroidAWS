package com.amazonaws.mobile.user.signin;
//
// Copyright 2015 Amazon.com, Inc. or its affiliates (Amazon). All Rights Reserved.
//
// Code generated by AWS Mobile Hub. Amazon gives unlimited permission to 
// copy, distribute and modify it.
//
// Source code generated from template: aws-my-sample-app-android v0.4

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.amazonaws.mobile.user.IdentityManager;
import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

/**
 * Sign-in provider for Facebook.
 */
public class FacebookSignInProvider implements SignInProvider {
    /** Log tag. */
    private static final String LOG_TAG = FacebookSignInProvider.class.getSimpleName();

    /** The Cognito login key for Facebook to be used in the Cognito login Map. */
    public static final String COGNITO_LOGIN_KEY_FACEBOOK = "graph.facebook.com";

    /** Facebook's callback manager. */
    private CallbackManager facebookCallbackManager;

    /** User's name. */
    private String userName;

    /** User's image Url. */
    private String userImageUrl;

    /**
     * Constuctor. Intitializes the SDK and debug logs the app KeyHash that must be set up with
     * the facebook backend to allow login from the app.
     *
     * @param context the context.
     */
    public FacebookSignInProvider(final Context context) {

        if (!FacebookSdk.isInitialized()) {
            Log.d(LOG_TAG, "Initializing Facebook SDK...");
            FacebookSdk.sdkInitialize(context);
            Utils.logKeyHash(context);
        }
    }

    /**
     * @return the Facebook AccessToken when signed-in with a non-expired token.
     */
    private AccessToken getSignedInToken() {
        final AccessToken accessToken = AccessToken.getCurrentAccessToken();
        if (accessToken != null && !accessToken.isExpired()) {
            Log.d(LOG_TAG, "Facebook Access Token is OK");
            return accessToken;
        }

        Log.d(LOG_TAG,"Facebook Access Token is null or expired.");
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isRequestCodeOurs(final int requestCode) {
        return FacebookSdk.isFacebookRequestCode(requestCode);
    }

    /** {@inheritDoc} */
    @Override
    public void handleActivityResult(final int requestCode, final int resultCode, final Intent data) {
        facebookCallbackManager.onActivityResult(requestCode, resultCode, data);
    }

    /** {@inheritDoc} */
    @Override
    public void initializeSignInButton(final Activity signInActivity, final View buttonView,
                                       final IdentityManager.SignInResultsHandler resultsHandler) {

        FacebookSdk.sdkInitialize(signInActivity);

        if (buttonView == null) {
            throw new IllegalArgumentException("Facebook login button view not passed in.");
        }

        facebookCallbackManager = CallbackManager.Factory.create();

        LoginManager.getInstance().registerCallback(facebookCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                Log.d(LOG_TAG, "Facebook provider sign-in succeeded.");
                resultsHandler.onSuccess(FacebookSignInProvider.this);
            }

            @Override
            public void onCancel() {
                Log.d(LOG_TAG, "Facebook provider sign-in canceled.");
                resultsHandler.onCancel(FacebookSignInProvider.this);
            }

            @Override
            public void onError(FacebookException exception) {
                Log.e(LOG_TAG, "Facebook provider sign-in error: " + exception.getMessage());
                resultsHandler.onError(FacebookSignInProvider.this, exception);
            }
        });

        buttonView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LoginManager.getInstance().logInWithReadPermissions(signInActivity,
                        Arrays.asList("public_profile"));
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public String getDisplayName() {
        return "Facebook";
    }

    /** {@inheritDoc} */
    @Override
    public String getCognitoLoginKey() {
        return COGNITO_LOGIN_KEY_FACEBOOK;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isUserSignedIn() {
        return getSignedInToken() != null;
    }

    /** {@inheritDoc} */
    @Override
    public String getToken() {
        AccessToken accessToken = getSignedInToken();
        if (accessToken != null) {
            return accessToken.getToken();
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void signOut() {
        clearUserInfo();
        LoginManager.getInstance().logOut();
    }

    private void clearUserInfo() {
        userName = null;
        userImageUrl = null;
    }

    /** {@inheritDoc} */
    @Override
    public String getUserName() {
        return userName;
    }

    /** {@inheritDoc} */
     @Override
    public String getUserImageUrl() {
        return userImageUrl;
    }

    /** {@inheritDoc} */
    public void reloadUserInfo() {
        clearUserInfo();
        if (!isUserSignedIn()) {
            return;
        }

        final Bundle parameters = new Bundle();
        parameters.putString("fields", "name,picture.type(large)");
        final GraphRequest graphRequest = new GraphRequest(AccessToken.getCurrentAccessToken(), "me");
        graphRequest.setParameters(parameters);
        GraphResponse response = graphRequest.executeAndWait();

        JSONObject json = response.getJSONObject();
        try {
            userName = json.getString("name");
            userImageUrl = json.getJSONObject("picture")
                    .getJSONObject("data")
                    .getString("url");

        } catch (final JSONException jsonException) {
            Log.e(LOG_TAG,
                    "Unable to get Facebook user info. " + jsonException.getMessage() + "\n" + response,
                    jsonException);
            // Nothing much we can do here.
        }
    }
}
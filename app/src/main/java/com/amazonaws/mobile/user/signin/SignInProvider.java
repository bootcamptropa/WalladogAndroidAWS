package com.amazonaws.mobile.user.signin;
//
// Copyright 2015 Amazon.com, Inc. or its affiliates (Amazon). All Rights Reserved.
//
// Code generated by AWS Mobile Hub. Amazon gives unlimited permission to 
// copy, distribute and modify it.
//
// Source code generated from template: aws-my-sample-app-android v0.4

import android.app.Activity;
import android.content.Intent;
import android.view.View;

import com.amazonaws.mobile.user.IdentityManager;
import com.amazonaws.mobile.user.IdentityProvider;

/**
 * Each sign-in provider implements this interface, in order to do sign-in button
 * initialization and to handle activity results that have been passed back to the
 * app, after a sign-in provider window has been dismissed.
 */
public interface SignInProvider extends IdentityProvider {
    /**
     * Call isRequestCodeOurs to determine if this provider should handle an activity result.
     * @param requestCode the requestCode from a previous call to onActivityResult.
     * @return true if the request code is from this provider, otherwise false.
     */
    boolean isRequestCodeOurs(int requestCode);

    /**
     * Call handleActivityResult to handle the activity result.
     * @param requestCode the request code.
     * @param resultCode the result code.
     * @param data the result intent.
     */
    void handleActivityResult(int requestCode, int resultCode, Intent data);

    /**
     * Initialize the sign-in button for the sign-in activity.
     * @param signInActivity the activity for sign-in.
     * @param buttonView the view for the sign-in button to initialize.
     * @param resultsHandler the resultsHandler for provider sign-in.
     */
    void initializeSignInButton(Activity signInActivity,
                                View buttonView,
                                IdentityManager.SignInResultsHandler resultsHandler);
}

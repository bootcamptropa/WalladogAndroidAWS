package com.mysampleapp.demo;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.amazonaws.mobile.AWSConfiguration;
import com.amazonaws.mobile.AWSMobileClient;
import com.mysampleapp.R;

import com.mysampleapp.SignInActivity;

public class UserFilesDemoFragment extends DemoFragmentBase {
    private static final String S3_PREFIX_PUBLIC = "public/";
    private static final String S3_PREFIX_PRIVATE = "private/";

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_demo_user_files, container, false);
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.button_userFiles_public)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        browse(AWSConfiguration.AMAZON_S3_USER_FILES_BUCKET, S3_PREFIX_PUBLIC);
                    }
                });
        view.findViewById(R.id.button_userFiles_private)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        final boolean isUserSignedIn = AWSMobileClient.defaultMobileClient()
                                .getIdentityManager()
                                .isUserSignedIn();
                        if (isUserSignedIn) {
                            final String identityId = AWSMobileClient.defaultMobileClient()
                                    .getIdentityManager()
                                    .getCredentialsProvider()
                                    .getCachedIdentityId();
                            browse(AWSConfiguration.AMAZON_S3_USER_FILES_BUCKET,
                                    S3_PREFIX_PRIVATE + identityId + "/");
                            return;
                        } else {
                            promptSignin();
                        }
                    }
                });
    }

    private void promptSignin() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity()).setTitle(
                R.string.main_fragment_title_user_files)
                .setNegativeButton(android.R.string.cancel, null);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                startActivity(new Intent(getActivity(), SignInActivity.class));
            }
        });
        builder.setMessage(R.string.user_files_demo_dialog_signin_message);
        builder.show();
    }

    private void browse(String bucket, String prefix) {
        UserFilesBrowserFragment fragment = new UserFilesBrowserFragment();
        Bundle args = new Bundle();
        args.putString(UserFilesBrowserFragment.BUNDLE_ARGS_S3_BUCKET, bucket);
        args.putString(UserFilesBrowserFragment.BUNDLE_ARGS_S3_PREFIX, prefix);
        fragment.setArguments(args);
        getActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_fragment_container, fragment)
                .commit();
    }
}

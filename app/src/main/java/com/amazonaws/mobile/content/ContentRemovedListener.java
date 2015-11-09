//
// Copyright 2015 Amazon.com, Inc. or its affiliates (Amazon). All Rights Reserved.
//
// Code generated by AWS Mobile Hub. Amazon gives unlimited permission to 
// copy, distribute and modify it.
//
// Source code generated from template: aws-my-sample-app-android v0.4
package com.amazonaws.mobile.content;

import java.io.File;

/** Listener for detecting content being removed due to cache pressure. */
public interface ContentRemovedListener {
    /**
     * Called on the main thread if an item is removed due to an item being added to the cache such
     * that the cache size is exceeded.
     * @param removedItem The removed file reference.
     */
    void onFileRemoved(File removedItem);

    /**
     * Called on the main thread if there is an error removing a file from the cache.  The Android
     * OS does not provide an exception or reason if this error occurs, so here we only give the
     * file.  This should not happen unless the file system permissions have been tampered with.
     *
     * @param file The file that failed removal by the OS.
     */
    void onRemoveError(File file);
}

package com.amazonaws.mobile.downloader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.amazonaws.mobile.downloader.query.DownloadState;
import com.amazonaws.mobile.downloader.request.DownloadAddRequest;
import com.amazonaws.mobile.downloader.service.DownloadService;
import com.amazonaws.mobile.util.ThreadUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Utility for downloading files via Http. */
public class HttpDownloadUtility extends BroadcastReceiver {
    private static final String LOG_TAG = HttpDownloadUtility.class.getSimpleName();
    private static final String DESCRIPTION = "Download initiated by HttpDownloadUtility.";
    private static final String INTENT_EXTRA_DOWNLOAD_OPERATION_ID = "DOWNLOAD_OPERATION_ID";
    private static final Object singletonLock = new Object();
    private static HttpDownloadUtility singleton;
    private final Context appContext;

    // The download intent actions we are interested in.
    private static final String[] downloadIntentActions = new String[] {
        DownloadService.ACTION_DOWNLOAD_ENQUEUED, DownloadService.ACTION_DOWNLOAD_ENQUEUE_FAILED,
        DownloadService.ACTION_DOWNLOAD_STARTED, DownloadService.ACTION_DOWNLOAD_PROGRESS,
        DownloadService.ACTION_DOWNLOAD_REMOVED, DownloadService.ACTION_DOWNLOAD_REMOVE_FAILED,
        DownloadService.ACTION_REQUEST_PAUSE_SUCCEEDED, DownloadService.ACTION_REQUEST_PAUSE_FAILED,
        DownloadService.ACTION_DOWNLOAD_RESUMED, DownloadService.ACTION_DOWNLOAD_RESUME_FAILED,
        DownloadService.ACTION_DOWNLOAD_COMPLETE, DownloadService.ACTION_DOWNLOAD_FAILED,
        DownloadService.ACTION_DOWNLOAD_PAUSED
    };

    private static final HashSet<String> addResponseIntentActions;
    private static final HashSet<String> removeResponseIntentActions;
    private static final HashSet<String> pauseResponseIntentActions;
    private static final HashSet<String> resumeResponseIntentActions;
    private static final HashSet<String> operationIntentActions;

    private static final HashSet<String> progressIntentActions;
    static {
        // ***********************************************************************
        // *****  The Following Actions are returned to the ResponseHandler,  *****
        // *****      the HttpDownloadListener is not concerned with them.       *****
        // ***********************************************************************
        addResponseIntentActions = new HashSet<>();
        addResponseIntentActions.add(DownloadService.ACTION_DOWNLOAD_ENQUEUED);
        addResponseIntentActions.add(DownloadService.ACTION_DOWNLOAD_ENQUEUE_FAILED);

        removeResponseIntentActions = new HashSet<>();
        removeResponseIntentActions.add(DownloadService.ACTION_DOWNLOAD_REMOVED);
        removeResponseIntentActions.add(DownloadService.ACTION_DOWNLOAD_REMOVE_FAILED);

        pauseResponseIntentActions = new HashSet<>();
        pauseResponseIntentActions.add(DownloadService.ACTION_REQUEST_PAUSE_SUCCEEDED);
        pauseResponseIntentActions.add(DownloadService.ACTION_REQUEST_PAUSE_FAILED);

        resumeResponseIntentActions = new HashSet<>();
        resumeResponseIntentActions.add(DownloadService.ACTION_DOWNLOAD_RESUMED);
        resumeResponseIntentActions.add(DownloadService.ACTION_DOWNLOAD_RESUME_FAILED);

        operationIntentActions = new HashSet<>();
        operationIntentActions.addAll(addResponseIntentActions);
        operationIntentActions.addAll(removeResponseIntentActions);
        operationIntentActions.addAll(pauseResponseIntentActions);
        operationIntentActions.addAll(resumeResponseIntentActions);

        // ***********************************************************************
        // ***** The Following Actions are returned to the HttpDownloadListener, *****
        // *****     the ResultsListener is not concerned with them.         *****
        // ***********************************************************************
        progressIntentActions = new HashSet<>();
        progressIntentActions.add(DownloadService.ACTION_DOWNLOAD_STARTED);
        progressIntentActions.add(DownloadService.ACTION_DOWNLOAD_PAUSED);
        progressIntentActions.add(DownloadService.ACTION_DOWNLOAD_PROGRESS);
        progressIntentActions.add(DownloadService.ACTION_DOWNLOAD_COMPLETE);
        progressIntentActions.add(DownloadService.ACTION_DOWNLOAD_FAILED);
    }

    /** Handlers for receiving results for request operations made to the DownloadService. */
    private final Map<String, ResponseHandler> resultsHandlerMap;

    /** For download requests, this map keeps track of the download listener that should
     * be used for the request, if the request succeeds. This is needed to remove the
     * listener if the download fails. */
    private final Map<String, HttpDownloadListener> requestToDownloadListenerMap;

    /** Listeners for receiving download status changes. */
    private final Map<String, Set<HttpDownloadListener>> downloadListeners;

    public static HttpDownloadUtility getInstance(final Context context) {
        synchronized (singletonLock) {
            if (singleton == null) {
                singleton = new HttpDownloadUtility(context.getApplicationContext());
            }
            return singleton;
        }
    }

    private HttpDownloadUtility(final Context context) {
        this.appContext = context.getApplicationContext();

        final IntentFilter downloadIntentFilter = new IntentFilter();
        for (final String action : downloadIntentActions) {
            downloadIntentFilter.addAction(action);
        }
        // Set up the local broadcast manager to handle receiving status updates.
        final LocalBroadcastManager localBroadcaster = LocalBroadcastManager.getInstance(context);
        // Register the receiver to listen for the download events we care about.
        localBroadcaster.registerReceiver(this, downloadIntentFilter);

        // Start the downloader.  The first time it starts  it will read its queue
        // and resume any downloads that had been running when the app was last killed.
        final Intent intent = new Intent(appContext, DownloadService.class);
        intent.setAction(DownloadService.ACTION_START_UP);
        context.startService(intent);

        resultsHandlerMap = new ConcurrentHashMap<>();
        requestToDownloadListenerMap = new ConcurrentHashMap<>();
        downloadListeners = new HashMap<>();
    }

    private long getDownloadId(final Intent intent) {
        return intent.getLongExtra(DownloadService.EXTRA_LONG_ID, DownloadService.INVALID_ID);
    }

    /**
     * Receiver for handling DownloadService responses and status updates.
     * @param context android context.
     * @param intent the intent received.
     */
    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String action = intent.getAction();

        // Check if we are receiving a progress update.
        if (progressIntentActions.contains(action)) {
            final String downloadLocation = intent.getStringExtra(DownloadService.EXTRA_LOCATION);
            if (downloadLocation == null) {
                Log.e(LOG_TAG, "No download location present for received action: " + action);
                return;
            }
            synchronized (downloadListeners) {
                // Look up the handlers registered for this download.
                final Set<HttpDownloadListener> listeners = downloadListeners.get(downloadLocation);
                if (listeners != null) {
                    // Make a copy to be used outside this thread.
                    final List<HttpDownloadListener> copiedListeners = new ArrayList<>(listeners);
                    ThreadUtils.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            handleProgressActions(intent, copiedListeners);
                            if (DownloadService.ACTION_DOWNLOAD_COMPLETE.equals(action) ||
                                DownloadService.ACTION_DOWNLOAD_FAILED.equals(action)) {
                                final String opId = intent.getStringExtra(INTENT_EXTRA_DOWNLOAD_OPERATION_ID);
                                removeDownloadListenerSetAtDownloadRequest(opId, downloadLocation);
                            }
                        }
                    });
                }
            }
            return;
        }

        // Check if we are receiving a DownloadService response.
        if (operationIntentActions.contains(action)) {
            // Get the operation request id and look up the response listener.
            final String opId = intent.getStringExtra(INTENT_EXTRA_DOWNLOAD_OPERATION_ID);
            if (opId == null) {
                // This may be due to a direct usage of DownloadService rather than using
                // HttpDownloadUtility.
                Log.d(LOG_TAG, "No request Id found. Ignoring response.");
                return;
            }
            // Remove the response handler, since it is no longer needed, now that the request
            // has been served.
            final ResponseHandler responseHandler = resultsHandlerMap.remove(opId);

            // Handle the responses on the UI thread.
            ThreadUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (addResponseIntentActions.contains(action)) {
                        handleResponseForAdd(intent, opId, responseHandler);
                    } else if (removeResponseIntentActions.contains(action)) {
                        handleResponseForRemoveRecord(intent, responseHandler);
                    } else if (pauseResponseIntentActions.contains(action)) {
                        handleResponseForPause(intent, responseHandler);
                    } else if (resumeResponseIntentActions.contains(action)) {
                        handleResponseForResume(intent, responseHandler);
                    }
                }
            });
            return;
        }

        Log.w(LOG_TAG, "Unexpected intent Received with action = " + action);
    }

    private void removeDownloadListenerSetAtDownloadRequest(final String requestId, final String downloadLocation) {
        // Remove the listener that was set in the download request..
        final HttpDownloadListener listener = requestToDownloadListenerMap.remove(requestId);
        if (listener != null) {
            removeDownloadListener(downloadLocation, listener);
        }
    }

    private void handleResponseForAdd(final Intent intent, final String requestId,
                                      final ResponseHandler handler) {
        if (DownloadService.ACTION_DOWNLOAD_ENQUEUE_FAILED.equals(intent.getAction())) {
            final String downloadUrl = intent.getStringExtra(DownloadService.EXTRA_URL);
            final String downloadLocation = intent.getStringExtra(DownloadService.EXTRA_LOCATION);
            removeDownloadListenerSetAtDownloadRequest(requestId, downloadLocation);
            final String errMsg = String.format("Download Failed. Could not add download url='%s' location='%s",
                downloadUrl, downloadLocation);
            Log.e(LOG_TAG, errMsg);
            if (handler != null) {
                handler.onError(errMsg);
            }
            return;
        }

        if (handler != null) {
            handler.onSuccess(getDownloadId(intent));
        }
    }

    private void handleResponseForRemoveRecord(final Intent intent, final ResponseHandler handler) {
        if (DownloadService.ACTION_DOWNLOAD_REMOVE_FAILED.equals(intent.getAction())) {
            final String errMsg = String.format("Remove Failed. No failed or complete download found with id(%d)",
                getDownloadId(intent));
            Log.e(LOG_TAG, errMsg);
            if (handler != null) {
                handler.onError(errMsg);
            }
            return;
        }
        if (handler != null) {
            handler.onSuccess(getDownloadId(intent));
        }
    }

    private void handleResponseForPause(final Intent intent, final ResponseHandler handler) {
        if (DownloadService.ACTION_REQUEST_PAUSE_FAILED.equals(intent.getAction())) {
            final String errMsg = String.format("Pause Failed. No running download found with id(%d).",
                getDownloadId(intent));
            Log.e(LOG_TAG, errMsg);
            if (handler != null) {
                handler.onError(errMsg);
            }
        }
        if (handler != null) {
            handler.onSuccess(getDownloadId(intent));
        }
    }

    private void handleResponseForResume(final Intent intent, final ResponseHandler handler) {
        if (DownloadService.ACTION_DOWNLOAD_RESUME_FAILED.equals(intent.getAction())) {
            final String errMsg = String.format("Resume Failed. No eligible download with id(%d).",
                getDownloadId(intent));
            Log.e(LOG_TAG, errMsg);
            if (handler != null) {
                handler.onError(errMsg);
            }
        }
        if (handler != null) {
            handler.onSuccess(getDownloadId(intent));
        }
    }

    private void handleProgressActions(final Intent intent, final List<HttpDownloadListener> listeners) {
        final long downloadId = getDownloadId(intent);
        switch(intent.getAction()) {
            case DownloadService.ACTION_DOWNLOAD_STARTED:
                for (HttpDownloadListener listener : listeners) {
                    listener.onStateChanged(getDownloadId(intent), DownloadState.IN_PROGRESS);
                }
                break;

            case DownloadService.ACTION_DOWNLOAD_PROGRESS:
                final long bytesTransferred = intent.getLongExtra(DownloadService.EXTRA_LONG_PROGRESS_CUMULATIVE, 0);
                final long bytesTotal = intent.getLongExtra(DownloadService.EXTRA_LONG_PROGRESS_TOTAL_SIZE, 0);
                for (HttpDownloadListener listener : listeners) {
                    listener.onProgressChanged(downloadId, bytesTransferred, bytesTotal);
                }
                break;

            case DownloadService.ACTION_DOWNLOAD_PAUSED:
                for (HttpDownloadListener listener : listeners) {
                    listener.onStateChanged(getDownloadId(intent), DownloadState.PAUSED);
                }
                break;

            case DownloadService.ACTION_DOWNLOAD_COMPLETE:
                for (HttpDownloadListener listener : listeners) {
                    listener.onStateChanged(downloadId, DownloadState.COMPLETE);
                }
                break;

            case DownloadService.ACTION_DOWNLOAD_FAILED:
                // Get the error code and error description from the intent.
                final String errorCode = intent.getStringExtra(DownloadService.EXTRA_DOWNLOAD_ERROR);
                final String errorDescription = intent.getStringExtra(DownloadService.EXTRA_COMPLETION_MESSAGE);
                Log.e(LOG_TAG, String.format("Download Failed. errorCode='%s' errorDescription='%s'",
                    errorCode, errorDescription));
                for (HttpDownloadListener listener : listeners) {
                    listener.onError(getDownloadId(intent), new HttpDownloadException(errorCode, errorDescription));
                }
                for (HttpDownloadListener listener : listeners) {
                    listener.onStateChanged(downloadId, DownloadState.FAILED);
                }
                break;
        }
    }

    /**
     * Register a response handler for a new request to DownloadService.
     *
     * @param handler the response handler.
     * @return a random request id.
     */
    private String generateRequestIdAndRegisterHandler(final ResponseHandler handler) {
        // Generate a random request id.
        final String randomRequestId = UUID.randomUUID().toString();

        if (handler != null) {
            // Set up the results listener for this request.
            resultsHandlerMap.put(randomRequestId, handler);
        }
        return randomRequestId;
    }

    /**
     * If starting the download fails the passed listener will receive the onError callback.
     * @param url the download url.
     * @param fileLocation the location to save the file as it is being downloaded.
     * @param downloadTitle An arbitrary string to associate a title with the download.
     * @param responseHandler listener to receive the result of this attempt to enqueue a download.
     * @param downloadListener Optional listener to receive status and progress updates for this
     *                         download. Null may be passed to not register a listener, otherwise
     *                         the listener will be registered with the HttpDownloadObserver that
     *                         will be returned.  The listener will automatically be cleared if
     *                         the adding the request fails.
     */
    public void download(final URI url, final String fileLocation, final String downloadTitle,
                  final ResponseHandler responseHandler, final HttpDownloadListener downloadListener) {

        final String randomRequestId = generateRequestIdAndRegisterHandler(responseHandler);
        // Keep track that this request add this listener, so it can be removed if the request fails.
        requestToDownloadListenerMap.put(randomRequestId, downloadListener);

        addDownloadListener(fileLocation, downloadListener);

        // Attempt to add the download. It will fail if the file is already downloading to the specified location.
        DownloadAddRequest downloadAddRequest = new DownloadAddRequest.Builder(url, fileLocation)
            .setForeground(false)
            .setSilent(false)
            .setWifiLock(true)
            .setDescription(DESCRIPTION)
            .setMobileNetworkProhibited(false)
            .setTitle(downloadTitle)
            .build();
        final Intent downloadIntent = downloadAddRequest.toIntent(appContext);
        downloadIntent.putExtra(INTENT_EXTRA_DOWNLOAD_OPERATION_ID, randomRequestId);
        // Intent extras are preserved in all response intents, we can add a request ID
        // to detect whether a subsequent error was directly related to this request.
        appContext.startService(downloadIntent);
    }

    // Set download listener based on file location.
    /* package */ boolean addDownloadListener(final String fileLocation, final HttpDownloadListener listener) {
        synchronized (downloadListeners) {
            Set<HttpDownloadListener> listenersForFile = downloadListeners.get(fileLocation);
            if (listenersForFile == null) {
                listenersForFile = new HashSet<HttpDownloadListener>();
                downloadListeners.put(fileLocation, listenersForFile);
            }
            // Add the download listener for this request
            listenersForFile.add(listener);
        }
        return true;
    }

    /* package */ boolean removeDownloadListener(final String fileLocation, final HttpDownloadListener listener) {
        synchronized (downloadListeners) {
            final Set<HttpDownloadListener> listenersForFile = downloadListeners.get(fileLocation);
            if (listenersForFile == null) {
                return false;
            }
            final boolean removed = listenersForFile.remove(listener);
            // Remove the list if it is empty.
            if (listenersForFile.size() == 0) {
                downloadListeners.remove(fileLocation);
            }
            return removed;
        }
    }

    private void sendUserRequestAction(final long id, final String action, final ResponseHandler responseHandler) {
        final String randomRequestId = generateRequestIdAndRegisterHandler(responseHandler);
        final Intent intent = new Intent(appContext, DownloadService.class);
        intent.setAction(action);
        intent.putExtra(DownloadService.EXTRA_LONG_ID, id);
        intent.putExtra(DownloadService.EXTRA_BOOL_BY_USER_REQUEST, true);
        intent.putExtra(INTENT_EXTRA_DOWNLOAD_OPERATION_ID, randomRequestId);
        appContext.startService(intent);
    }
    /**
     * Pauses a download task with the given id.  If this succeeds
     * subsequently the download state should change to PAUSED and
     * registered listeners will receive the state change.
     *
     * @param id A download id specifying the download to be paused.
     * @param responseHandler a handler to receive the result of this pause operation.
     */
    public void pause(final long id, final ResponseHandler responseHandler) {
        sendUserRequestAction(id, DownloadService.ACTION_REQUEST_PAUSE, responseHandler);
    }

    /**
     * Resumes the download task with the given id. This will fail for
     * downloads that are in the completed or failed state.
     *
     * @param id A download id specifying the download to be resumed
     * @param responseHandler a handler to receive the result of this pause operation.
     */
    public void resume(final long id, final ResponseHandler responseHandler) {
        sendUserRequestAction(id, DownloadService.ACTION_RESUME_DOWNLOAD, responseHandler);
    }

    /**
     * Cancels the download task with the given id. If the request
     * succeeds it will cause the download state to be changed to
     * failed and a registered HttpDownloadListener will receive the state
     * change.
     *
     * @param id A download id specifying the download to be canceled
     */
    public void cancel(final long id, final ResponseHandler responseHandler) {
        sendUserRequestAction(id, DownloadService.ACTION_CANCEL_DOWNLOAD, responseHandler);
    }

    /**
     * Removes the recorder for a finished download with the given id
     * from the download queue. The download status must be in the
     * completed or failed state.
     *
     * @param id A download id specifying the download to be deleted.
     */
    public void removeFinishedDownload(final long id, final ResponseHandler responseHandler) {
        sendUserRequestAction(id, DownloadService.ACTION_REMOVE_DOWNLOAD, responseHandler);
    }

    public HttpDownloadObserver getDownloadById(final long id) {
        return HttpDownloadObserver.getDownloadById(appContext, id);
    }

    public List<HttpDownloadObserver> getAllDownloadsByDownloadState(final DownloadState... downloadStates) {
        return HttpDownloadObserver.getAllDownloadsByDownloadState(appContext, downloadStates);
    }

    public List<HttpDownloadObserver> getAllDownloads() {
        // run a query over all downloads
        return HttpDownloadObserver.getAllDownloads(appContext);
    }
}

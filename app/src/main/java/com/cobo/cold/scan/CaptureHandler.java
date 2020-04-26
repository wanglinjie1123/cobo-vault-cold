/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cobo.cold.scan;

import android.os.Handler;
import android.os.Message;

import com.cobo.cold.scan.camera.CameraManager;
import com.cobo.cold.scan.common.Constant;
import com.cobo.cold.scan.decode.DecodeThread;
import com.google.zxing.Result;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Objects;

public final class CaptureHandler extends Handler {

    private final Host host;
    private final DecodeThread decodeThread;
    private State state;
    private final CameraManager cameraManager;

    private StringBuilder result = new StringBuilder();
    private ScannedData[] mScannedDatas;

    private enum State {
        PREVIEW, SUCCESS, DONE
    }


    public CaptureHandler(Host host, CameraManager cameraManager) {
        this.host = host;
        decodeThread = new DecodeThread(host);
        decodeThread.start();
        state = State.SUCCESS;

        // Start ourselves capturing previews and decoding.
        this.cameraManager = cameraManager;
        cameraManager.startPreview();
        restartPreviewAndDecode();
    }

    @Override
    public void handleMessage(Message message) {
        switch (message.what) {
            case Constant.RESTART_PREVIEW:
                restartPreviewAndDecode();
                break;
            case Constant.DECODE_SUCCEEDED:
                String text = ((Result) message.obj).getText();
                try {
                    JSONObject obj = new JSONObject(text);
                    ScannedData data = ScannedData.fromJson(obj);
                    if (mScannedDatas == null) {
                        mScannedDatas = new ScannedData[data.total];
                    }
                    if (mScannedDatas[data.index] == null) {
                        mScannedDatas[data.index] = data;
                    }
                    publishProgress();
                } catch (JSONException e) {
                    e.printStackTrace();
                    //not json return
                    state = State.SUCCESS;
                    host.handleDecode(text);
                    return;
                }

                if (Arrays.stream(mScannedDatas).anyMatch(Objects::isNull)) {
                    state = State.PREVIEW;
                    cameraManager.requestPreviewFrame(decodeThread.getHandler(),
                            Constant.DECODE);
                } else {
                    state = State.SUCCESS;
                    host.handleDecode(mScannedDatas);
                }

                break;
            case Constant.DECODE_FAILED:
                state = State.PREVIEW;
                cameraManager.requestPreviewFrame(decodeThread.getHandler(),
                        Constant.DECODE);
                break;
            case Constant.RETURN_SCAN_RESULT:
                break;
        }
    }

    private void publishProgress() {
        int scan;
        scan = (int) Arrays.stream(mScannedDatas).filter(Objects::nonNull).count();
        host.handleProgress(mScannedDatas.length, scan);
    }


    public void quitSynchronously() {
        state = State.DONE;
        cameraManager.stopPreview();
        Message quit = Message.obtain(decodeThread.getHandler(), Constant.QUIT);
        quit.sendToTarget();
        try {
            // Wait at most half a second; should be enough time, and onPause()
            // will timeout quickly
            decodeThread.join(500L);
        } catch (InterruptedException e) {
            // continue
        }

        // Be absolutely sure we don't send any queued up messages
        removeMessages(Constant.DECODE_SUCCEEDED);
        removeMessages(Constant.DECODE_FAILED);
    }

    public void restartPreviewAndDecode() {
        if (state == State.SUCCESS) {
            state = State.PREVIEW;
            mScannedDatas = null;
            cameraManager.requestPreviewFrame(decodeThread.getHandler(),
                    Constant.DECODE);
        }
    }

}
